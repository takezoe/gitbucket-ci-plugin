package io.github.gitbucket.ci.hook

import gitbucket.core.plugin.RepositoryHook
import io.github.gitbucket.ci.model.Profile._
import profile.blockingApi._

class CIRepositoryHook extends RepositoryHook {

  override def deleted(owner: String, repository: String)(implicit session: Session): Unit = {
    CIConfigs.filter { t => (t.userName === owner.bind) && (t.repositoryName === repository.bind) }.delete
    CIResults.filter { t => (t.userName === owner.bind) && (t.repositoryName === repository.bind) }.delete
  }

  override def renamed(owner: String, repository: String, newRepository: String)(implicit session: Session): Unit = {
    CIConfigs.filter { t => (t.userName === owner.bind) && (t.repositoryName === repository.bind) }.list.foreach { config =>
      CIConfigs += config.copy(repositoryName = newRepository)
    }
    CIResults.filter { t => (t.userName === owner.bind) && (t.repositoryName === repository.bind) }.list.foreach { result =>
      CIResults += result.copy(repositoryName = newRepository)
    }
    deleted(owner, repository)
  }

}
