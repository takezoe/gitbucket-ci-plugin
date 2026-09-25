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
    val job = BuildJob("root", "test", "root", "test", 1, "master", "0" * 40, "msg", "root", "root@x", None, None, None, None, now, None, account, config)

    val thread = new BuildJobThread(new LinkedBlockingQueue[BuildJob](), new LinkedBlockingQueue[BuildJobThread]())
    val method = classOf[BuildJobThread].getDeclaredMethod(
      "runDockerComposeJob", classOf[BuildJob], classOf[File], classOf[File], classOf[String], classOf[Long], classOf[Option[_]])
    method.setAccessible(true)

    // "build" exits 1 (failure); run/down would exit 42 if they were ever invoked
    val dir = Files.createTempDirectory("ci-test").toFile
    val fakeCompose = script(dir, """test "$3" = build && exit 1; exit 42""")

    val result = method.invoke(thread, job, dir, dir, fakeCompose, java.lang.Long.valueOf(Long.MaxValue), None).asInstanceOf[Integer].intValue()
    assert(result == 1, "must return the build's own exit code, not run's, when the build fails")
  }

  test("runProcess kills a hung process once its timeout elapses, freeing the worker thread") {
    val now = new java.util.Date()
    val account = Account(0L, "root", "root", "root@x", "", false, None, now, now, None, None, false, false, None)
    val config = CIConfig("root", "test", "script", "", false, None, None, false)
    val job = BuildJob("root", "test", "root", "test", 1, "master", "0" * 40, "msg", "root", "root@x", None, None, None, None, now, None, account, config)

    val thread = new BuildJobThread(new LinkedBlockingQueue[BuildJob](), new LinkedBlockingQueue[BuildJobThread]())
    val method = classOf[BuildJobThread].getDeclaredMethod(
      "runProcess", classOf[BuildJob], classOf[File], classOf[File], classOf[String], classOf[Long], classOf[Option[_]])
    method.setAccessible(true)

    val dir = Files.createTempDirectory("ci-test").toFile
    val start = System.currentTimeMillis()

    // A shell whose child sleeps far past the deadline, ignoring SIGTERM: the whole tree must be killed,
    // otherwise the child keeps the output pipe open and blocks the worker thread anyway.
    val pidFile = new File(dir, "child.pid")
    val command = script(dir, s"trap '' TERM; sleep 60 & echo $$! > ${pidFile.getAbsolutePath}; wait")
    val result = method.invoke(thread, job, dir, dir, command, java.lang.Long.valueOf(start + 300), None).asInstanceOf[Integer].intValue()
    val elapsed = System.currentTimeMillis() - start

    assert(result != 0, "a timed-out build must not report success")
    assert(elapsed < 15000, s"the timeout must bound how long the worker thread is occupied, took ${elapsed}ms")
    // Where nothing reaps orphans (e.g. `tail` as PID 1 in an act container), the killed child lingers as a
    // zombie, which isAlive still reports; it's dead and has released the pipe, so count it as killed.
    val childPid = Files.readString(pidFile.toPath).trim.toLong
    val stat = new File(s"/proc/$childPid/stat")
    val zombie = stat.exists() && Files.readString(stat.toPath).split("\\) ").last.startsWith("Z")
    assert(zombie || !ProcessHandle.of(childPid).map[Boolean](_.isAlive).orElse(false), "the build's child process must be killed too")
  }

  test("the timeout covers the whole build, not each process") {
    val method = classOf[BuildJobThread].getDeclaredMethod(
      "runDockerComposeJob", classOf[BuildJob], classOf[File], classOf[File], classOf[String], classOf[Long], classOf[Option[_]])
    method.setAccessible(true)

    // "build" uses 2s of the 3s budget; "run" would take 60s. A per-process timeout would end after ~5s.
    val dir = Files.createTempDirectory("ci-test").toFile
    val fakeCompose = script(dir, """case "$3" in build) sleep 2;; run) sleep 60;; esac""")
    val start = System.currentTimeMillis()

    val result = method.invoke(newThread(), newJob("docker-compose"), dir, dir, fakeCompose, java.lang.Long.valueOf(start + 3000), None).asInstanceOf[Integer].intValue()
    val elapsed = System.currentTimeMillis() - start

    assert(result != 0)
    assert(elapsed < 4500, s"the run step must only get what's left of the build's time, took ${elapsed}ms")
  }

  private def newJob(buildType: String): BuildJob = {
    val now = new java.util.Date()
    val account = Account(0L, "root", "root", "root@x", "", false, None, now, now, None, None, false, false, None)
    BuildJob("root", "test", "root", "test", 1, "master", "0" * 40, "msg", "root", "root@x", None, None, None, None, now, None, account,
      CIConfig("root", "test", buildType, "", false, None, None, false))
  }

  private def newThread() = new BuildJobThread(new LinkedBlockingQueue[BuildJob](), new LinkedBlockingQueue[BuildJobThread]())

  private def script(dir: File, body: String): String = {
    val file = new File(dir, "test.sh")
    Files.writeString(file.toPath, body + "\n")
    s"sh ${file.getAbsolutePath}"
  }

}
