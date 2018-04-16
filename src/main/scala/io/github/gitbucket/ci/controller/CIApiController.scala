package io.github.gitbucket.ci.controller

import gitbucket.core.util.Implicits._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.{ReferrerAuthenticator, UsersAuthenticator}
import io.github.gitbucket.ci.api.JsonFormat

class CIApiController extends ControllerBase
  with UsersAuthenticator
  with ReferrerAuthenticator
  with AccountService
  with RepositoryService {

  get("/api/circleci/v1.1/me")(usersOnly {
    JsonFormat(Map(
      "login" -> context.loginAccount.get.userName,
      "basic_email_prefs" -> "smart"
    ))
  })

  get("/api/circleci/v1.1/:owner/:repository")(referrersOnly { repository =>

  })
}
