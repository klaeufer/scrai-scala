package edu.luc.cs.scrai

import dotty.tools.repl.State

/** Scala 3.3 completion API: `completionsWithSignatures` yields
 *  (label, signature) pairs; only labels feed the JLine completer.
 */
final class VersionedAiRepl(config: Config, generator: Generator, classpath: String)
    extends AiRepl(config, generator, classpath):
  protected def completionLabels(cursor: Int, line: String, state: State): Seq[String] =
    completionsWithSignatures(cursor, line, state).map(_._1)
