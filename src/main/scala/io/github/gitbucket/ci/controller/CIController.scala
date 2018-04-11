package io.github.gitbucket.ci.controller

import java.io.FileInputStream

import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.util._
import gitbucket.core.util.Implicits._
import gitbucket.core.view.helpers.datetimeAgo
import io.github.gitbucket.ci.manager.BuildManager
import io.github.gitbucket.ci.model.{CIConfig, CISystemConfig}
import io.github.gitbucket.ci.service.CIService
import io.github.gitbucket.ci.util.{CIUtils, JobStatus}
import org.scalatra.forms._
import org.apache.commons.io.IOUtils
import org.eclipse.jgit.api.Git
import org.json4s.jackson.Serialization
import org.scalatra.{BadRequest, Ok}

object CIController {

  case class ApiJobOutput(
    status: String,
    output: String
  )

  case class ApiJobStatus(
    buildNumber: Int,
    status: String,
    target: String,
    targetUrl: String,
    sha: String,
    message: String,
    userName: String,
    committer: String,
    author: String,
    startTime: String,
    duration: String
  )

  case class BuildConfigForm(
    enableBuild: Boolean,
    buildType: Option[String],
    buildScript: Option[String],
    buildFile: Option[String],
    notification: Boolean,
    skipWords: Option[String],
    runWords: Option[String]
  )

  case class CISystemConfigForm(
    maxBuildHistory: Int,
    maxParallelBuilds: Int
  )

}

