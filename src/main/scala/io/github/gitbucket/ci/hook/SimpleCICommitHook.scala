package io.github.gitbucket.ci.hook

import gitbucket.core.plugin.ReceiveHook
import gitbucket.core.model.Profile._
import gitbucket.core.service.{AccountService, RepositoryService}
import io.github.gitbucket.ci.service.SimpleCIService
import org.eclipse.jgit.transport.{ReceiveCommand, ReceivePack}
import profile.api._


class SimpleCICommitHook extends ReceiveHook with SimpleCIService with RepositoryService with AccountService {

  override def postReceive(owner: String, repository: String, receivePack: ReceivePack, command: ReceiveCommand, pusher: String)
                          (implicit session: Session): Unit = {
    val branch = command.getRefName.stripPrefix("refs/heads/")
    if(branch != command.getRefName){
      getRepository(owner, repository).foreach { repositoryInfo =>
        if(repositoryInfo.repository.defaultBranch == branch){
          loadCIConfig(owner, repository).foreach { config =>
            runBuild(owner, repository, command.getNewId.name, config)
          }
        }
      }
    }
  }

}
