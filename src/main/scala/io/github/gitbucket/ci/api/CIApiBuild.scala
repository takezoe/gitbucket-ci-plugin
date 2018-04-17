package io.github.gitbucket.ci.api

import java.util.Date

import gitbucket.core.api.ApiPath
import io.github.gitbucket.ci.model.CIResult
import io.github.gitbucket.ci.service.BuildJob

case class CIApiBuild(
  vcs_url: ApiPath,
  build_url: ApiPath,
  build_num: Long,
  branch: String,
  vcs_revision: String,
  committer_name: String,
  committer_email: String,
  subject: String,
  body: String,
  why: String,
  dont_build: Option[String],
  queued_at: Date,
  start_time: Option[Date],
  stop_time: Option[Date],
  build_time_millis: Option[Long],
  username: String,
  reponame: String,
  lifecycle: String,
  outcome: Option[String],
  status: String,
  retry_of: Option[String],
  previous: Option[CIApiPreviousBuild]
)

object CIApiBuild {
  def apply(job: BuildJob): CIApiBuild = {
    CIApiBuild(
      vcs_url = ApiPath(s"/git/${job.userName}/${job.repositoryName}"),
      build_url = ApiPath(s"/${job.userName}/${job.repositoryName}/build/${job.buildNumber}"),
      build_num = job.buildNumber,
      branch = job.buildBranch,
      vcs_revision = job.sha,
      committer_name = job.commitUserName,
      committer_email = job.commitMailAddress,
      subject = job.commitMessage,
      body = "",
      why = "gitbucket",
      dont_build = None,
      queued_at = null, // TODO It doesn't have a queued dates currently.
      start_time = job.startTime,
      stop_time = None,
      build_time_millis = None,
      username = job.userName,
      reponame = job.repositoryName,
      lifecycle = "running",
      outcome = None,
      status = "running",
      retry_of = None,
      previous = None
    )
  }

  def apply(result: CIResult): CIApiBuild = {
    CIApiBuild(
      vcs_url = ApiPath(s"/git/${result.userName}/${result.repositoryName}"),
      build_url = ApiPath(s"/${result.userName}/${result.repositoryName}/build/${result.buildNumber}"),
      build_num = result.buildNumber,
      branch = result.buildBranch,
      vcs_revision = result.sha,
      committer_name = result.commitUserName,
      committer_email = result.commitMailAddress,
      subject = result.commitMessage,
      body = "",
      why = "gitbucket",
      dont_build = None,
      queued_at = null, // TODO It doesn't have a queued dates currently.
      start_time = Some(result.startTime),
      stop_time = Some( result.endTime),
      build_time_millis = Some( result.endTime.getTime - result.startTime.getTime),
      username = result.userName,
      reponame = result.repositoryName,
      lifecycle = "finished",
      outcome = Some(result.status),
      status = result.status,
      retry_of = None,
      previous = None
    )
  }
}

case class CIApiPreviousBuild(
  status: String,
  build_num: Long
)