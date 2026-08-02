<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Edit within 24h window

- **Plan**: context/changes/edit-within-24h-window/plan.md
- **Scope**: Full plan (Phases 1-9)
- **Date**: 2026-08-02
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — HabitDetailMapper's `today` bypasses the injected Clock

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailMapper.kt:15
- **Detail**: `toHabitDetailRows` takes a `now: Instant` parameter (the injected `Clock`'s time,
  used correctly for `eligibleForEdit`), but the `today`/`yesterday` values that decide *which
  unlogged rows even appear in the list* are computed via `LocalDate.now()` — the real system
  clock, not `now`. This is the one line in the whole change that doesn't go through the `Clock`
  seam the plan introduced specifically to make 24h-boundary logic deterministic and testable
  (plan.md: "this is the first `Clock` seam so the 24h boundary is testable with `Clock.fixed(...)`").
  `HabitDetailViewModelTest.kt` doesn't catch this because every test's fixed clock is set to the
  real `Instant.now()`, so the two never diverge under test. It's also a latent flakiness risk:
  `Clock.fixed(now, ZoneOffset.UTC)` pins UTC while `LocalDate.now()` uses the JVM's default zone —
  near a day boundary in a non-UTC zone these can disagree about what "today" is.
- **Fix**: Derive `today` from the `now` parameter instead of the system clock: `LocalDate
  .ofInstant(now, ZoneId.systemDefault())` (matching `ClockModule`'s `Clock.systemDefaultZone()`).
- **Decision**: FIXED — applied the recommended fix.

### F2 — Habit save failure never distinguishes window-expiry from a generic failure (journal does)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailViewModel.kt:161-167,
  HabitDetailScreen.kt (snackbar message), strings.xml (missing string)
- **Detail**: `JournalEntryDetailViewModel` checks `result.exceptionOrNull() is
  EditWindowExpiredException` and surfaces a dedicated `journal_detail_edit_window_expired_error`
  message plus flips `isEditable`/`isEditing` so the screen visibly reverts to read-only.
  `HabitDetailViewModel.onSaveClicked`'s failure branch just sets `saveError = true` regardless of
  cause, and `HabitDetailScreen` shows one generic `habit_detail_error` ("Something went wrong.
  Please try again.") for every failure — including the exact scenario this feature exists to
  handle: a user leaves Habit Detail's edit mode open past 24h, then saves. That save will fail
  every time on retry, but the message tells them to retry anyway. No
  `habit_detail_edit_window_expired_error` string was added, unlike journal's equivalent.
- **Fix A ⭐ Recommended**: Detect window-expiry in the batch and surface a dedicated message
  - Strength: Full parity with the journal detail screen's already-shipped pattern in this same
    change; correctly tells the user retrying won't help.
  - Tradeoff: `onSaveClicked` processes a *batch* of rows (not journal's single value), so
    "detect expiry" means checking whether *any* failed result's exception is
    `EditWindowExpiredException` — a few extra lines, not a one-liner like F1.
  - Confidence: HIGH — the exact detection logic (`result.exceptionOrNull() is
    EditWindowExpiredException`) already exists and works in `JournalEntryDetailViewModel`.
  - Blind spot: Haven't designed the exact partial-failure UX (e.g. what if one row is a generic
    failure and another is expiry, in the same Save) — worth a quick decision before implementing.
- **Fix B**: Leave the generic message, note as a follow-up
  - Strength: The underlying enforcement is already correct and safe (the use case still blocks
    the write) — this is copy/UX polish, not a correctness or data-safety bug.
  - Tradeoff: A user who hits this will see a misleading "try again" for a save that can never
    succeed, until they give up or reopen the screen.
  - Confidence: MEDIUM — depends how often a real user would actually leave Habit Detail open past
    24h before saving; likely rare in practice.
  - Blind spot: No usage data on how often this edge case is actually hit.
- **Decision**: FIXED — applied Fix A. Added `saveErrorIsWindowExpired` to `HabitDetailUiState`,
  computed in `onSaveClicked` via `results.any { it.exceptionOrNull() is
  EditWindowExpiredException }`, new `habit_detail_edit_window_expired_error` string, screen picks
  the message accordingly. Added 2 tests. `isEditMode` is intentionally left unchanged on any
  failure (matches prior generic-failure behavior; partial-failure auto-revert was flagged as an
  open design question, not addressed here).

### F3 — `alreadyLogged` field is computed but never consumed by real UI logic

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailUiState.kt:22-23,
  HabitDetailMapper.kt:27
- **Detail**: `HabitDetailRowUiState.alreadyLogged` is populated by `toHabitDetailRows` but is only
  ever referenced in `HabitDetailScreen.kt`'s preview sample data — no real rendering logic reads
  it. Either it's dead state left over from an earlier design iteration (this screen went through
  several redesigns during Phase 8/9), or the screen is missing intended behavior (e.g. visually
  distinguishing "not yet logged" rows) the field was meant to support.
- **Fix**: Remove the unused field, consistent with the project's "don't add state for scenarios
  that can't happen" convention — nothing in the current design reads it, and it's cheap to
  recompute later if a real need shows up.
- **Decision**: SKIPPED

### F4 — Edit-window enforcement is conventional, not structural

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt:32-39,
  HabitRepositoryImpl.kt:62-71
- **Detail**: `JournalRepository.updateEntry`/`HabitRepository.updateCheckIn` have no window check
  of their own — the 24h enforcement lives entirely in `UpdateJournalEntryUseCase`/
  `UpdateHabitCheckInUseCase`. Today there's exactly one calling path into each repository method
  (confirmed via grep), so this isn't a live bypass. But nothing in the type system stops a future
  ViewModel from injecting the repository directly and calling `updateEntry`/`updateCheckIn`
  without ever going through `EditWindow.isEditable`.
- **Fix**: Add a one-line KDoc note on both repository interface methods: "callers must check
  `EditWindow` first — enforced by `Update*UseCase`, not here." Purely documentation; no functional
  change. (Given this project's convention against defensive code for scenarios that can't happen
  yet, skipping this is also reasonable — it's a judgment call, not a correctness gap.)
- **Decision**: FIXED — added the note to both `JournalRepository.updateEntry` and
  `HabitRepository.updateCheckIn`.
