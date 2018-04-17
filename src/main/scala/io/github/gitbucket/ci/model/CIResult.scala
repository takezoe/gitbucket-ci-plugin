package io.github.gitbucket.ci.model

import io.github.gitbucket.ci.util.JobStatus

trait CIResultComponent { self: gitbucket.core.model.Profile =>
  import profile.api._
  import self._

  lazy val CIResults = TableQuery[CIResults]

  class CIResults(tag: Tag) extends Table[CIResult](tag, "CI_RESULT") {
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val repositoryName = column[String]("REPOSITORY_NAME")
    val buildUserName = column[String]("BUILD_USER_NAME")
    val buildRepositoryName = column[String]("BUILD_REPOSITORY_NAME")
    val buildNumber = column[Int]("BUILD_NUMBER")
    val buildBranch = column[String]("BUILD_BRANCH")
    val sha = column[String]("SHA")
    val commitMessage = column[String]("COMMIT_MESSAGE")
    val commitUserName = column[String]("COMMIT_USER_NAME")
    val commitMailAddress = column[String]("COMMIT_MAIL_ADDRESS")
    val pullRequestId = column[Int]("PULL_REQUEST_ID")
    val queuedTime = column[java.util.Date]("QUEUED_TIME")
    val startTime = column[java.util.Date]("START_TIME")
    val endTime = column[java.util.Date]("END_TIME")
    val exitCode = column[Int]("EXIT_CODE")
    val status = column[String]("STATUS")
    val buildAuthor = column[String]("BUILD_AUTHOR")
    val buildScript = column[String]("BUILD_SCRIPT")
    def * = (userName, repositoryName, buildUserName, buildRepositoryName, buildNumber, buildBranch, sha, commitMessage, commitUserName, commitMailAddress, pullRequestId.?, queuedTime, startTime, endTime, exitCode, status, buildAuthor, buildScript) <> (CIResult.tupled, CIResult.unapply)
  }
}

case class CIResult(
  userName: String,
  repositoryName: String,
  buildUserName: String,
  buildRepositoryName: String,
  buildNumber: Int,
  buildBranch: String,
  sha: String,
  commitMessage: String,
  commitUserName: String,
  commitMailAddress: String,
  pullRequestId: Option[Int],
  queuedTime: java.util.Date,
  startTime: java.util.Date,
  endTime: java.util.Date,
  exitCode: Int,
  status: String,
  buildAuthor: String,
  buildScript: String
){
  lazy val apiStatus = if(status == JobStatus.Success) "success" else "failed"
}
