package edu.luc.cs.scrai

import dotty.tools.repl.State

/** Scala 3.7 completion API: `completions` yields labeled candidates. */
final class VersionedAiRepl(config: Config, generator: Generator, classpath: String)
    extends AiRepl(config, generator, classpath):
  protected def completionLabels(cursor: Int, line: String, state: State): Seq[String] =
    completions(cursor, line, state).map(_.label)