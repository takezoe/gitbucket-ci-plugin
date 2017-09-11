import javax.servlet.ServletContext

import gitbucket.core.controller.Context
import gitbucket.core.plugin.{Link, PluginRegistry, ReceiveHook}
import gitbucket.core.service.{RepositoryService, SystemSettingsService}
import io.github.gitbucket.ci.controller.CIController
import io.github.gitbucket.ci.hook.CICommitHook
import io.github.gitbucket.ci.manager.BuildManager
import io.github.gitbucket.solidbase.migration.LiquibaseMigration
import io.github.gitbucket.solidbase.model.Version

class Plugin extends gitbucket.core.plugin.Plugin {

  override val pluginId: String = "ci"

  override val pluginName: String = "CI Plugin"

  override val description: String = "This plugin adds simple CI functionality to GitBucket."

  override val versions: List[Version] = List(
    new Version("1.0.0",
      new LiquibaseMigration("update/gitbucket-ci_1.0.0.xml"))
  )

  override val assetsMappings = Seq("/ci" -> "/gitbucket/ci/assets")

  override val controllers = Seq(
    "/*" -> new CIController()
  )

  override val repositoryMenus = Seq(
    (repository: RepositoryService.RepositoryInfo, context: Context) => Some(Link("build", "Build", "/build"))
  )

  override val repositorySettingTabs = Seq(
    (repository: RepositoryService.RepositoryInfo, context: Context) => Some(Link("build", "Build", s"settings/build"))
  )

  override val receiveHooks: Seq[ReceiveHook] = Seq(new CICommitHook())

  BuildManager.startBuildManager()

  override def shutdown(registry: PluginRegistry, context: ServletContext,
                        settings: SystemSettingsService.SystemSettings): Unit = {
    BuildManager.shutdownBuildManager()
  }
}
