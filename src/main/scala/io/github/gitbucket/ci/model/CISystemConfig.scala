package io.github.gitbucket.ci.model

trait CISystemConfigComponent { self: gitbucket.core.model.Profile =>
  import profile.api._
  import self._

  lazy val CISystemConfigs = TableQuery[CISystemConfigs]

  class CISystemConfigs(tag: Tag) extends Table[CISystemConfig](tag, "CI_SYSTEM_CONFIG") {
    val maxBuildHistory = column[Int]("MAX_BUILD_HISTORY")
    val maxParallelBuilds = column[Int]("MAX_PARALLEL_BUILDS")
    def * = (maxBuildHistory, maxParallelBuilds) <> (CISystemConfig.tupled, CISystemConfig.unapply)
  }
}

case class CISystemConfig(
  maxBuildHistory: Int,
  maxParallelBuilds: Int
)
