package edu.luc.cs.scrai

import sbt._
import sbt.Keys._
import sbtcompat.PluginCompat._

/** Replaces `Compile / console` and `Compile / consoleQuick` with the scrai AI REPL.
 *
 *  Opt in with `enablePlugins(ScraiPlugin)`. The matching `scrai-runtime`
 *  artifact is resolved for the build's Scala line and launched on a child
 *  JVM wired to the controlling terminal, so evaluation stays in the user's
 *  project classpath.
 */
object ScraiPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def requires = plugins.JvmPlugin

  private val Scrai = config("scrai").hide
  override def projectConfigurations: Seq[Configuration] = Seq(Scrai)

  /** Map a supported project Scala version to its runtime artifact, or None. */
  private def runtimeModule(scalaVersion: String): Option[String] =
    if (scalaVersion.startsWith("3.3.")) Some("scrai-runtime_3.3")
    else if (scalaVersion.startsWith("3.4.")) Some("scrai-runtime_3.4")
    else if (scalaVersion.startsWith("3.5.")) Some("scrai-runtime_3.5")
    else if (scalaVersion.startsWith("3.6.")) Some("scrai-runtime_3.6")
    else if (scalaVersion.startsWith("3.7.")) Some("scrai-runtime_3.7")
    else if (scalaVersion.startsWith("3.8.")) Some("scrai-runtime_3.8")
    else if (scalaVersion.startsWith("3.9.")) Some("scrai-runtime_3.9")
    else if (scalaVersion.startsWith("3.10.")) Some("scrai-runtime_3.10")
    else None

  private val supportedHint =
    "scrai supports Scala 3.3.x through 3.10.x projects; set scalaVersion to one of those to use `console` or `consoleQuick`."

  private def launch(projectCp: Seq[File], toolCp: Seq[File], directory: File): Unit = {
    val evalCp = projectCp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
    val fullCp = (projectCp ++ toolCp).map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
    val javaExecutable = file(sys.props("java.home")) / "bin" / "java"
    // sbt can redirect stdin, so use the controlling terminal explicitly on Unix.
    val process = new ProcessBuilder(
      javaExecutable.getAbsolutePath,
      "--enable-native-access=ALL-UNNAMED",
      "-cp",
      fullCp,
      "-Dscrai.repl.classpath=" + evalCp,
      "edu.luc.cs.scrai.Main"
    ).directory(directory).inheritIO()
    if (!sys.props("os.name").toLowerCase.contains("windows")) {
      val tty = file("/dev/tty")
      process.redirectInput(tty).redirectOutput(tty).redirectError(tty)
    }
    val code = process.start().waitFor()
    if (code != 0) sys.error("scrai REPL exited with status " + code)
  }

  /** Reusable console tasks for inConfig(MyConfig)(ScraiPlugin.consoleSettings). */
  def consoleSettings: Seq[Setting[_]] = Seq(
    console := Def.uncached {
      runtimeModule(scalaVersion.value).getOrElse(sys.error(supportedHint))
      val toolCp = update.value.select(configurationFilter(Scrai.name))
      launch(Compat.projectPaths.value, toolCp, baseDirectory.value)
    },
    consoleQuick := Def.uncached {
      runtimeModule(scalaVersion.value).getOrElse(sys.error(supportedHint))
      val toolCp = update.value.select(configurationFilter(Scrai.name))
      // Keep this task independent of fullClasspath, which compiles project sources.
      launch(Compat.quickProjectPaths.value, toolCp, baseDirectory.value)
    }
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies ++= runtimeModule(scalaVersion.value).toSeq.map { name =>
      "edu.luc.cs" % name % BuildInfo.version % Scrai.name
    }
  ) ++ inConfig(Compile)(consoleSettings) ++
    inConfig(Runtime)(consoleSettings) ++ inConfig(Test)(consoleSettings)
}
