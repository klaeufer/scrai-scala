import scala.sys.process.*

ThisBuild / organization := "edu.luc.scrai"
ThisBuild / version := sys.props.getOrElse("scrai.version", "0.1.0-SNAPSHOT")

// Supported lines. Keep runtime module names in sync with ScraiPlugin.runtimeModule.
val scala33 = "3.3.7"
val scala34 = "3.4.3"
val scala35 = "3.5.2"
val scala36 = "3.6.4"
val scala37 = "3.7.4"
val scala38 = "3.8.4"
val scala39 = "3.9.0"
val scala310 = "3.10.0"
val sbt1Version = "1.13.0"
val sbt2Version = "2.0.11"
val pluginScala212 = "2.12.21"
val pluginScala3 = "3.8.4" // matches sbt 2.0.11's embedded Scala
val standalonePackage = taskKey[File]("Package the standalone Scala 3.9 AI REPL distribution")

lazy val root = project.in(file("."))
  .aggregate(runtime, plugin)
  .settings(
    name := "scrai-scala",
    publish / skip := true,
    standalonePackage := {
      val log = streams.value.log
      val staging = target.value / "standalone-staging"
      val archive = target.value / "scrai-standalone-scala3.9.zip"
      IO.delete(staging)
      IO.delete(archive)
      val bin = staging / "bin"
      val lib = staging / "lib"
      IO.createDirectory(bin)
      IO.createDirectory(lib)

      val runtimeJar = (runtime / Compile / packageBin).value
      val runtimeClasspath = (runtime / Compile / fullClasspath).value.files
        .filter(_.isFile)
        .filterNot(_ == runtimeJar)
      IO.copyFile(runtimeJar, lib / runtimeJar.getName)
      runtimeClasspath.foreach(jar => IO.copyFile(jar, lib / jar.getName))

      val launcher = bin / "scrai"
      IO.write(launcher,
        """#!/bin/sh
          |set -eu
          |SOURCE=$0
          |while [ -h "$SOURCE" ]; do
          |  SOURCE_DIR=$(CDPATH= cd -P "$(dirname -- "$SOURCE")" && pwd)
          |  SOURCE=$(readlink "$SOURCE")
          |  case "$SOURCE" in
          |    /*) ;;
          |    *) SOURCE="$SOURCE_DIR/$SOURCE" ;;
          |  esac
          |done
          |SCRIPT_DIR=$(CDPATH= cd -P "$(dirname -- "$SOURCE")" && pwd)
          |if ! command -v java >/dev/null 2>&1; then
          |  echo "scrai requires Java 17 or newer on PATH." >&2
          |  exit 1
          |fi
          |exec java -cp "$SCRIPT_DIR/../lib/*" scrai.Main "$@"
          |""".stripMargin)
      launcher.setExecutable(true)
      Seq("LICENSE-Scala", "THIRD-PARTY-NOTICES.md").foreach { name =>
        IO.copyFile(baseDirectory.value / name, staging / name)
      }

      // sbt's ZIP helper does not preserve the executable bit. Prefer the
      // platform ZIP tool so extracted releases can invoke bin/scrai directly;
      // retain the sbt fallback for platforms without that command.
      val unix = sys.props.get("os.name").exists { name =>
        val lower = name.toLowerCase
        lower.contains("mac") || lower.contains("linux")
      }
      if (unix && Process(Seq("zip", "-q", "-r", archive.getAbsolutePath, "bin", "lib", "LICENSE-Scala", "THIRD-PARTY-NOTICES.md"), staging).! == 0)
        ()
      else
        IO.zip(Path.allSubpaths(staging).toSeq.map { case (file, relative) => file -> relative }, archive)
      log.info("Wrote standalone distribution: " + archive)
      archive
    },
    // Publish every supported variant so fixtures and users on any supported
    // line can resolve everything from one command.
    addCommandAlias(
      "publishAllLocal",
      "runtime/publishLocal; plugin/publishLocal; ++3.3.7; runtime/publishLocal; ++3.4.3; runtime/publishLocal; ++3.5.2; runtime/publishLocal; ++3.6.4; runtime/publishLocal; ++3.7.4; runtime/publishLocal; ++3.8.4; runtime/publishLocal; ++3.9.0; runtime/publishLocal; ++3.10.0; runtime/publishLocal; ++3.8.4; plugin/publishLocal"
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
    crossScalaVersions := Seq(scala310, scala39, scala38, scala37, scala36, scala35, scala34, scala33),
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
      val process = new java.lang.ProcessBuilder(javaExecutable.getAbsolutePath, "-cp", cp, "scrai.Main")
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