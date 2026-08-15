<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: UX Refinements Implementation Plan

- **Plan**: context/changes/ux-refinements/plan.md
- **Scope**: Phase 1 of 6 (full plan review — all 6 phases)
- **Date**: 2026-08-11
- **Verdict**: REJECTED → RESOLVED 2026-08-15 (all findings fixed, see Decisions below)
- **Findings**: 1 critical, 1 warning, 1 observation — all fixed

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS (was FAIL — see F1 resolution) |
| Architecture | PASS |
| Pattern Consistency | PASS (was WARNING — see F2 resolution) |
| Success Criteria | PASS |

## Findings

### F1 — Sign-out via the new account sheet leaves stale local data and a stale sync flag

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:169-182 (onSignOutConfirmed)
- **Detail**: Before this change, sign-out was only reachable via `PreferencesScreen → AccountScreen`, so `AccountViewModel` was always alive to catch it. `AccountViewModel` clears synced local data on sign-out via an auth-state-transition observer (`AccountViewModel.kt:69-78`): `if (previousAuthState is AuthState.SignedIn && state is AuthState.SignedOut) { clearSyncedLocalData() }`. `ClearSyncedLocalDataUseCase` clears local journal entries, local habits, and resets the `hasSyncedLocalData` flag (`ClearSyncedLocalDataUseCase.kt`).

  The new top-bar bottom sheet gives users a second, primary sign-out entry point (`MainViewModel.onSignOutConfirmed()` calling `SignOutUseCase` directly) that never instantiates `AccountViewModel` and has no equivalent observer. Two consequences:
  1. **Data leakage on a shared device**: after signing out via the sheet, the previous account's journal entries and habits remain visible on the Main hub to whoever uses the device next while signed out.
  2. **Broken US-04 acceptance criterion**: `hasSyncedLocalData` stays stale `true`. If the user then signs into a *different* account, `proceedAfterAuthSuccess` (in both `AccountViewModel` and `OnboardingViewModel`) sees the stale flag and silently background-syncs instead of showing the one-time data-review screen US-04 requires.

  Confirmed via code: `MainViewModel`'s constructor no longer injects `ObserveOnboardingStateUseCase` at all (removed as part of the Focus cleanup), so nothing in this ViewModel touches local-data clearing or the sync flag.

- **Fix A ⭐ Recommended**: Add the same `previousAuthState`-transition observer to `MainViewModel`, calling `ClearSyncedLocalDataUseCase` on a SignedIn→SignedOut transition, mirroring `AccountViewModel.kt:67-80` exactly.
  - Strength: Identical to an already-shipped, already-tested pattern in this codebase — minimal risk, fast to verify, no new abstractions.
  - Tradeoff: Duplicates the "clear synced data on sign-out" logic across two ViewModels; a third future sign-out entry point could reintroduce this same gap.
  - Confidence: HIGH — this is a direct copy of existing, working code.
  - Blind spot: None significant.
- **Fix B**: Move the clear-on-sign-out side effect out of ViewModels entirely — e.g. into `AuthRepositoryImpl.signOut()` (or a repository-level observer of its own session state) so every current and future sign-out call site gets it automatically, without needing to remember to wire an observer per screen.
  - Strength: Closes this entire class of bug permanently — impossible to add a third sign-out surface that forgets this.
  - Tradeoff: `AuthRepository` currently has no dependency on `JournalRepository`/`HabitRepository`/`OnboardingRepository`; wiring this in means either giving the data layer a new cross-repository dependency, or introducing a use case that composes `SignOutUseCase` + `ClearSyncedLocalDataUseCase` behind one call — per this project's lesson "a use case must depend on repositories directly, never inject one use case into another," that composite would need to depend on `AuthRepository` + the three repositories directly rather than wrapping the two existing use cases.
  - Confidence: MEDIUM — architecturally cleaner, but a bigger change to design and test correctly in this review pass.
  - Blind spot: Haven't checked whether `AccountViewModel`'s own observer would need to be removed/deduplicated once this is centralized, or left as (now-redundant) belt-and-suspenders.
