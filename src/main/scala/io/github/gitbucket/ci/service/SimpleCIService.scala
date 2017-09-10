package io.github.gitbucket.ci.service

import io.github.gitbucket.ci.manager.BuildManager
import io.github.gitbucket.ci.model._
import io.github.gitbucket.ci.model.Profile._
import gitbucket.core.model.Profile.profile.blockingApi._
import scala.sys.process._

case class BuildJob(userName: String, repositoryName: String, buildNumber: Long, sha: String, startTime: Option[Long], config: CIConfig)

trait SimpleCIService {

  def saveCIConfig(userName: String, repositoryName: String, config: Option[CIConfig])(implicit s: Session): Unit = {
    CIConfigs.filter(t => (t.userName === userName.bind) && (t.repositoryName === repositoryName.bind)).delete
    config.foreach { config => CIConfigs += config }
  }

  def loadCIConfig(userName: String, repositoryName: String)(implicit s: Session): Option[CIConfig] = {
    CIConfigs.filter(t => (t.userName === userName.bind) && (t.repositoryName === repositoryName.bind)).firstOption
  }

  def getBuildResults(userName: String, repositoryName: String): Seq[BuildResult] = {
    Option(BuildManager.buildResults.get((userName, repositoryName))).getOrElse(Nil)
  }

  def getBuildResult(userName: String, repositoryName: String, buildNumber: Long): Option[BuildResult] = {
    getBuildResults(userName, repositoryName).find(_.buildNumber == buildNumber)
  }

  def runBuild(userName: String, repositoryName: String, sha: String, setting: CIConfig): Unit = {
    val results = Option(BuildManager.buildResults.get((userName, repositoryName))).getOrElse(Nil)
    val buildNumber = (results.map(_.buildNumber) match {
      case Nil => 0
      case seq => seq.max
    }) + 1

    BuildManager.queueBuildJob(BuildJob(userName, repositoryName, buildNumber, sha, None, setting))
  }

  def killBuild(userName: String, repositoryName: String, buildNumber: Long): Unit = {
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

//case class BuildSetting(userName: String, repositoryName: String, script: String)

case class BuildResult(userName: String, repositoryName: String, sha: String,
  buildNumber: Long, success: Boolean, start: Long, end: Long, output: String)