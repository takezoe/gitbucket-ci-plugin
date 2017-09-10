package io.github.gitbucket.ci.controller

import java.io.ByteArrayOutputStream

import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.util.{JGitUtil, OwnerAuthenticator, ReferrerAuthenticator, WritableUsersAuthenticator}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.ci.model.CIConfig
import io.github.gitbucket.ci.service.SimpleCIService
import io.github.gitbucket.scalatra.forms._
import org.eclipse.jgit.api.Git
import org.fusesource.jansi.HtmlAnsiOutputStream
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
      gitbucket.ci.html.output(repository, buildNumber, status)
    } getOrElse NotFound()
  })

  get("/:owner/:repository/build/output/:buildNumber")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toInt

    getRunningJobs(repository.owner, repository.name)
      .find { case (job, sb) => job.buildNumber == buildNumber }
      .map  { case (job, sb) =>
        contentType = formats("json")
        JobOutput("running", colorize(sb.toString))
    } orElse {
      getCIResults(repository.owner, repository.name)
        .find { result => result.buildNumber == buildNumber }
        .map  { result =>
          contentType = formats("json")
          JobOutput(result.status, colorize(getCIResultOutput(result)))
        }
    } getOrElse NotFound()
  })

  ajaxPost("/:owner/:repository/build/run")(writableUsersOnly { repository =>
    loadCIConfig(repository.owner, repository.name).map { config =>
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        JGitUtil.getDefaultBranch(git, repository).map { case (objectId, revision) =>
          runBuild(repository.owner, repository.name, objectId.name, config)
        }
      }
      Ok()
    } getOrElse BadRequest()
  })

  ajaxPost("/:owner/:repository/build/kill/:buildNumber")(writableUsersOnly { repository =>
    val buildNumber = params("buildNumber").toInt
    killBuild(repository.owner, repository.name, buildNumber)
    Ok()
  })

  ajaxGet("/:owner/:repository/build/status")(referrersOnly { repository =>
    import gitbucket.core.view.helpers._

    val queuedJobs = getQueuedJobs(repository.owner, repository.name).map { job =>
      JobStatus(
        buildNumber = job.buildNumber,
        status      = "waiting",
        sha         = job.sha,
        startTime   = "",
        endTime     = "" ,
        duration    = ""
      )
    }

    val runningJobs = getRunningJobs(repository.owner, repository.name).map { case (job, _) =>
      JobStatus(
        buildNumber = job.buildNumber,
        status      = "running",
        sha         = job.sha,
        startTime   = job.startTime.map { startTime => datetime(startTime) }.getOrElse(""),
        endTime     = "",
        duration    = ""
      )
    }

    val finishedJobs = getCIResults(repository.owner, repository.name).map { result =>
      JobStatus(
        buildNumber = result.buildNumber,
        status      = result.status,
        sha         = result.sha,
        startTime   = datetime(result.startTime),
        endTime     = datetime(result.endTime),
        duration    = ((result.endTime.getTime - result.startTime.getTime) / 1000) + "sec"
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


  case class JobOutput(status: String, output: String)
  case class JobStatus(buildNumber: Int, status: String, sha: String, startTime: String, endTime: String, duration: String)

  @throws[java.io.IOException]
  private def colorize(text: String) = {
    using(new ByteArrayOutputStream()){ os =>
      using(new HtmlAnsiOutputStream(os)){ hos =>
        hos.write(text.getBytes("UTF-8"))
      }
      new String(os.toByteArray, "UTF-8")
    }
  }

}
