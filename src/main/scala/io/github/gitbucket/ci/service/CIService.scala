package io.github.gitbucket.ci.service

import gitbucket.core.model.Account
import io.github.gitbucket.ci.manager.BuildManager
import io.github.gitbucket.ci.model._
import io.github.gitbucket.ci.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.{AccountService, RepositoryService}
import io.github.gitbucket.ci.util.CIUtils
import org.apache.commons.io.FileUtils

case class BuildJob(
  userName: String,
  repositoryName: String,
  buildUserName: String,
  buildRepositoryName: String,
  buildNumber: Int,
  buildBranch: String,
  sha: String,
  commitMessage: String,
  commitUserName: String,
  commitMailAddress: String,
  pullRequestId: Option[Int],
  startTime: Option[java.util.Date],
  buildAuthor: Account,
  config: CIConfig
)

object BuildNumberGenerator extends CIService with AccountService with RepositoryService {

  private val map = new scala.collection.mutable.HashMap[(String, String), Int]()

  def generateBuildNumber(userName: String, repositoryName: String)(implicit s: Session): Int = synchronized {
    val buildNumber = map.get((userName, repositoryName)).map(_ + 1).getOrElse {
      (getCIResults(userName, repositoryName).map(_.buildNumber) match {
        case Nil => 0
        case seq => seq.max
      }) + 1
    }

    map.put((userName, repositoryName), buildNumber)

    buildNumber
  }

}

trait CIService { self: AccountService with RepositoryService =>

  def saveCIConfig(userName: String, repositoryName: String, config: Option[CIConfig])(implicit s: Session): Unit = {
    CIConfigs.filter { t =>
      (t.userName === userName.bind) && (t.repositoryName === repositoryName.bind)
    }.delete

    config.foreach { config => CIConfigs += config }
  }

  def loadCIConfig(userName: String, repositoryName: String)(implicit s: Session): Option[CIConfig] = {
    CIConfigs.filter { t =>
      (t.userName === userName.bind) && (t.repositoryName === repositoryName.bind)
    }.firstOption
  }

  def getCIResults(userName: String, repositoryName: String)(implicit s: Session): Seq[CIResult] = {
    CIResults.filter { t =>
      (t.userName === userName.bind) && (t.repositoryName === repositoryName.bind)
    }.list
  }

  def getCIResult(userName: String, repositoryName: String, buildNumber: Int)(implicit s: Session): Option[CIResult] = {
    CIResults.filter { t =>
      (t.userName === userName.bind) && (t.repositoryName === repositoryName.bind) && (t.buildNumber === buildNumber.bind)
    }.firstOption
  }

  def getLatestCIStatus(userName: String, repositoryName: String, branchName: String)(implicit s: Session): String = {
    import scala.collection.JavaConverters._
    if (BuildManager.queue.iterator.asScala.exists{ job =>
      job.userName == userName && job.repositoryName == repositoryName && job.buildBranch == branchName
    }){
      "waiting"
    }else if ( BuildManager.threads.exists{ thread =>
      thread.runningJob.get.exists { job =>
        job.userName == userName && job.repositoryName == repositoryName && job.buildBranch == branchName
      }
    }){
      "running"
    }else{
      CIResults.filter { t =>
        (t.userName === userName.bind) && (t.repositoryName === repositoryName.bind) && (t.buildBranch === branchName.bind)
      }.sortBy(_.buildNumber.desc).map{_.status}.firstOption.getOrElse("uknown")
    }
  }

  def runBuild(userName: String, repositoryName: String, buildUserName: String, buildRepositoryName: String,
               buildBranch: String, sha: String, commitMessage: String, commitUserName: String, commitMailAddress: String,
               pullRequestId: Option[Int], buildAuthor: Account, config: CIConfig)(implicit s: Session): Unit = {
    BuildManager.queueBuildJob(BuildJob(
      userName            = userName,
      repositoryName      = repositoryName,
      buildUserName       = buildUserName,
      buildRepositoryName = buildRepositoryName,
      buildNumber         = BuildNumberGenerator.generateBuildNumber(userName, repositoryName),
      buildBranch         = buildBranch,
      sha                 = sha,
      commitMessage       = commitMessage,
      commitUserName      = commitUserName,
      commitMailAddress   = commitMailAddress,
      pullRequestId       = pullRequestId,
      startTime           = None,
      buildAuthor         = buildAuthor,
      config              = config
    ))
  }

  def cancelBuild(userName: String, repositoryName: String, buildNumber: Int): Unit = {
    BuildManager.threads.find { thread =>
      thread.runningJob.get.exists { job =>
        job.userName == userName && job.repositoryName == repositoryName && job.buildNumber == buildNumber
      }
    }.foreach { thread =>
      thread.cancel()
    }
  }

  def getRunningJobs(userName: String, repositoryName: String): Seq[(BuildJob, StringBuffer)] = {
    BuildManager.threads
      .map { thread => (thread, thread.runningJob.get) }
      .collect { case (thread, Some(job)) if(job.userName == userName && job.repositoryName == repositoryName) =>
        (job, thread.sb)
      }
  }

  def getQueuedJobs(userName: String, repositoryName: String): Seq[BuildJob] = {
    import scala.collection.JavaConverters._
    BuildManager.queue.iterator.asScala.filter { job =>
      job.userName == userName && job.repositoryName == repositoryName
    }.toSeq
  }

  def saveCIResult(result: CIResult, output: String)(implicit s: Session): Unit = {
    // Delete older results
    val results = getCIResults(result.userName, result.repositoryName).sortBy(_.buildNumber)
    if (results.length >= BuildManager.MaxBuildHistoryPerProject){
      results.take(results.length - BuildManager.MaxBuildHistoryPerProject + 1).foreach { result =>
        // Delete from database
        CIResults.filter { t =>
          (t.userName       === result.userName.bind) &&
          (t.repositoryName === result.repositoryName.bind) &&
          (t.buildNumber    === result.buildNumber.bind)
        }.delete

        // Delete files
        val buildDir = CIUtils.getBuildDir(result.userName, result.repositoryName, result.buildNumber)
        if(buildDir.exists){
          FileUtils.deleteQuietly(buildDir)
        }
      }
    }

    // Insert new result
    CIResults += result

    // Save result output as file
    val buildDir = CIUtils.getBuildDir(result.userName, result.repositoryName, result.buildNumber)
    if(!buildDir.exists){
      buildDir.mkdirs()
    }
    FileUtils.write(new java.io.File(buildDir, "output"), output, "UTF-8")
  }

  def getCIResultOutput(result: CIResult): String = {
    val buildDir = CIUtils.getBuildDir(result.userName, result.repositoryName, result.buildNumber)
    val file = new java.io.File(buildDir, "output")
    if(file.exists){
      FileUtils.readFileToString(file, "UTF-8")
    } else ""
  }

//  @deprecated("Use RepositoryService#hasOwnerRole instead.", "1.0.0")
//  def hasOwnerRole(owner: String, repository: String, loginAccount: Option[Account])(implicit s: Session): Boolean = {
//    loginAccount match {
//      case Some(a) if(a.isAdmin) => true
//      case Some(a) if(a.userName == owner) => true
//      case Some(a) if(getGroupMembers(owner).exists(_.userName == a.userName)) => true
//      case Some(a) if(getCollaboratorUserNames(owner, repository, Seq(Role.ADMIN)).contains(a.userName)) => true
//      case _ => false
//    }
//  }

}
