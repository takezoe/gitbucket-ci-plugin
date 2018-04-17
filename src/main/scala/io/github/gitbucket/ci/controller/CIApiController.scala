package io.github.gitbucket.ci.controller

import java.util.Date

import gitbucket.core.api.ApiPath
import gitbucket.core.util.Implicits._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.SyntaxSugars.defining
import gitbucket.core.util.{ReferrerAuthenticator, UsersAuthenticator}
import io.github.gitbucket.ci.api.{CIApiBuild, CIApiPreviousBuild, JsonFormat}
import io.github.gitbucket.ci.service.CIService

import scala.collection.convert.WrapAsJava.asJavaCollection

class CIApiController extends ControllerBase
  with UsersAuthenticator
  with AccountService
  with RepositoryService
  with CIService {

  get("/api/circleci/v1.1/*"){
    NotFound()
  }

  get("/api/circleci/v1.1/me")(usersOnly {
    JsonFormat(Map(
      "login" -> context.loginAccount.get.userName,
      "basic_email_prefs" -> "smart"
    ))
  })

  get("/api/circleci/v1.1/:owner/:repository")(referrersOnly { repository =>
    val queuedJobs = getQueuedJobs(repository.owner, repository.name).map { job => CIApiBuild(job) }
    val runningJobs = getRunningJobs(repository.owner, repository.name).map { case (job, _) => CIApiBuild(job) }
    val buildResults = getCIResults(repository.owner, repository.name).map { result => CIApiBuild(result) }
    val results = (queuedJobs ++ runningJobs ++ buildResults).sortBy(_.build_num * -1)

    // Fill previous property
    val finalResults = results.zipWithIndex.map { case (result, i) =>
      if(i < results.size() - 1){
        val previous = results(i + 1)
        result.copy(previous = Some(CIApiPreviousBuild(
          status = previous.status,
          build_num = previous.build_num
        )))
      } else {
        result
      }
    }

    JsonFormat(finalResults)
  })

  private def referrersOnly(action: (RepositoryInfo) => Any) = {
    {
      defining(request.paths) { paths =>
        getRepository(params("owner"), params("repository")).map { repository =>
          if (isReadable(repository.repository, context.loginAccount)) {
            action(repository)
          } else {
            Unauthorized()
          }
        } getOrElse NotFound()
      }
    }
  }
}
