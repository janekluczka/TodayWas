<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Sync & Deletion Critical-Path Coverage

- **Plan**: context/changes/testing-sync-deletion-critical-path/plan.md
- **Scope**: Full plan (3 of 3 phases)
- **Date**: 2026-09-12
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Plan-drift summary

All 8 planned changes verified MATCH against the plan's Intent/Contract:

1. `RepositoryModule.kt` — `@Singleton` added to `bindJournalRepository`/`bindHabitRepository`. MATCH.
2. `RepositoryModuleScopeTest.kt` (new) — reflection-based scope assertion, both methods. MATCH.
3. `AccountViewModel.kt` `onSyncConfirmClicked()` — conditions on result, reuses existing `AuthException`-cast idiom on failure. MATCH.
4. `OnboardingAccountSetupViewModel.kt` `onSyncConfirmClicked()` — mirrored fix. MATCH.
5. `AccountViewModelTest.kt` — failure-path test (ShowError, no markLocalDataSynced, stays on DATA_SYNC_REVIEW). MATCH.
6. `OnboardingAccountSetupViewModelTest.kt` — mirrored test. MATCH.
7. `SyncMergeIntegrationTest.kt` (new) — real DAO → real mapper → real merge, all 3 entity types. MATCH.
8. `test-plan.md` §6.3/§6.5 — cookbook + per-rollout-phase notes updated accurately. MATCH.

"What We're NOT Doing" respect-check — all 5 exclusions CONFIRMED RESPECTED via direct `git diff` inspection: no Hilt test infra added, `proceedAfterAuthSuccess`'s fire-and-forget branch untouched, `OnboardingAccountSetupViewModel.finish()` untouched, no `SyncMergeTest.kt` additions, `SyncLocalDataUseCase` not scoped.

## Safety/pattern summary

- `@Singleton` scoping is safe: the only mutable field in either repository is the `Mutex` itself — exactly what needed sharing for the fix to work. No other shared mutable state introduced.
- `isSyncing` resets unconditionally in both ViewModels before branching on success/failure — no stuck-loading path. The `if (isSyncing) return` guard, combined with `Dispatchers.Main.immediate`, closes the double-tap window structurally (same idiom as existing sign-in/sign-up/sign-out guards).
- Diffs are minimal in both source commits — exactly the stated methods changed, no unrelated refactoring or import churn.
- New tests follow existing naming (`` `should [outcome] when [scenario]` ``) and AAA-comment conventions exactly.

## Success criteria

- Automated: `testDebugUnitTest`, `ktlintCheck`, `assembleDebug` all re-verified passing together in one run.
- Manual: Phase 1's (1.4) was genuinely driven on a physical emulator session (write → schedule sync → rapid force-stop/relaunch ×4, logcat checked for crash/ANR — none found). Phase 2's (2.4) is transparently annotated as covered by the automated failure-path tests instead of a live repro, by explicit user choice (would have required signing out of a real account). Phase 3 correctly has no manual item (pure test addition, no behavior change). No rubber-stamping detected.

## Findings

### F1 — First reflection-based test in the repo

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/test/java/pl/luczka/todaywas/di/RepositoryModuleScopeTest.kt
- **Detail**: Uses `java.lang.reflect` to assert `@Singleton` presence on `RepositoryModule`'s `@Binds` methods — no prior test in this repo targets a Hilt `@Module` directly or uses reflection. Not a violation of any existing convention (none existed for this case); a deliberate, minimal choice over standing up Hilt test infrastructure from scratch.
- **Fix**: None needed — noted for future readers as the first example of this pattern.
- **Decision**: SKIPPED

### F2 — DB not closed in a try/finally in the new integration test

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/test/java/pl/luczka/todaywas/data/util/SyncMergeIntegrationTest.kt:52,81,105
- **Detail**: `db.close()` runs before the Assert block, not in try/finally, so a DAO exception before that line would leak the Robolectric DB. This exactly mirrors the existing convention in `HabitCheckInDaoTest.kt`/`JournalEntryDaoTest.kt` — inherited, not introduced by this change. A repo-wide gap, not specific to this file.
- **Fix**: None needed for this change — would be a separate, repo-wide cleanup (wrap all Room-test `db.close()` calls in try/finally) if ever prioritized.
- **Decision**: SKIPPED
