package io.github.gitbucket.ci.controller

import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.util.{JGitUtil, OwnerAuthenticator, ReferrerAuthenticator, WritableUsersAuthenticator}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.ci.model.CIConfig
import io.github.gitbucket.ci.service.SimpleCIService
import io.github.gitbucket.ci.util.{CIUtils, JobStatus}
import io.github.gitbucket.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.json4s.jackson.Serialization
import org.scalatra.{BadRequest, Ok}

class CIController extends ControllerBase
  with SimpleCIService with AccountService with RepositoryService
  with ReferrerAuthenticator with WritableUsersAuthenticator with OwnerAuthenticator {

  case class BuildConfigForm(
    enableBuild: Boolean,
    buildScript: Option[String]
  )

  val buildConfigForm = mapping(
    "enableBuild" -> trim(label("Enable build", boolean())),
    "buildScript" -> trim(label("Build script", optional(text())))
  )(BuildConfigForm.apply)

  case class ApiJobOutput(
    status: String,
    output: String
  )

  case class ApiJobStatus(
    buildNumber: Int,
    status: String,
    sha: String,
    startTime: String,
    endTime: String,
    duration: String
  )

  get("/:owner/:repository/build")(referrersOnly { repository =>
    if(loadCIConfig(repository.owner, repository.name).isDefined){
      gitbucket.ci.html.results(repository,
        hasDeveloperRole(repository.owner, repository.name, context.loginAccount))
    } else {
      gitbucket.ci.html.guide(repository,
        hasOwnerRole(repository.owner, repository.name, context.loginAccount))
    }
  })

  get("/:owner/:repository/build/:buildNumber")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toInt

    getRunningJobs(repository.owner, repository.name)
      .find { case (job, _) => job.buildNumber == buildNumber }
      .map  { case (job, _) => (job.buildNumber, "running")
    }.orElse {
      getCIResults(repository.owner, repository.name)
        .find { result => result.buildNumber == buildNumber }
        .map { result => (result.buildNumber, result.status) }
    }.map { case (buildNumber, status) =>
      gitbucket.ci.html.output(repository, buildNumber, status,
        hasDeveloperRole(repository.owner, repository.name, context.loginAccount))
    } getOrElse NotFound()
  })

  get("/:owner/:repository/build/output/:buildNumber")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toInt

    getRunningJobs(repository.owner, repository.name)
      .find { case (job, sb) => job.buildNumber == buildNumber }
      .map  { case (job, sb) =>
        contentType = formats("json")
        ApiJobOutput("running", CIUtils.colorize(sb.toString))
    } orElse {
      getCIResults(repository.owner, repository.name)
        .find { result => result.buildNumber == buildNumber }
        .map  { result =>
          contentType = formats("json")
          ApiJobOutput(result.status, CIUtils.colorize(getCIResultOutput(result)))
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
            commitUserName      = revCommit.getCommitterIdent.getName, // TODO
            pullRequestId       = None,
            buildAuthor         = context.loginAccount.get,
            config              = config
          )
        }
      }
      Ok()
    } getOrElse BadRequest()
  })

  ajaxPost("/:owner/:repository/build/restart/:buildNumber")(writableUsersOnly { repository =>
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
          pullRequestId       = result.pullRequestId,
          buildAuthor         = context.loginAccount.get,
          config              = config
        )
        Ok()
      }
    } getOrElse BadRequest()
  })

  ajaxPost("/:owner/:repository/build/cancel/:buildNumber")(writableUsersOnly { repository =>
    val buildNumber = params("buildNumber").toInt
    cancelBuild(repository.owner, repository.name, buildNumber)
    Ok()
  })

  ajaxGet("/:owner/:repository/build/status")(referrersOnly { repository =>
    import gitbucket.core.view.helpers._

    val queuedJobs = getQueuedJobs(repository.owner, repository.name).map { job =>
      ApiJobStatus(
        buildNumber = job.buildNumber,
        status      = JobStatus.Waiting,
        sha         = job.sha,
        startTime   = "",
        endTime     = "" ,
        duration    = ""
      )
    }

    val runningJobs = getRunningJobs(repository.owner, repository.name).map { case (job, _) =>
      ApiJobStatus(
        buildNumber = job.buildNumber,
        status      = JobStatus.Running,
        sha         = job.sha,
        startTime   = job.startTime.map { startTime => datetime(startTime) }.getOrElse(""),
        endTime     = "",
        duration    = ""
      )
    }

    val finishedJobs = getCIResults(repository.owner, repository.name).map { result =>
      ApiJobStatus(
        buildNumber = result.buildNumber,
        status      = result.status,
        sha         = result.sha,
        startTime   = datetime(result.startTime),
        endTime     = datetime(result.endTime),
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
      // TODO buildScript is required!!
      saveCIConfig(repository.owner, repository.name, Some(CIConfig(repository.owner, repository.name, form.buildScript.getOrElse(""))))
    } else {
      saveCIConfig(repository.owner, repository.name, None)
    }
    flash += "info" -> "Build configuration has been updated."
    redirect(s"/${repository.owner}/${repository.name}/settings/build")
  })

}
