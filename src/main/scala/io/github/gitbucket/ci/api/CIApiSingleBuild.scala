package io.github.gitbucket.ci.api

import java.util.Date

import gitbucket.core.api.ApiPath
import io.github.gitbucket.ci.model.CIResult
import io.github.gitbucket.ci.util.JobStatus

case class CIApiSingleBuild(
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
  steps: Seq[CIApiSingleBuildStep]
)

object CIApiSingleBuild {
  def apply(result: CIResult): CIApiSingleBuild = {
    CIApiSingleBuild(
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
      queued_at = result.queuedTime,
      start_time = Some(result.startTime),
      stop_time = Some( result.endTime),
      build_time_millis = Some(result.endTime.getTime - result.startTime.getTime),
      username = result.userName,
      reponame = result.repositoryName,
      lifecycle = "finished",
      outcome = Some(result.apiStatus),
      status = result.apiStatus,
      retry_of = None,
      steps = Seq(
        CIApiSingleBuildStep(
          name = "build",
          actions = Seq(
            CIApiSingleBuildStepAction(
              bash_command = result.buildScript,
              run_time_millis = result.endTime.getTime - result.startTime.getTime,
              start_time = result.startTime,
              messages = Nil,
              step = 1,
              exit_code = result.exitCode,
              end_time = result.endTime,
              index = 0,
              status = result.apiStatus,
              `type` = "build",
              failed = if(result.status == JobStatus.Failure) Some(true) else None
            )
          )
        )
      )
    )
  }
}

case class CIApiSingleBuildStep(
  name: String,
  actions: Seq[CIApiSingleBuildStepAction]
)

case class CIApiSingleBuildStepAction(
  bash_command: String,
  run_time_millis: Long,
  start_time: Date,
  messages: Seq[String],
  step: Int,
  exit_code: Int,
  end_time: Date,
  index: Int,
  status: String,
  `type`: String,
  failed: Option[Boolean]
)