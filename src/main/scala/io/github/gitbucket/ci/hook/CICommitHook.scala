package io.github.gitbucket.ci.hook

import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.model.Profile._
import gitbucket.core.service._
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.JGitUtil
import io.github.gitbucket.ci.model.CIConfig
import io.github.gitbucket.ci.service.CIService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}
import profile.blockingApi._
import scala.util.Using

class CICommitHook extends ReceiveHook
  with CIService with RepositoryService with AccountService with CommitStatusService with SystemSettingsService {

  override def postReceive(owner: String, repository: String, receivePack: ReceivePack,
                           command: ReceiveCommand, pusher: String, mergePullRequest: Boolean)(implicit session: Session): Unit = {
    val branch = command.getRefName.stripPrefix("refs/heads/")
    if(branch != command.getRefName && command.getType != ReceiveCommand.Type.DELETE){
      getRepository(owner, repository).foreach { repositoryInfo =>
        Using.resource(Git.open(getRepositoryDir(owner, repository))) { git =>
          val sha = command.getNewId.name
          val revCommit = JGitUtil.getRevCommitFromId(git, command.getNewId)
          // A brand-new branch has no "before" commit; JGit represents that as the zero id.
          val beforeSha = Option(command.getOldId).filterNot(_.equals(ObjectId.zeroId)).map(_.name)

          loadCIConfig(owner, repository).foreach { buildConfig =>
            if(buildConfig.skipWordsSeq.find(revCommit.getFullMessage.contains).isEmpty){
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
                commitBeforeSha     = beforeSha,
                pullRequestId       = None,
                pullRequestTitle    = None,
                pullRequestTargetBranch = None,
                pusher              = pusher,
                config              = buildConfig
              )
            }
          }

          for {
            (pullreq, issue) <- PullRequests
              .join(Issues).on { (t1, t2) => t1.byPrimaryKey(t2.userName, t2.repositoryName, t2.issueId) }
              .filter { case (t1, t2) =>
                (t1.requestUserName === owner.bind) && (t1.requestRepositoryName === repository.bind) &&
                  (t1.requestBranch === branch.bind) && (t2.closed === false.bind)
              }
              .list
            buildConfig <- loadCIConfig(pullreq.userName, pullreq.repositoryName)
          } yield {
            val isFork = owner != pullreq.userName || repository != pullreq.repositoryName
            if(buildConfig.skipWordsSeq.find(revCommit.getFullMessage.contains).isEmpty && buildConfig.allowsBuild(isFork)){
              runBuild(
                userName            = pullreq.userName,
                repositoryName      = pullreq.repositoryName,
                buildUserName       = owner,
                buildRepositoryName = repository,
                buildBranch         = branch,
                sha                 = sha,
                commitMessage       = revCommit.getShortMessage,
                commitUserName      = revCommit.getCommitterIdent.getName,
                commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
                commitBeforeSha     = beforeSha,
                pullRequestId       = Some(pullreq.issueId),
                pullRequestTitle    = Some(issue.title),
                pullRequestTargetBranch = Some(pullreq.branch),
                pusher              = pusher,
                config              = buildConfig
              )
            }
          }
        }
      }
    }
  }

  private def runBuild(userName: String, repositoryName: String, buildUserName: String, buildRepositoryName: String,
                       buildBranch: String, sha: String, commitMessage: String, commitUserName: String, commitMailAddress: String,
                       pullRequestId: Option[Int], pusher: String, config: CIConfig,
                       commitBeforeSha: Option[String], pullRequestTitle: Option[String],
                       pullRequestTargetBranch: Option[String])(implicit session: Session): Unit = {
    getAccountByUserName(pusher).foreach { pusherAccount =>
      runBuild(
        userName                = userName,
        repositoryName          = repositoryName,
        buildUserName           = buildUserName,
        buildRepositoryName     = buildRepositoryName,
        buildBranch             = buildBranch,
        sha                     = sha,
        commitMessage           = commitMessage,
        commitUserName          = commitUserName,
        commitMailAddress       = commitMailAddress,
        commitBeforeSha         = commitBeforeSha,
        pullRequestId           = pullRequestId,
        pullRequestTitle        = pullRequestTitle,
        pullRequestTargetBranch = pullRequestTargetBranch,
        buildAuthor             = pusherAccount,
        config                  = config
      )
    }
  }

}