class CIController extends ControllerBase
  with CIService with AccountService with RepositoryService
  with ReferrerAuthenticator with WritableUsersAuthenticator with OwnerAuthenticator with AdminAuthenticator {
  import CIController._

  val buildConfigForm = mapping(
    "enableBuild" -> trim(label("Enable build", boolean())),
    "buildType" -> trim(label("Build type", optionalRequiredIfChecked("enableBuild", text()))),
    "buildScript" -> trim(label("Build script", optionalRequired(_("buildType") == Seq("script"), text()))),
    "buildFile" -> trim(label("Build file", optionalRequired(_("buildType") == Seq("file"), text()))),
    "notification" -> trim(label("Notification", boolean())),
    "skipWords" -> trim(label("Skip words", optional(text()))),
    "runWords" -> trim(label("Run words", optional(text())))
  )(BuildConfigForm.apply)

  val ciSystemConfigForm = mapping(
    "maxBuildHistory" -> trim(label("Max build history", number())),
    "maxParallelBuilds" -> trim(label("Max parallel builds", number()))
  )(CISystemConfigForm.apply)

  get("/:owner/:repository/build")(referrersOnly { repository =>
    if(loadCIConfig(repository.owner, repository.name).isDefined){
      gitbucket.ci.html.results(repository,
        hasDeveloperRole(repository.owner, repository.name, context.loginAccount),
        hasOwnerRole(repository.owner, repository.name, context.loginAccount))
    } else {
      gitbucket.ci.html.guide(repository,
        hasOwnerRole(repository.owner, repository.name, context.loginAccount))
    }
  })

  get("/:owner/:repository/build/:buildNumber")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toInt

    getRunningJobs(repository.owner, repository.name)
      .find { case (job, _) => job.buildNumber == buildNumber }
      .map  { case (job, _) => //(job.buildNumber, JobStatus.Running)
        ApiJobStatus(
          buildNumber = job.buildNumber,
          status      = JobStatus.Running,
          target      = job.pullRequestId.map("PR #" + _.toString).getOrElse(job.buildBranch),
          targetUrl   = createTargetUrl(job.buildUserName, job.buildRepositoryName, job.buildBranch, job.pullRequestId, repository),
          sha         = job.sha,
          message     = job.commitMessage,
          userName    = getAccountByMailAddress(job.commitMailAddress).map(_.userName).getOrElse(""),
          committer   = job.commitUserName,
          author      = job.buildAuthor.userName,
          startTime   = job.startTime.map { startTime => datetimeAgo(startTime) }.getOrElse(""),
          duration    = ""
        )
    }.orElse {
      getCIResults(repository.owner, repository.name)
        .find { result => result.buildNumber == buildNumber }
        .map { result =>
          ApiJobStatus(
            buildNumber = result.buildNumber,
            status      = JobStatus.Running,
            target      = result.pullRequestId.map("PR #" + _.toString).getOrElse(result.buildBranch),
            targetUrl   = createTargetUrl(result.buildUserName, result.buildRepositoryName, result.buildBranch, result.pullRequestId, repository),
            sha         = result.sha,
            message     = result.commitMessage,
            userName    = getAccountByMailAddress(result.commitMailAddress).map(_.userName).getOrElse(""),
            committer   = result.commitUserName,
            author      = result.buildAuthor,
            startTime   = datetimeAgo(result.startTime),
            duration    = ((result.endTime.getTime - result.startTime.getTime) / 1000) + " sec"
          )
        }
    }.map { status =>
      gitbucket.ci.html.output(repository, status,
        hasDeveloperRole(repository.owner, repository.name, context.loginAccount))
    } getOrElse NotFound()
  })

  ajaxGet("/:owner/:repository/build/:buildNumber/output")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toInt

    getRunningJobs(repository.owner, repository.name)
      .find { case (job, sb) => job.buildNumber == buildNumber }
      .map  { case (job, sb) =>
        contentType = formats("json")
        Serialization.write(ApiJobOutput("running", CIUtils.colorize(sb.toString)))
    } orElse {
      getCIResults(repository.owner, repository.name)
        .find { result => result.buildNumber == buildNumber }
        .map  { result =>
          contentType = formats("json")
          Serialization.write(ApiJobOutput(result.status, CIUtils.colorize(getCIResultOutput(result))))
        }
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/build/run")(writableUsersOnly { repository =>
    loadCIConfig(repository.owner, repository.name).map { config =>
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        JGitUtil.getDefaultBranch(git, repository).map { case (objectId, revision) =>
          val revCommit = JGitUtil.getRevCommitFromId(git, objectId)
          runBuild(
            userName            = repository.owner,
            repositoryName      = repository.name,
            buildUserName       = repository.owner,
            buildRepositoryName = repository.name,
            buildBranch         = revision,
            sha                 = objectId.name,
            commitMessage       = revCommit.getShortMessage,
            commitUserName      = revCommit.getCommitterIdent.getName,
            commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
            pullRequestId       = None,
            buildAuthor         = context.loginAccount.get,
            config              = config
          )
        }
      }
      Ok()
    } getOrElse BadRequest()
  })

  ajaxPost("/:owner/:repository/build/:buildNumber/restart")(writableUsersOnly { repository =>
    val buildNumber = params("buildNumber").toInt
    loadCIConfig(repository.owner, repository.name).flatMap { config =>
      getCIResult(repository.owner, repository.name, buildNumber).map { result =>
        runBuild(
          userName            = result.userName,
          repositoryName      = result.repositoryName,
          buildUserName       = result.buildUserName,
          buildRepositoryName = result.buildRepositoryName,
          buildBranch         = result.buildBranch,
          sha                 = result.sha,
          commitMessage       = result.commitMessage,
          commitUserName      = result.commitUserName,
          commitMailAddress   = result.commitMailAddress,
          pullRequestId       = result.pullRequestId,
          buildAuthor         = context.loginAccount.get,
          config              = config
        )
        Ok()
      }
    } getOrElse BadRequest()
  })

  ajaxPost("/:owner/:repository/build/:buildNumber/cancel")(writableUsersOnly { repository =>
    val buildNumber = params("buildNumber").toInt
    cancelBuild(repository.owner, repository.name, buildNumber)
    Ok()
  })

  get("/:owner/:repository/build/:buildNumber/workspace/*")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toInt
    val path = multiParams("splat").headOption.getOrElse("")
    val file = new java.io.File(CIUtils.getBuildDir(repository.owner, repository.name, buildNumber), s"workspace/${path}")
    if(file.isFile){
      contentType = FileUtil.getMimeType(path)
      response.setContentLength(file.length.toInt)
      using(new FileInputStream(file)){ in =>
        IOUtils.copy(in, response.getOutputStream)
      }
    } else {
      gitbucket.ci.html.workspace(
        repository,
        buildNumber,
        "workspace" +: path.split("/").filter(_.nonEmpty).toSeq,
        file.listFiles.toSeq.filterNot(_.getName == ".git").sortWith { (file1, file2) =>
          (file1.isDirectory, file2.isDirectory) match {
            case (true , false) => true
            case (false, true ) => false
            case _ => file1.getName.compareTo(file2.getName) < 0
          }
        }
      )
    }
  })

  private def createTargetUrl(buildUserName: String, buildRepositoryName:String, buildBranch: String,
                              pullRequestId: Option[Int], repository: RepositoryInfo): String = {
    pullRequestId match {
      case Some(id) => s"${context.path}/${repository.owner}/${repository.name}/pull/${id}"
      case None     => s"${context.path}/${buildUserName}/${buildRepositoryName}/tree/${buildBranch}"
    }
  }

  ajaxGet("/:owner/:repository/build/status")(referrersOnly { repository =>
    val queuedJobs = getQueuedJobs(repository.owner, repository.name).map { job =>
      ApiJobStatus(
        buildNumber = job.buildNumber,
        status      = JobStatus.Waiting,
        target      = job.pullRequestId.map("PR #" + _.toString).getOrElse(job.buildBranch),
        targetUrl   = createTargetUrl(job.buildUserName, job.buildRepositoryName, job.buildBranch, job.pullRequestId, repository),
        sha         = job.sha,
        message     = job.commitMessage,
        userName    = getAccountByMailAddress(job.commitMailAddress).map(_.userName).getOrElse(""),
        committer   = job.commitUserName,
        author      = job.buildUserName,
        startTime   = "",
        duration    = ""
      )
    }

    val runningJobs = getRunningJobs(repository.owner, repository.name).map { case (job, _) =>
      ApiJobStatus(
        buildNumber = job.buildNumber,
        status      = JobStatus.Running,
        target      = job.pullRequestId.map("PR #" + _.toString).getOrElse(job.buildBranch),
        targetUrl   = createTargetUrl(job.buildUserName, job.buildRepositoryName, job.buildBranch, job.pullRequestId, repository),
        sha         = job.sha,
        message     = job.commitMessage,
        userName    = getAccountByMailAddress(job.commitMailAddress).map(_.userName).getOrElse(""),
        committer   = job.commitUserName,
        author      = job.buildUserName,
        startTime   = job.startTime.map { startTime => datetimeAgo(startTime) }.getOrElse(""),
        duration    = ""
      )
    }

    val finishedJobs = getCIResults(repository.owner, repository.name).map { result =>
      ApiJobStatus(
        buildNumber = result.buildNumber,
        status      = result.status,
        target      = result.pullRequestId.map("PR #" + _.toString).getOrElse(result.buildBranch),
        targetUrl   = createTargetUrl(result.buildUserName, result.buildRepositoryName, result.buildBranch, result.pullRequestId, repository),
        sha         = result.sha,
        message     = result.commitMessage,
        userName    = getAccountByMailAddress(result.commitMailAddress).map(_.userName).getOrElse(""),
        committer   = result.commitUserName,
        author      = result.buildUserName,
        startTime   = datetimeAgo(result.startTime),
        duration    = ((result.endTime.getTime - result.startTime.getTime) / 1000) + " sec"
      )
    }

    contentType = formats("json")
    Serialization.write((queuedJobs ++ runningJobs ++ finishedJobs).sortBy(_.buildNumber * -1))(jsonFormats)
  })

  get("/:owner/:repository/settings/build")(ownerOnly { repository =>
    gitbucket.ci.html.config(repository, loadCIConfig(repository.owner, repository.name), flash.get("info"))
  })

  post("/:owner/:repository/settings/build", buildConfigForm)(ownerOnly { (form, repository) =>
    if(form.enableBuild){
      val buildType = form.buildType.getOrElse("script")
      saveCIConfig(repository.owner, repository.name, Some(
        CIConfig(
          repository.owner,
          repository.name,
          buildType,
          (buildType match {
            case "script" => form.buildScript.getOrElse("")
            case "file" => form.buildFile.getOrElse("")
            case _ => ""
          }),
          form.notification,
          form.skipWords,
          form.runWords
        )
      ))
    } else {
      saveCIConfig(repository.owner, repository.name, None)
    }
    flash += "info" -> "Build configuration has been updated."
    redirect(s"/${repository.owner}/${repository.name}/settings/build")
  })

  get("/:owner/:repository/build/:branch/badge.svg")(referrersOnly { repository =>
    contentType = "image/svg+xml"
    getLatestCIStatus(repository.owner, repository.name, params("branch")) match {
      case "success" => """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="90" height="20"><linearGradient id="b" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient><clipPath id="a"><rect width="90" height="20" rx="3" fill="#fff"/></clipPath><g clip-path="url(#a)"><path fill="#555" d="M0 0h37v20H0z"/><path fill="#4c1" d="M37 0h53v20H37z"/><path fill="url(#b)" d="M0 0h90v20H0z"/></g><g fill="#fff" text-anchor="middle" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11"><text x="18.5" y="15" fill="#010101" fill-opacity=".3">Build</text><text x="18.5" y="14">Build</text><text x="62.5" y="15" fill="#010101" fill-opacity=".3">success</text><text x="62.5" y="14">success</text></g></svg>"""
      case "failure" => """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="82" height="20"><linearGradient id="b" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient><clipPath id="a"><rect width="82" height="20" rx="3" fill="#fff"/></clipPath><g clip-path="url(#a)"><path fill="#555" d="M0 0h37v20H0z"/><path fill="#e05d44" d="M37 0h45v20H37z"/><path fill="url(#b)" d="M0 0h82v20H0z"/></g><g fill="#fff" text-anchor="middle" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11"><text x="18.5" y="15" fill="#010101" fill-opacity=".3">Build</text><text x="18.5" y="14">Build</text><text x="58.5" y="15" fill="#010101" fill-opacity=".3">failure</text><text x="58.5" y="14">failure</text></g></svg>"""
      case "waiting" => """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="86" height="20"><linearGradient id="b" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient><clipPath id="a"><rect width="86" height="20" rx="3" fill="#fff"/></clipPath><g clip-path="url(#a)"><path fill="#555" d="M0 0h37v20H0z"/><path fill="#dfb317" d="M37 0h49v20H37z"/><path fill="url(#b)" d="M0 0h86v20H0z"/></g><g fill="#fff" text-anchor="middle" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11"><text x="18.5" y="15" fill="#010101" fill-opacity=".3">Build</text><text x="18.5" y="14">Build</text><text x="60.5" y="15" fill="#010101" fill-opacity=".3">waiting</text><text x="60.5" y="14">waiting</text></g></svg>"""
      case "running" => """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="90" height="20"><linearGradient id="b" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient><clipPath id="a"><rect width="90" height="20" rx="3" fill="#fff"/></clipPath><g clip-path="url(#a)"><path fill="#555" d="M0 0h37v20H0z"/><path fill="#fe7d37" d="M37 0h53v20H37z"/><path fill="url(#b)" d="M0 0h90v20H0z"/></g><g fill="#fff" text-anchor="middle" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11"><text x="18.5" y="15" fill="#010101" fill-opacity=".3">Build</text><text x="18.5" y="14">Build</text><text x="62.5" y="15" fill="#010101" fill-opacity=".3">running</text><text x="62.5" y="14">running</text></g></svg>"""
      case _ => """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="98" height="20"><linearGradient id="b" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient><clipPath id="a"><rect width="98" height="20" rx="3" fill="#fff"/></clipPath><g clip-path="url(#a)"><path fill="#555" d="M0 0h37v20H0z"/><path fill="#9f9f9f" d="M37 0h61v20H37z"/><path fill="url(#b)" d="M0 0h98v20H0z"/></g><g fill="#fff" text-anchor="middle" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11"><text x="18.5" y="15" fill="#010101" fill-opacity=".3">Build</text><text x="18.5" y="14">Build</text><text x="66.5" y="15" fill="#010101" fill-opacity=".3">unknown</text><text x="66.5" y="14">unknown</text></g></svg>"""
    }
  })

  get("/admin/build")(adminOnly {
    gitbucket.ci.html.system(loadCISystemConfig())
  })

  post("/admin/build", ciSystemConfigForm)(adminOnly { form =>
    saveCISystemConfig(CISystemConfig(maxBuildHistory = form.maxBuildHistory, maxParallelBuilds = form.maxParallelBuilds))
    BuildManager.setMaxParallelBuilds(form.maxParallelBuilds)
    redirect("/admin/build")
  })

}

