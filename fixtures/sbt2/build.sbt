import edu.luc.cs.scrai.ScraiPlugin

// CI sets SCRAI_TEST_SCALA_VERSION to exercise a specific supported line.
scalaVersion := sys.env.getOrElse("SCRAI_TEST_SCALA_VERSION", "3.9.0")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScraiPlugin)
  .settings(name := "scrai-fixture-sbt2")
