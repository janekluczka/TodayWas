<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Architecture Hardening

- **Plan**: context/changes/architecture-hardening/plan.md
- **Scope**: Phase 1-5 of 5 (full plan, all complete)
- **Date**: 2026-08-20
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Grounding

Two parallel sub-agents reviewed the full diff (`master..feature/architecture-hardening`, 8 commits,
56 files changed, 3705 insertions / 917 deletions): one for plan-vs-actual drift across all 5
phases plus both out-of-band commits, one for safety/quality/pattern compliance across all 34
production files touched. Automated verification (`ktlintCheck`, `testDebugUnitTest`,
`assembleDebug`) independently re-run against the current `feature/architecture-hardening` HEAD —
`BUILD SUCCESSFUL`. All manual Progress checkboxes (1.4-1.6, 2.2-2.3, 3.4-3.7, 4.5-4.9, 5.4) were
verified live on the Pixel emulator against the real linked Supabase project during implementation,
not rubber-stamped — screenshots, logcat traces, and direct SQL row checks confirm each one.

## Findings

### F1 — `LocalHabitDataSourceImpl.clearAll()` is not transactional, unlike its sibling `deleteHabitAndCheckIns()`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `app/src/main/java/pl/luczka/todaywas/data/local/api/LocalHabitDataSourceImpl.kt:93-96`
- **Detail**: `clearAll()` performs `habitCheckInDao.clearAll(); habitDao.clearAll()` inside
  `safeDbCall { }` only — no `transactionRunner.runInTransaction { }`, unlike `deleteHabitAndCheckIns()`
  a few lines above in the same class, which explicitly wraps its two cross-table writes in a
  transaction "so a habit is never left with only some of its check-ins deleted." `clearAll()` runs
  on sign-out/account-switch (via `HabitRepositoryImpl.clearLocal()`); a crash between the two
  `DELETE`s would leave stale rows behind. Low real-world risk (both statements are idempotent
  deletes, and `safeDbCall`'s retry would likely finish the job on next attempt), but it's an
  inconsistency with the atomicity pattern this same diff establishes one method above it.
- **Fix**: Wrap `clearAll()`'s two DAO calls in `transactionRunner.runInTransaction { }`, matching
  `deleteHabitAndCheckIns()`'s existing shape in the same file.
- **Decision**: FIXED (applied — `ktlintCheck`/`testDebugUnitTest` re-verified, BUILD SUCCESSFUL)

### F2 — `ObserveHabitContributionUseCase`/`ObserveJournalContributionUseCase` have an undocumented dependency asymmetry

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/ObserveHabitContributionUseCase.kt`
  vs. `ObserveJournalContributionUseCase.kt`
- **Detail**: `ObserveJournalContributionUseCase` injects `JournalRepository` directly (matching the
  project's "use case depends on repository, not another use case" convention).
  `ObserveHabitContributionUseCase` instead takes `checkIns: Flow<List<HabitCheckIn>>` as a
  caller-supplied parameter rather than injecting `HabitRepository`. This is deliberate — plan's
  own Phase 4 item 3 explains `HabitDetailViewModel` needs check-ins scoped to one habit, which the
  repository's `observeCheckIns()` (all habits) can't express — so it's not a convention violation,
  just an asymmetry between two similarly-named use cases that could confuse a future reader who
  hasn't read the plan.
- **Fix**: Add a one-line KDoc/comment on `ObserveHabitContributionUseCase` explaining why it takes
  check-ins as a parameter instead of injecting `HabitRepository`, unlike its journal sibling.
- **Decision**: FIXED (fixed differently — user chose to remove the asymmetry rather than document
  it: added `HabitRepository.observeCheckIns(habitId: String): Flow<List<HabitCheckIn>>`,
  implemented in `HabitRepositoryImpl` as a filter over the existing local check-ins flow, and
  `FakeHabitRepository`. `ObserveHabitContributionUseCase` now injects `HabitRepository` directly
  and takes `habitId: String` instead of a caller-supplied `Flow<List<HabitCheckIn>>`, matching
  `ObserveJournalContributionUseCase`'s shape exactly. `HabitDetailViewModel`/
  `HabitDetailViewModelTest` updated to the new call shape. `ktlintCheck`/`testDebugUnitTest`/
  `assembleDebug` re-verified — BUILD SUCCESSFUL; habit detail contribution grid manually
  re-verified live on the emulator, renders identically to before)

## Additional notes (non-blocking, no action needed)

- Both out-of-band commits (`f0bb263` onConflict fix, `7aa35cb` NavDisplay crash guard) verified
  correct and present exactly as documented in memory.
- `LocalJournalDataSourceImpl`/`LocalHabitDataSourceImpl`'s `applyRemoteSnapshot`/`purgeDeletedBefore`
  aren't individually wrapped in `safeDbCall`, but the outer `remoteCall { }` in
  `HabitRepositoryImpl`/`JournalRepositoryImpl.syncWithRemote()` is the real safety net — failures
  still surface as `Result.failure`, never crash. Not obvious from reading the data-source classes
  in isolation; worth keeping in mind if this file is touched again.
- `androidx-work-runtime` (not `-ktx`) confirmed sufficient for `CoroutineWorker` — the ktx
  coroutine APIs are merged into the base artifact as of 2.11.2, per the plan's own "Critical
  Implementation Details" note.
