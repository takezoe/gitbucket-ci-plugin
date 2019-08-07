package io.github.gitbucket.ci.hook

import gitbucket.core.controller.Context
import gitbucket.core.model.Issue
import gitbucket.core.plugin.PullRequestHook
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.model.Profile._
import gitbucket.core.service._
import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.JGitUtil
import io.github.gitbucket.ci.service.CIService
import org.eclipse.jgit.api.Git
import profile.api._
import scala.util.Using

class CIPullRequestHook extends PullRequestHook
  with PullRequestService with IssuesService with CommitsService with AccountService with WebHookService
  with WebHookPullRequestService with WebHookPullRequestReviewCommentService with ActivityService with MergeService
  with RepositoryService with LabelsService with PrioritiesService with MilestonesService with CIService {

  override def created(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = {
    if(issue.isPullRequest){
      for {
        (_, pullreq) <- getPullRequest(issue.userName, issue.repositoryName, issue.issueId)
        buildAuthor  <- context.loginAccount
        buildConfig  <- loadCIConfig(pullreq.userName, pullreq.repositoryName)
      } yield {
        val revCommit = Using.resource(Git.open(getRepositoryDir(pullreq.requestUserName, pullreq.requestRepositoryName))) { git =>
          val objectId = git.getRepository.resolve(pullreq.commitIdTo)
          JGitUtil.getRevCommitFromId(git, objectId)
        }
        runBuild(
          userName            = pullreq.userName,
          repositoryName      = pullreq.repositoryName,
          buildUserName       = pullreq.requestUserName,
          buildRepositoryName = pullreq.requestRepositoryName,
          buildBranch         = pullreq.requestBranch,
          sha                 = pullreq.commitIdTo,
          commitMessage       = revCommit.getShortMessage,
          commitUserName      = revCommit.getCommitterIdent.getName,
          commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
          pullRequestId       = Some(pullreq.issueId),
          buildAuthor         = buildAuthor,
          config              = buildConfig
        )
      }
    }
  }

  override def addedComment(commentId: Int, content: String, issue: Issue, repository: RepositoryInfo)
                           (implicit session: Session, context: Context): Unit = {
    if(issue.isPullRequest){
      for {
        (_, pullreq) <- getPullRequest(issue.userName, issue.repositoryName, issue.issueId)
        buildAuthor  <- context.loginAccount
        buildConfig  <- loadCIConfig(pullreq.userName, pullreq.repositoryName)
      } yield {
        if(!buildConfig.runWordsSeq.find(content.contains).isEmpty){
          val revCommit = Using.resource(Git.open(getRepositoryDir(pullreq.requestUserName, pullreq.requestRepositoryName))) { git =>
            val objectId = git.getRepository.resolve(pullreq.commitIdTo)
            JGitUtil.getRevCommitFromId(git, objectId)
          }
          runBuild(
            userName            = pullreq.userName,
            repositoryName      = pullreq.repositoryName,
            buildUserName       = pullreq.requestUserName,
            buildRepositoryName = pullreq.requestRepositoryName,
            buildBranch         = pullreq.requestBranch,
            sha                 = pullreq.commitIdTo,
            commitMessage       = revCommit.getShortMessage,
            commitUserName      = revCommit.getCommitterIdent.getName,
            commitMailAddress   = revCommit.getCommitterIdent.getEmailAddress,
            pullRequestId       = Some(pullreq.issueId),
            buildAuthor         = buildAuthor,
            config              = buildConfig
          )
        }
      }
    }
  }


}
