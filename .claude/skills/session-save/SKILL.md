---
name: session-save
description: >
  Summarize the current conversation and write it to a local, gitignored
  handoff file so a future session (e.g. after /clear, or on a different
  machine) can pick up where this one left off without needing clipboard
  access. Use when the user asks to "save the session", "checkpoint this",
  "save progress before I go", or similar — especially useful when working
  over remote control / remote desktop where copy-paste isn't available.
  Pairs with /session-load, which reads this file back in and deletes it.
allowed-tools:
  - Read
  - Write
  - Bash
---

# Session Save

Writes a self-contained handoff summary of the current conversation to
`.claude/session-handoff.md` — gitignored, local to this machine. The
companion skill `/session-load` reads it back into a future session and
deletes it once loaded, so there's never more than one stale handoff lying
around.

This exists specifically for working over remote control / remote desktop,
where the clipboard often isn't available to manually copy a summary between
sessions or machines.

## Procedure

### Step 1 — Check for an existing, unloaded handoff

```bash
test -f .claude/session-handoff.md
```

If one already exists, it means a previous save was never loaded. Ask the
user via a plain question (or `AskUserQuestion` if available):

- "Overwrite it" — the old handoff is discarded in favor of this one.
- "Append a new dated section instead" — keep the old content, add this
  summary below it under its own heading, so nothing is lost.
- "Cancel" — don't save right now.

If no file exists, skip straight to Step 2.

### Step 2 — Ensure it's gitignored

```bash
grep -qxF '.claude/session-handoff.md' .gitignore || echo '.claude/session-handoff.md' >> .gitignore
```

Do this every run — cheap, idempotent, and protects against someone having
removed the line.

### Step 3 — Gather objective repo state

Run and capture (don't just narrate from memory — get current facts):

```bash
git branch --show-current
git status --short
git log --oneline -8
```

### Step 4 — Write the summary

Compose a summary from the full conversation so far, written so that a
fresh session with **no memory of this conversation** can read it cold and
continue naturally. Do not assume the reader has any prior context — spell
out acronyms, file paths, and decisions in full the first time each is
mentioned. Structure:

```markdown
# Session Handoff — <ISO 8601 timestamp>

## Where things stand

<2-4 sentences: what this conversation was working on, in plain language.>

## What was done

<Bulleted list of concrete accomplishments — commits made, files written,
decisions finalized. Cite file paths and commit hashes where relevant.>

## Key decisions and why

<Any non-obvious choices made and the reasoning, especially ones a fresh
session might otherwise second-guess or redo differently.>

## Current repo state

- Branch: <branch>
- Uncommitted changes: <yes/no, summary if yes>
- Last few commits:
  <output of git log --oneline -8>

## Open threads / next steps

<What's in progress, what was explicitly deferred, what the user said they
want to do next. Be specific — "continue X" is less useful than "user asked
to do X after Y; Y is done, X hasn't started.">

## Anything else a fresh session needs to know

<Gotchas, user preferences expressed this session, things to avoid
repeating or re-asking.>
```

Length: as long as it needs to be to be genuinely useful — err on the side
of completeness over brevity, since the whole point is avoiding re-deriving
context. But don't pad; every section should carry real signal.

### Step 5 — Write and confirm

Write to `.claude/session-handoff.md` (or append per the Step 1 choice).
Confirm to the user in one line: the path, and a reminder that `/session-load`
in a future session will read it back and delete it.

## Notes

- This file is a **local, ephemeral handoff note**, not a durable memory
  record. If something learned this session should persist long-term across
  many future sessions (not just the next one), that belongs in the
  project's memory system, not here — mention this to the user if what
  they're saving sounds like it should outlive a single handoff.
- Never commit this file. The gitignore entry in Step 2 is not optional.
