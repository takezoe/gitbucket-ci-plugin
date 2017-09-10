package io.github.gitbucket.ci.model

trait CIResultComponent { self: gitbucket.core.model.Profile =>
  import profile.api._
  import self._

  lazy val CIResults = TableQuery[CIResults]

  class CIResults(tag: Tag) extends Table[CIResult](tag, "CI_RESULT") {
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val repositoryName = column[String]("REPOSITORY_NAME")
    val buildNumber = column[Int]("BUILD_NUMBER")
    val sha = column[String]("SHA")
    val startTime = column[java.util.Date]("START_TIME")
    val endTime = column[java.util.Date]("END_TIME")
    val status = column[String]("STATUS")
    def * = (userName, repositoryName, buildNumber, sha, startTime, endTime, status) <> (CIResult.tupled, CIResult.unapply)
  }
}

case class CIResult(
  userName: String,
  repositoryName: String,
  buildNumber: Int,
  sha: String,
  startTime: java.util.Date,
  endTime: java.util.Date,
  status: String
)
