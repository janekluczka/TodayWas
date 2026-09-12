<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Cascade & Account-Boundary Regression Locks

- **Plan**: context/changes/testing-cascade-account-boundary-locks/plan.md
- **Scope**: Full plan (3 of 3 phases)
- **Date**: 2026-09-12
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Plan-drift summary

All 5 planned changes verified MATCH against the plan's Intent/Contract, functionally:

1. `FakeAuthRepository.kt` — `signOutClearsSessionAnyway` flag added, gated correctly, default `false` preserves existing behavior. MATCH.
2. `SignOutUseCaseTest.kt` — new test for the decoupled scenario. MATCH.
3. `LocalHabitDataSourceIntegrationTest.kt` (new) — real Room DB, recording DAO wrappers via delegation, order+success and mid-cascade-failure (retry-aware) tests. MATCH.
4. `FakeRemoteHabitDataSource.kt`/`FakeRemoteHabitCheckInDataSource.kt` — additive `callOrderLog` param. MATCH (minor literal-text drift, see F3).
5. `HabitRepositoryImplTest.kt` — new GC purge-order test. MATCH (minor literal-text drift, see F2).

"What We're NOT Doing" respect-check — all 4 exclusions CONFIRMED RESPECTED: no offline-sign-out test added, `FakeAuthRepository`'s API not generally refactored, `FakeTransactionRunner` not extended, zero production files (`app/src/main`) touched across all three commits.

## Safety/pattern summary

- `RecordingHabitDao`/`RecordingHabitCheckInDao`'s `by real` delegation is a sound pattern — doesn't interfere with Room's `@Transaction`-wrapped default methods or `Flow`-returning methods.
- The retry-count math (`safeDbCall`'s one automatic retry → 4-element call-order log for the failure case) was verified correct by code inspection and by the tests actually passing.
- Both `FakeAuthRepository` and the two remote fakes' extensions are confirmed additive/backward-compatible via diff inspection.
- Naming (`` `should [outcome] when [scenario]` ``) and AAA-comment conventions followed exactly; `LocalHabitDataSourceIntegrationTest.kt` mirrors `SyncMergeIntegrationTest.kt`'s Robolectric/Room builder pattern exactly. ktlint clean.

## Success criteria

- Automated: `testDebugUnitTest`, `ktlintCheck`, `assembleDebug` all re-verified passing together in one run (all `UP-TO-DATE`).
- Manual: none required anywhere in this plan (test-only, no behavior change) — correctly reflected in Progress.

## Findings

### F1 — DB not closed in try/finally in the new integration test

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/test/java/pl/luczka/todaywas/data/local/api/LocalHabitDataSourceIntegrationTest.kt:104,151
- **Detail**: `db.close()` runs before the assert block, not in try/finally. The two `.single{}` lookups right before `close()` can themselves throw if the row set doesn't match expectations, which would skip `close()` and leak the Robolectric SQLite connection for that test run. This exactly mirrors the existing pattern in `SyncMergeIntegrationTest.kt` (already flagged and skipped as F2 in the prior rollout phase's implementation review) — inherited, not newly introduced by this change.
- **Fix**: None needed for this change — same repo-wide, pre-existing gap already triaged once; would be a single cleanup pass across all Room-integration tests if ever prioritized.
- **Decision**: PENDING

### F2 — GC purge-order test only seeds local tombstoned records

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/src/test/java/pl/luczka/todaywas/data/repository/HabitRepositoryImplTest.kt:514-565
- **Detail**: The plan said to construct the tombstoned habit and check-in "locally and remotely"; the actual test only seeds them in the local fake. Functionally inconsequential — production `syncWithRemote()` calls both remote purges unconditionally once signed in, regardless of whether matching remote DTOs exist.
- **Fix**: None needed — the test already proves the intended behavior correctly.
- **Decision**: PENDING

### F3 — Call-order log append isn't literally the first statement

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/src/test/java/pl/luczka/todaywas/data/remote/api/FakeRemoteHabitDataSource.kt:31, FakeRemoteHabitCheckInDataSource.kt:31
- **Detail**: `purgeCallCount++` executes one line before `callOrderLog?.add(...)`, not literally "before doing anything else" as the plan's contract text specified. Inert bookkeeping — doesn't affect ordering semantics, failure paths, or any test assertion.
- **Fix**: None needed.
- **Decision**: PENDING
