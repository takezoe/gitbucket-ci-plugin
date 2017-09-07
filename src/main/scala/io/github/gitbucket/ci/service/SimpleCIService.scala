package io.github.gitbucket.ci.service

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.jgit.api.Git

import scala.sys.process._

object BuildManager {

  val MaxBuildPerProject = 10
  val buildSettings = new ConcurrentHashMap[(String, String), BuildSetting]()
  val buildResults = new ConcurrentHashMap[(String, String), Seq[BuildResult]]()
  val runningJob = new AtomicReference[Option[BuildJob]](None)

  val queue = new LinkedBlockingQueue[BuildJob]()

  def queueBuildJob(job: BuildJob): Unit = {
    queue.add(job)
  }

  def startBuildManager(): Unit = {
    // TODO interrupt this thread when GitBucket is shutdown
    new Thread(){
      override def run(): Unit = {
        println("** Start BuildManager **")
        while(true){
          runBuild(queue.take())
        }
        println("** Exit BuildManager **")
      }
    }.start()
  }

  // TODO Manage running job status
  private def runBuild(job: BuildJob): Unit = {
    runningJob.set(Some(job))

    val startTime = System.currentTimeMillis
    val sb = new StringBuilder()

    val exitValue = try {
      val dir = new File(s"/tmp/${job.userName}-${job.repositoryName}-${job.buildNumber}")
      if (dir.exists()) {
        FileUtils.deleteDirectory(dir)
      }

      using(Git.cloneRepository()
        .setURI(getRepositoryDir(job.userName, job.repositoryName).toURI.toString)
        .setDirectory(dir).call()) { git =>

        git.checkout().setName(job.sha).call()

        val process = Process(job.setting.script, dir).run(new BuildProcessLogger(sb))

        while (process.isAlive()) {
          Thread.sleep(1000)
        }

        process.exitValue()
      }
    } catch {
      case e: Exception => {
        sb.append(ExceptionUtils.getStackTrace(e))
        e.printStackTrace()
        -1
      }
    } finally {
      runningJob.set(None)
    }

    val endTime = System.currentTimeMillis

    val result = BuildResult(job.userName, job.repositoryName, job.sha, job.buildNumber, exitValue == 0, startTime, endTime, sb.toString)

    val results = Option(buildResults.get((job.userName, job.repositoryName))).getOrElse(Nil)
    BuildManager.buildResults.put((job.userName, job.repositoryName),
      (if (results.length >= MaxBuildPerProject) results.tail else results) :+ result
    )

    println("Build number: " + job.buildNumber)
    println("Total: " + (endTime - startTime) + "msec")
    println("Finish build with exit code: " + exitValue)
  }

}

case class BuildJob(userName: String, repositoryName: String, buildNumber: Long, sha: String, setting: BuildSetting)

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

    BuildManager.queueBuildJob(BuildJob(userName, repositoryName, buildNumber, sha, setting))
  }

  def getRunningJob(userName: String, repositoryName: String): Option[BuildJob] = {
    BuildManager.runningJob.get.filter { job =>
      job.userName == userName && job.repositoryName == repositoryName
    }
  }

  def getQueuedJobs(userName: String, repositoryName: String): Seq[BuildJob] = {
    import scala.collection.JavaConverters._
    BuildManager.queue.iterator.asScala.filter { job =>
      job.userName == userName && job.repositoryName == repositoryName
    }.toSeq
  }
}

class BuildProcessLogger(sb: StringBuilder) extends ProcessLogger {

  override def err(s: => String): Unit = {
    sb.append(s + "\n")
    println(s) // TODO Debug
  }

  override def out(s: => String): Unit = {
    sb.append(s + "\n")
    println(s) // TODO Debug
  }

  override def buffer[T](f: => T): T = ???

}

case class BuildSetting(userName: String, repositoryName: String, script: String)

case class BuildResult(userName: String, repositoryName: String, sha: String,
  buildNumber: Long, success: Boolean, start: Long, end: Long, output: String)