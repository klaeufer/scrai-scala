ThisBuild / organization := "edu.luc.scrai"
ThisBuild / version := "0.1.0-SNAPSHOT"

// Supported lines. Keep runtime module names in sync with ScraiPlugin.runtimeModule.
val scala33 = "3.3.7"
val scala34 = "3.4.3"
val scala35 = "3.5.2"
val scala36 = "3.6.4"
val scala37 = "3.7.4"
val scala38 = "3.8.4"
val scala39 = "3.9.0"
val sbt1Version = "1.13.0"
val sbt2Version = "2.0.11"
val pluginScala212 = "2.12.21"
val pluginScala3 = "3.8.4" // matches sbt 2.0.11's embedded Scala

lazy val root = project.in(file("."))
  .aggregate(runtime, plugin)
  .settings(
    name := "scrai-scala",
    publish / skip := true,
    // Publish every supported variant so fixtures and users on any supported
    // line can resolve everything from one command.
    addCommandAlias(
      "publishAllLocal",
       "runtime/publishLocal; plugin/publishLocal; ++3.3.7; runtime/publishLocal; ++3.4.3; runtime/publishLocal; ++3.5.2; runtime/publishLocal; ++3.6.4; runtime/publishLocal; ++3.7.4; runtime/publishLocal; ++3.8.4; plugin/publishLocal"
    )
  )

lazy val runtime = project.in(file("runtime"))
  .settings(
    name := "scrai-runtime",
    // One artifact per Scala line (e.g. scrai-runtime_3.3); crossPaths off
    // because the line suffix is added manually above.
    moduleName := "scrai-runtime_" + scalaVersion.value.split('.').take(2).mkString("."),
    crossPaths := false,
    scalaVersion := scala39,
    crossScalaVersions := Seq(scala39, scala38, scala37, scala36, scala35, scala34, scala33),
    libraryDependencies ++= {
      val sv = scalaVersion.value
      Seq(
        "org.scala-lang" %% "scala3-compiler" % sv,
        "com.lihaoyi" %% "ujson" % "3.3.1",
        "org.scalameta" %% "munit" % "1.0.4" % Test
      ) ++ (if (sv.startsWith("3.3.") || sv.startsWith("3.4.") ||
             sv.startsWith("3.5.") || sv.startsWith("3.6.") ||
             sv.startsWith("3.7.")) Seq.empty
            else Seq("org.scala-lang" %% "scala3-repl" % sv))
    },
    Compile / mainClass := Some("scrai.Main"),
    // Line-specific sources: sbt only adds scala-<binary>, which is "3" for
    // every Scala 3 version, so the minor-line directories must be wired manually.
    Compile / unmanagedSourceDirectories += (Compile / sourceDirectory).value /
      ("scala-" + scalaVersion.value.split('.').take(2).mkString(".")),
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

lazy val plugin = project.in(file("plugin"))
  .settings(
    name := "sbt-scrai",
    sbtPlugin := true,
    scalaVersion := pluginScala212,
    crossScalaVersions := Seq(pluginScala212, pluginScala3),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => sbt1Version
        case _      => sbt2Version
      }
    },
    addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.2.0"),
    // Bake the release version into the plugin so it can resolve the matching
    // scrai-runtime artifact from user builds.
    Compile / sourceGenerators += Def.task {
      val f = (Compile / sourceManaged).value / "scrai" / "BuildInfo.scala"
      IO.write(f, "package scrai\nobject BuildInfo {\n  val version = \"" + version.value + "\"\n}\n")
      Seq(f)
    }.taskValue
  )