package io.github.gitbucket.ci.util

import java.io.{ByteArrayOutputStream, File}

import gitbucket.core.util.Directory
import org.fusesource.jansi.HtmlAnsiOutputStream

import scala.util.Using

object CIUtils {

  val ContextName = "gitbucket-ci"

  def getBuildDir(userName: String, repositoryName: String, buildNumber: Int): File = {
    val dir = Directory.getRepositoryFilesDir(userName, repositoryName)
    new java.io.File(dir, s"build/${buildNumber}")
  }

  def colorize(text: String) = {
    Using.resource(new ByteArrayOutputStream()){ os =>
      Using.resource(new HtmlAnsiOutputStream(os)){ hos =>
        hos.write(text.getBytes("UTF-8"))
      }
      new String(os.toByteArray, "UTF-8")
    }
  }

  def isWindows: Boolean = File.separatorChar == '\\'

}
