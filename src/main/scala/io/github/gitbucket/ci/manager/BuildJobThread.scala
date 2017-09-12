package io.github.gitbucket.ci.manager

import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference

import gitbucket.core.model.CommitState
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.{AccountService, CommitStatusService, RepositoryService, SystemSettingsService}
import gitbucket.core.servlet.Database
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import io.github.gitbucket.ci.model.CIResult
import io.github.gitbucket.ci.service._
import io.github.gitbucket.ci.util.{CIUtils, JobStatus}
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory

import scala.sys.process.{Process, ProcessLogger}
import scala.util.control.ControlThrowable


class BuildJobThread(queue: LinkedBlockingQueue[BuildJob]) extends Thread
  with CommitStatusService with AccountService with RepositoryService with SimpleCIService with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[BuildJobThread])

  val cancelled = new AtomicReference[Boolean](false)
  val runningProcess = new AtomicReference[Option[Process]](None)
  val runningJob = new AtomicReference[Option[BuildJob]](None)
  val sb = new StringBuffer()

  override def run(): Unit = {
    logger.info("Start BuildJobThread-" + this.getId)
    try {
      while(true){
        runBuild(queue.take())
      }
    } catch {
      case _: InterruptedException => cancel()
    }
    logger.info("Stop BuildJobThread-" + this.getId)
  }

  private def initState(job: Option[BuildJob]): Unit = {
    cancelled.set(false)
    runningProcess.set(None)
    runningJob.set(job)
    sb.setLength(0)
  }

  private def runBuild(job: BuildJob): Unit = {
    val startTime = new java.util.Date()
    initState(Some(job.copy(startTime = Some(startTime))))

    val targetUrl = loadSystemSettings().baseUrl.map { baseUrl =>
      s"${baseUrl}/${job.userName}/${job.repositoryName}/build/${job.buildNumber}"
    }

    Database() withTransaction { implicit session =>
      createCommitStatus(
        userName       = job.userName,
        repositoryName = job.repositoryName,
        sha            = job.sha,
        context        = CIUtils.ContextName,
        state          = CommitState.PENDING,
        targetUrl      = targetUrl,
        description    = None,
        now            = new java.util.Date(),
        creator        = job.buildAuthor // TODO??
      )
    }

    try {
      val exitValue = try {
        val buildDir = CIUtils.getBuildDir(job.userName, job.repositoryName, job.buildNumber)
        val dir = new File(buildDir, "workspace")
        if (dir.exists()) {
          FileUtils.deleteDirectory(dir)
        }

        if(cancelled.get() == true){
          throw new BuildJobCancelException()
        }

        sb.append(s"git clone ${job.userName}/${job.repositoryName}\n")

        // git clone
        using(Git.cloneRepository()
          .setURI(getRepositoryDir(job.userName, job.repositoryName).toURI.toString)
          .setDirectory(dir).call()) { git =>

          if(cancelled.get() == true){
            throw new BuildJobCancelException()
          }

          sb.append(s"git checkout ${job.sha}\n")

          // git checkout
          git.checkout().setName(job.sha).call()

          if(cancelled.get() == true){
            throw new BuildJobCancelException()
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
          logger.error(s"${job.userName}/${job.repositoryName} #${job.buildNumber}", e)
          -1
        }
        case _: ControlThrowable =>
          -1
      }

      val endTime = new java.util.Date()

      // Create or update commit status
      Database() withTransaction { implicit session =>
        saveCIResult(
          CIResult(
            userName            = job.userName,
            repositoryName      = job.repositoryName,
            buildUserName       = job.buildUserName,
            buildRepositoryName = job.buildRepositoryName,
            buildNumber         = job.buildNumber,
            buildBranch         = job.buildBranch,
            sha                 = job.sha,
            commitMessage       = job.commitMessage,
            commitUserName      = job.commitUserName,
            commitMailAddress   = job.commitMailAddress,
            pullRequestId       = job.pullRequestId,
            startTime           = startTime,
            endTime             = endTime,
            status              = if(exitValue == 0) JobStatus.Success else JobStatus.Failure,
            buildAuthor         = job.buildAuthor.userName
          ),
          sb.toString
        )

        createCommitStatus(
          userName       = job.userName,
          repositoryName = job.repositoryName,
          sha            = job.sha,
          context        = CIUtils.ContextName,
          state          = if(exitValue == 0) CommitState.SUCCESS else CommitState.FAILURE,
          targetUrl      = targetUrl,
          description    = None,
          now            = endTime,
          creator        = job.buildAuthor // TODO??
        )
      }

      logger.info("Build number: " + job.buildNumber)
      logger.info("Total: " + (endTime.getTime - startTime.getTime) + " msec")
      logger.info("Finish build with exit code: " + exitValue)

    } finally {
      initState(None)
    }
  }

  def cancel(): Unit = {
    cancelled.set(true)
    runningProcess.get.foreach(_.destroy())
  }
}

/**
 * Used to abort build job immediately in BuildJobThread.
 */
private class BuildJobCancelException extends ControlThrowable

/**
 * Used to capture output of the build process.
 */
private class BuildProcessLogger(sb: StringBuffer) extends ProcessLogger {

  override def err(s: => String): Unit = {
    sb.append(s + "\n")
  }

  override def out(s: => String): Unit = {
    sb.append(s + "\n")
  }

  override def buffer[T](f: => T): T = ???

}
