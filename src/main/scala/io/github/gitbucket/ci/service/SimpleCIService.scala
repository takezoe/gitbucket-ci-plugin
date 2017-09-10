package io.github.gitbucket.ci.service

import io.github.gitbucket.ci.manager.BuildManager
import io.github.gitbucket.ci.model._
import io.github.gitbucket.ci.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import scala.sys.process._

case class BuildJob(userName: String, repositoryName: String, buildNumber: Int, sha: String, startTime: Option[Long], config: CIConfig)

trait SimpleCIService {

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

  def runBuild(userName: String, repositoryName: String, sha: String, config: CIConfig)(implicit s: Session): Unit = {
    val buildNumber = (getCIResults(userName, repositoryName).map(_.buildNumber) match {
      case Nil => 0
      case seq => seq.max
    }) + 1

    BuildManager.queueBuildJob(BuildJob(userName, repositoryName, buildNumber, sha, None, config))
  }

  def killBuild(userName: String, repositoryName: String, buildNumber: Int): Unit = {
    BuildManager.threads.find { thread =>
      thread.runningJob.get.exists { job =>
        job.userName == userName && job.repositoryName == repositoryName && job.buildNumber == buildNumber
      }
    }.foreach { thread =>
      thread.kill()
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
    CIResults += result
  }

  def getCIResultOutput(result: CIResult): String = {
    ""
  }
}

class BuildProcessLogger(sb: StringBuffer) extends ProcessLogger {

  override def err(s: => String): Unit = {
    sb.append(s + "\n")
    println(s)
  }

  override def out(s: => String): Unit = {
    sb.append(s + "\n")
    println(s)
  }

  override def buffer[T](f: => T): T = ???

}
