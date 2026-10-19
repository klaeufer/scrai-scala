ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "edu.luc.scrai"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = project.in(file(".")).settings(
  name := "scrai-scala",
  libraryDependencies ++= Seq(
    "org.scala-lang" %% "scala3-compiler" % scalaVersion.value,
    "org.scala-lang" %% "scala3-repl" % scalaVersion.value,
    "com.lihaoyi" %% "ujson" % "3.3.1",
    "org.scalameta" %% "munit" % "1.0.4" % Test
  ),
  Compile / mainClass := Some("scrai.Main"),
  Compile / run / fork := true,
  Compile / run / connectInput := true,
  Compile / console := {
    val cp = (Compile / fullClasspath).value.files.mkString(java.io.File.pathSeparator)
    val javaExecutable = file(sys.props("java.home")) / "bin" / "java"
    // A real inherited TTY is essential: sbt's Fork helper pipes stdin.
    // sbt's own launcher can also redirect its stdin, so use the controlling
    // terminal explicitly on Unix instead of inheriting that redirected fd.
    val process = new ProcessBuilder(javaExecutable.getAbsolutePath, "-cp", cp, "scrai.Main")
      .directory(baseDirectory.value).inheritIO()
    if (!sys.props("os.name").toLowerCase.contains("windows")) {
      val tty = file("/dev/tty")
      process.redirectInput(tty).redirectOutput(tty).redirectError(tty)
    }
    val code = process.start().waitFor()
    if (code != 0) sys.error("AI REPL exited with status " + code)
  },
  Test / fork := true
)