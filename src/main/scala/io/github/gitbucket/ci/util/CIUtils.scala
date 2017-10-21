package io.github.gitbucket.ci.util

import java.io.{ByteArrayOutputStream, File}

import gitbucket.core.util.Directory
import gitbucket.core.util.SyntaxSugars.using
import org.fusesource.jansi.HtmlAnsiOutputStream

object CIUtils {

  val ContextName = "gitbucket-ci"

  def getBuildDir(userName: String, repositoryName: String, buildNumber: Int): File = {
    val dir = Directory.getRepositoryFilesDir(userName, repositoryName)
    new java.io.File(dir, s"build/${buildNumber}")
  }

  def colorize(text: String) = {
    using(new ByteArrayOutputStream()){ os =>
      using(new HtmlAnsiOutputStream(os)){ hos =>
        hos.write(text.getBytes("UTF-8"))
      }
      new String(os.toByteArray, "UTF-8")
    }
  }

  def isWindows: Boolean = File.separatorChar == '\\'

}
