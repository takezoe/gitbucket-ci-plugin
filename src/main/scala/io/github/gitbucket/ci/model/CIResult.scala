package io.github.gitbucket.ci.model

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
    val pullRequestId = column[Int]("PULL_REQUEST_ID")
    val startTime = column[java.util.Date]("START_TIME")
    val endTime = column[java.util.Date]("END_TIME")
    val status = column[String]("STATUS")
    val buildAuthor = column[String]("BUILD_AUTHOR")
    def * = (userName, repositoryName, buildUserName, buildRepositoryName, buildNumber, buildBranch, sha, commitMessage, commitUserName, pullRequestId.?, startTime, endTime, status, buildAuthor) <> (CIResult.tupled, CIResult.unapply)
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
  pullRequestId: Option[Int],
  startTime: java.util.Date,
  endTime: java.util.Date,
  status: String,
  buildAuthor: String
)
