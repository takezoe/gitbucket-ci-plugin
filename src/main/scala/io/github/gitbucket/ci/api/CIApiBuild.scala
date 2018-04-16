package io.github.gitbucket.ci.api

import java.util.Date

case class CIApiBuild(
  vcs_url: String,
  build_url: String,
  build_num: Long,
  branch: String,
  vcs_revision: String,
  committer_name: String,
  committer_email: String,
  subject: String,
  body: String,
  why: String,
  dont_build: String,
  queued_at: Date,
  start_time: Date,
  stop_time: Date,
  build_time_millis: Long,
  username: String,
  reponame: String,
  lifecycle: String,
  outcome: String,
  status: String,
  retry_of: String,
  previous: CIApiPreviousBuild
)

case class CIApiPreviousBuild(
  status: String,
  build_num: Long
)