# Journal Edit Screen & AI-Assist Unification — Plan Brief

> Full plan: `context/changes/journal-edit-screen-refine/plan.md`

## What & Why

Journal editing currently lives in a bottom sheet toggled by a flag inside the Detail screen —
never a real nav destination. This plan gives it a proper Edit Journal Entry screen that mirrors Add
Journal Entry's layout (fixed date instead of a picker, text pre-filled), and unifies "help me
start"/"help me refine" into one contextual pattern used identically on both screens: start while
the text field is empty, refine the moment it isn't — including fixing refine's quota logic, which
today uses a stale client-side cap disconnected from the real server-enforced daily limit.

## Starting Point

Add Journal Entry is already a full screen with a working "help me start" bottom sheet gated by the
server's real `remainingToday` quota. Editing and "help me refine" both live inside
`JournalEntryDetailScreen`'s `isEditing` bottom sheet — refine there uses a separate client-side
`regenerationsUsed < MAX_REGENERATIONS` cap that's redundant with (and inconsistent with) the shared
server-side daily counter both features actually draw from. Refine also has no "thoughts" input
today, unlike start.

## Desired End State

Tapping Edit on an editable entry navigates to a dedicated Edit screen — pinned date, pre-filled
text, same contextual start/refine switching as Add. Refine now takes optional thoughts just like
start, and both are capped purely by the real server quota. Saving returns to Detail showing the
updated text immediately. Delete stays exclusively on Detail, reachable at any entry age.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Code sharing between Add/Edit | Two subflows (`create/`, `edit/`), cross-import shared pieces | Matches the existing precedent (`HelpMeStartUiState`/`MAX_REGENERATIONS` already cross-imported into `detail/`), avoids inventing a new shared-package abstraction |
| Start↔refine trigger | Any non-empty text switches to refine | Matches the existing precedent — starter prompts already hide once text isn't empty |
| Refine quota | Delete client `MAX_REGENERATIONS`, use server `remainingToday` only | Matches start exactly; the server already enforces one shared daily counter for both |
| Refine input | Add an optional "thoughts about the refinement" field | User's explicit ask; requires an edge-function change since it currently rejects text+thoughts together |
| Edit back/cancel | Confirm-discard dialog on unsaved changes | User's explicit choice — a new UI pattern for this app, no existing precedent |
| Date display | Add a non-interactive mode to `DsDateStrip` | Keeps Edit visually identical to Add, as requested, rather than a different-looking static component |
| Habit check-ins | Out of scope | Keeps this change focused; a symmetrical follow-up if needed |
| Delete placement | Stays on Detail only | Must remain reachable regardless of the 24h window; Edit is only reachable within it |
| Test coverage | Unit tests only, mirroring existing suite | Matches this codebase's current testing balance for journal flows |

## Scope

**In scope:**
- New Edit Journal Entry screen + nav entry, Detail simplification
- Contextual "help me start"/"help me refine" switch on both Add and Edit
- Refine gains an optional thoughts input (edge function + Android changes)
- Refine's quota logic unified with start's (server-driven, no client cap)
- `DsDateStrip` non-interactive display mode
- Fix: Detail no longer shows stale text after an edit (one-shot load → refresh-on-enter)

**Out of scope:**
- Habit check-in editing
- Delete on the Edit screen
- Instrumented/E2E tests
- Changes to the 24h edit-window semantics themselves
- Changes to the daily AI-assist quota's size or enforcement mechanism

## Architecture / Approach

`create/` keeps owning "start" pieces (`HelpMeStartUiState`, `HelpMeStartBottomSheet`,
`StarterPromptList`); a new `edit/` package owns "refine" pieces (`HelpMeRefineUiState`, extended
with `thoughts`/`remainingToday`) plus the new `EditJournalEntryScreen`/ViewModel. Each screen
cross-imports the other's flow when it needs it — Add gets refine, Edit gets start — exactly
mirroring how `detail/` already cross-imports from `create/` today. Detail loses all edit/refine
state and code, keeping only viewing + Delete, and gains a `ScreenEntered` intent so it re-fetches
the entry every time it becomes the active screen (fixing the staleness bug the Add/Edit split would
otherwise introduce).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend: refine + thoughts | Edge function + Android data/domain layers accept thoughts on refine | Requires redeploying a live, shared edge function |
| 2. Read-only date display | `DsDateStrip` gains a non-interactive mode | Low risk, additive design-system change |
| 3. Dedupe shared helpers | One canonical tone-label/error-message helper, renamed shared strings | Pure refactor; risk is missing a reference during rename |
| 4. Build Edit screen | New `edit/` package, fully unit-tested, not yet wired in | Largest phase; isolated from the running app until Phase 5 |
| 5. Wire it up | Nav entry, Detail cutover, Add gets refine, dead code removed | Most files touched in one phase; staleness fix must land correctly |

**Prerequisites:** None beyond this being a solo change on its own feature branch
(`feature/journal-edit-screen-refine`).
**Estimated effort:** ~5 focused sessions, one per phase.

## Open Risks & Assumptions

- Phase 1's edge function redeploy touches live, shared infrastructure — requires explicit
  confirmation before deploying, and testing against the real deployed function (no local Supabase
  functions emulator is set up for this project).
- Phases 4 and 5 temporarily duplicate `HelpMeRefineUiState`/`HelpMeRefineStep` between `detail/` and
  `edit/` — intentional, resolved when Phase 5 deletes the old `detail/` copies.
- No instrumented tests cover the new nav wiring itself (Detail → Edit → back) — relying on manual
  verification for that specific path, consistent with this codebase's current testing balance.

## Success Criteria (Summary)

- A user can tap Edit on a recent journal entry, land on a dedicated screen with the date pinned and
  text pre-filled, use either "help me start" (if cleared) or "help me refine" (with optional
  thoughts) depending on whether the field is empty, save, and immediately see the update on Detail.
- Add Journal Entry gains the same "help me refine" capability once the user has typed anything.
- Refine and start share one server-enforced daily quota with no separate client-side cap anywhere.
