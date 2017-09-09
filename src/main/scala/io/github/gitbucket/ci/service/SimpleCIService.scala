package io.github.gitbucket.ci.service

import java.util.concurrent._
import scala.sys.process._

object BuildManager {

  val MaxParallelBuilds = 4
  val MaxBuildsPerProject = 10

  val buildSettings = new ConcurrentHashMap[(String, String), BuildSetting]()
  val buildResults = new ConcurrentHashMap[(String, String), Seq[BuildResult]]()

  val queue = new LinkedBlockingQueue[BuildJob]()
  val threads = Range(0, MaxParallelBuilds).map { _ => new BuildJobThread(queue) }

  def queueBuildJob(job: BuildJob): Unit = {
    queue.add(job)
  }

  def startBuildManager(): Unit = {
    threads.foreach { thread =>
      thread.start()
    }
  }

}

case class BuildJob(userName: String, repositoryName: String, buildNumber: Long, sha: String, startTime: Option[Long], setting: BuildSetting)

trait SimpleCIService {

  def saveBuildSetting(userName: String, repositoryName: String, setting: Option[BuildSetting]): Unit = {
    setting match {
      case Some(setting) => BuildManager.buildSettings.put((userName, repositoryName), setting)
      case None => BuildManager.buildSettings.remove((userName, repositoryName))
    }
  }

  def loadBuildSetting(userName: String, repositoryName: String): Option[BuildSetting] = {
    Option(BuildManager.buildSettings.get((userName, repositoryName)))
  }

  def getBuildResults(userName: String, repositoryName: String): Seq[BuildResult] = {
    Option(BuildManager.buildResults.get((userName, repositoryName))).getOrElse(Nil)
  }

  def getBuildResult(userName: String, repositoryName: String, buildNumber: Long): Option[BuildResult] = {
    getBuildResults(userName, repositoryName).find(_.buildNumber == buildNumber)
  }

  def runBuild(userName: String, repositoryName: String, sha: String, setting: BuildSetting): Unit = {
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

case class BuildSetting(userName: String, repositoryName: String, script: String)

case class BuildResult(userName: String, repositoryName: String, sha: String,
  buildNumber: Long, success: Boolean, start: Long, end: Long, output: String)