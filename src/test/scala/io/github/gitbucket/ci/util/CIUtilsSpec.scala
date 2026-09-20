package io.github.gitbucket.ci.util

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

}
