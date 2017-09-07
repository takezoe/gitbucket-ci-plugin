package io.github.gitbucket.ci.service

import java.io.File
import java.util.concurrent.ConcurrentHashMap

import gitbucket.core.util.Directory.getRepositoryDir
import gitbucket.core.util.SyntaxSugars.using
import org.eclipse.jgit.api.Git

import scala.sys.process._

trait SimpleCIService {

  val MaxBuildPerProject = 10

  val buildSettings = new ConcurrentHashMap[(String, String), BuildSetting]()
  val buildResults = new ConcurrentHashMap[(String, String), Seq[BuildResult]]()

  def saveBuildSetting(userName: String, repositoryName: String, setting: Option[BuildSetting]): Unit = {
    setting match {
      case Some(setting) => buildSettings.put((userName, repositoryName), setting)
      case None => buildSettings.remove((userName, repositoryName))
    }
  }

  def loadBuildSetting(userName: String, repositoryName: String): Option[BuildSetting] = {
    Option(buildSettings.get((userName, repositoryName)))
  }

  def getBuildResults(userName: String, repositoryName: String): Seq[BuildResult] = {
    Option(buildResults.get((userName, repositoryName))).getOrElse(Nil)
  }

  def runBuild(userName: String, repositoryName: String, branch: String, setting: BuildSetting): Unit = {
    val startTime = System.currentTimeMillis

    val results = Option(buildResults.get((userName, repositoryName))).getOrElse(Nil)
    val buildNumber = (results.map(_.buildNumber) match {
      case Nil => 0
      case seq => seq.max
    }) + 1

    val dir = new File(s"/tmp/${userName}-${repositoryName}-${buildNumber}")

    Git.cloneRepository()
      .setURI(getRepositoryDir(userName, repositoryName).toURI.toString)
      .setDirectory(dir)
      .setBranch(branch)
      .call()

    val sb = new StringBuilder()

    val process = Process(setting.script, dir).run(new ProcessLogger {
      override def err(s: => String): Unit = {
        sb.append(s)
        println(s) // TODO Debug
      }
      override def out(s: => String): Unit = {
        sb.append(s)
        println(s) // TODO Debug
      }
      override def buffer[T](f: => T): T = ???
    })

    while(process.isAlive()){
      Thread.sleep(1000)
    }

    val endTime = System.currentTimeMillis

    val exitValue = process.exitValue()

    val result = BuildResult(userName, repositoryName, branch, buildNumber, exitValue == 0, startTime, endTime, sb.toString)

    buildResults.put((userName, repositoryName),
      (if(results.length >= MaxBuildPerProject) results.tail else results) :+ result
    )

    println("Build number: " + buildNumber)
    println("Total: " + (endTime - startTime) + "msec")
    println("Finish build with exit code: " + exitValue)
  }

}

case class BuildSetting(userName: String, repositoryName: String, script: String)

case class BuildResult(userName: String, repositoryName: String, branch: String,
  buildNumber: Long, success: Boolean, start: Long, end: Long, output: String)