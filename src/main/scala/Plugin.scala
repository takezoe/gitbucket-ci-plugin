import java.util
import javax.servlet.ServletContext

import gitbucket.core.controller.Context
import gitbucket.core.plugin._
import gitbucket.core.service.RepositoryService.RepositoryInfo
import gitbucket.core.service.{AccountService, RepositoryService, SystemSettingsService}
import gitbucket.core.util.Directory
import gitbucket.core.model.Profile.profile.blockingApi._
import io.github.gitbucket.ci.controller.{CIApiController, CIController}
import io.github.gitbucket.ci.hook.{CICommitHook, CIPullRequestHook, CIRepositoryHook}
import io.github.gitbucket.ci.manager.BuildManager
import io.github.gitbucket.solidbase.migration.LiquibaseMigration
import io.github.gitbucket.solidbase.model.Version
import java.io.File

import gitbucket.core.service.SystemSettingsService.SystemSettings
import gitbucket.core.servlet.Database
import io.github.gitbucket.ci.service.CIService
import org.apache.commons.io.FileUtils

class Plugin extends gitbucket.core.plugin.Plugin with CIService with AccountService with RepositoryService {

  override val pluginId: String = "ci"

  override val pluginName: String = "CI Plugin"

  override val description: String = "This plugin adds simple CI functionality to GitBucket."

  override val versions: List[Version] = List(
    new Version("1.0.0",
      new LiquibaseMigration("update/gitbucket-ci_1.0.0.xml")),
    new Version("1.0.1"),
    new Version("1.1.0",
      new LiquibaseMigration("update/gitbucket-ci_1.1.0.xml")),
    new Version("1.2.0", (moduleId: String, version: String, context: util.Map[String, AnyRef]) => {
      // Move repositories/USER/REPO.git/build to repositories/USER/REPO/build
      for {
        userDir <- {
          val dir = new File(Directory.RepositoryHome)
          if(dir.exists && dir.isDirectory) dir.listFiles(_.isDirectory).toSeq else Nil
        }
        repositoryDir <- userDir.listFiles(_.getName.endsWith(".git"))
        buildDir <- Seq(new File(repositoryDir, "build")).filter(f => f.exists && f.isDirectory)
      } yield {
        val userName = userDir.getName
        val repositoryName = repositoryDir.getName.replaceFirst("\\.git", "")
        val newBuildDir = new java.io.File(Directory.getRepositoryFilesDir(userName, repositoryName), "build")
        FileUtils.moveDirectory(buildDir, newBuildDir)
      }
    }),
    new Version("1.2.1"),
    new Version("1.3.0"),
    new Version("1.4.0",
      new LiquibaseMigration("update/gitbucket-ci_1.4.0.xml")),
    new Version("1.5.0",
      new LiquibaseMigration("update/gitbucket-ci_1.5.0.xml")),
    new Version("1.6.0",
      new LiquibaseMigration("update/gitbucket-ci_1.6.0.xml")),
    new Version("1.6.1"),
    new Version("1.6.2"),
    new Version("1.6.3")
  )

  override val assetsMappings = Seq("/ci" -> "/gitbucket/ci/assets")
  override val controllers = Seq("/*" -> new CIController(), "/api/circleci/v1.1/*" -> new CIApiController())

  override val repositoryMenus = Seq(
    (repository: RepositoryInfo, context: Context) => Some(Link("build", "Build", "/build", Some("sync")))
  )

  override val repositorySettingTabs = Seq(
    (repository: RepositoryInfo, context: Context) => Some(Link("build", "Build", "settings/build"))
  )

  override val systemSettingMenus: Seq[(Context) => Option[Link]] = Seq(
    (ctx: Context) => Some(Link("build", "Build", "admin/build", Some("gear")))
  )

  override val receiveHooks: Seq[ReceiveHook] = Seq(new CICommitHook())
  override val repositoryHooks: Seq[RepositoryHook] = Seq(new CIRepositoryHook())
  override val pullRequestHooks: Seq[PullRequestHook] = Seq(new CIPullRequestHook())

  override def initialize(registry: PluginRegistry, context: ServletContext, settings: SystemSettings): Unit = {
    super.initialize(registry, context, settings)

    Database() withTransaction { implicit session =>
      BuildManager.setMaxParallelBuilds(loadCISystemConfig().maxParallelBuilds)
    }
  }


  override def shutdown(registry: PluginRegistry, context: ServletContext,
                        settings: SystemSettingsService.SystemSettings): Unit = {
    BuildManager.shutdownBuildManager()
  }
}
