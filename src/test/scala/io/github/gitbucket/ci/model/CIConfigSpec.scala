package io.github.gitbucket.ci.model

import org.scalatest.funsuite.AnyFunSuite

class CIConfigSpec extends AnyFunSuite {

  private def config(
    skipWords: Option[String] = None,
    runWords: Option[String] = None,
    buildForkPullRequests: Boolean = false
  ): CIConfig =
    CIConfig(
      userName = "root",
      repositoryName = "test",
      buildType = "script",
      buildScript = "echo hello",
      notification = false,
      skipWords = skipWords,
      runWords = runWords,
      buildForkPullRequests = buildForkPullRequests
    )

  test("skipWordsSeq is empty when skipWords is not set") {
    assert(config(skipWords = None).skipWordsSeq == Nil)
  }

  test("skipWordsSeq splits on comma and trims whitespace") {
    assert(config(skipWords = Some("[ci skip], [skip ci]")).skipWordsSeq == Seq("[ci skip]", "[skip ci]"))
  }

  test("skipWordsSeq handles a single word with no comma") {
    assert(config(skipWords = Some("[ci skip]")).skipWordsSeq == Seq("[ci skip]"))
  }

  test("runWordsSeq is empty when runWords is not set") {
    assert(config(runWords = None).runWordsSeq == Nil)
  }

  test("runWordsSeq splits on comma and trims whitespace") {
    assert(config(runWords = Some("ok to test, test this please")).runWordsSeq == Seq("ok to test", "test this please"))
  }

  test("allowsBuild is always true for a same-repo branch, regardless of the fork flag") {
    assert(config(buildForkPullRequests = false).allowsBuild(isFork = false))
    assert(config(buildForkPullRequests = true).allowsBuild(isFork = false))
  }

  test("allowsBuild rejects fork-originated code by default") {
    assert(!config(buildForkPullRequests = false).allowsBuild(isFork = true))
  }

  test("allowsBuild permits fork-originated code once explicitly enabled") {
    assert(config(buildForkPullRequests = true).allowsBuild(isFork = true))
  }

}
