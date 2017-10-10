name := "gitbucket-ci-plugin"

organization := "io.github.gitbucket"

version := "1.1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "io.github.gitbucket" %% "gitbucket"         % "4.17.0" % "provided",
  "org.fusesource.jansi" % "jansi"             % "1.16",
  "com.typesafe.play"   %% "twirl-compiler"    % "1.3.0"  % "provided",
  "javax.servlet"        % "javax.servlet-api" % "3.1.0"  % "provided"
)

resolvers += Resolver.bintrayRepo("bkromhout", "maven")

enablePlugins(SbtTwirl)
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
