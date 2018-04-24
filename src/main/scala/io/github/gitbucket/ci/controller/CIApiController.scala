package io.github.gitbucket.ci.controller

import gitbucket.core.util.Implicits._
import gitbucket.core.controller.ControllerBase
import gitbucket.core.model.Role
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars._
import gitbucket.core.util.{JGitUtil, Keys, UsersAuthenticator}
import io.github.gitbucket.ci.api.{CIApiBuild, CIApiPreviousBuild, CIApiSingleBuild, JsonFormat}
import io.github.gitbucket.ci.service.CIService
import org.eclipse.jgit.api.Git
import org.scalatra.{BadRequest, Ok}

class CIApiController extends ControllerBase
  with UsersAuthenticator
  with AccountService
  with RepositoryService
  with CIService {

  before("/api/circleci/v1.1/*"){
    contentType = formats("json")
    request.setAttribute(Keys.Request.APIv3, true)
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

  get("/api/circleci/v1.1/project/gitbucket/:owner/:repository")(referrersOnly { repository =>
    JsonFormat(getBuilds(repository.owner, repository.name))
  })

  get("/api/circleci/v1.1/project/gitbucket/:owner/:repository/tree/:branch")(referrersOnly { repository =>
    val branch = params("branch")
    if(repository.branchList.contains(branch)){
      JsonFormat(getBuilds(repository.owner, repository.name).filter(_.branch == params("branch")))
    } else NotFound()
  })

  get("/api/circleci/v1.1/project/gitbucket/:owner/:repository/:build_num")(referrersOnly { repository =>
    val buildNumber = params("build_num").toInt
    getCIResult(repository.owner, repository.name, buildNumber).map { result =>
      JsonFormat(CIApiSingleBuild(result))
    } getOrElse NotFound()
  })

  post("/api/circleci/v1.1/project/gitbucket/:owner/:repository/:build_num/retry")(writableUsersOnly { repository =>
    val buildNumber = params("build_num").toInt
    loadCIConfig(repository.owner, repository.name).flatMap { config =>
      getCIResult(repository.owner, repository.name, buildNumber).map { result =>
        runBuild(
          userName            = result.userName,
          repositoryName      = result.repositoryName,
          buildUserName       = result.buildUserName,
          buildRepositoryName = result.buildRepositoryName,
          buildBranch         = result.buildBranch,
          sha                 = result.sha,
          commitMessage       = result.commitMessage,
          commitUserName      = result.commitUserName,
          commitMailAddress   = result.commitMailAddress,
          pullRequestId       = result.pullRequestId,
          buildAuthor         = context.loginAccount.get,
          config              = config
        )
        Ok()
      }
    } getOrElse BadRequest()
  })

  post("/api/circleci/v1.1/project/gitbucket/:owner/:repository/:build_num/cancel")(writableUsersOnly { repository =>
    val buildNumber = params("buildNumber").toInt
    cancelBuild(repository.owner, repository.name, buildNumber)
    Ok()
  })

  post("/api/circleci/v1.1/project/gitbucket/:owner/:repository")(writableUsersOnly { repository =>
    loadCIConfig(repository.owner, repository.name).map { config =>
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        JGitUtil.getDefaultBranch(git, repository).map { case (objectId, revision) =>
          val revCommit = JGitUtil.getRevCommitFromId(git, objectId)
          runBuild(
            userName            = repository.owner,
            repositoryName      = repository.name,
            buildUserName       = repository.owner,
            buildRepositoryName = repository.name,
            buildBranch         = revision,
            sha                 = objectId.name,
            commitMessage       = revCommit.getShortMessage,
            commitUserName      = revCommit.getCommitterIdent.getName,
            commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
            pullRequestId       = None,
            buildAuthor         = context.loginAccount.get,
            config              = config
          )
        }
      }
      Ok()
    } getOrElse BadRequest()
  })

  post("/api/circleci/v1.1/project/gitbucket/:owner/:repository/tree/:branch")(writableUsersOnly { repository =>
    val branch = params("branch")
    loadCIConfig(repository.owner, repository.name).map { config =>
      using(Git.open(getRepositoryDir(repository.owner, repository.name))) { git =>
        val objectId = git.getRepository.resolve(branch)
        val revCommit = JGitUtil.getRevCommitFromId(git, objectId)
        runBuild(
          userName            = repository.owner,
          repositoryName      = repository.name,
          buildUserName       = repository.owner,
          buildRepositoryName = repository.name,
          buildBranch         = branch,
          sha                 = objectId.name,
          commitMessage       = revCommit.getShortMessage,
          commitUserName      = revCommit.getCommitterIdent.getName,
          commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
          pullRequestId       = None,
          buildAuthor         = context.loginAccount.get,
          config              = config
        )
      }
      Ok()
    } getOrElse BadRequest()
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

  private def writableUsersOnly(action: (RepositoryInfo) => Any) = {
    getRepository(params("owner"), params("repository")).map { repository =>
      context.loginAccount match {
        case Some(x) if (x.isAdmin)                                                          => action(repository)
        case Some(x) if (params("owner") == x.userName)                                      => action(repository)
        case Some(x) if (getGroupMembers(repository.owner).exists(_.userName == x.userName)) => action(repository)
        case Some(x)
          if (getCollaboratorUserNames(params("owner"), params("repository"), Seq(Role.ADMIN, Role.DEVELOPER))
            .contains(x.userName)) =>
          action(repository)
        case _ => Unauthorized()
      }
    } getOrElse NotFound()
  }
}
