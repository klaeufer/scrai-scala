package edu.luc.cs.scrai

import sbt._
import sbt.Keys._
import sbtcompat.PluginCompat._

/** sbt 2 classpath access: `Classpath` is `Seq[Attributed[HashedVirtualFileRef]]`. */
private[scrai] object Compat:
  /** Class directories and dependency jars of the user's project. */
  val projectPaths = Def.task:
    implicit val conv: FileConverter = fileConverter.value
    toNioPaths(fullClasspath.value).map(_.toFile)

  /** External dependencies only: do not compile or include the project's classes. */
  val quickProjectPaths = Def.task:
    implicit val conv: FileConverter = fileConverter.value
    toNioPaths(externalDependencyClasspath.value).map(_.toFile)
