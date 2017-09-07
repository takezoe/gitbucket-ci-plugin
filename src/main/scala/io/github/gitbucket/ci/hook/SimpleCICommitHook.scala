package io.github.gitbucket.ci.hook

import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.model.Profile._
import io.github.gitbucket.ci.service.{BuildSetting, SimpleCIService}
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}
import profile.api._


class SimpleCICommitHook extends ReceiveHook with SimpleCIService {

  override def postReceive(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)
                          (implicit session: Session): Unit = {
    // TODO Don't run a build if the pushed commit is not for the default branch.
    runBuild(owner, repository, command.getNewId.name, BuildSetting(owner, repository, "sbt compile"))
  }

}
