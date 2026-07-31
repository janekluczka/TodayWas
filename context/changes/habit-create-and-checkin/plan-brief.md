# Habit Creation and Check-in — Plan Brief

> Full plan: `context/changes/habit-create-and-checkin/plan.md`

## What & Why

S-03 in the roadmap: a user creates a habit (binary done/not-done, or a numeric scale with a
user-defined range) and logs check-ins for it — independent of journaling — seen on the main screen
as a colored/status cell per habit. This is the second core-loop slice after S-02's journal entry,
completing FR-002/FR-004's must-have requirements.

## Starting Point

S-02 (journal-daily-entry) is fully implemented: `TodayWasDatabase` is at `version = 2`, the
retry-once-then-fail write pattern is established, and the main-screen FAB is already built
expand-ready for a second action. `Focus`/`FocusUiState` already model `HABIT`, but nothing
currently renders behind it besides a placeholder string.

## Desired End State

A user with Habit (or Both) focus sees a Habit section on the main screen listing every habit with
today's status. A "Create habit" FAB action is always available; a "Log check-ins" action appears
once at least one habit exists, opening one screen that logs the *whole day across every habit at
once* — a 7-day date strip up top, one input row per unlogged habit below (already-logged habits
show read-only with a green "Done" label), saved all-or-nothing.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Habit fields | Name, type, description, and (for scale) user-defined min/max | User wants flexibility beyond a fixed 1–5 scale and room for notes on what counts. |
| Re-logging a habit+day | Reject (unique index), not upsert | Consistent single write-semantics with journal; no edit flow exists yet in this slice anyway. |
| Backfill window | Bounded to the last 7 days | Keeps the new date-strip component small; covers the realistic "forgot a day or two" case. |
| Scale input control | New `TodayWasStepper` (+/- clamped to range) | Works for any user-defined range without laying out a variable number of buttons. |
| Check-in flow shape | **One screen logs all habits for a selected day at once** (journal-style "log the whole day"), not a per-habit flow | User explicitly wants day-at-a-time logging, matching journal's mental model, rather than picking one habit at a time. |
| Already-logged rows | Shown read-only with a green "Done" label, not hidden | User wants to see the full day's picture at a glance, not just what's left to do. |
| Batch save | All-or-nothing in one transaction | Avoids partial-day state from a single flaky write; retry re-enters the whole day. |
| Habit edit/delete | Out of scope this slice | Matches S-02's scope discipline — deferred alongside the 24h edit window (S-04). |
| **Architecture change**: how input screens get "what's still loggable" | Each ViewModel derives it itself via an injected use case, not received pre-computed through a nav key | User explicitly asked to stop passing this kind of data from `MainViewModel` through nav keys; applied to **both** the new habit flow and retrofitted onto the existing journal flow for consistency. |

## Scope

**In scope:**
- Habit creation (name, type, description, scale range) — unlimited habits.
- Logging check-ins for one or more habits at once, for today or the prior 6 days.
- Main-screen Habit section (today-status per habit) and two new FAB actions.
- Retrofitting `AddJournalEntryViewModel` to self-derive addable slots (drops assisted injection).
- A generalized `TodayWasDateStrip` design-system component (replaces journal's private, 2-value
  `DayStrip`) and a new `TodayWasStepper`.

**Out of scope:**
- Editing or deleting habits or check-ins (S-04 territory).
- Backfill beyond 7 days.
- Per-habit contribution/history view (FR-011/S-05).
- A real Room `Migration` (destructive migration continues).

## Architecture / Approach

Mirrors S-02's Data → Domain → Presentation → UI layering exactly, with one new domain concept:
two combinator use cases (`ObserveHabitCheckInBoardUseCase`, `ObserveAddableJournalDateSlotsUseCase`)
that each screen's ViewModel *and* `MainViewModel` independently observe, so neither the habit nor
the journal input flow receives pre-computed availability data via navigation — each derives its
own view of the same underlying source. Batch check-in saves use a `@Transaction`-annotated
default-body DAO method (not a bare list `@Insert`) to guarantee all-or-nothing atomicity.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Data layer | `Habit`/`HabitCheckIn` entities, DAOs, DB v3, repository (retry-once + atomic batch insert) | Getting the `@Transaction` batch-insert atomicity right (has a dedicated test) |
| 2. Domain | Thin use cases + the two combinator use cases | Combinator flows need correct re-emission on either source changing |
| 3. Presentation | `MainViewModel` habit support, `CreateHabitViewModel`, `LogHabitCheckInsViewModel`, journal ViewModel retrofit | The journal retrofit is a real behavior-preserving refactor — regression risk on the existing flow |
| 4. UI | `TodayWasStepper`, generalized `TodayWasDateStrip`, main screen + 2 new screens, nav wiring, strings | Generalizing the date-strip while preserving the validated swipe/centered UX |

**Prerequisites:** S-01 (onboarding/focus pick) — already done. S-02's patterns (retry-once write,
MVI shape, FAB expand mechanism) are the template.
**Estimated effort:** ~4 sessions across 4 phases, comparable to S-02 plus the journal-retrofit
sub-task in Phase 3.

## Open Risks & Assumptions

- Destructive migration (`fallbackToDestructiveMigration`) wipes local data on this version bump —
  same accepted risk as S-02, now compounding for a second time.
- The journal retrofit in Phase 3 changes a working, manually-verified flow (S-02's Add-entry
  screen) — Phase 3's manual verification explicitly re-checks the journal flow end-to-end as a
  regression gate, not just the new habit flow.
- No foreign-key constraint between check-ins and habits — acceptable now since deletion isn't in
  scope, but will need revisiting the moment habit deletion is built.

## Success Criteria (Summary)

- A user can create both a binary and a scale habit, log check-ins for one or more habits for today
  or a backfilled day within the last week, and see today's status reflected immediately on the
  main screen — all with zero account required.
- A forced write failure during a batch check-in save leaves no partial state, and journal's
  existing Add-entry flow keeps working unchanged after the ViewModel-ownership retrofit.