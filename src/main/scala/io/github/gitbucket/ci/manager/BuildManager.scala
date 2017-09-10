package io.github.gitbucket.ci.manager

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}
import io.github.gitbucket.ci.service.{BuildJob, BuildResult}

object BuildManager {

  val MaxParallelBuilds = 4
  val MaxBuildsPerProject = 10

//  val buildSettings = new ConcurrentHashMap[(String, String), BuildSetting]()
  val buildResults = new ConcurrentHashMap[(String, String), Seq[BuildResult]]()

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

