package io.github.gitbucket.ci.model

import io.github.gitbucket.ci.util.JobStatus
import org.scalatest.funsuite.AnyFunSuite

class CIResultSpec extends AnyFunSuite {

  private def result(status: String): CIResult =
    CIResult(
      userName = "root",
      repositoryName = "test",
      buildUserName = "root",
      buildRepositoryName = "test",
      buildNumber = 1,
      buildBranch = "master",
      sha = "0" * 40,
      commitMessage = "message",
      commitUserName = "root",
      commitMailAddress = "root@localhost",
      pullRequestId = None,
      queuedTime = new java.util.Date(),
      startTime = new java.util.Date(),
      endTime = new java.util.Date(),
      exitCode = if (status == JobStatus.Success) 0 else 1,
      status = status,
      buildAuthor = "root",
      buildScript = "echo hello"
    )

  test("apiStatus is 'success' when status is success") {
    assert(result(JobStatus.Success).apiStatus == "success")
  }

  test("apiStatus is 'failed' when status is failure") {
    assert(result(JobStatus.Failure).apiStatus == "failed")
  }

  test("apiStatus is 'failed' for any non-success status") {
    assert(result("cancelled").apiStatus == "failed")
  }

}
