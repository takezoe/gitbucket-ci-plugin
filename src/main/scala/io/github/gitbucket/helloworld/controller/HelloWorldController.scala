package io.github.gitbucket.helloworld.controller

import gitbucket.core.controller.ControllerBase

class HelloWorldController extends ControllerBase {

  get("/helloworld"){
    "Hello World!"
  }

}
