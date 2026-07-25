---
name: session-load
description: >
  Load the local session handoff file written by /session-save, present its
  contents so this session picks up where the previous one left off, then
  delete the file. Use when the user asks to "load the session", "resume
  from where I left off", "load my checkpoint", or similar at the start of
  a new conversation. Pairs with /session-save.
allowed-tools:
  - Read
  - Bash
---

# Session Load

Reads `.claude/session-handoff.md` (written by `/session-save`), surfaces it
so the current session has full context, then deletes the file — a handoff
note is meant to be read once and consumed, not left lying around as a
second source of truth alongside the actual conversation history.

## Procedure

### Step 1 — Check the file exists

```bash
test -f .claude/session-handoff.md
```

If it doesn't exist, tell the user plainly: no saved handoff was found at
`.claude/session-handoff.md`, and stop. Don't invent a summary from
whatever's in the current conversation — that defeats the purpose.

### Step 2 — Read and present it

Read the full file. Present it back to the user as your response — either
verbatim or lightly reformatted for readability, but don't drop content.
This confirms to the user (who may not be able to easily verify the file's
contents themselves, especially over remote control) exactly what was
loaded, and gives them a chance to correct anything before continuing.

### Step 3 — Delete it

```bash
rm .claude/session-handoff.md
```

Confirm the deletion in the same message: something like "Loaded and
removed the local handoff file — ready to continue from here."

### Step 4 — Continue naturally

Treat the loaded content as real context for this conversation going
forward — pick up the open threads / next steps it named, without waiting
for the user to re-explain anything it already covered.

## Notes

- **Always delete after reading**, even if the user doesn't explicitly ask —
  that's the entire point of the pairing with `/session-save` (at most one
  live handoff at a time, no stale duplicates accumulating).
- If the user says they want to load it but keep the file around too,
  that's a real request — skip Step 3 that one time, but mention that
  `/session-save`'s "already exists" check next time will otherwise prompt
  about overwrite/append.
