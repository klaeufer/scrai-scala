# Third-party source

`runtime/src/main/scala/scrai/AiTerminal.scala` is adapted from the Scala 3.3.7
compiler's `dotty/tools/repl/JLineTerminal.scala`:
https://github.com/scala/scala3/blob/3.3.7/compiler/src/dotty/tools/repl/JLineTerminal.scala

Copyright the Scala contributors; distributed under Apache License 2.0.
The upstream license is included in `LICENSE-Scala`. Changes add AI widgets,
configuration, and a separate history file, and move the class into `scrai`.