name := "gitbucket-ci-plugin"
organization := "io.github.gitbucket"
version := "1.11.0"
scalaVersion := "2.13.7"
gitbucketVersion := "4.36.2"
scalacOptions += "-deprecation"
libraryDependencies ++= Seq(
  "org.fusesource.jansi" %  "jansi"                % "1.18",
  "org.scalatest"        %% "scalatest"            % "3.0.8" % "test",
  "com.dimafeng"         %% "testcontainers-scala" % "0.38.7" % "test",
  "org.testcontainers"   %  "mysql"                % "1.15.1" % "test",
  "org.testcontainers"   %  "postgresql"           % "1.15.1" % "test"
)

