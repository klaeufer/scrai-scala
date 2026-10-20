// Adapted from Scala 3.3.7 JLineTerminal.scala (Apache-2.0).
// Copyright the Scala contributors. See LICENSE-Scala and THIRD-PARTY-NOTICES.md.
package edu.luc.cs.scrai

import scala.language.unsafeNulls
import dotty.tools.repl.ParseResult
import org.jline.keymap.KeyMap
import java.util.concurrent.{ExecutionException, TimeUnit, TimeoutException}
import scala.util.control.NonFatal

import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.parsing.Scanners.Scanner
import dotty.tools.dotc.parsing.Tokens.*
import dotty.tools.dotc.printing.SyntaxHighlighting
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.util.SourceFile
import org.jline.reader
import org.jline.reader.Parser.ParseContext
import org.jline.reader.*
import org.jline.reader.impl.LineReaderImpl
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString

class AiTerminal(config: Config, generator: Generator, session: SessionContext) extends java.io.Closeable {
  // import java.util.logging.{Logger, Level}
  // Logger.getLogger("org.jline").setLevel(Level.FINEST)

  private val terminal =
    var builder = TerminalBuilder.builder()
    if System.getenv("TERM") == "dumb" then
      // Force dumb terminal if `TERM` is `"dumb"`.
      // Note: the default value for the `dumb` option is `null`, which allows
      // JLine to fall back to a dumb terminal. This is different than `true` or
      // `false` and can't be set using the `dumb` setter.
      // This option is used at https://github.com/jline/jline3/blob/894b5e72cde28a551079402add4caea7f5527806/terminal/src/main/java/org/jline/terminal/TerminalBuilder.java#L528.
      builder.dumb(true)
    builder.build()
  private val history = new DefaultHistory

  private def blue(str: String)(using Context) =
    if (ctx.settings.color.value != "never") Console.BLUE + str + Console.RESET
    else str
  protected def promptStr = "scala"
  private def prompt(using Context)        = blue(s"\n$promptStr> ")
  private def newLinePrompt(using Context) = blue("     | ")

