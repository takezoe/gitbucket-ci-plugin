package io.github.gitbucket.ci.util

import java.io.{ByteArrayOutputStream, File}

import gitbucket.core.util.Directory
import org.apache.commons.io.FileUtils
import org.fusesource.jansi.HtmlAnsiOutputStream

import scala.util.Using

object CIUtils {

  val ContextName = "gitbucket-ci"

  /** The build log, the only file kept once a build directory is trimmed. */
  val OutputFileName = "output"

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

  /** The builds whose directories get trimmed: all but the newest `keep`. */
  def buildsToTrim(buildNumbers: Seq[Int], keep: Int): Seq[Int] =
    buildNumbers.sorted(Ordering[Int].reverse).drop(keep max 0)

  /**
   * Deletes everything in a build directory but its log: the workspace, and the caches tools put
   * under HOME (which is the build directory). Returns whether anything was deleted.
   */
  def trimBuildDir(buildDir: File): Boolean = {
    val files = Option(buildDir.listFiles).toSeq.flatten.filterNot(_.getName == OutputFileName)
    files.foreach(FileUtils.deleteQuietly)
    files.nonEmpty
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
