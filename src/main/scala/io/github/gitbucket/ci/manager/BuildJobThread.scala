package io.github.gitbucket.ci.manager

import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference

import gitbucket.core.model.CommitState
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.{AccountService, CommitStatusService, RepositoryService}
import gitbucket.core.servlet.Database
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import io.github.gitbucket.ci.model.CIResult
import io.github.gitbucket.ci.service._
import io.github.gitbucket.ci.util.{CIUtils, JobStatus}
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.jgit.api.Git

import scala.sys.process.{Process, ProcessLogger}
import scala.util.control.ControlThrowable


class BuildJobThread(queue: LinkedBlockingQueue[BuildJob]) extends Thread
  with CommitStatusService with AccountService with RepositoryService with SimpleCIService {

  val killed = new AtomicReference[Boolean](false)
  val runningProcess = new AtomicReference[Option[Process]](None)
  val runningJob = new AtomicReference[Option[BuildJob]]()
  val sb = new StringBuffer()

  override def run(): Unit = {
    try {
      while(true){
        runBuild(queue.take())
      }
    } catch {
      case _: InterruptedException => ()
    }
  }

  private def initState(job: Option[BuildJob]): Unit = {
    killed.set(false)
    runningProcess.set(None)
    runningJob.set(job)
    sb.setLength(0)
  }

  private def runBuild(job: BuildJob): Unit = {
    val startTime = new java.util.Date()
    initState(Some(job.copy(startTime = Some(startTime))))

    try {
      val exitValue = try {
        val buildDir = CIUtils.getBuildDir(job.userName, job.repositoryName, job.buildNumber)
        val dir = new File(buildDir, "workspace")
        if (dir.exists()) {
          FileUtils.deleteDirectory(dir)
        }

        if(killed.get() == true){
          throw new BuildJobKillException()
        }

        sb.append(s"git clone ${job.userName}/${job.repositoryName}\n")

        // git clone
        using(Git.cloneRepository()
          .setURI(getRepositoryDir(job.userName, job.repositoryName).toURI.toString)
          .setDirectory(dir).call()) { git =>

          if(killed.get() == true){
            throw new BuildJobKillException()
          }

          sb.append(s"git checkout ${job.sha}\n")

          // git checkout
          git.checkout().setName(job.sha).call()

          if(killed.get() == true){
            throw new BuildJobKillException()
          }

          // run script
          // TODO This works on only Linux or Mac...
          val buildFile = new File(buildDir, "build.sh")
          FileUtils.write(buildFile, "#!/bin/sh\n" + job.config.buildScript.replaceAll("\r\n", "\n"), "UTF-8")
          buildFile.setExecutable(true)

          val process = Process("../build.sh", dir).run(new BuildProcessLogger(sb))
          runningProcess.set(Some(process))

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
        case e: ControlThrowable =>
          -1
      }

      val endTime = new java.util.Date()

      // Create or update commit status
      Database() withTransaction { implicit session =>
        saveCIResult(
          CIResult(
            userName       = job.userName,
            repositoryName = job.repositoryName,
            buildNumber    = job.buildNumber,
            sha            = job.sha,
            startTime      = startTime,
            endTime        = endTime,
            status         = if(exitValue == 0) JobStatus.Success else JobStatus.Failure
          ),
          sb.toString
        )

        createCommitStatus(
          userName       = job.userName,
          repositoryName = job.repositoryName,
          sha            = job.sha,
          context        = CIUtils.ContextName,
          state          = if(exitValue == 0) CommitState.SUCCESS else CommitState.FAILURE,
          targetUrl      = None,
          description    = None,
          now            = endTime,
          creator        = job.creator
        )
      }

      println("Build number: " + job.buildNumber)
      println("Total: " + (endTime.getTime - startTime.getTime) + "msec")
      println("Finish build with exit code: " + exitValue)

    } finally {
      initState(None)
    }
  }

  def kill(): Unit = {
    killed.set(true)
    runningProcess.get.foreach(_.destroy())
  }
}

// Used to abort build job immediately in BuildJobThread
private class BuildJobKillException extends ControlThrowable

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
