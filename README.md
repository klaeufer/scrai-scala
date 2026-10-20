# scrai-scala

[![CI](https://github.com/klaeufer/scrai-scala/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/klaeufer/scrai-scala/actions/workflows/ci.yml)
[![Standalone release](https://github.com/klaeufer/scrai-scala/actions/workflows/release.yml/badge.svg)](https://github.com/klaeufer/scrai-scala/actions/workflows/release.yml)
[![Scala 3.3–3.10](https://img.shields.io/badge/Scala-3.3%E2%80%933.10-FDCB00?logo=scala&logoColor=white)](#quick-start)
[![sbt 1.13 / 2.0](https://img.shields.io/badge/sbt-1.13%20%2F%202.0-FF6F00?logo=sbt&logoColor=white)](#quick-start)
[![Standalone Scala 3.9.0](https://img.shields.io/badge/standalone-Scala%203.9.0-FDCB00?logo=scala&logoColor=white)](#standalone-quick-install)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-437291?logo=openjdk&logoColor=white)](#quick-start)
[![macOS and Linux](https://img.shields.io/badge/platform-macOS%20%7C%20Linux-555555?logo=linux&logoColor=white)](#development-and-tests)
[![AI development transcript](https://img.shields.io/badge/AI%20development-transcript-6f42c1)](doc/ai/transcript.md)

A teaching-oriented AI input widget for the **official Scala 3 REPL**.
Type a `//` request, press **Ctrl-G**, review the generated Scala, then press
Enter to evaluate it. Generation never submits the code automatically.

**scrai** (pronounced “scry”) blends **Scala**, **REPL**, and **AI**—with a nod
to *scrying*, or seeking insight. It turns `//` comments into editable Scala code
while leaving you in control of what runs.

## Quick start

Requirements: JDK 17 or newer, a real interactive terminal, and a Groq API key.
Supported combinations: **sbt 1.13.x and sbt 2.0.x**, each with **Scala 3.3.x
through 3.10.x** projects (scrai drives the compiler's REPL internals and ships
one runtime variant per Scala minor line; the dev build itself uses sbt 1.13.0).

### Standalone quick install

The standalone distribution runs the AI-enabled REPL with **Scala 3.9.0** and
does not require sbt, Scala CLI, or a local Scala installation. It requires Java
17 or newer and a real interactive terminal:

```sh
curl -fsSL https://github.com/klaeufer/scrai-scala/releases/latest/download/install.sh \
  -o /tmp/install-scrai.sh
sh /tmp/install-scrai.sh

export GROQ_API_KEY=your-key
scrai
```

The release installer is pinned to the application version it came from. It
downloads that exact GitHub Release archive, verifies its SHA-256 checksum, and
installs under `~/.local/share/scrai/<scrai-version>` with a launcher at
`~/.local/bin/scrai`. It does not use `sudo` or modify shell startup files.
Add `~/.local/bin` to `PATH` if the installer reports that it is not already
there. The standalone REPL evaluates Scala code in its own process; use the sbt
plugin below when the REPL must see a project's classpath and dependencies.

### In your own project (sbt plugin)

Until the plugin is published to Maven Central, publish every supported variant
locally from a clone:

```sh
sbt publishAllLocal
```

Then add to `project/plugins.sbt`:

```scala
addSbtPlugin("edu.luc.scrai" % "sbt-scrai" % "0.1.0-SNAPSHOT")
```

and enable it in `build.sbt`:

```scala
import scrai.ScraiPlugin

lazy val root = project.in(file(".")).enablePlugins(ScraiPlugin)
```

`sbt console` now starts the AI REPL, with your project's classpath available
for evaluation exactly as in the stock REPL.

### In this repository (development)

```fish
cd /Users/laufer/Work/teaching/cs371/scrai-scala
set -gx GROQ_API_KEY your-key
sbt runtime/console
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
- The sbt plugin overrides `Compile / console` when `enablePlugins(ScraiPlugin)`
  is set. It does not support sbt console `initialCommands`, `cleanupCommands`,
  or arbitrary console-specific compiler options yet.
- The input parser/highlighter is adapted from the official REPL. Completion
  candidates use the compiler, but the stock REPL's extra signature display on
  an exact completion match is not currently reproduced.
- A child JVM uses the controlling terminal (`/dev/tty` on Unix) and the full project classpath. It
  does not share sbt's JVM state. Use a terminal rather than an IDE debug console.
- Depending on the JDK, Scala and JLine may emit warnings about deprecated or
  restricted JVM APIs.

## Development and tests

```sh
sbt runtime/test                              # unit tests on the default 3.9 line
sbt "++3.3.7" "runtime/test"                  # test the 3.3 line
sbt "++3.10.0" "runtime/test"                 # test the 3.10 line
python3 scripts/pty_smoke.py                  # PTY smoke via `sbt runtime/console`
sbt publishAllLocal                           # publish all runtime + plugin variants
SCRAI_SMOKE_CWD=fixtures/sbt1 SCRAI_SMOKE_TASK=console python3 scripts/pty_smoke.py
SCRAI_SMOKE_CWD=fixtures/sbt2 SCRAI_SMOKE_TASK=console python3 scripts/pty_smoke.py
```

The CI cross-build runs the runtime unit suite on every supported line:
`3.3.7`, `3.4.3`, `3.5.2`, `3.6.4`, `3.7.4`, `3.8.4`, `3.9.0`, and
`3.10.0`. The fixture matrix exercises all of those Scala lines with both sbt 1
and sbt 2. `publishAllLocal` publishes the eight runtime artifacts and both sbt
plugin variants required by those fixtures.

Layout: `runtime/` (the REPL widget, cross-built for Scala 3.3 through 3.10 with
per-line source directories), `plugin/` (the sbt plugin, cross-built for sbt 1
and sbt 2 via `crossSbtVersions` plus `sbt2-compat`), and `fixtures/`
(standalone consumer builds on sbt 1 and sbt 2 used by CI to smoke-test the
plugin through `publishLocal`).

Unit tests use a local HTTP server; the pseudo-terminal smoke test invokes
`sbt console` with a fake model and exercises real terminal editing. Neither
requires a Groq key or sends requests to Groq. The smoke test requires macOS or
Linux and Python 3, and writes its terminal transcript to
`/tmp/scrai-pty-transcript.log`. `SCRAI_SMOKE_CWD` and `SCRAI_SMOKE_TASK` point
it at a fixture build instead of this repository. The runtime suite and local
publication workflow have been validated across the complete Scala 3.3–3.10
matrix; GitHub Actions is configured to repeat that validation on pushes and
pull requests.

Implementation:

- `runtime/src/main/scala/scrai/Config.scala`: XDG config, profiles, validation.
- `runtime/src/main/scala/scrai/Generation.scala`: prompt extraction, bounded context, asynchronous HTTP client.
- `runtime/src/main/scala/scrai/AiTerminal.scala`: official terminal adaptation plus generate/restore widgets.
- `runtime/src/main/scala/scrai/Main.scala`: `ReplDriver` subclass; evaluation and completion stay with Scala.
- `runtime/src/main/scala-3.3/` through `scala-3.10/`: the per-line completion API shims.
- `plugin/src/main/scala/scrai/ScraiPlugin.scala`: resolves the matching `scrai-runtime` artifact and launches the child JVM on the project classpath.
- `build.sbt`: multi-module build with the runtime and sbt-plugin cross-builds.

See `THIRD-PARTY-NOTICES.md` and `LICENSE-Scala` for the adapted Scala source.

Tagged releases build and test the standalone Scala 3.9.0 archive on GitHub
Actions. The release assets include the ZIP, its SHA-256 checksum, and this
installer script.

The development history of this project, including the decisions behind the
plugin conversion, is recorded in [`doc/ai/transcript.md`](doc/ai/transcript.md).