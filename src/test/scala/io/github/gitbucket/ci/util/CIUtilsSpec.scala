package io.github.gitbucket.ci.util

import java.io.File
import java.nio.file.Files

import org.apache.commons.io.FileUtils
import org.scalatest.funsuite.AnyFunSuite

class CIUtilsSpec extends AnyFunSuite {

  test("getBuildDir builds a path under the repository files dir, namespaced by build number") {
    val dir = CIUtils.getBuildDir("root", "test", 42)
    assert(dir.getPath.replace('\\', '/').endsWith("root/test/build/42"))
  }

  test("isWindows reflects the platform's file separator") {
    assert(CIUtils.isWindows == (java.io.File.separatorChar == '\\'))
  }

  test("colorize renders plain text unchanged (no ANSI codes)") {
    assert(CIUtils.colorize("hello world") == "hello world")
  }

  test("colorize converts ANSI color codes into HTML span markup") {
    val ansiRed = "[31mfailure[0m"
    val html = CIUtils.colorize(ansiRed)
    assert(html.contains("failure"))
    assert(html.contains("<span") || html.contains("style="))
    assert(!html.contains(""))
  }

  test("logFrom returns only what was logged since the given offset") {
    val log = new StringBuffer("line 1\nline 2\n")
    val (start, text) = CIUtils.logFrom(log, 7)
    assert(start == 7 && text == "line 2\n")

    log.append("line 3\n")
    assert(CIUtils.logFrom(log, start + text.length) == (14, "line 3\n"))
    assert(CIUtils.logFrom(log, log.length) == (log.length, ""), "nothing new yet")
  }

  test("logFrom starts over with the whole log when the offset doesn't fit it") {
    val log = new StringBuffer("line 1\n")
    assert(CIUtils.logFrom(log, 0) == (0, "line 1\n"))
    assert(CIUtils.logFrom(log, 99) == (0, "line 1\n"))
    assert(CIUtils.logFrom(log, -1) == (0, "line 1\n"))
  }

  test("buildsToTrim keeps the newest builds, whatever order they come in") {
    assert(CIUtils.buildsToTrim(Seq(3, 7, 5, 1), keep = 2).sorted == Seq(1, 3))
  }

  test("buildsToTrim trims every build when keep is 0, and none when keep covers them all") {
    assert(CIUtils.buildsToTrim(Seq(1, 2, 3), keep = 0).sorted == Seq(1, 2, 3))
    assert(CIUtils.buildsToTrim(Seq(1, 2, 3), keep = 3).isEmpty)
    assert(CIUtils.buildsToTrim(Seq(1, 2, 3), keep = -1).sorted == Seq(1, 2, 3))
  }

  test("trimBuildDir deletes the workspace and HOME caches but keeps the build log") {
    val buildDir = Files.createTempDirectory("ci-build").toFile
    try {
      FileUtils.write(new File(buildDir, "output"), "log", "UTF-8")
      FileUtils.write(new File(buildDir, "workspace/target/app.jar"), "jar", "UTF-8")
      FileUtils.write(new File(buildDir, ".m2/repository/x.pom"), "pom", "UTF-8")
      FileUtils.write(new File(buildDir, "build.sh"), "echo", "UTF-8")

      val trimmed = CIUtils.trimBuildDir(buildDir)
      assert(trimmed.map(_._1.getName).sorted == Seq(".m2", "build.sh", "workspace"))
      assert(trimmed.forall(_._2), "every entry must report a successful delete")
      assert(buildDir.list().toSeq == Seq("output"))
      assert(FileUtils.readFileToString(new File(buildDir, "output"), "UTF-8") == "log")

      assert(CIUtils.trimBuildDir(buildDir).isEmpty, "an already trimmed directory has nothing left to delete")
    } finally {
      FileUtils.deleteQuietly(buildDir)
    }
  }

  test("trimBuildDir reports the entries it could not delete") {
    // A read-only directory doesn't stop root (or Windows) from deleting what's in it
    assume(!CIUtils.isWindows && System.getProperty("user.name") != "root")
    val buildDir = Files.createTempDirectory("ci-build").toFile
    val locked = new File(buildDir, "workspace")
    try {
      FileUtils.write(new File(buildDir, "output"), "log", "UTF-8")
      FileUtils.write(new File(locked, "app.jar"), "jar", "UTF-8")
      FileUtils.write(new File(buildDir, "build.sh"), "echo", "UTF-8")
      locked.setWritable(false)

      val trimmed = CIUtils.trimBuildDir(buildDir).map { case (file, deleted) => (file.getName, deleted) }
      assert(trimmed.sorted == Seq(("build.sh", true), ("workspace", false)))
    } finally {
      locked.setWritable(true)
      FileUtils.deleteQuietly(buildDir)
    }
  }

  test("trimBuildDir tolerates a missing build directory") {
    assert(CIUtils.trimBuildDir(new File(Files.createTempDirectory("ci-build").toFile, "missing")).isEmpty)
  }

}
