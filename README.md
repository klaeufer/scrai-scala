# scrai-scala

A teaching-oriented AI input widget for the **official Scala 3 REPL**.
Type a `//` request, press **Ctrl-G**, review the generated Scala, then press
Enter to evaluate it. Generation never submits the code automatically.

**scrai** (pronounced “scry”) blends **Scala**, **REPL**, and **AI**—with a nod
to *scrying*, or seeking insight. It turns `//` comments into editable Scala code
while leaving you in control of what runs.

## Quick start

Requirements: JDK 17 or newer, sbt, a real interactive terminal, and a Groq API key.
The project pins Scala 3.9.0 LTS and sbt 1.13.0 because it uses compiler REPL internals.

In fish:

```fish
cd /Users/laufer/Work/teaching/cs371/scrai-scala
set -gx GROQ_API_KEY your-key
sbt console
```

In bash/zsh, use `export GROQ_API_KEY=your-key` instead. Set the key before
starting sbt; don't put it into Scala input, history, or the configuration file.
Missing credentials do not prevent normal Scala evaluation.

```scala
scala> val words = List("pear", "apple", "plum", "apricot")
scala> // group words by their first letter
```

Press Ctrl-G. A typical result is `words.groupBy(_.head)`, inserted into the
editable input, not executed. Edit it or press Enter to evaluate. For multiline
results the usual Scala REPL acceptance rules apply: move to the last line
before accepting, and enter a blank line if Scala requests a continuation.
You can paste multiple `//` lines as one prompt.

| Input | Action |
|---|---|
| Ctrl-G | Generate from an input buffer consisting entirely of `//` comments |
| Escape, then g | Restore the original prompt after generation, before submitting |
| Ctrl-C during generation | Cancel the request and retain the prompt |
| Ctrl-C while editing | Discard the current input and return to a fresh prompt |
| Tab | Compiler-backed Scala completion |
| `:ai-clear` | Clear the context sent to the model, without deleting Scala definitions |
| `:reset` | Official REPL reset; also clears AI context |
| `:quit` or Ctrl-D | Exit the REPL and return to sbt, or exit a batch invocation |

Other keys typed while generation is pending are discarded; wait for completion
or cancel with Ctrl-C. The model request has a configurable timeout.

## Configuration

No config file is needed for the default Groq profile. To customize, copy
`scrai.example.ini` to `$XDG_CONFIG_HOME/scrai.ini` (default
`~/.config/scrai.ini`), or set `SCRAI_CONFIG` to an absolute file path.
`SCRAI_PROFILE` overrides the selected named profile.

```ini
[scrai]
profile = groq
hotkey = ^G
restore_hotkey = ^[g
context_chars = 12000
timeout_seconds = 30
instructions = Prefer clear, concise examples suitable for live teaching.

[groq]
base_url = https://api.groq.com/openai/v1
api_key_env = GROQ_API_KEY
model = openai/gpt-oss-120b
temperature = 0.2
max_tokens = 2048
```

Hotkeys use JLine notation: `^G` means Ctrl-G; `^[g` means Escape followed by g.
Profiles inherit settings from `[scrai]`. Configuration is loaded at startup;
restart the console after editing it. Unknown keys and invalid values are errors.
The client uses OpenAI-compatible chat completions; `max_tokens` in this config
is sent as `max_completion_tokens` in the HTTP request.

Groq model availability and free-plan limits can change. Choose a model your
account can access. This project does not promise unlimited or permanently free
inference and does not automatically fall back to another model/provider.

## Privacy and limitations

- Generation sends your prompt and a bounded tail of previous submitted Scala
  source to the configured endpoint. Set `context_chars = 0` to send no history.
- History can contain failed submissions. Results and runtime object contents
  are not captured; generated `resN` bindings are not reconstructed. Commands
  such as `:load` are not expanded into the AI context.
- `:ai-clear` clears only in-memory AI context, not terminal history. Input
  history is stored separately from Scala's normal history in `~/.scrai_history`.
- Generated code is untrusted and can perform arbitrary effects when **you**
  execute it. There is no sandbox, automatic evaluation, or automatic repair.
- HTTPS is required except for local servers. API keys are read from environment
  variables; provider error bodies are not printed.
- This is a project-local launcher, not an installable sbt plugin. It overrides
  `Compile / console` only. It does not support sbt console `initialCommands`,
  `cleanupCommands`, or arbitrary console-specific compiler options yet.
- The input parser/highlighter is adapted from the official REPL. Completion
  candidates use the compiler, but the stock REPL's extra signature display on
  an exact completion match is not currently reproduced.
- A child JVM uses the controlling terminal (`/dev/tty` on Unix) and the full project classpath. It
  does not share sbt's JVM state. Use a terminal rather than an IDE debug console.
- Depending on the JDK, Scala and JLine may emit warnings about deprecated or
  restricted JVM APIs.

## Development and tests

```sh
sbt test
python3 scripts/pty_smoke.py
```

Unit tests use a local HTTP server; the pseudo-terminal smoke test invokes
`sbt console` with a fake model and exercises real terminal editing. Neither
requires a Groq key or sends requests to Groq. The smoke test requires macOS or
Linux and Python 3, and writes its terminal transcript to
`/tmp/scrai-pty-transcript.log`.

Implementation:

- `Config.scala`: XDG config, profiles, validation.
- `Generation.scala`: prompt extraction, bounded context, asynchronous HTTP client.
- `AiTerminal.scala`: official terminal adaptation plus generate/restore widgets.
- `Main.scala`: `ReplDriver` subclass; evaluation and completion stay with Scala.
- `build.sbt`: project-local `sbt console` task and inherited-terminal launcher.

See `THIRD-PARTY-NOTICES.md` and `LICENSE-Scala` for the adapted Scala source.