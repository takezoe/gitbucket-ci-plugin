package io.github.gitbucket.ci.manager

import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import gitbucket.core.model.CommitState
import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.service.{AccountService, CommitStatusService, RepositoryService, SystemSettingsService}
import gitbucket.core.servlet.Database
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.Mailer
import io.github.gitbucket.ci.model.CIResult
import io.github.gitbucket.ci.service._
import io.github.gitbucket.ci.util.{CIUtils, JobStatus}
import io.github.gitbucket.markedj.{Marked, Options}
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory

import scala.sys.process.{Process, ProcessLogger}
import scala.util.control.ControlThrowable
import scala.util.Using


class BuildJobThread(queue: LinkedBlockingQueue[BuildJob], threads: LinkedBlockingQueue[BuildJobThread]) extends Thread
  with CommitStatusService with AccountService with RepositoryService with CIService with SystemSettingsService {

  private val logger = LoggerFactory.getLogger(classOf[BuildJobThread])

  val cancelled = new AtomicReference[Boolean](false)
  val runningProcess = new AtomicReference[Option[Process]](None)
  val runningJob = new AtomicReference[Option[BuildJob]](None)
  val sb = new StringBuffer()
  val continue = new AtomicBoolean(true)

  override def run(): Unit = {
    logger.info("Start BuildJobThread-" + this.getId)
    try {
      while(continue.get()){
        runBuild(queue.take())
      }
    } catch {
      case _: InterruptedException => cancel()
    }
    threads.remove(this)
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

    val settings = loadSystemSettings()
    val targetUrl = settings.baseUrl.map { baseUrl =>
      s"${baseUrl}/${job.userName}/${job.repositoryName}/build/${job.buildNumber}"
    }

    val systemCIConfig = Database() withTransaction { implicit session =>
      createCommitStatus(
        userName       = job.userName,
        repositoryName = job.repositoryName,
        sha            = job.sha,
        context        = CIUtils.ContextName,
        state          = CommitState.PENDING,
        targetUrl      = targetUrl,
        description    = None,
        now            = new java.util.Date(),
        creator        = job.buildAuthor // TODO right??
      )
      loadCISystemConfig()
    }
    val dockerCommand = systemCIConfig.dockerCommand.getOrElse("docker")
    val dockerComposeCommand = systemCIConfig.dockerComposeCommand.getOrElse("docker-compose")

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

        sb.append(s"git clone ${job.buildUserName}/${job.buildRepositoryName}\n")

        // git clone
        Using.resource(Git.cloneRepository()
          .setURI(getRepositoryDir(job.buildUserName, job.buildRepositoryName).toURI.toString)
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

          job.config.buildType match {
            case "script" =>
              runScriptJob(job, buildDir, dir)
            case "file" =>
              runFileJob(job, buildDir, dir)
            case "docker" =>
              if(systemCIConfig.enableDocker){
                runDockerJob(job, buildDir, dir, dockerCommand)
              }else{
                throw new RuntimeException("Docker job is disabled.")
              }
            case "docker-compose" =>
              if(systemCIConfig.enableDockerCompose){
                runDockerComposeJob(job, buildDir, dir, dockerComposeCommand)
              }else{
                throw new RuntimeException("Docker compose job is disabled.")
              }
            case _ =>
              throw new MatchError("Invalid build type: " + job.config.buildType)
          }
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
            queuedTime          = job.queuedTime,
            startTime           = startTime,
            endTime             = endTime,
            exitCode            = exitValue,
            status              = if(exitValue == 0) JobStatus.Success else JobStatus.Failure,
            buildAuthor         = job.buildAuthor.userName,
            buildScript         = job.config.buildScript
          ),
          sb.toString,
          loadCISystemConfig()
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
          creator        = job.buildAuthor // TODO right??
        )

        // Send email
        if(job.config.notification && settings.useSMTP && exitValue != 0){
          val committer = getAccountByMailAddress(job.commitMailAddress, false).map(_.mailAddress).toSeq
          val collaborators = getCollaboratorUserNames(job.userName, job.repositoryName).flatMap { userName =>
            getAccountByUserName(userName).map(_.mailAddress)
          }

          val subject = createMailSubject(job)
          val markdown = createMailContent(job, settings, targetUrl)
          val html = markdown2html(markdown)

          val mailer = new Mailer(settings)

          val recipients = (committer ++ collaborators).distinct
          recipients.foreach { to =>
            mailer.send(to, subject, markdown, Some(html))
          }
        }
      }

      logger.info("Build number: " + job.buildNumber)
      logger.info("Total: " + (endTime.getTime - startTime.getTime) + " msec")
      logger.info("Finish build with exit code: " + exitValue)

    } finally {
      initState(None)
    }
  }

  private def runProcess(job: BuildJob, buildDir: File, workspaceDir: File, command: String): Int = {
    val process = Process(command, workspaceDir,
      "CI" -> "true",
      "HOME" -> buildDir.getAbsolutePath,
      "CI_BUILD_DIR" -> buildDir.getAbsolutePath,
      "CI_BUILD_NUMBER" -> job.buildNumber.toString,
      "CI_BUILD_BRANCH" -> job.buildBranch,
      "CI_COMMIT_ID" -> job.sha,
      "CI_COMMIT_MESSAGE" -> job.commitMessage,
      "CI_REPO_SLUG" -> s"${job.userName}/${job.repositoryName}",
      "CI_PULL_REQUEST" -> job.pullRequestId.map(_.toString).getOrElse("false"),
      "CI_PULL_REQUEST_SLUG" -> (if (job.pullRequestId.isDefined) s"${job.buildUserName}/${job.buildRepositoryName}" else "")
    ).run(new BuildProcessLogger(sb))
    runningProcess.set(Some(process))

    while (process.isAlive()) {
      Thread.sleep(1000)
    }

    val exitValue = process.exitValue()
    if(exitValue != 0){
      sb.append(s"EXIT CODE: ${exitValue}\n")
    }
    exitValue
  }

  private def runScriptJob(job: BuildJob, buildDir: File, workspaceDir: File): Int = {
    // run script
    val command = prepareBuildScript(buildDir, job.config.buildScript)
    runProcess(job, buildDir, workspaceDir, command)
  }

  private def runFileJob(job: BuildJob, buildDir: File, workspaceDir: File): Int = {
    // run script
    val command = prepareBuildFile(buildDir, job.config.buildScript)
    runProcess(job, buildDir, workspaceDir, command)
  }

  private def runDockerJob(job: BuildJob, buildDir: File, workspaceDir: File, dockerCommand: String): Int = {
    val tagName = s"gitbucket-ci/${job.buildUserName}/${job.buildRepositoryName}:${job.sha.substring(0, 7)}"
    val containerName = s"${job.buildUserName}-${job.buildRepositoryName}-${job.buildNumber}"
    val dockerfile = if(job.config.buildScript.nonEmpty){job.config.buildScript}else{"Dockerfile"}

    val buildContainerCommand = s"${dockerCommand} build -f ${dockerfile} -t ${tagName} ${workspaceDir.getAbsolutePath}"
    val runContainerCommand = s"${dockerCommand} run --rm --name ${containerName} ${tagName}"

    sb.append(s"${buildContainerCommand}\n")
    val buildResult = runProcess(job, buildDir, workspaceDir, buildContainerCommand)
    if (buildResult == 0){
      sb.append(s"${runContainerCommand}\n")
      val exitCode = runProcess(job, buildDir, workspaceDir, runContainerCommand)

      val imageId = Process(s"""${dockerCommand} images --format {{.ID}} ${tagName}""").!!.stripLineEnd
      val rmImageCommand = s"${dockerCommand} rmi --force ${imageId}"
      sb.append(s"$rmImageCommand\n")
      runProcess(job, buildDir, workspaceDir, rmImageCommand)

      exitCode
    }else{
      buildResult
    }
  }

  private def runDockerComposeJob(job: BuildJob, buildDir: File, workspaceDir: File, composeCommand: String): Int = {
    val composeFile = if(job.config.buildScript.nonEmpty){job.config.buildScript}else{"docker-compose.yml"}
    val containerName = s"gitbucket_ci_${job.buildUserName}_${job.buildRepositoryName}_${job.buildNumber}"

    val buildCommand = s"${composeCommand} -f ${composeFile} build"
    // TODO: specify service name (currently consider as "ci")
    val runCommand = s"${composeCommand} -f ${composeFile} run --name ${containerName} -T --rm ci"
    val downCommand = s"${composeCommand} -f ${composeFile} down --rmi all"

    sb.append(s"${buildCommand}\n")
    val buildResult = runProcess(job, buildDir, workspaceDir, buildCommand)
    if(buildCommand != 0){
      sb.append(s"${runCommand}\n")
      val exitCode = runProcess(job, buildDir, workspaceDir, runCommand)
      runProcess(job, buildDir, workspaceDir, downCommand)

      exitCode
    }else{
      buildResult
    }
  }

  private def createMailSubject(job: BuildJob): String = {
    val sb = new StringBuilder()

    sb.append(s"[${job.userName}/${job.repositoryName}] Build #${job.buildNumber} failed ")
    job.pullRequestId match {
      case Some(id) =>
        sb.append(s"(PR #${id})")
      case None =>
        sb.append(s"(${job.buildBranch})")
    }

    sb.toString
  }

  private def createMailContent(job: BuildJob, settings: SystemSettings, targetUrl: Option[String]): String = {
    val sb = new StringBuilder()

    settings.baseUrl match {
      case Some(baseUrl) =>
        sb.append(s"[${job.sha.substring(0, 7)}](${baseUrl}/${job.userName}/${job.repositoryName}/commit/${job.sha}) by ${job.commitUserName}\n")
      case None =>
        sb.append(s"${job.sha.substring(0, 7)} by ${job.commitUserName}\n")
    }
    sb.append(job.commitMessage)
    sb.append("\n")

    targetUrl.foreach { url =>
      sb.append("\n")
      sb.append("----\n")
      sb.append(s"[View it on GitBucket](${url})\n")
    }

    sb.toString
  }

  private def markdown2html(markdown: String): String = {
    val options = new Options()
    options.setBreaks(true)
    Marked.marked(markdown, options)
  }

  def cancel(): Unit = {
    cancelled.set(true)
    runningProcess.get.foreach(_.destroy())
  }

  private def prepareBuildScript(buildDir: File, buildScript: String): String = {
    if(CIUtils.isWindows){
      val buildFile = new File(buildDir, "build.bat")
      FileUtils.write(buildFile, buildScript, "UTF-8")
      buildFile.setExecutable(true)
      buildFile.getAbsolutePath
    } else {
      val buildFile = new File(buildDir, "build.sh")
      FileUtils.write(buildFile, "#!/bin/sh\n" + buildScript.replaceAll("\r\n", "\n"), "UTF-8")
      buildFile.setExecutable(true)
      "../build.sh"
    }
  }

  private def prepareBuildFile(buildDir: File, buildScript: String): String = {
    if(CIUtils.isWindows){
      val dir = new File(buildDir, "workspace")
      new File(dir, buildScript).getAbsolutePath
    } else {
      "./" + buildScript
    }
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
