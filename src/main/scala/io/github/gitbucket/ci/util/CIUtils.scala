package io.github.gitbucket.ci.util

import java.io.ByteArrayOutputStream

import gitbucket.core.util.Directory
import gitbucket.core.util.SyntaxSugars.using
import io.github.gitbucket.ci.model.CIResult
import org.fusesource.jansi.HtmlAnsiOutputStream

object CIUtils {

  val ContextName = "gitbucket-ci"

  def getBuildResultDir(result: CIResult): java.io.File = {
    val dir = Directory.getRepositoryDir(result.userName, result.repositoryName)
    new java.io.File(dir, s"build/${result.buildNumber}")
  }

  def colorize(text: String) = {
    using(new ByteArrayOutputStream()){ os =>
      using(new HtmlAnsiOutputStream(os)){ hos =>
        hos.write(text.getBytes("UTF-8"))
      }
      new String(os.toByteArray, "UTF-8")
    }
  }

}
