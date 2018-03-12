package io.github.gitbucket.ci.manager

import java.util.concurrent.LinkedBlockingQueue
import io.github.gitbucket.ci.service.BuildJob

object BuildManager {

  val queue = new LinkedBlockingQueue[BuildJob]()
  val threads = new LinkedBlockingQueue[BuildJobThread]()

  def queueBuildJob(job: BuildJob): Unit = {
    queue.add(job)
  }

  def shutdownBuildManager(): Unit = {
    threads.forEach(_.interrupt())
  }

  def setMaxParallelBuilds(maxParallelBuilds: Int): Unit = {
    if(maxParallelBuilds > threads.size){
      for(_ <- 1 to maxParallelBuilds - threads.size){
        val thread = new BuildJobThread(queue)
        threads.add(thread)
        thread.start()
      }
    } else if (maxParallelBuilds < threads.size){
      for(_ <- 1 to threads.size - maxParallelBuilds){
        val thread = threads.poll()
        thread.exitThread.set(true)
      }
    }
  }

}
