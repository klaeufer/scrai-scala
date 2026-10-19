# Third-party source

`runtime/src/main/scala/scrai/AiTerminal.scala` is adapted from the Scala 3.3.7
compiler's `dotty/tools/repl/JLineTerminal.scala`:
https://github.com/scala/scala3/blob/3.3.7/compiler/src/dotty/tools/repl/JLineTerminal.scala

Copyright the Scala contributors; distributed under Apache License 2.0.
The upstream license is included in `LICENSE-Scala`. Changes add AI widgets,
configuration, and a separate history file, and move the class into `scrai`.

# Third-party dependencies

The following binary dependencies are used unmodified and are not redistributed
here; each remains under its own license.

- `com.github.sbt:sbt2-compat` (Apache License 2.0) — used by the `plugin`
  module to compile a single plugin source against both sbt 1 and sbt 2.
- `com.lihaoyi:ujson` (MIT License) — JSON encoding for the model request.
- `org.scalameta:munit` (Apache License 2.0) — test framework, test scope only.
- `org.scala-lang:scala3-compiler` and `org.scala-lang:scala3-repl` (Apache
  License 2.0) — the compiler and REPL that scrai drives; the upstream
  license is included in `LICENSE-Scala`.
