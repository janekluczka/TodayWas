# Edit within 24h window — Plan Brief

> Full plan: `context/changes/edit-within-24h-window/plan.md`

## What & Why

Let a user edit a journal entry or habit check-in within 24 hours of creating it; after that it's
still viewable but permanently read-only (FR-006, US-03). This is the last piece needed before
either core-loop vertical (journaling, habit tracking) is feature-complete against the PRD.

## Starting Point

Both `JournalEntryEntity` and `HabitCheckInEntity` already store a `createdAt` timestamp — set on
write, never read back. Neither the journal detail screen nor the habit check-in screen has any
edit path today: the journal detail screen is a bare stateless composable, and an already-logged
habit row is permanently disabled. Nothing in the codebase currently injects `Clock` for
testable time comparisons.

## Desired End State

Opening a recent journal entry shows an "Edit" action that turns the text editable in place, with
Save/Cancel; after 24h, no edit action appears. Tapping a habit on the Main screen opens a new,
minimal per-habit screen showing its Today and Yesterday values — each editable if not yet logged,
or if logged and within 24h, and locked otherwise. "Log check-ins" itself stays exactly as it is
today (insert-only), just narrowed to a Today/Yesterday window instead of 7 days.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Time source | Inject `java.time.Clock` via Hilt | Boundary-exact 24h tests need `Clock.fixed(...)`, not real-clock calls | Plan |
| Journal edit UX | Promote detail screen to a full MVI screen with in-place Edit mode | Matches "a flow gets its own ViewModel once it's screen-scale" | Plan |
| Habit edit UX | New per-habit Habit Detail screen (Today/Yesterday), reached from Main; Log check-ins stays insert-only | Input and Edit are separate concerns per your steer — not merged into one screen/Save action | Plan |
| Habit backfill window | Narrow "Log check-ins" from 7 days to Today/Yesterday only | Matches journal's own window; removed the now-pointless deeper backfill since editing lives elsewhere | Plan |
| Habit Detail scope | Minimal placeholder (2 rows), not the full contribution grid | Grid is FR-011/roadmap S-05, a separate future change — this ships editing now without pulling that scope in | Plan |
| Habit list entry point | Reuse Main's existing Habit section (make rows tappable) | It already lists every habit — no separate "habit list" screen needed | Plan |
| DAO writes | Dedicated `@Update` query per DAO (not upsert) | Explicit, fails loudly if the row doesn't exist | Plan |
| Window enforcement | Domain use cases (`Update*UseCase`), not the repository | Matches `AddJournalEntryUseCase` already owning policy; repos stay pure persistence | Plan |
| `createdAt` on edit | Never changes — window is always creation-anchored | Matches FR-006's literal "within 24 hours of creation" | Plan |
| Nav key shape | `JournalEntryDetailKey` carries only `id`; screen fetches fresh | Avoids a stale nav-key snapshot diverging from the DB after an edit | Plan |
| Journal edit scope | Text only — date is immutable | Keeps the update simple; date re-picking isn't required by FR-006 | Plan |
| Testing depth | Explicit boundary tests (just-under / exactly / just-over 24h) via fixed `Clock` | Off-by-one on the cutoff is invisible manually but easy to get wrong in code | Plan |
| Mid-edit expiry | Save re-checks the window and fails with an inline error; reverts to read-only | Satisfies "enforce at write time, not just in the UI" for the one case where open-time and save-time diverge | Plan |
| Scope | One change, phased per vertical (journal then habit) | Matches roadmap's S-04 slice definition; shared Clock/EditWindow built once | Plan |

## Scope

**In scope:**
- Journal entry text editing within 24h, enforced at both screen-load and save time
- A new per-habit Habit Detail screen for viewing/editing that habit's Today and Yesterday values
- Narrowing "Log check-ins" from a 7-day backfill window to Today/Yesterday
- New `Clock` DI seam and shared `EditWindow` policy object
- New DAO update queries, repository update methods, domain update use cases for both verticals
- Boundary-accurate unit tests for the 24h cutoff

**Out of scope:**
- Changing a journal entry's date
- Any editing inside "Log check-ins" itself — it stays insert-only
- The full GitHub-style contribution grid (FR-011/roadmap S-05) — Habit Detail is a minimal
  placeholder for it
- A separate "habit list" screen — Main's existing Habit section serves that role
- DB schema migration (no new columns needed)
- Continuous/live re-evaluation of the window while a screen sits open

## Architecture / Approach

Same four-layer shape as the prior two slices, applied to both verticals: data (DAO + repository
update methods) → domain (window-checked update use cases, built on a shared `EditWindow` policy
object and injected `Clock`) → presentation (ViewModel state machines) → UI (screen wiring). The
window check lives in the domain use case, which receives the row's `createdAt` from its caller
(already loaded from existing data) rather than re-fetching it. The habit vertical reuses the
existing `ObserveHabitCheckInBoardUseCase` for reads (filtered client-side to one habit, two dates)
and the existing `LogHabitCheckInsUseCase` for inserts — only the update path and the new screen
are new.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Shared foundation | `Clock` DI + `EditWindow` policy with boundary tests | Off-by-one at the exact 24h mark |
| 2. Journal — Data | DAO `getById`/`update`, repository `getEntry`/`updateEntry` | — |
| 3. Journal — Domain | `GetJournalEntryUseCase`, `UpdateJournalEntryUseCase` | — |
| 4. Journal — Presentation | `JournalEntryDetailViewModel` (load/edit/save/cancel) | Expired-vs-generic failure branching |
| 5. Journal — UI | Detail screen edit mode, nav key → id-only | — |
| 6. Habit — Data | DAO `getByHabitAndDate`/`update`, repository `updateCheckIn` | — |
| 7. Habit — Domain | `UpdateHabitCheckInUseCase` | — |
| 8. Habit — Presentation | Log check-ins window narrowed; Main navigation; `HabitDetailViewModel` | Partitioning a mixed insert+update Save, though bounded to 2 rows |
| 9. Habit — UI | Main habit rows tappable; new `HabitDetailScreen` + nav wiring | — |

**Prerequisites:** S-02 (journal-daily-entry) and S-03 (habit-create-and-checkin) both archived —
both entity types already exist with the `createdAt` column this plan relies on.
**Estimated effort:** ~9 phases across 2 verticals; each phase is a single commit-sized unit
matching the prior slices' pace.

## Open Risks & Assumptions

- No existing pattern in this codebase passes per-entry nav data into a Hilt ViewModel
  (no `SavedStateHandle`/`@AssistedInject` usage anywhere yet). This plan resolves it with a
  `LaunchedEffect(id)`-dispatched `Load` intent rather than introducing a new DI mechanism —
  reasonable, but worth flagging as the first instance of this pattern in the codebase.
- The Habit Detail Save partitioning logic (Phase 8) is the most structurally novel piece of this
  plan — worth extra attention in implementation and review, though it's bounded to at most 2 rows
  (Today, Yesterday) rather than an unbounded board, which keeps the risk contained.
- Habit Detail is intentionally a minimal placeholder ahead of the future contribution-grid work
  (S-05) — expect it to be revisited and extended, not treated as the final design for that screen.

## Success Criteria (Summary)

- A user can edit a journal entry or habit check-in they created less than 24h ago, and the change
  persists.
- After 24h, neither can be edited — enforced even if a save is attempted from a screen left open
  past expiry, not just hidden in the UI.
