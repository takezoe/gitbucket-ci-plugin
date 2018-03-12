package io.github.gitbucket.ci.manager

import java.util.concurrent.LinkedBlockingQueue
import io.github.gitbucket.ci.service.BuildJob

object BuildManager {

  val MaxParallelBuilds = 2
//  val MaxBuildHistoryPerProject = 20

  val queue = new LinkedBlockingQueue[BuildJob]()

  // TODO Is it possible to apply MaxParallelBuilds change dynamically?
  val threads = Range(0, MaxParallelBuilds).map { _ => new BuildJobThread(queue) }

  def queueBuildJob(job: BuildJob): Unit = {
    queue.add(job)
  }

  def startBuildManager(): Unit = {
    threads.foreach(_.start())
  }

  def shutdownBuildManager(): Unit = {
    threads.foreach(_.interrupt())
  }

}
