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

  /**
   * What a running build has logged since `from`, as (start, text). Starts over at 0 when `from` doesn't fit the
   * log, so callers replace rather than append whenever start is 0. Every append to the log is whole lines.
   */
  def logFrom(log: StringBuffer, from: Int): (Int, String) = log.synchronized {
    val start = if (from >= 0 && from <= log.length) from else 0
    (start, log.substring(start))
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
