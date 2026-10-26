import edu.luc.cs.scrai.ScraiPlugin

// CI sets SCRAI_TEST_SCALA_VERSION to exercise a specific supported line.
scalaVersion := sys.env.getOrElse("SCRAI_TEST_SCALA_VERSION", "3.9.0")
libraryDependencies += "com.lihaoyi" %% "ujson" % "3.3.1" % Runtime

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

lazy val Extra = config("extra") extend Test

lazy val root = project
  .in(file("."))
  .configs(Extra)
  .enablePlugins(ScraiPlugin)
  .settings(name := "scrai-fixture-sbt1")

  .settings(inConfig(Extra)(Defaults.testSettings))
  .settings(inConfig(Extra)(ScraiPlugin.consoleSettings))
