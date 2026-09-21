package io.github.gitbucket.ci.manager

import java.io.File
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue

import gitbucket.core.model.Account
import io.github.gitbucket.ci.model.CIConfig
import io.github.gitbucket.ci.service.BuildJob
import org.scalatest.funsuite.AnyFunSuite

class BuildJobThreadSpec extends AnyFunSuite {

  test("runDockerComposeJob does not run after a failed build") {
    val now = new java.util.Date()
    val account = Account(0L, "root", "root", "root@x", "", false, None, now, now, None, None, false, false, None)
    val config = CIConfig("root", "test", "docker-compose", "", false, None, None, false)
    val job = BuildJob("root", "test", "root", "test", 1, "master", "0" * 40, "msg", "root", "root@x", None, now, None, account, config)

    val thread = new BuildJobThread(new LinkedBlockingQueue[BuildJob](), new LinkedBlockingQueue[BuildJobThread]())
    val method = classOf[BuildJobThread].getDeclaredMethod(
      "runDockerComposeJob", classOf[BuildJob], classOf[File], classOf[File], classOf[String])
    method.setAccessible(true)

    // "build" exits 1 (failure); run/down would exit 42 if they were ever invoked
    val fakeCompose = """sh -c 'test "$3" = build && exit 1; exit 42' --"""
    val dir = Files.createTempDirectory("ci-test").toFile

    val result = method.invoke(thread, job, dir, dir, fakeCompose).asInstanceOf[Integer].intValue()
    assert(result == 1, "must return the build's own exit code, not run's, when the build fails")
  }

}
