package io.github.gitbucket.ci.hook

import org.scalatest.funsuite.AnyFunSuite

class CIPullRequestHookSpec extends AnyFunSuite {

  import CIPullRequestHook.shouldRunOnComment

  test("does not run when the commenter lacks write access, even with a matching run word") {
    assert(!shouldRunOnComment(isWriter = false, content = "ok to test", runWords = Seq("ok to test")))
  }

  test("does not run when the comment has no matching run word, even for a writer") {
    assert(!shouldRunOnComment(isWriter = true, content = "nice PR!", runWords = Seq("ok to test")))
  }

  test("runs only when the commenter is a writer and the comment matches a run word") {
    assert(shouldRunOnComment(isWriter = true, content = "ok to test", runWords = Seq("ok to test")))
  }

}
