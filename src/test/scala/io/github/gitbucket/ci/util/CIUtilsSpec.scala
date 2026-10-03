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

}
