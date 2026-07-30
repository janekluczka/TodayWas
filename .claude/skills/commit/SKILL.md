---
name: commit
description: >
  Stage and create a git commit with a correctly formatted Conventional
  Commits message — subject line under 70 chars, body as a flat bullet
  list of significant changes (not wrapped prose paragraphs), and the
  standard Claude Code trailer. Use whenever the user asks to "commit",
  "commit this", "make a commit", or similar. Fixes the recurring bug
  where commit bodies end up with stray mid-sentence line breaks.
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

# /commit — Conventional Commit, Correctly Formatted

Creates a git commit whose message is guaranteed well-formed: a single
Conventional Commits subject line, a blank line, then a **flat bullet
list** of the significant changes (never wrapped prose), then the
standard trailer. Exists because passing multi-line messages through
`-m "$(cat <<EOF ... EOF)"` has repeatedly produced commits with odd
hard line-breaks stuck mid-sentence (visible with `git log`) — see
recent history on this repo for examples. This skill avoids that by
never asking a shell to carry the message at all.

## Why the old approach broke

Composing the body as flowing prose and typing it wrapped to look nice
in chat produces **literal embedded newlines mid-sentence**. `git log`
never reflows a commit body — it prints exactly the bytes it was given
— so those chat-width line breaks land permanently in history at
whatever column they happened to be typed, regardless of how wide the
reader's terminal actually is. Piping that through a heredoc doesn't
cause the problem; authoring the text as wrapped prose in the first
place does.

## Procedure

### Step 1 — Gather state (parallel)

Run together:

```bash
git status --short
git diff --staged
git diff
git log --oneline -8
```

If nothing is staged and there are unstaged/untracked changes relevant
to the request, stage the specific files by name (never `git add -A`
or `git add .`). If it's unclear which changes belong in this commit,
ask.

If a file looks like it might hold secrets (`.env`, credentials, keys)
even under an innocuous name, read it before staging and flag it to
the user rather than committing it silently.

### Step 2 — Determine the type and (optional) scope

Pick one Conventional Commits type from what's actually observed in
this repo's history: `feat`, `fix`, `chore`, `refactor`, `style`,
`docs`, `test`, `build`, `ci`, `revert`. Match the diff to the type
that best describes the *nature* of the change, not its size.

Scope is optional and this repo's history doesn't use one — omit it
unless the user asks for one.

### Step 3 — Draft the subject line

`<type>: <imperative summary>` — one line, **no line break**, no
trailing period, ideally ≤ 70 characters. Imperative mood ("add",
"fix", "route" — not "added"/"fixes"/"routes").

### Step 4 — Draft the body as a flat bullet list

The user prefers a list of significant changes over long descriptive
sentences. Rules that prevent the recurring formatting bug:

- Each bullet starts with `- ` and is **one single logical line** —
  do not insert a manual line break in the middle of a bullet to make
  it "look" a certain width. If a bullet needs a line break, that's a
  sign it should be split into two bullets instead.
- One bullet per significant change. Skip trivial/mechanical changes
  that don't need calling out.
- No body at all for a genuinely single-purpose, self-explanatory
  commit — an empty body beats a padded one.
- State *what changed*, and *why* only when the why isn't obvious from
  the subject/diff (matches the "why over what" guidance already in
  play for this repo).

### Step 5 — Assemble the full message and write it to a file

Compose the complete message as one string:

```
<type>: <subject>

- <bullet 1>
- <bullet 2>

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: <the current session URL, same as used elsewhere this session>
```

(Omit the blank-line-plus-bullets block entirely if Step 4 concluded
there's no body.)

Write this **verbatim string** to a scratch file using the `Write`
tool (not a shell heredoc, not `-m` with embedded `` `n`` / `\n``
sequences) — e.g. `<scratchpad>/commit-message.txt`. Using `Write`
guarantees the bytes on disk are exactly what was composed, with zero
shell-quoting or console-width interference from either PowerShell or
Bash.

### Step 6 — Commit from the file

```bash
git commit -F "<path to the scratch file>"
```

`-F` reads the message byte-for-byte from the file — this is the step
that actually eliminates the bug, since no shell ever reflows or
re-escapes the content between composition and commit.

### Step 7 — Verify

```bash
git log -1 --format=%B
git status --short
```

Confirm the printed message matches what was composed line-for-line —
no unexpected breaks inside a bullet — and that the working tree is
clean of anything that should have been included.

## Notes

- Never use `--no-verify` or `--no-gpg-sign` unless the user explicitly
  asks.
- Never amend an existing commit unless the user explicitly asks —
  always create a new commit.
- Never push as part of this skill; pushing is a separate, explicit
  request.
- This skill only creates the commit. If the user also wants a PR, use
  the normal PR flow afterward — don't fold PR creation into this
  skill's scope.
