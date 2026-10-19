package scrai

import dotty.tools.dotc.core.Contexts.*
import dotty.tools.repl.*
import org.jline.reader.*
import scala.jdk.CollectionConverters.*

final class AiRepl(config: Config, generator: Generator, classpath: String)
    extends ReplDriver(Array("-classpath", classpath, "-color:never")):
  override def runUntilQuit(using start: State = initialState)(): State =
    val session = new SessionContext(config.contextChars)
    val terminal = new AiTerminal(config, generator, session)
    println(s"Scala AI REPL — ${config.model}")
    println(s"${config.hotkey}: generate from // comments; ${config.restoreHotkey}: restore prompt; :quit: exit")
    println(s"Generation sends your prompt and up to ${config.contextChars} session characters to ${config.baseUrl}.")
    var state = start
    var done = false
    try runBody {
      while !done do
        given Context = state.context
        val completer: Completer = (_, line, candidates) =>
          val matches = completions(line.cursor(), line.line(), state)
          candidates.addAll(matches.map(_.label).distinct.map { label =>
            new Candidate(label, label.stripPrefix("`").stripSuffix("`"), null, null, null, null, false)
          }.asJava)
        try
          val input = terminal.readLine(completer)
          if input.trim == ":ai-clear" then
            session.clear()
            println("AI context cleared (Scala definitions are unchanged).")
          else
            val parsed = ParseResult(input)(using state)
            if parsed == Quit then done = true
            else
              state = interpret(parsed)(using state)
              if input.trim.startsWith(":reset") then session.clear()
              else if !input.trim.startsWith(":") && Prompt.extract(input).isEmpty then session.add(input)
        catch
          case _: EndOfFileException => done = true
          case _: UserInterruptException => ()
      state
    }
    finally terminal.close()

object Main:
  def main(args: Array[String]): Unit =
    val config = Config.load()
    val repl = new AiRepl(config, new GroqGenerator(config), System.getProperty("java.class.path"))
    repl.runUntilQuit()