- **Decision**: FIXED via Fix B — `SignOutUseCase` now depends on `AuthRepository` +
  `JournalRepository` + `HabitRepository` + `OnboardingRepository` directly (per this project's "a
  use case must depend on repositories directly, never inject one use case into another" lesson),
  and clears synced local data + resets `hasSyncedLocalData` itself, gated on the actual
  SignedIn→SignedOut auth-state transition around the `signOut()` call (checked before and after,
  not on `signOut()`'s own `Result`) — an exact port of `AccountViewModel`'s former
  `previousAuthState`-transition observer, now living in the use case so every sign-out call site
  gets it automatically instead of needing its own observer wiring. `ClearSyncedLocalDataUseCase`
  is now fully subsumed and was deleted (along with its dedicated test) since nothing else called
  it. `AccountViewModel`'s observer was removed as the now-redundant belt-and-suspenders case
  flagged as a blind spot in Fix B's original writeup. New `SignOutUseCaseTest.kt` covers the
  transition gating directly; `MainViewModelTest.kt` gained matching sign-out clear/no-clear
  coverage that was previously only exercised via `AccountViewModelTest.kt`.

### F2 — Account sheet has no loading indicator for the initial auth-state fetch

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt (AccountBottomSheet, `AuthStateUi.Loading -> Unit` branch)
- **Detail**: `AccountScreenContent` renders `DsLoadingIndicator()` for `AuthStateUi.Loading` (`AccountScreen.kt:114`). The new bottom sheet renders nothing for the same state — just empty padding. `MainUiState.authState` starts as `Loading`, so a user tapping the account icon before the first `observeAuthState()` emission sees a blank sheet with no signal anything is happening.
- **Fix**: Add a `DsLoadingIndicator()` branch to `AccountBottomSheet`'s `when (uiState.authState)`, matching `AccountScreen`'s handling.
- **Decision**: FIXED — added the `DsLoadingIndicator()` branch, plus a new `AuthStateUi.Loading` +
  `isAccountSheetVisible = true` case in `MainScreenPreviewStateProvider` per this project's
  every-screen-ships-previews convention.

### F3 — No error feedback on a failed sign-out from the sheet

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:169-182, MainScreen.kt
- **Detail**: This matches the plan's explicit Contract ("no dedicated error surface needed beyond that — sign-out failure just leaves the sheet open to retry"), so it's not a plan deviation. But it's a real UX gap versus `AccountScreen`'s established pattern: `AccountViewModel` maps `SignOutUseCase` failures to a `ShowError` event with a snackbar; `MainUiEvent` has no equivalent case and `MainScreen` has no `SnackbarHost`, so a failed sign-out (e.g. network error) gives the user zero feedback beyond the spinner stopping. State itself is correctly re-triable — verified by the passing `SignOutConfirmed fails` test (isSigningOut resets, dialog/sheet stay open).
- **Fix**: Not required by the plan. If product wants parity with `AccountScreen`'s error surfacing, add a `ShowError` `MainUiEvent` + `SnackbarHost` to `MainScreen`.
- **Decision**: FIXED (opted for parity) — added `MainUiEvent.ShowError(error: AuthErrorUiState)`,
  emitted from `onSignOutConfirmed()` on failure using the same `AuthException`/`AuthError.Unknown`
  mapping `AccountViewModel` uses, plus a `SnackbarHost` (`DsSnackbarHost`) wired into
  `MainScreenContent` and the shared `AuthErrorUiState.message()` mapper, mirroring
  `AccountScreen.kt`'s pattern exactly.

## Automated Success Criteria

- `./gradlew.bat ktlintCheck` — PASS
- `./gradlew.bat testDebugUnitTest` — PASS (261/262; the sole failure,
  `TodayWasDatabaseTest > should survive recreating the database instance from the same file when
  onboarding was completed`, reproduces identically on a clean stash of this change with none of
  these fixes applied — a pre-existing Robolectric/SQLite environment issue, unrelated to this
  review's findings or their fixes)
