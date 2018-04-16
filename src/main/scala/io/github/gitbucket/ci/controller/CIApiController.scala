package io.github.gitbucket.ci.controller

import gitbucket.core.util.Implicits._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.util.UsersAuthenticator
import io.github.gitbucket.ci.api.JsonFormat

class CIApiController extends ControllerBase with UsersAuthenticator {

  get("/api/circleci/v1.1/me")(usersOnly {
    JsonFormat(Map(
      "login" -> context.loginAccount.get.userName,
      "basic_email_prefs" -> "smart"
    ))
  })

}
