name := "gitbucket-ci-plugin"
organization := "io.github.gitbucket"
version := "1.8.1"
scalaVersion := "2.12.8"
gitbucketVersion := "4.31.0"
libraryDependencies ++= Seq(
  "org.fusesource.jansi" %  "jansi"                % "1.16",
  "org.scalatest"        %% "scalatest"            % "3.0.5" % "test",
  "com.dimafeng"         %% "testcontainers-scala" % "0.27.0" % "test",
  "org.testcontainers"   %  "mysql"                % "1.11.3" % "test",
  "org.testcontainers"   %  "postgresql"           % "1.11.3" % "test"
)

