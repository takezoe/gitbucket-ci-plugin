package io.github.gitbucket.ci.controller

import gitbucket.core.controller.ControllerBase
import io.github.gitbucket.ci.service.{BuildSetting, SimpleCIService}

class SimpleCIController extends ControllerBase with SimpleCIService {

  get("/helloworld"){
    runBuild("root", "test", "master", BuildSetting("root", "test", "./build.sh"))
  }

}
