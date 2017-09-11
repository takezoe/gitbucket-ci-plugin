package io.github.gitbucket.ci.hook

import gitbucket.core.model.CommitState
import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.model.Profile._
import gitbucket.core.service._
import io.github.gitbucket.ci.model.CIConfig
import io.github.gitbucket.ci.service.SimpleCIService
import io.github.gitbucket.ci.util.CIUtils
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}
import profile.blockingApi._

class CICommitHook extends ReceiveHook
  with SimpleCIService with RepositoryService with AccountService with CommitStatusService with SystemSettingsService {

  override def postReceive(owner: String, repository: String, receivePack: ReceivePack,
                           command: ReceiveCommand, pusher: String)(implicit session: Session): Unit = {
    val branch = command.getRefName.stripPrefix("refs/heads/")
    if(branch != command.getRefName){
      getRepository(owner, repository).foreach { repositoryInfo =>
        loadCIConfig(owner, repository).foreach { config =>
          val sha = command.getNewId.name
          if (repositoryInfo.repository.defaultBranch == branch) {
            runBuild(owner, repository, sha, pusher, config)
          } else {
            val pullRequests = PullRequests
              .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
              .filter { case (t1, t2) =>
                (t1.requestUserName === owner.bind) && (t1.requestRepositoryName === repository.bind) &&
                (t1.requestBranch === branch.bind) && (t2.closed === false.bind)
              }
              .list

            if (pullRequests.nonEmpty) {
              runBuild(owner, repository, sha, pusher, config)
            }
          }
        }
      }
    }
  }

  private def runBuild(owner: String, repository: String, sha: String, pusher: String, config: CIConfig)
                      (implicit session: Session): Unit = {
    getAccountByUserName(pusher).foreach { pusherAccount =>
      val buildNumber = runBuild(owner, repository, sha, pusherAccount, config)
      val targetUrl = loadSystemSettings().baseUrl.map { baseUrl =>
        s"${baseUrl}/${owner}/${repository}/build/${buildNumber}"
      }
      createCommitStatus(
        userName       = owner,
        repositoryName = repository,
        sha            = sha,
        context        = CIUtils.ContextName,
        state          = CommitState.PENDING,
        targetUrl      = targetUrl,
        description    = None,
        now            = new java.util.Date(),
        creator        = pusherAccount
      )
    }
  }

}
