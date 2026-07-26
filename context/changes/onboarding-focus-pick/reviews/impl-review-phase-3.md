<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Onboarding Focus Pick Implementation Plan

- **Plan**: context/changes/onboarding-focus-pick/plan.md
- **Scope**: Phase 3 of 4
- **Date**: 2026-07-26
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Notes:
- Two deliberate, pre-approved deviations from Phase 3's literal text were verified as executed correctly, not flagged as drift: (1) `androidx-hilt-lifecycle-viewmodel-compose` added to `gradle/libs.versions.toml`/`app/build.gradle.kts` to backfill a Phase 1 gap; (2) a full MVI rework (sealed `MainIntent` + `onIntent()`, sealed `MainUiEvent` + `events` flow, one type per file) requested by the user mid-phase and now recorded in `context/foundation/lessons.md`.
- All 17 `MainViewModelTest` cases genuinely exercise their target behavior (state assertions, not smoke tests). `./gradlew.bat testDebugUnitTest ktlintCheck` both green.

## Findings

### F1 — onSkipOnboarding discards the write-failure Result

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:147-156
- **Detail**: `onSkipOnboarding()`'s null-focus branch calls `skipOnboarding()` (returns `Result<Unit>`) and unconditionally proceeds to `_uiState.update { it.copy(currentFocus = Focus.BOTH, showOnboardingDialog = false) }` regardless of success/failure. Every other write path in this file (`onConfirmSelection`) branches on `result.isSuccess`. If the underlying repository write fails (even after its retry-once), the dialog closes and the UI reports focus "Both" as if it persisted, but the DB still has `onboardingCompleted = false` — the dialog will reappear on next launch with no indication anything went wrong in between.
- **Fix A ⭐ Recommended**: Only update `currentFocus`/close the dialog on success; on failure, leave the dialog open and surface it the same way `onConfirmSelection` does (`saveError = true`), even though Phase 4's Skip button has no dedicated retry affordance yet — this can reuse the existing Focus-pick "Try again" UI since Skip-when-null only ever fires from `WELCOME`/`FOCUS_PICK`.
  - Strength: Matches the existing `onConfirmSelection` success/failure pattern exactly — no new UI concept needed.
  - Tradeoff: Skip stops being "always instant" — a failed skip now requires the user to notice `saveError` and retry, which the plan's Skip UX didn't originally design for.
  - Confidence: MED — correct given the codebase's existing error-handling convention, but the plan never designed a Skip-failure UI path, so Phase 4 will need a small addition.
  - Blind spot: Haven't seen Phase 4's dialog code (not yet built) to know how naturally this slots in.
- **Fix B**: Keep Skip "always closes immediately" for UX simplicity, but only set `currentFocus = Focus.BOTH` when `result.isSuccess` — on failure, leave `currentFocus` as-is (still `null`) and still close the dialog, accepting that a failed skip silently reverts to showing onboarding again next launch (already-existing retry-once-then-fail semantics in the repository are the safety net).
  - Strength: No new UI/error-state plumbing needed; smallest possible change.
  - Tradeoff: A failed skip is invisible to the user in the moment — they only discover it if the app is relaunched and onboarding reappears.
  - Confidence: MED — matches "Skip is instant" intent from the plan's Critical Implementation Details, but silently drops a write failure with zero feedback.
  - Blind spot: Whether silent failure here is acceptable is a product call, not a purely technical one.
- **Decision**: FIXED (Fix A)

### F2 — onboardingStateInitialized flag mutated inside StateFlow.update's replayable lambda

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:47-65
- **Detail**: `onboardingStateInitialized` (a plain `var`) is read and mutated *inside* the `_uiState.update { }` lambda (line 52). `MutableStateFlow.update`'s CAS-retry loop is allowed to re-invoke that lambda on contention; if a retry happens after the flag already flipped to `true`, the retry falls into the `else` branch and the one-time initialization is silently skipped on that emission. Not reachable today under single-threaded `Dispatchers.Main.immediate`, but it's a latent trap if that confinement assumption ever changes, and it wouldn't be caught by the current `UnconfinedTestDispatcher`-based tests either.
- **Fix**: Compute `val isFirstEmission = !onboardingStateInitialized` and flip `onboardingStateInitialized = true` *before* calling `.update`, so the lambda body only reads `isFirstEmission` and has no side effect that could be replayed.
- **Decision**: FIXED

### F3 — uiState exposed via type-narrowing instead of asStateFlow()

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:38
- **Detail**: `val uiState: StateFlow<MainUiState> = _uiState` only narrows the compile-time type; a caller that casts back to `MutableStateFlow` can still mutate it directly, bypassing the ViewModel.
- **Fix**: `_uiState.asStateFlow()` for a real read-only view (needs `import kotlinx.coroutines.flow.asStateFlow`).
- **Decision**: FIXED

### F4 — No re-entrancy guard on onConfirmSelection/onRetrySave while isSaving

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:90-116, 169-171
- **Detail**: Nothing stops `onConfirmSelection()`/`onRetrySave()` from being invoked again while a prior call is still in flight (`isSaving == true`), e.g. a double-tap before the UI disables the button. Currently harmless since `saveFocus` is an idempotent upsert, but there's no defense-in-depth guard.
- **Fix**: Add `if (_uiState.value.isSaving) return` at the top of `onConfirmSelection()`.
- **Decision**: FIXED
