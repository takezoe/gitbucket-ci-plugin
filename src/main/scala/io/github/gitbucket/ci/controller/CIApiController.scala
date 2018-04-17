package io.github.gitbucket.ci.controller

import gitbucket.core.util.Implicits._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.UsersAuthenticator
import io.github.gitbucket.ci.api.{CIApiBuild, CIApiPreviousBuild, JsonFormat}
import io.github.gitbucket.ci.service.CIService

class CIApiController extends ControllerBase
  with UsersAuthenticator
  with AccountService
  with RepositoryService
  with CIService {

  before("/api/circleci/v1.1/*"){
    contentType = formats("json")
  }

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
    JsonFormat(getBuilds(repository.owner, repository.name))
  })

  get("/api/circleci/v1.1/:owner/:repository/tree/:branch")(referrersOnly { repository =>
    val branch = params("branch")
    if(repository.branchList.contains(branch)){
      JsonFormat(getBuilds(repository.owner, repository.name).filter(_.branch == params("branch")))
    } else NotFound()
  })

  private def getBuilds(owner: String, repository: String): Seq[CIApiBuild] = {
    val queuedJobs = getQueuedJobs(owner, repository).map { job => CIApiBuild(job) }
    val runningJobs = getRunningJobs(owner, repository).map { case (job, _) => CIApiBuild(job) }
    val buildResults = getCIResults(owner, repository).map { result => CIApiBuild(result) }
    val builds = (queuedJobs ++ runningJobs ++ buildResults).sortBy(_.build_num * -1)

    // Fill previous property
    builds.zipWithIndex.map { case (result, i) =>
      if(i < builds.size - 1){
        val previous = builds(i + 1)
        result.copy(previous = Some(CIApiPreviousBuild(
          status = previous.status,
          build_num = previous.build_num
        )))
      } else {
        result
      }
    }
  }

  private def referrersOnly(action: (RepositoryInfo) => Any) = {
    getRepository(params("owner"), params("repository")).map { repository =>
      if (isReadable(repository.repository, context.loginAccount)) {
        action(repository)
      } else {
        Unauthorized()
      }
    } getOrElse NotFound()
  }
}
