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

object CIPullRequestHook {

  /**
   * Whether a PR comment should re-trigger a build: the commenter must be a writer
   * on the repository AND the comment must contain a configured run word.
   */
  private[hook] def shouldRunOnComment(isWriter: Boolean, content: String, runWords: Seq[String]): Boolean =
    isWriter && runWords.exists(content.contains)

}

class CIPullRequestHook extends PullRequestHook
  with PullRequestService with IssuesService with CommitsService with AccountService with WebHookService
  with WebHookPullRequestService with WebHookPullRequestReviewCommentService with ActivityService with MergeService
  with RepositoryService with LabelsService with PrioritiesService with MilestonesService with CIService 
  with RequestCache {

  private def runBuildWith(issue: Issue, repository: RepositoryInfo, isMergeRequest: Boolean)(implicit session: Session, context: Context): Unit = {
    if(issue.isPullRequest){
      for {
        (_, pullreq) <- getPullRequest(issue.userName, issue.repositoryName, issue.issueId)
        buildAuthor  <- context.loginAccount
        buildConfig  <- loadCIConfig(pullreq.userName, pullreq.repositoryName)
      } yield {
        val isFork = pullreq.requestUserName != pullreq.userName || pullreq.requestRepositoryName != pullreq.repositoryName
        // A merge builds the base repo's own (already-reviewed) branch, so the fork gate doesn't apply.
        if(isMergeRequest || buildConfig.allowsBuild(isFork)){
          val revCommit = Using.resource(Git.open(getRepositoryDir(pullreq.requestUserName, pullreq.requestRepositoryName))) { git =>
            val objectId = git.getRepository.resolve(pullreq.commitIdTo)
            JGitUtil.getRevCommitFromId(git, objectId)
          }
          runBuild(
            userName            = pullreq.userName,
            repositoryName      = pullreq.repositoryName,
            buildUserName       = pullreq.requestUserName,
            buildRepositoryName = pullreq.requestRepositoryName,
            buildBranch         = isMergeRequest match {
                case true => pullreq.branch
                case false => pullreq.requestBranch
            },
            sha                 = isMergeRequest match {
                case true => Using.resource(Git.open(getRepositoryDir(pullreq.userName, pullreq.repositoryName))) { git =>
                  val objectId = git.getRepository.resolve(pullreq.branch)
                  objectId.name
                }
                case false => pullreq.commitIdTo
            },
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

  override def created(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = 
    runBuildWith(issue, repository, false)

  override def merged(issue: Issue, repository: RepositoryInfo)(implicit session: Session, context: Context): Unit = 
    runBuildWith(issue, repository, true)


  override def addedComment(commentId: Int, content: String, issue: Issue, repository: RepositoryInfo)
                           (implicit session: Session, context: Context): Unit = {
    if(issue.isPullRequest){
      for {
        (_, pullreq) <- getPullRequest(issue.userName, issue.repositoryName, issue.issueId)
        buildAuthor  <- context.loginAccount
        buildConfig  <- loadCIConfig(pullreq.userName, pullreq.repositoryName)
      } yield {
        val isWriter = isWritable(repository.repository, Some(buildAuthor))
        if(CIPullRequestHook.shouldRunOnComment(isWriter, content, buildConfig.runWordsSeq)){
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
