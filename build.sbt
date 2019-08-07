name := "gitbucket-ci-plugin"
organization := "io.github.gitbucket"
version := "1.9.0"
scalaVersion := "2.13.0"
gitbucketVersion := "4.32.0"
libraryDependencies ++= Seq(
  "org.fusesource.jansi" %  "jansi"                % "1.18",
  "org.scalatest"        %% "scalatest"            % "3.0.8" % "test",
  "com.dimafeng"         %% "testcontainers-scala" % "0.29.0" % "test",
  "org.testcontainers"   %  "mysql"                % "1.12.0" % "test",
  "org.testcontainers"   %  "postgresql"           % "1.12.0" % "test"
)

