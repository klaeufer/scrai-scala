package edu.luc.cs.scrai

import sbt._
import sbt.Keys._

/** sbt 1 classpath access: `Classpath` is `Seq[Attributed[File]]`. */
private[scrai] object Compat {
  /** Class directories and dependency jars of the user's project. */
  val projectPaths = Def.task {
    (Compile / fullClasspath).value.files
  }
}
