---
name: pr
description: >
  Push the current branch and open a GitHub pull request using this
  repo's .github/PULL_REQUEST_TEMPLATE.md, filled in correctly —
  Summary as a flat bullet list of significant changes across every
  commit on the branch (not prose), Test plan as a checklist. Use
  whenever the user asks to "open a PR", "create a pull request", or
  similar. Companion to /commit — fixes the same class of formatting
  bug by never handing a multi-line body to the shell.
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

# /pr — Pull Request From the Repo's Template

Opens a GitHub pull request whose body is guaranteed well-formed: it
follows `.github/PULL_REQUEST_TEMPLATE.md` exactly, with a **flat
bullet list** under `## Summary` (never wrapped prose) and a checklist
under `## Test plan`. Companion to the `/commit` skill and fixes the
identical bug: composing a PR body inline and handing it to `gh pr
create --body "..."` (or through a shell heredoc) has produced bodies
with stray mid-sentence line breaks, same as commit messages did.
This skill never lets a shell carry the body text.

## Procedure

### Step 1 — Load the template

```bash
cat .github/PULL_REQUEST_TEMPLATE.md
```

If it doesn't exist, stop and tell the user — don't invent a different
shape. (It should exist; this skill and `/commit` were added together.)

### Step 2 — Gather full branch state (parallel)

```bash
git status --short
git branch --show-current
git rev-parse --abbrev-ref --symbolic-full-name @{u}
git log --oneline master...HEAD
git diff master...HEAD --stat
```

If the branch has no upstream yet, `@{u}` errors — that's expected and
just means Step 5 needs `-u`. If the branch is already merged into
`master` or has no commits ahead of it, stop and tell the user rather
than opening an empty PR.

Read **every** commit on the branch (`git log master...HEAD`, not just
the latest one) — the summary must reflect the whole branch, not the
last commit.

### Step 3 — Draft the title

Short (under 70 characters), no trailing period. If every commit on
the branch shares one Conventional Commits type, prefix the title with
it (`feat: ...`, `refactor: ...`) matching this repo's past PR titles
(e.g. `feat: journal daily entry (S-02)`). If the branch mixes types
(e.g. a feature plus incidental style/docs cleanup), use a plain
descriptive title with no prefix — don't force a misleading single
type.

### Step 4 — Fill in the template

Take the template read in Step 1 and fill its sections:

- **`## Summary`** — one bullet per significant change across the
  whole branch, most important first. Each bullet is a single logical
  line, same rule as `/commit`: no manual line break mid-bullet: if it
  needs one, split it into two bullets. Skip mechanical/trivial
  commits (formatting-only, typo fixes) unless nothing else qualifies.
- **`## Test plan`** — a checklist of concrete verification steps for
  a reviewer to run: relevant Gradle tasks from this repo (e.g.
  `./gradlew.bat ktlintCheck`, `./gradlew.bat testDebugUnitTest`,
  `./gradlew.bat assembleDebug`), plus manual smoke-test steps specific
  to what changed. Check a box (`- [x]`) only for something actually
  verified earlier in this session with visible passing output — leave
  it unchecked (`- [ ]`) otherwise. Don't claim verification that
  didn't happen.

Do not add sections the template doesn't have, and don't drop sections
it does have.

### Step 5 — Write the body to a file

Compose the filled-in template as one string, append the standard
footer, and write it verbatim with the `Write` tool to a scratch file
(e.g. `<scratchpad>/pr-body.txt`) — never inline in a `gh` flag or a
shell heredoc, for the same reason `/commit` writes to a file first:
zero shell reflow/re-escaping between composition and use.

```
## Summary
- <bullet>
- <bullet>

## Test plan
- [ ] <item>
- [ ] <item>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

<the current session URL, same as used elsewhere this session>
```

### Step 6 — Push the branch

Tell the user what's about to happen, then push:

```bash
git push -u origin <branch>
```

(Drop `-u` if the branch already tracks a remote per Step 2 and is
just behind — plain `git push` then.) Never force-push here; if the
branch and remote have diverged, stop and ask the user how to
reconcile rather than overwriting.

### Step 7 — Create the PR

```bash
gh pr create --title "<title>" --body-file "<path to the scratch file>" --base master
```

Use `--draft` if the user asked for a draft PR.

### Step 8 — Verify and report

```bash
gh pr view --json url,title -q '"\(.title)\n\(.url)"'
```

Confirm the printed title/body match what was composed — no stray
breaks inside a bullet — and give the user the PR URL.

## Notes

- Never use `--no-verify` on the push.
- Never force-push (`--force`/`-f`) as part of this skill.
- This skill only opens the PR; it doesn't merge, request reviewers,
  or edit an existing PR. Those are separate, explicit requests.
- If the user wants to update an already-open PR's description instead
  of opening a new one, that's a different flow (`gh pr edit
  --body-file`) — ask before assuming which one they want.