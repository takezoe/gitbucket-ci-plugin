package io.github.gitbucket.ci.controller

import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import gitbucket.core.util.{JGitUtil, ReferrerAuthenticator}
import gitbucket.core.util.Implicits._
import io.github.gitbucket.ci.service.{BuildSetting, SimpleCIService}
import org.eclipse.jgit.api.Git
import org.scalatra.Ok


class SimpleCIController extends ControllerBase
  with SimpleCIService with AccountService with RepositoryService
  with ReferrerAuthenticator {

  get("/:owner/:repository/build")(referrersOnly { repository =>
    val buildResults = getBuildResults(repository.owner, repository.name)
    gitbucket.ci.html.buildresults(repository, buildResults.reverse, None)
  })

  get("/:owner/:repository/build/:buildNumber")(referrersOnly { repository =>
    val buildNumber = params("buildNumber").toLong
    getBuildResult(repository.owner, repository.name, buildNumber).map { buildResult =>
      gitbucket.ci.html.buildresult(repository, buildResult, None)
    } getOrElse NotFound()
  })


  get("/helloworld"){
    getRepository("root", "test").map { repository =>
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        JGitUtil.getDefaultBranch(git, repository).map { case (objectId, revision) =>
          runBuild("root", "test", revision, BuildSetting("root", "test", "./build.sh"))
        }
      }
    }
    Ok()
  }

}
