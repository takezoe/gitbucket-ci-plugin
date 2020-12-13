gitbucket-ci-plugin [![build](https://github.com/takezoe/gitbucket-ci-plugin/workflows/build/badge.svg?branch=master)](https://github.com/takezoe/gitbucket-ci-plugin/actions?query=workflow%3Abuild+branch%3Amaster)
========
GitBucket plug-in that adds simple CI ability to GitBucket.

![Build results](gitbucket-ci-plugin_results.png)

![Build output](gitbucket-ci-plugin_output.png)

This plug-in allows repository owners to configure build command, and run them at following timing:

- Push commits to the default branch
- Create a new pull request
- Push additional commits to the pull request branch

### Skip and re-run by keywords

You can skip a build by including specific words in the commit message. Moreover you can also re-run the pull request build by adding a comment including specific words. These words can be set at the build settings.

### Variables in build script

In the build script, following environment variables are available:

- `CI` (true)
- `HOME` (root of the build directory)
- `CI_BUILD_DIR` (same as HOME)
- `CI_BUILD_NUMBER`
- `CI_BUILD_BRANCH`
- `CI_COMMIT_ID`
- `CI_COMMIT_MESSAGE`
- `CI_REPO_SLUG` ("owner/repo")
- `CI_PULL_REQUEST` (pull request id or "false")
- `CI_PULL_REQUEST_SLUG` ("owner/repo" or "")

### Web API

This plugin has [CircleCI  API v1.1](https://circleci.com/docs/api/v1-reference/) compatible Web API. Supported APIs are below:

- User (`GET /api/circleci/v1.1/me`)
- Recent Builds For a Single Project (`GET /api/circleci/v1.1/project/gitbucket/:owner/:repository`)
- Recent Builds For a Project Branch (`GET /api/circleci/v1.1/project/gitbucket/:owner/:repository/tree/:branch`)
- Single Build (`GET /api/circleci/v1.1/project/gitbucket/:owner/:repository/:buildNum`)
- Retry a Build (`POST /api/circleci/v1.1/project/gitbucket/:owner/:repository/:buildNum/retry`)
- Cancel a Build (`POST /api/circleci/v1.1/project/gitbucket/:owner/:repository/:buildNum/cancel`)
- Trigger a new Build (`POST /api/circleci/v1.1/project/gitbucket/:owner/:repository`)
- Trigger a new Build with a Branch (`POST /api/circleci/v1.1/project/gitbucket/:owner/:repository/tree/:branch`)

While CircleCI API takes the token via query string, in this plugin, `Authorization` header (application token or basic authentication) is available as same as other [GitBucket API](https://github.com/gitbucket/gitbucket/wiki/API-WebHook).

### Cautions

Note that you must not use this plug-in in public environment because it allows executing any commands on a GitBucket instance. It will be **a serious security hole**.

In addition, this plug-in is made to just experiment continuous integration on GitBucket easily without complex settings of webhook or Jenkins. It doesn't have flexibility and scalability, and also has a security issue which is mentioned above. Therefore, if you like it and would like to use for your project actually, we recommend to setup Jenkins or other CI tool and move to it.

## Compatibility

Plugin version | GitBucket version
:--------------|:--------------------
1.11.x         | 4.35.x -
1.10.x         | 4.34.x -
1.9.x          | 4.32.x -
1.8.x          | 4.31.x -
1.7.x          | 4.30.x -
1.6.x -        | 4.24.0 -
1.5.x -        | 4.23.1 -
1.4.x -        | 4.23.0
1.3.x -        | 4.19.x -
1.0.x - 1.2.x  | 4.17.x, 4.18.x

## Installation

Download jar file from [the release page](https://github.com/takezoe/gitbucket-ci-plugin/releases) and put into `GITBUCKET_HOME/plugins`.

## Build

Run `sbt assembly` and copy generated `/target/scala-2.13/gitbucket-ci-plugin-x.x.x.jar` to `~/.gitbucket/plugins/` (If the directory does not exist, create it by hand before copying the jar), or just run `sbt install`.

## Release Notes

### 1.11.0
- Update for GitBucket 4.35.0 compatibility

### 1.10.0
- Update for GitBucket 4.34.0 compatibility

### 1.9.1
- Run build after a pull request is merged

### 1.9.0
- Update for GitBucket 4.32.0 and Scala 2.13.0

### 1.8.1
- Bug fix

### 1.8.0
- Docker support

### 1.7.0
- Update for GitBucket 4.30.x

### 1.6.0
- Some CircleCI compatible Web API

### 1.5.0

- Build branches even other than the default branch
- Support the use of an arbitrary file in the git repository as a build script

### 1.4.0

- Max parallel builds and max stored history became configurable

### 1.3.0

- Update for Scalatra 2.6
- Fix skipping pull request build bug

### 1.2.0

- Build workspace browser
- Altered build directories location

### 1.1.0

- Skip build by commit message
- Re-run build by pull request comment
- Supply environment variables in build script

### 1.0.1

- Build status badge
- Fix pull request build bug

### 1.0.0

- First release
