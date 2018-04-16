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

class CIApiController extends ControllerBase
  with UsersAuthenticator
  with AccountService
  with RepositoryService
  with CIService {

  get("/api/circleci/v1.1/me")(usersOnly {
    JsonFormat(Map(
      "login" -> context.loginAccount.get.userName,
      "basic_email_prefs" -> "smart"
    ))
  })

  get("/api/circleci/v1.1/:owner/:repository")(referrersOnly { repository =>
    val queuedJobs = getQueuedJobs(repository.owner, repository.name).reverse

    // TODO

    val runningJobs = getRunningJobs(repository.owner, repository.name).reverse

    // TODO

    val buildResults = getCIResults(repository.owner, repository.name).reverse
    JsonFormat(buildResults.zipWithIndex.map { case (result, i) =>
      CIApiBuild(
        vcs_url = ApiPath(s"/git/${result.userName}/${result.repositoryName}"),
        build_url = ApiPath(s"/${result.userName}/${result.repositoryName}/build/${result.buildNumber}"),
        build_num = result.buildNumber,
        branch = result.buildBranch,
        vcs_revision = result.sha,
        committer_name = result.commitUserName,
        committer_email = result.commitMailAddress,
        subject = result.commitMessage,
        body = "",
        why = "gitbucket",
        dont_build = null,
        queued_at = result.startTime, // TODO
        start_time = result.startTime,
        stop_time = result.endTime,
        build_time_millis = result.endTime.getTime - result.startTime.getTime,
        username = result.userName,
        reponame = result.repositoryName,
        lifecycle = "finished",
        outcome = result.status,
        status = result.status,
        retry_of = null,
        previous = if(i < buildResults.size - 1){
          val previousResult = buildResults(i + 1)
          Some(CIApiPreviousBuild(
            status = previousResult.status,
            build_num = previousResult.buildNumber
          ))
        } else None
      )
    })
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
