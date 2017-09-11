package io.github.gitbucket.ci.manager

import java.util.concurrent.LinkedBlockingQueue
import io.github.gitbucket.ci.service.BuildJob

object BuildManager {

  val MaxParallelBuilds = 4
  val MaxBuildHistoryPerProject = 10

  val queue = new LinkedBlockingQueue[BuildJob]()
  val threads = Range(0, MaxParallelBuilds).map { _ => new BuildJobThread(queue) }

  def queueBuildJob(job: BuildJob): Unit = {
    queue.add(job)
  }

  def startBuildManager(): Unit = {
    threads.foreach { thread =>
      thread.start()
    }
  }

}
