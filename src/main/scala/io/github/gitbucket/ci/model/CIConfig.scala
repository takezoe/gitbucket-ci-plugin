package io.github.gitbucket.ci.model

trait CIConfigComponent { self: gitbucket.core.model.Profile =>
  import profile.api._
  import self._

  lazy val CIConfigs = TableQuery[CIConfigs]

  class CIConfigs(tag: Tag) extends Table[CIConfig](tag, "CI_CONFIG") {
    val userName = column[String]("USER_NAME", O PrimaryKey)
    val fullName = column[String]("FULL_NAME")
    val buildScript = column[String]("BUILD_CONFIG")
    def * = (userName, fullName, buildScript) <> (CIConfig.tupled, CIConfig.unapply)
  }
}

case class CIConfig(
  userName: String,
  fullName: String,
  buildScript: String
)
