<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Entry Delete Implementation Plan

- **Plan**: context/changes/entry-delete/plan.md
- **Scope**: Phase 1 of 4 through Phase 4 of 4 (full plan)
- **Date**: 2026-08-17
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

## Findings

### F1 — CLAUDE.md's hard edit-window rule doesn't document the delete carve-out

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: CLAUDE.md (hard rules section); `domain/usecase/DeleteJournalEntryUseCase.kt`, `DeleteHabitUseCase.kt`, `DeleteHabitCheckInUseCase.kt`
- **Detail**: None of the three delete use cases check `EditWindow.isEditable`, unlike `UpdateJournalEntryUseCase`/`UpdateHabitCheckInUseCase`, which both gate on it. This was a deliberate, explicitly-confirmed decision during planning (plan.md "What We're NOT Doing": *"No change to the 24h EditWindow gate on Update — Delete is intentionally unrestricted by age"*) — not an oversight. But CLAUDE.md's hard rule still reads *"Journal/habit entries are editable only within 24 hours of creation (FR-006); after that they become read-only. Enforce this at write time, not just in the UI"* with no carve-out mentioned. A future reader (human or agent) working from CLAUDE.md alone would reasonably read unrestricted delete as a violation of this hard rule, not as an intentional exception.
- **Fix**: Add a one-line carve-out to CLAUDE.md's hard rule bullet, e.g. *"...after that they become read-only for editing. Deletion remains available at any time by design (see `entry-delete` change) — this is the one exception to the read-only rule."*
- **Decision**: FIXED — added carve-out sentence to CLAUDE.md's hard rules section.

### F2 — Non-atomic habit-delete cascade can silently strand check-in history on partial failure

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `data/repository/HabitRepositoryImpl.kt:105-111` (`deleteHabit`); `ui/habit/detail/HabitDetailViewModel.kt:256-269` (`onDeleteHabitConfirmed`)
- **Detail**: `deleteHabit` calls `habitCheckInDao.deleteByHabitId(id)` first, then `habitDao.deleteById(id)`, with no transaction wrapping the two calls (a documented, accepted tradeoff per the plan's Implementation Approach). If the first succeeds and the second then fails (even after `safeDbCall`'s one retry), the habit survives with all its check-in history permanently gone — and the user only sees a generic `deleteHabitError` snackbar, with no indication their history is already gone. No test exercises this specific partial-failure ordering.
- **Fix A ⭐ Recommended**: Reorder `deleteHabit` to delete the habit row first, then the check-ins.
  - Strength: Turns the failure mode from "user-visible silent history loss on a habit that's still there" into "invisible orphaned local check-in rows" (harmless — nothing queries check-ins for a nonexistent habit, so they never surface in the UI).
  - Tradeoff: Orphaned local check-ins would persist indefinitely if the second step fails, since there's no local FK/cascade and `syncWithRemote()` never deletes local rows absent remotely — a slow, invisible space leak rather than a correctness bug.
  - Confidence: MED — a one-line DAO-call reorder, but the failure-mode semantics aren't unit-tested either way.
  - Blind spot: Haven't verified no other local query assumes every check-in has a live parent habit row.
- **Fix B**: Keep the current order, but give `deleteHabit` a richer failure result so the ViewModel can show a distinct "check-ins were removed but the habit couldn't be deleted" message.
  - Strength: Gives the user accurate information instead of a generic error, without changing which data survives a partial failure.
  - Tradeoff: More surface area — new state, new string, new test — for an edge case the plan already assessed as low-odds.
  - Confidence: MED — requires `deleteHabit` to return more than `Result<Unit>` to distinguish "which half failed."
  - Blind spot: Same detection question — no current mechanism reports which of the two calls failed.
