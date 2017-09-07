import gitbucket.core.controller.Context
import gitbucket.core.plugin.Link
import gitbucket.core.service.RepositoryService
import io.github.gitbucket.ci.controller.SimpleCIController
import io.github.gitbucket.ci.service.BuildManager
import io.github.gitbucket.solidbase.model.Version

class Plugin extends gitbucket.core.plugin.Plugin {
  override val pluginId: String = "ci"
  override val pluginName: String = "CI Plugin"
  override val description: String = "This plugin adds simple CI functionality to GitBucket."
  override val versions: List[Version] = List(new Version("1.0.0"))

  override val controllers = Seq(
    "/*" -> new SimpleCIController()
  )

  override val repositoryMenus = Seq(
    (repository: RepositoryService.RepositoryInfo, context: Context) => Some(Link("build", "Build", "/build"))
  )

  BuildManager.startBuildManager()
}
