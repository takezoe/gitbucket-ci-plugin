package io.github.gitbucket.ci.controller

import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.ReferrerAuthenticator
import io.github.gitbucket.ci.service.{BuildSetting, SimpleCIService}

class SimpleCIController extends ControllerBase
  with SimpleCIService with AccountService with RepositoryService
  with ReferrerAuthenticator {

  get("/:owner/:repository/build")(referrersOnly { repository =>
    val buildResults = getBuildResults(repository.owner, repository.name)
    gitbucket.ci.html.buildresults(repository, buildResults.reverse, None)
  })

  get("/helloworld"){
    runBuild("root", "test", "master", BuildSetting("root", "test", "./build.sh"))
  }
}
