# AI-assisted development transcripts

This directory documents how **scrai-scala** was built with the assistance of
an AI coding agent (Cline, working with the repository owner through a chat
interface in VS Code). The sessions ran 19–20 October 2026 and produced the implementation and CI
changes documented here. The documentation commits are called out separately
in the transcript's commit map.

- [`transcript.md`](transcript.md) — the chronological transcript of the
  development sessions: what was requested, what was decided, what broke, and
  how each stage was validated.

These documents are a curated record of the collaboration, not a verbatim log
of every message. They are committed so the project's provenance — including
which parts were designed, reviewed, debugged, and approved by a human — stays
with the code. All AI-proposed changes were made in a plan/act workflow: the
agent researched and proposed plans for review in plan mode, and only edited
files after the owner approved switching to act mode.

The transcript is reconstructed from the artifacts the sessions left behind —
the git history, the build and smoke-test logs, and the committed diffs — so
its claims can be checked against the code. Compiler and resolver errors are
quoted verbatim.