  /** Blockingly read line from `System.in`
   *
   *  This entry point into JLine handles everything to do with terminal
   *  emulation. This includes:
   *
   *  - Multi-line support
   *  - Copy-pasting
   *  - History
   *  - Syntax highlighting
   *  - Auto-completions
   *
   *  @throws EndOfFileException This exception is thrown when the user types Ctrl-D.
   */
  def readLine(
    completer: Completer // provide auto-completions
  )(using Context): String = {
    import LineReader.Option.*
    import LineReader.*
    val userHome = System.getProperty("user.home")
    val lineReader = LineReaderBuilder
      .builder()
      .terminal(terminal)
      .history(history)
      .completer(completer)
      .highlighter(new Highlighter)
      .parser(new Parser)
      .variable(HISTORY_FILE, s"$userHome/.scrai_history") // Keep AI REPL history separate
      .variable(SECONDARY_PROMPT_PATTERN, "%M") // A short word explaining what is "missing",
                                                // this is supplied from the EOFError.getMissing() method
      .variable(LIST_MAX, 400)                  // Ask user when number of completions exceed this limit (default is 100).
      .variable(BLINK_MATCHING_PAREN, 0L)       // Don't blink the opening paren after typing a closing paren.
      .variable(WORDCHARS,
        LineReaderImpl.DEFAULT_WORDCHARS.filterNot("*?.[]~=/&;!#%^(){}<>".toSet)) // Finer grained word boundaries
      .option(INSERT_TAB, true)                 // At the beginning of the line, insert tab instead of completing.
      .option(AUTO_FRESH_LINE, true)            // if not at start of line before prompt, move to new line.
      .option(DISABLE_EVENT_EXPANSION, true)    // don't process escape sequences in input
      .build()

    var original: scala.Option[String] = None
    def report(message: String): Unit = lineReader.printAbove(s"[scrai] $message")
    lineReader.getWidgets.put("scrai-generate", new Widget {
      def apply(): Boolean =
        Prompt.extract(lineReader.getBuffer.toString) match
          case None => report("Type a // request first (the entire buffer must be comments).")
          case Some(prompt) =>
            val before = lineReader.getBuffer.toString
            try
              report("Generating Scala; Ctrl-C cancels. Nothing will execute automatically.")
              val future = generator.generate(prompt, session.snapshot)
              // Deliver Ctrl-C as input while this widget owns the reader;
              // signal dispatch can otherwise wait for JLine's editing lock.
              val savedAttributes = terminal.getAttributes
              val requestAttributes = new org.jline.terminal.Attributes(savedAttributes)
              requestAttributes.setLocalFlag(org.jline.terminal.Attributes.LocalFlag.ISIG, false)
              terminal.setAttributes(requestAttributes)
              val cancelled = new java.util.concurrent.atomic.AtomicBoolean(false)
              val previousInterrupt = terminal.handle(org.jline.terminal.Terminal.Signal.INT,
                _ => cancelled.set(true))
              try
                var result: scala.Option[String] = None
                while result.isEmpty do
                  if cancelled.get() then throw new InterruptedException("Generation cancelled")
                  try result = Some(future.get(100, TimeUnit.MILLISECONDS))
                  catch
                    case _: TimeoutException =>
                      val impl = lineReader.asInstanceOf[LineReaderImpl]
                      val ch = if impl.peekCharacter(1L) >= 0 then impl.readCharacter() else -2
                      if ch == 3 then throw new InterruptedException("Generation cancelled")
                      else if ch >= 0 then report("Please wait for generation, or press Ctrl-C to cancel.")
                original = Some(before)
                lineReader.getBuffer.clear()
                lineReader.getBuffer.write(result.get)
              finally
                terminal.setAttributes(savedAttributes)
                terminal.handle(org.jline.terminal.Terminal.Signal.INT, previousInterrupt)
                if !future.isDone then future.cancel(true)
            catch
              case _: InterruptedException => report("Generation cancelled; prompt preserved.")
              case error: ExecutionException => report(scala.Option(error.getCause).getOrElse(error).getMessage)
              case NonFatal(error) => report(scala.Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
        true
    })
    lineReader.getWidgets.put("scrai-restore", new Widget {
      def apply(): Boolean =
        original match
          case Some(prompt) =>
            lineReader.getBuffer.clear()
            lineReader.getBuffer.write(prompt)
          case None => report("No generated prompt to restore in this input.")
        true
    })
    import scala.jdk.CollectionConverters.*
    lineReader.getKeyMaps.values().asScala.foreach { keys =>
      keys.bind(new Reference("scrai-generate"), KeyMap.translate(config.hotkey))
      keys.bind(new Reference("scrai-restore"), KeyMap.translate(config.restoreHotkey))
    }
    lineReader.readLine(prompt)
  }

  def close(): Unit = terminal.close()

  /** Provide syntax highlighting */
  private class Highlighter(using Context) extends reader.Highlighter {
    def highlight(reader: LineReader, buffer: String): AttributedString = {
      val highlighted = SyntaxHighlighting.highlight(buffer)
      AttributedString.fromAnsi(highlighted)
    }
    override def setErrorPattern(errorPattern: java.util.regex.Pattern): Unit = {}
    override def setErrorIndex(errorIndex: Int): Unit = {}
  }

  /** Provide multi-line editing support */
  private class Parser(using Context) extends reader.Parser {

    /**
     * @param cursor     The cursor position within the line
     * @param line       The unparsed line
     * @param word       The current word being completed
     * @param wordCursor The cursor position within the current word
     */
    private class ParsedLine(
      val cursor: Int, val line: String, val word: String, val wordCursor: Int
    ) extends reader.ParsedLine {
      // Using dummy values, not sure what they are used for
      def wordIndex = -1
      def words = java.util.Collections.emptyList[String]
    }

    def parse(input: String, cursor: Int, context: ParseContext): reader.ParsedLine = {
      def parsedLine(word: String, wordCursor: Int) =
        new ParsedLine(cursor, input, word, wordCursor)
      // Used when no word is being completed
      def defaultParsedLine = parsedLine("", 0)

      def incomplete(): Nothing = throw new EOFError(
        // Using dummy values, not sure what they are used for
        /* line    = */ -1,
        /* column  = */ -1,
        /* message = */ "",
        /* missing = */ newLinePrompt)

      case class TokenData(token: Token, start: Int, end: Int)
      def currentToken: TokenData /* | Null */ = {
        val source = SourceFile.virtual("<completions>", input)
        val scanner = new Scanner(source)(using ctx.fresh.setReporter(Reporter.NoReporter))
        var lastBacktickErrorStart: Option[Int] = None

        while (scanner.token != EOF) {
          val start = scanner.offset
          val token = scanner.token
          scanner.nextToken()
          val end = scanner.lastOffset

          val isCurrentToken = cursor >= start && cursor <= end
          if (isCurrentToken)
            return TokenData(token, lastBacktickErrorStart.getOrElse(start), end)


          // we need to enclose the last backtick, which unclosed produces ERROR token
          if (token == ERROR && input(start) == '`') then
            lastBacktickErrorStart = Some(start)
          else
            lastBacktickErrorStart = None
        }
        null
      }

      def acceptLine = {
        val onLastLine = !input.substring(cursor).contains(System.lineSeparator)
        onLastLine && !ParseResult.isIncomplete(input)
      }

      context match {
        case ParseContext.ACCEPT_LINE if acceptLine =>
          // using dummy values, resulting parsed input is probably unused
          defaultParsedLine

        // In the situation where we have a partial command that we want to
        // complete we need to ensure that the :<partial-word> isn't split into
        // 2 tokens, but rather the entire thing is treated as the "word", in
        //   order to insure the : is replaced in the completion.
        case ParseContext.COMPLETE if input.startsWith(":") =>
            parsedLine(input, cursor)

        case ParseContext.COMPLETE =>
          // Parse to find completions (typically after a Tab).
          def isCompletable(token: Token) = isIdentifier(token) || isKeyword(token)
          currentToken match {
            case TokenData(token, start, end) if isCompletable(token) =>
              val word = input.substring(start, end)
              val wordCursor = cursor - start
              parsedLine(word, wordCursor)
            case _ =>
              defaultParsedLine
          }

        case _ =>
          incomplete()
      }
    }
  }
}
