name := "gitbucket-ci-plugin"
organization := "io.github.gitbucket"
version := "1.11.0"
scalaVersion := "2.13.18"
gitbucketVersion := "4.47.0"
scalacOptions ++= Seq("-deprecation", "-Xsource:3-cross")
libraryDependencies ++= Seq(
  "org.fusesource.jansi" %  "jansi"                % "1.18",
  "org.scalatest"        %% "scalatest"            % "3.2.19" % "test",
  "com.dimafeng"         %% "testcontainers-scala" % "0.43.0" % "test",
  "org.testcontainers"   %  "mysql"                % "1.21.3" % "test",
  "org.testcontainers"   %  "postgresql"           % "1.21.3" % "test"
)