- **Decision**: FIXED — via a third option (user's suggestion), refined twice: `deleteHabit` now
  wraps both DAO calls in a transaction, making the cascade genuinely atomic instead of picking a
  "less bad" failure ordering. First pass injected `TodayWasDatabase` directly and used
  `database.withTransaction {}`, but the user flagged that as leaking a persistence-framework
  detail into the repository layer (every other repository here depends only on DAOs) and forcing
  `HabitRepositoryImplTest.kt` onto Robolectric + a real in-memory database just to satisfy the
  constructor. Replaced with a small `TransactionRunner` interface
  (`data/util/TransactionRunner.kt`) + `RoomTransactionRunner` impl (`data/util/RoomTransactionRunner.kt`,
  wraps `TodayWasDatabase.withTransaction`), bound via Hilt in `di/DatabaseModule.kt`.
  `HabitRepositoryImpl` now depends on the abstraction, not the concrete database class.
  `HabitRepositoryImplTest.kt` dropped Robolectric entirely and uses a trivial `FakeTransactionRunner`
  (`data/util/FakeTransactionRunner.kt`, just invokes the block) — cleaner than the first pass in every
  respect. All tests pass. Known limitation unchanged: since the DAOs in tests are fakes, there's no
  unit test proving the rollback-on-partial-failure behavior itself, only that production code now
  routes through a real transaction.
  A related idea surfaced during triage — reordering delete to call the remote API first and then
  refetch, rather than local-first-then-background-push — was explicitly deferred, not adopted: it
  would only apply to the signed-in path (offline/signed-out deletes have no API to call and would
  still need local-only atomicity), and would break from the local-first pattern every other write
  in this codebase follows. Belongs in a separate, deliberately-scoped change (candidate:
  `architecture-hardening`, which already owns the related sync-reliability work) rather than being
  folded into this fix.

### F3 — `HabitDaoTest`/`HabitCheckInDaoTest` never got delete-path tests the plan promised

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: `app/src/test/java/pl/luczka/todaywas/data/local/dao/HabitDaoTest.kt`; `HabitCheckInDaoTest.kt`
- **Detail**: Plan Phase 4's Automated Verification and the Testing Strategy section both explicitly call for "new `HabitDaoTest`, `HabitCheckInDaoTest`" Robolectric-backed cases covering `deleteById`/`deleteByHabitId`. Neither file was touched in this branch's diff and neither contains any reference to `deleteById`/`deleteByHabitId` — confirmed by two independent review passes. By contrast, `JournalEntryDaoTest.kt` *does* have a matching test (`` `should remove only the matching entry when deleteById is called` ``). The actual delete behavior is exercised indirectly via `HabitRepositoryImplTest.kt`'s fakes and live adb verification, but there's no direct proof the real Room-generated SQL for the two new `@Query` methods is correct.
- **Fix**: Add a `deleteById` test to `HabitDaoTest.kt` and `deleteById`/`deleteByHabitId` tests to `HabitCheckInDaoTest.kt`, mirroring `JournalEntryDaoTest.kt`'s existing pattern (AAA structure, `` `should [outcome] when [scenario]` `` naming).
- **Decision**: FIXED — added `` `should remove only the matching habit when deleteById is called` `` to `HabitDaoTest.kt`, and `` `should remove only the matching check-in when deleteById is called` `` + `` `should remove every check-in for the habit when deleteByHabitId is called` `` to `HabitCheckInDaoTest.kt`. `ktlintCheck` + `testDebugUnitTest` both pass.

### F4 — `HabitDetailScreen` ships no previews for the new delete dialogs or new UiState fields

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `ui/habit/detail/HabitDetailScreen.kt` (`DeleteHabitDialog`, `DeleteCheckInDialog`, `HabitDetailScreenPreviewStateProvider`)
- **Detail**: `JournalEntryDetailScreen.kt` correctly added a `DeleteEntryDialogPreview` for its new dialog, per lessons.md's "every screen/component ships previews for meaningful states" rule. `HabitDetailScreen.kt` added no equivalent preview for either `DeleteHabitDialog` or `DeleteCheckInDialog`, and `HabitDetailScreenPreviewStateProvider` wasn't updated to cover any of the six new `HabitDetailUiState` fields (`isDeleteHabitDialogVisible`, `isDeletingHabit`, `deleteHabitError`, `checkInPendingDelete`, `isDeletingCheckIn`, `deleteCheckInError`) — both new dialogs are unverifiable via Compose preview.
- **Fix**: Add `DeleteHabitDialogPreview`/`DeleteCheckInDialogPreview` composables (mirroring `DeleteEntryDialogPreview`'s shape) and extend `HabitDetailScreenPreviewStateProvider` with at least one state covering the delete-dialog-visible cases.
- **Decision**: FIXED — added both preview composables, plus a third `HabitDetailScreenPreviewStateProvider` state with `isDeleteHabitDialogVisible = true`. `ktlintCheck` + `assembleDebug` both pass.
