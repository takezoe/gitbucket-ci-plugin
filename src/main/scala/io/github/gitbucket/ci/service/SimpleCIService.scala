package io.github.gitbucket.ci.service

import java.io.File

import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import org.eclipse.jgit.api.Git
import scala.sys.process._

trait SimpleCIService {

  def saveBuildSetting(setting: Option[BuildSetting]): Unit = {
    // TODO
  }

  def loadBuildSetting(userName: String, repositoryName: String): Option[BuildSetting] = {
    // TODO
    None
  }

  def getBuildResults(userName: String, repositoryName: String): Seq[BuildResult] = Nil

  def runBuild(userName: String, repositoryName: String, branch: String, setting: BuildSetting): Unit = {
    val startTime = System.currentTimeMillis

    val dir = new File("/tmp/" + repositoryName)

    Git.cloneRepository()
      .setURI(getRepositoryDir(userName, repositoryName).toURI.toString)
      .setDirectory(dir)
      .call()

    val process = Process(setting.script, dir).run(new ProcessLogger {
      override def err(s: => String): Unit = println(s)
      override def out(s: => String): Unit = println(s)
      override def buffer[T](f: => T): T = ???
    })

    while(process.isAlive()){
      Thread.sleep(1000)
    }

    val endTime = System.currentTimeMillis

    val exitValue = process.exitValue()
    
    println("Total: " + (endTime - startTime) + "msec")
    println("Finish build with exit code: " + exitValue)
  }

}

case class BuildSetting(userName: String, repositoryName: String, script: String)

case class BuildResult(userName: String, repositoryName: String, branch: String,
  buildNumber: Long, success: Boolean, start: Long, end: Long, output: String)