organization := "pt.tecnico.dsi"
name := "akka-kadmin"

javacOptions ++= Seq(
  "-Xlint",
  "-encoding", "UTF-8",
  "-Dfile.encoding=utf-8"
)

scalaVersion := "2.12.0"
scalacOptions ++= Seq(
  "-deprecation",                   //Emit warning and location for usages of deprecated APIs.
  "-encoding", "UTF-8",             //Use UTF-8 encoding. Should be default.
  "-feature",                       //Emit warning and location for usages of features that should be imported explicitly.
  "-language:implicitConversions",  //Explicitly enables the implicit conversions feature
  "-unchecked",                     //Enable detailed unchecked (erasure) warnings
  "-Xfatal-warnings",               //Fail the compilation if there are any warnings.
  "-Xlint",                         //Enable recommended additional warnings.
  //"-Yinline-warnings",              //Emit inlining warnings.
  "-Yno-adapted-args",              //Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code"                //Warn when dead code is identified.
)

val akkaVersion = "2.4.12"
libraryDependencies ++= Seq(
  "pt.tecnico.dsi" %% "kadmin" % "7.0.0",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "org.iq80.leveldb" % "leveldb" % "0.9" % Test,
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8" % Test,
  //Logging
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Test,
  "ch.qos.logback" % "logback-classic" % "1.1.7" % Test,
  //Testing
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,

  "commons-io" % "commons-io" % "2.5" % Test
)
resolvers += Opts.resolver.sonatypeReleases

//This is needed for LevelDB to work in tests
fork in Test := true

autoAPIMappings := true
scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits", "-diagrams")

site.settings
site.includeScaladoc()
ghpages.settings
git.remoteRepo := s"git@github.com:ist-dsi/${name.value}.git"

licenses += "MIT" -> url("http://opensource.org/licenses/MIT")
homepage := Some(url(s"https://github.com/ist-dsi/${name.value}"))
scmInfo := Some(ScmInfo(homepage.value.get, git.remoteRepo.value))

publishMavenStyle := true
publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)
publishArtifact in Test := false
sonatypeProfileName := organization.value

pomIncludeRepository := { _ => false }
pomExtra :=
  <developers>
    <developer>
      <id>Lasering</id>
      <name>Simão Martins</name>
      <url>https://github.com/Lasering</url>
    </developer>
  </developers>

dependencyUpdatesFailBuild := true
import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  releaseStepCommand("dependencyUpdates"),
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommand("doc"),
  releaseStepCommand("""eval "./test.sh" !"""),
  setReleaseVersion,
  tagRelease,
  releaseStepCommand("ghpagesPushSite"),
  releaseStepCommand("publishSigned"),
  releaseStepCommand("sonatypeRelease"),
  pushChanges,
  setNextVersion
)