package io.github.gitbucket.ci.controller

import java.io.ByteArrayOutputStream

import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.util.{JGitUtil, ReferrerAuthenticator, StringUtil, WritableUsersAuthenticator}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.ci.service.{BuildSetting, SimpleCIService}
import org.eclipse.jgit.api.Git
import org.fusesource.jansi.HtmlAnsiOutputStream
import org.scalatra.Ok


class SimpleCIController extends ControllerBase
  with SimpleCIService with AccountService with RepositoryService
  with ReferrerAuthenticator with WritableUsersAuthenticator {

  get("/:owner/:repository/build")(referrersOnly { repository =>
    gitbucket.ci.html.buildresults(repository,
      getBuildResults(repository.owner, repository.name).reverse,
      getRunningJob(repository.owner, repository.name),
      getQueuedJobs(repository.owner, repository.name), None)
  })

  get("/:owner/:repository/build/:buildNumber")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toLong
    getBuildResult(repository.owner, repository.name, buildNumber).map { buildResult =>
      colorize(StringUtil.escapeHtml(buildResult.output))
    } getOrElse NotFound()
  })

  post("/:owner/:repository/build/run")(writableUsersOnly { repository =>
    using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
      JGitUtil.getDefaultBranch(git, repository).map { case (objectId, revision) =>
        runBuild("root", "gitbucket", objectId.name, BuildSetting("root", "gitbucket", "sbt compile"))
      }
    }
    redirect(s"/${repository.owner}/${repository.name}/build")
  })

  import java.io.IOException

  @throws[IOException]
  private def colorize(text: String) = {
    val os = new ByteArrayOutputStream()
    val hos = new HtmlAnsiOutputStream(os)
    hos.write(text.getBytes("UTF-8"))
    hos.close
    new String(os.toByteArray, "UTF-8")
  }

  get("/helloworld"){
    getRepository("root", "test").map { repository =>
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        JGitUtil.getDefaultBranch(git, repository).map { case (objectId, revision) =>
          runBuild("root", "gitbucket", objectId.name, BuildSetting("root", "gitbucket", "sbt compile"))
        }
      }
    }
    Ok()
  }

}
