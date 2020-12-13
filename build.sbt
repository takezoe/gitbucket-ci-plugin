name := "gitbucket-ci-plugin"
organization := "io.github.gitbucket"
version := "1.11.0"
scalaVersion := "2.13.1"
gitbucketVersion := "4.35.0"
scalacOptions += "-deprecation"
libraryDependencies ++= Seq(
  "org.fusesource.jansi" %  "jansi"                % "1.18",
  "org.scalatest"        %% "scalatest"            % "3.0.8" % "test",
  "com.dimafeng"         %% "testcontainers-scala" % "0.37.0" % "test",
  "org.testcontainers"   %  "mysql"                % "1.12.0" % "test",
  "org.testcontainers"   %  "postgresql"           % "1.14.3" % "test"
)

