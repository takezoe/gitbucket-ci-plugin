name := "gitbucket-ci-plugin"
organization := "io.github.gitbucket"
version := "1.6.4"
scalaVersion := "2.12.6"
gitbucketVersion := "4.25.0"
libraryDependencies ++= Seq(
  "org.fusesource.jansi"    %  "jansi"               % "1.16",
  "org.scalatest"           %% "scalatest"           % "3.0.5" % "test",
  "com.wix"                 %  "wix-embedded-mysql"  % "3.0.0" % "test",
  "ru.yandex.qatools.embed" %  "postgresql-embedded" % "2.6" % "test"
)

