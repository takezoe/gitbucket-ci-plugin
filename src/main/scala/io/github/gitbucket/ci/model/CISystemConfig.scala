package io.github.gitbucket.ci.model

trait CISystemConfigComponent { self: gitbucket.core.model.Profile =>
  import profile.api._
  import self._

  lazy val CISystemConfigs = TableQuery[CISystemConfigs]

  class CISystemConfigs(tag: Tag) extends Table[CISystemConfig](tag, "CI_SYSTEM_CONFIG") {
    val maxBuildHistory = column[Int]("MAX_BUILD_HISTORY")
    val maxParallelBuilds = column[Int]("MAX_PARALLEL_BUILDS")
    val enableDocker = column[Boolean]("ENABLE_DOCKER")
    val dockerCommand = column[String]("DOCKER_COMMAND")
    val enableDockerCompose = column[Boolean]("ENABLE_DOCKER_COMPOSE")
    val dockerComposeCommand = column[String]("DOCKER_COMPOSE_COMMAND")
    val buildTimeoutMinutes = column[Int]("BUILD_TIMEOUT_MINUTES")
    val maxKeptWorkspaces = column[Int]("MAX_KEPT_WORKSPACES")
    def * = (maxBuildHistory, maxParallelBuilds, enableDocker, dockerCommand.?, enableDockerCompose, dockerComposeCommand.?, buildTimeoutMinutes, maxKeptWorkspaces) <> (CISystemConfig.tupled, CISystemConfig.unapply)
  }
}

case class CISystemConfig(
  maxBuildHistory: Int,
  maxParallelBuilds: Int,
  enableDocker: Boolean,
  dockerCommand: Option[String],
  enableDockerCompose: Boolean,
  dockerComposeCommand: Option[String],
  // Wall-clock cap on a whole build, across all processes it spawns; <= 0 disables the timeout.
  buildTimeoutMinutes: Int,
  // Only the newest N builds of a repository keep their build directory; older ones keep just their output log.
  maxKeptWorkspaces: Int
)
