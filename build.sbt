name := "gitbucket-ci-plugin"
organization := "io.github.gitbucket"
version := "1.8.1"
scalaVersion := "2.12.8"
gitbucketVersion := "4.31.0"
libraryDependencies ++= Seq(
  "org.fusesource.jansi"    %  "jansi"               % "1.16",
  "org.scalatest"           %% "scalatest"           % "3.0.5" % "test",
  "com.wix"                 %  "wix-embedded-mysql"  % "3.0.0" % "test",
  "ru.yandex.qatools.embed" %  "postgresql-embedded" % "2.6" % "test"
)

