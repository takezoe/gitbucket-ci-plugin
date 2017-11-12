import java.io.File

name := "gitbucket-ci-plugin"

organization := "io.github.gitbucket"

version := "1.2.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "io.github.gitbucket" %% "gitbucket"         % "4.17.0" % "provided",
  "org.fusesource.jansi" % "jansi"             % "1.16",
  "javax.servlet"        % "javax.servlet-api" % "3.1.0"  % "provided"
)

resolvers += Resolver.bintrayRepo("bkromhout", "maven")

enablePlugins(SbtTwirl)
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)

val installKey = TaskKey[Unit]("install")
installKey := {
  val file = assembly.value

  val GitBucketHome = (System.getProperty("gitbucket.home") match {
    // -Dgitbucket.home=<path>
    case path if(path != null) => new File(path)
    case _ => scala.util.Properties.envOrNone("GITBUCKET_HOME") match {
      // environment variable GITBUCKET_HOME
      case Some(env) => new File(env)
      // default is HOME/.gitbucket
      case None => {
        val oldHome = new File(System.getProperty("user.home"), "gitbucket")
        if(oldHome.exists && oldHome.isDirectory && new File(oldHome, "version").exists){
          //FileUtils.moveDirectory(oldHome, newHome)
          oldHome
        } else {
          new File(System.getProperty("user.home"), ".gitbucket")
        }
      }
    }
  })

  val PluginDir = new File(GitBucketHome, "plugins")
  if(!PluginDir.exists){
    PluginDir.mkdirs()
  }

  val log = streams.value.log
  log.info(s"Copying ${file.getAbsolutePath} to ${PluginDir.getAbsolutePath}")

  IO.copyFile(file, new File(PluginDir, file.getName))
}