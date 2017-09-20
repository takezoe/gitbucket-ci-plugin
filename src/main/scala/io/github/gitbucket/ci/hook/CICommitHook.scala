package io.github.gitbucket.ci.hook

import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.model.Profile._
import gitbucket.core.service._
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.JGitUtil
import gitbucket.core.util.SyntaxSugars.using
import io.github.gitbucket.ci.model.CIConfig
import io.github.gitbucket.ci.service.CIService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}
import profile.blockingApi._

class CICommitHook extends ReceiveHook
  with CIService with RepositoryService with AccountService with CommitStatusService with SystemSettingsService {

  override def postReceive(owner: String, repository: String, receivePack: ReceivePack,
                           command: ReceiveCommand, pusher: String)(implicit session: Session): Unit = {
    val branch = command.getRefName.stripPrefix("refs/heads/")
    if(branch != command.getRefName && command.getType != ReceiveCommand.Type.DELETE){
      getRepository(owner, repository).foreach { repositoryInfo =>
        using(Git.open(getRepositoryDir(owner, repository))) { git =>
          val sha = command.getNewId.name
          val revCommit = JGitUtil.getRevCommitFromId(git, command.getNewId)

          if (repositoryInfo.repository.defaultBranch == branch) {
            loadCIConfig(owner, repository).foreach { config =>
              runBuild(
                userName            = owner,
                repositoryName      = repository,
                buildUserName       = owner,
                buildRepositoryName = repository,
                buildBranch         = branch,
                sha                 = sha,
                commitMessage       = revCommit.getShortMessage,
                commitUserName      = revCommit.getCommitterIdent.getName,
                commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
                pullRequestId       = None,
                pusher              = pusher,
                config              = config
              )
            }
          } else {
            val pullRequests = PullRequests
              .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
              .filter { case (t1, t2) =>
                (t1.requestUserName === owner.bind) && (t1.requestRepositoryName === repository.bind) &&
                (t1.requestBranch === branch.bind) && (t2.closed === false.bind)
              }
              .list

            pullRequests.headOption.foreach { case (pullRequest, issue) =>
              loadCIConfig(pullRequest.userName, pullRequest.repositoryName).foreach { config =>
                runBuild(
                  userName            = pullRequest.userName,
                  repositoryName      = pullRequest.repositoryName,
                  buildUserName       = owner,
                  buildRepositoryName = repository,
                  buildBranch         = branch,
                  sha                 = sha,
                  commitMessage       = revCommit.getShortMessage,
                  commitUserName      = revCommit.getCommitterIdent.getName,
                  commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
                  pullRequestId       = Some(pullRequest.issueId),
                  pusher              = pusher,
                  config              = config
                )
              }
            }
          }
        }
      }
    }
  }

  private def runBuild(userName: String, repositoryName: String, buildUserName: String, buildRepositoryName: String,
                       buildBranch: String, sha: String, commitMessage: String, commitUserName: String, commitMailAddress: String,
                       pullRequestId: Option[Int], pusher: String, config: CIConfig)(implicit session: Session): Unit = {
    getAccountByUserName(pusher).foreach { pusherAccount =>
      runBuild(
        userName            = userName,
        repositoryName      = repositoryName,
        buildUserName       = buildUserName,
        buildRepositoryName = buildRepositoryName,
        buildBranch         = buildBranch,
        sha                 = sha,
        commitMessage       = commitMessage,
        commitUserName      = commitUserName,
        commitMailAddress   = commitMailAddress,
        pullRequestId       = pullRequestId,
        buildAuthor         = pusherAccount,
        config              = config
      )
    }
  }

}
