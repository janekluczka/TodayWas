# UX Refinements Implementation Plan

## Overview

Replace the bottom-navigation tab bar with a single hub-style main screen, remove the onboarding
"app focus" concept end-to-end (FR-001), and move account access from a Preferences tab to a
top-bar icon button that opens a bottom sheet (account card + sign-out, or a sign-in/sign-up
prompt).

## Current State Analysis

- `MainShellScreen.kt` hosts a 4-tab bottom nav (Home / Journal-placeholder / Habits-placeholder /
  Preferences) driven by plain `rememberSaveable` Compose state — not a nested Nav3 setup, contrary
  to `lessons.md`'s general guidance for bottom nav.
- `MainScreen` already renders both a Journal section and a Habit section, but only when
  `uiState.focus == FocusUiState.BOTH`; `JOURNAL`/`HABIT` render one section each, and `null` shows
  an empty-state placeholder.
- `Focus` is a full-stack concept: a non-null Room column (`UserPreferencesEntity.focus`) →
  `OnboardingRepository`/`OnboardingRepositoryImpl` → `SelectFocusUseCase`/`SkipOnboardingUseCase`
  → `OnboardingViewModel`/`MainViewModel` → ~7 test files.
- `OnboardingRepositoryImpl.saveFocus()` is currently the **only** place that flips
  `onboardingCompleted = true` — removing the focus-pick step requires moving that trigger
  elsewhere.
- Account/auth is already fully built: `AccountScreen` (SIGN_IN/SIGN_UP/DATA_SYNC_REVIEW/SUCCESS
  steps) + `AccountViewModel`, reached today via Preferences tab → `AccountCard` → `AccountKey`
  pushed onto the root back stack.
- `PreferencesScreen` contains nothing but the `AccountCard` (confirmed by reading the file).
- `:core:designsystem` already has `DsTopBar` (with an `actions` slot), `DsModalBottomSheet`, and
  `DsAlertDialog` — no new design-system components are needed.
- `DatabaseModule.kt` already configures `.fallbackToDestructiveMigration(dropAllTables = true)` —
  dropping the `focus` column only requires a Room DB version bump, no explicit `Migration`.

## Desired End State

- `MainKey`'s destination is a single hub screen (today's `MainScreen`, now reached directly, no
  `MainShellScreen` wrapper) that always renders both the Journal section and the Habit section,
  with a top bar showing the app title and an account icon action.
- Tapping the account icon opens a bottom sheet: signed-in shows the account email + a "Sign out"
  button gated by a confirmation dialog; signed-out shows a prompt card that closes the sheet and
  pushes the existing full `AccountScreen`.
- No bottom navigation bar exists anywhere in the app. `MainShellScreen.kt` and `ui/preferences/`
  are deleted.
- Onboarding is `WELCOME → ACCOUNT_INFO → ALL_SET` (no focus-pick step); reaching `ALL_SET` (or
  tapping Skip from any step) is what marks onboarding completed — no dependency on any focus value.
- `Focus`, `FocusUiState`, `FocusMapper`, and the Room `focus` column no longer exist anywhere in
  the codebase.
- `prd.md`'s FR-001 and any focus-dependent main-screen description are amended to match.

**Verification**: `./gradlew.bat testDebugUnitTest`, `./gradlew.bat ktlintCheck`, and
`./gradlew.bat assembleDebug` all pass; a manual run confirms the hub always shows both sections,
the account sheet works both signed-in and signed-out, sign-out requires confirmation, the
onboarding flow completes without a focus step, and a fresh install (post destructive migration)
works end to end.

### Key Discoveries

- `DatabaseModule.kt:22` — destructive migration fallback already configured; only
  `TodayWasDatabase.kt:8`'s `version = 4` needs to become `5`.
- `OnboardingRepositoryImpl.kt:26-35` — `saveFocus()` both persists focus AND sets
  `onboardingCompleted = true`; its replacement (`completeOnboarding()`) only needs to do the
  latter.
- `MainViewModel.kt:94-106` — `observeOnboardingState()` is combined **solely** to read `.focus`;
  once focus is gone, `MainViewModel` no longer needs `ObserveOnboardingStateUseCase` at all.
- `MainViewModel.kt:177-189` (`FocusUiState?.toFabActions()`) already contains the exact "both"
  logic (journal action gated on addable slots being non-empty, `CREATE_HABIT` always available,
  `LOG_HABIT_CHECK_INS` gated on habits existing) — removing the `Focus` receiver turns this into a
  plain function, not new logic to design.
- `AccountViewModel.kt:274-287` (`onSignOutClicked`) is the exact sign-out pattern
  (`SignOutUseCase`, `isSigningOut` loading flag, `AuthException` → error mapping) to mirror inside
  `MainViewModel`.
- `FakeOnboardingRepository` exists in two places: a shared fake at
  `app/src/test/java/pl/luczka/todaywas/data/repository/FakeOnboardingRepository.kt` (used by
  `OnboardingViewModelTest`, `RootViewModelTest`, `AccountViewModelTest`,
  `MarkLocalDataSyncedUseCaseTest`, `ClearSyncedLocalDataUseCaseTest`) and a private inline one
  inside `MainViewModelTest.kt` — both override `saveFocus` and need updating.

## What We're NOT Doing

- Not building a replacement settings/preferences screen — the concept is deleted, not relocated.
- Not adding any bottom navigation (tabs, rail) — explicitly deferred.
- Not embedding sign-in/sign-up forms inline in the bottom sheet — the sheet only prompts; the
  existing full `AccountScreen` (with its data-sync review step) handles the actual flow.
- Not changing `AccountScreen`/`AccountViewModel`'s multi-step sign-up flow itself.
- Not adding avatar/initials or any per-sign-in-state top-bar icon visuals — one generic account
  icon regardless of state.
- Not writing an explicit Room `Migration` for the dropped column — relying on the already
  configured destructive fallback (accepted as fine pre-launch, per `tech-stack.md`).
- Not redesigning FAB behavior beyond making today's BOTH-focus behavior unconditional.

## Implementation Approach

Work bottom-up: remove `Focus` from the data layer first (Phase 1), then from onboarding
(Phase 2), then collapse `MainScreen`'s focus branching (Phase 3) before layering the new
account-sheet UI onto the now-simpler `MainScreen` (Phase 4), then delete the now-redundant
navigation shell and Preferences screen (Phase 5), and close with the PRD amendment (Phase 6). Each
phase updates its own affected tests in the same phase rather than deferring coverage.

## Critical Implementation Details

- **State sequencing (onboarding completion)**: today, `onConfirmFocus()` in `OnboardingViewModel`
  persists before advancing the step, with `isSaving`/`saveError` driving the Next button's
  loading/retry state on `FOCUS_PICK`. That whole loading/retry contract needs to move to the
  `ACCOUNT_INFO → ALL_SET` transition — persist-then-advance, with the Next button on `ACCOUNT_INFO`
  showing the loading/retry state instead. Getting the order backwards (advance-then-persist) would
  let a user reach `ALL_SET` while `onboardingCompleted` is still false, racing `RootViewModel`'s
  routing decision on process death.
- **Migration data loss**: bumping `TodayWasDatabase`'s version with
  `fallbackToDestructiveMigration(dropAllTables = true)` wipes **all** local tables (journal
  entries, habits, check-ins, user preferences) on next launch, not just `user_preferences`. This is
  accepted pre-launch (see `tech-stack.md`), but is a real, if intentional, side effect worth knowing
  before running the app locally after this change lands.

## Phase 1: Data layer & onboarding domain

### Overview

Remove `Focus` from the Room schema, `OnboardingRepository`, and the two use cases that touch it,
replacing the focus-driven completion write with a plain `completeOnboarding()`.

### Changes Required:

#### 1. `Focus` domain model

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/Focus.kt`

**Intent**: Delete — no longer a domain concept.

**Contract**: File removed; all imports of `pl.luczka.todaywas.domain.model.Focus` across the
codebase are cleaned up in the phases that touch each caller.

#### 2. Room schema

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/UserPreferencesEntity.kt`

**Intent**: Drop the `focus` column — it's no longer part of user preferences.

**Contract**: `UserPreferencesEntity(id: Int = 0, onboardingCompleted: Boolean, hasSyncedLocalData: Boolean = false)`.

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/TodayWasDatabase.kt`

**Intent**: Bump the schema version so Room's existing destructive-fallback recreates the table
without the dropped column.

**Contract**: `@Database(..., version = 5, ...)` (was `4`). No `Migration` class needed —
`DatabaseModule.kt`'s `fallbackToDestructiveMigration(dropAllTables = true)` already handles it.

#### 3. Onboarding domain state

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/OnboardingState.kt`

**Intent**: Drop the `focus` field — onboarding state is now just completion + sync status.

**Contract**: `OnboardingState(completed: Boolean, hasSyncedLocalData: Boolean)`.

#### 4. Onboarding repository

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/OnboardingRepository.kt`

**Intent**: Replace the focus-saving method with a plain completion method — this is now the sole
completion trigger, decoupled from any focus value.

**Contract**: Remove `suspend fun saveFocus(focus: Focus): Result<Unit>`; add
`suspend fun completeOnboarding(): Result<Unit>`.

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/OnboardingRepositoryImpl.kt`

**Intent**: Implement `completeOnboarding()` using the same upsert-with-retry contract `saveFocus()`
used, but only setting `onboardingCompleted = true` (no focus field left to set). Update
`observeState()`'s entity→domain mapping to match the new `OnboardingState` shape.

**Contract**: `completeOnboarding()` upserts `UserPreferencesEntity` with `onboardingCompleted =
true`, reusing the existing `upsertWithRetry` helper unchanged.

#### 5. Use cases

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/SelectFocusUseCase.kt`

**Intent**: Delete — no longer needed.

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/SkipOnboardingUseCase.kt`

**Intent**: Rename to `CompleteOnboardingUseCase.kt` — this use case's real job was always "mark
onboarding done"; it now also becomes the use case for the `ACCOUNT_INFO → ALL_SET` transition in
Phase 2, not just the Skip button.

**Contract**: `class CompleteOnboardingUseCase @Inject constructor(private val repository:
OnboardingRepository) { suspend operator fun invoke(): Result<Unit> = repository.completeOnboarding() }`.

#### 6. Tests

**File**: `app/src/test/java/pl/luczka/todaywas/domain/usecase/SelectFocusUseCaseTest.kt`

**Intent**: Delete along with the use case.

**File**: `app/src/test/java/pl/luczka/todaywas/data/repository/OnboardingRepositoryImplTest.kt`

**Intent**: Update test cases from exercising `saveFocus()` to exercising `completeOnboarding()`
(same upsert-with-retry behavior, just without a focus argument/assertion).

**File**: `app/src/test/java/pl/luczka/todaywas/data/repository/FakeOnboardingRepository.kt`

**Intent**: Replace the `saveFocus` override with a `completeOnboarding` override (sets
`onboardingCompleted = true` on the fake's backing state, same as today's fake behavior minus
focus).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Fresh install (or clear app data) launches without crashing past the destructive migration.

---

## Phase 2: Onboarding UI — drop the focus-pick step

### Overview

Remove `FOCUS_PICK` from the onboarding step machine and move the completion trigger to the
`ACCOUNT_INFO → ALL_SET` transition (and Skip).

### Changes Required:

#### 1. Onboarding step/state/intent

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingStep.kt`

**Intent**: Drop the focus-pick step from the flow.

**Contract**: `enum class OnboardingStep { WELCOME, ACCOUNT_INFO, ALL_SET }`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingUiState.kt`

**Intent**: Drop the two focus-selection fields — nothing about focus is tracked anymore.

**Contract**: Remove `selectedFocus: FocusUiState?` and `confirmedFocus: FocusUiState?`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingIntent.kt`

**Intent**: Drop the focus-selection intent.

**Contract**: Remove `FocusOptionSelected`.

#### 2. Onboarding ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingViewModel.kt`

**Intent**: Replace `SelectFocusUseCase` with `CompleteOnboardingUseCase`, wire it into the
`ACCOUNT_INFO → ALL_SET` transition (mirroring the persist-then-advance, `isSaving`/`saveError`
retry contract `onConfirmFocus()` used for `FOCUS_PICK`), and simplify Skip/back-navigation now
that there's no focus state to branch on.

**Contract**:
- Constructor: `selectFocus: SelectFocusUseCase` → `completeOnboarding: CompleteOnboardingUseCase`.
- `onNextClicked()`: `WELCOME` advances straight to `ACCOUNT_INFO`; `ACCOUNT_INFO` calls a new
  `onCompleteAccountStep()` (replacing `onConfirmFocus()`) that sets `isSaving`, calls
  `completeOnboarding()`, and on success advances to `ALL_SET` with the same `allSetReason`
  computation already in place, or sets `saveError` on failure.
- `onStepBack()`: drop the `FOCUS_PICK` case; `onAccountInfoStepBack()`'s `CHOICE` branch now goes
  back to `step = OnboardingStep.WELCOME` instead of restoring a focus selection.
- `onSkipClicked()`: drop the `confirmedFocus != null` early-finish branch — Skip always calls
  `completeOnboarding()` (same `isSaving`/`saveError` handling as today) then finishes on success.

#### 3. Onboarding screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingScreen.kt`

**Intent**: Remove the focus-pick page and retarget the Next button's loading/retry copy and
enabled-state logic from `FOCUS_PICK` to `ACCOUNT_INFO`.

**Contract**: Remove `FocusPickStepBody`, its `when` branch in the pager content, and the
`FocusUiState.label()` extension; `nextButtonLabel`/`nextButtonEnabled` move their loading/error/
retry cases from `OnboardingStep.FOCUS_PICK` to `OnboardingStep.ACCOUNT_INFO` (only when no sub-step
form is active — i.e. the `CHOICE` sub-step, since `SIGN_IN`/`SIGN_UP` sub-steps have their own
submit buttons); update the preview provider to drop all `FOCUS_PICK`-related preview states.

#### 4. UI-layer Focus types

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/FocusUiState.kt`

**Intent**: Delete.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/FocusMapper.kt`

**Intent**: Delete.

#### 5. Root routing comment

**File**: `app/src/main/java/pl/luczka/todaywas/ui/RootViewModel.kt`

**Intent**: Update the stale comment (currently references `saveFocus()`) to describe the new
completion trigger. No logic changes — `RootViewModel` already routes purely off
`state.completed`.

#### 6. Tests

**File**: `app/src/test/java/pl/luczka/todaywas/ui/onboarding/OnboardingViewModelTest.kt`

**Intent**: Remove focus-selection/confirm test cases; update Skip/back-navigation/finish test
cases for the new step machine and `CompleteOnboardingUseCase`; add a case covering the
`ACCOUNT_INFO → ALL_SET` persist-then-advance/retry-on-failure behavior (replacing the equivalent
`FOCUS_PICK` coverage).

**File**: `app/src/test/java/pl/luczka/todaywas/ui/RootViewModelTest.kt`

**Intent**: Drop the `Focus` import and `focus =` argument from `OnboardingState(...)`
construction; update the comment referencing focus-flips-completed.

**File**: `app/src/main/res/values/strings.xml`

**Intent**: Remove `onboarding_focus_pick_title/error/retry/confirm` and `focus_journal/habit/both`;
add equivalent retry/error copy for the `ACCOUNT_INFO` step's Next-button contract described above.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Fresh onboarding run: Welcome → Account (choice/sign-in/sign-up/data-sync-review as applicable) →
  All set, with no focus-pick screen shown.
- Tapping Skip from any onboarding step finishes onboarding and lands on the main screen.
- Killing/reopening the app mid-`ACCOUNT_INFO` step and completing it still correctly flips
  `onboardingCompleted` before routing to Main on next launch.

---

## Phase 3: Main screen becomes the hub

### Overview

Remove `focus` from `MainViewModel`/`MainUiState` and always render both the Journal and Habit
sections with both sets of FAB actions.

### Changes Required:

#### 1. Main UI state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiState.kt`

**Intent**: Drop the focus field — the hub no longer varies by focus.

**Contract**: Remove `focus: FocusUiState?`.

#### 2. Main ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Drop the now-unused `ObserveOnboardingStateUseCase` dependency entirely (it was only
combined to read `.focus`), and turn `FocusUiState?.toFabActions()` into an unconditional function
using the same underlying gating logic.

**Contract**:
- Constructor: remove `observeOnboardingState: ObserveOnboardingStateUseCase`.
- `RawMainSources`/`CombinedMainState`: drop the `focus` field; the `init` block's `combine(...)`
  drops `observeOnboardingState()` as a source.
- `toFabActions(addableSlots, habits): List<FabActionUiState>` (no `Focus` receiver): always
  includes `ADD_JOURNAL_ENTRY` when `addableSlots.isNotEmpty()`, always includes `CREATE_HABIT`,
  includes `LOG_HABIT_CHECK_INS` when `habits.isNotEmpty()` — i.e. today's `BOTH` branch,
  unconditionally.

#### 3. Main screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Always render the Journal section and the Habit section stacked (today's `BOTH` layout)
— no more `when (uiState.focus)` branching or empty-state placeholder.

**Contract**: `MainScreenContent`'s body becomes the unconditional
`Column { JournalSection(weight 1f); HabitSection(weight 1f) }`; the `main_empty_state` branch and
string are removed. Update `MainScreenPreviewStateProvider` to drop `focus =` from every preview
state and consolidate the now-redundant focus-only preview variants (JOURNAL-only, HABIT-only) into
states that vary journal/habit content directly (empty hub, hub with journal entries, hub with
habits).

#### 4. Tests

**File**: `app/src/test/java/pl/luczka/todaywas/ui/main/MainViewModelTest.kt`

**Intent**: Remove the `focus`/`Focus` parameter from the `viewModel()` test helper and delete the
file's private inline `FakeOnboardingRepository` and `observeOnboardingState` wiring entirely (no
longer a `MainViewModel` dependency). Rewrite the focus-parameterized fabActions test cases
(JOURNAL-only, HABIT-only, BOTH) into direct addable-slots/habits-based cases matching the new
unconditional `toFabActions` contract.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Main screen (via the still-present Home tab) shows both Journal and Habit sections regardless of
  what was picked during onboarding (there is no longer anything to pick).
- FAB offers both "Add journal entry" and habit actions together.

---

## Phase 4: Account bottom sheet + top bar

### Overview

Add a top-bar account icon to `MainScreen` that opens a bottom sheet reflecting live auth state,
with a confirmation dialog before sign-out.

### Changes Required:

#### 1. Main UI state/intent/event

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiState.kt`

**Intent**: Add the account-sheet slice of state.

**Contract**: Add `authState: AuthStateUi`, `isAccountSheetVisible: Boolean`,
`isSignOutConfirmVisible: Boolean`, `isSigningOut: Boolean`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainIntent.kt`

**Intent**: Add intents for opening/closing the sheet, the sign-in/up prompt, and the sign-out
confirm flow.

**Contract**: Add `AccountIconClicked`, `AccountSheetDismissed`, `SignInSignUpPromptClicked`,
`SignOutClicked`, `SignOutConfirmed`, `SignOutCancelled`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiEvent.kt`

**Intent**: Add the one-shot navigation event for the sign-in/up prompt.

**Contract**: Add `NavigateToAccount`.

#### 2. Main ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Feed `observeAuthState()` (already injected for background sync) into `uiState` as a
live `StateFlow` slice, and add a sign-out flow mirroring `AccountViewModel.onSignOutClicked()`'s
`isSigningOut`/`SignOutUseCase`/`AuthException`-mapping pattern.

**Contract**: Inject `SignOutUseCase`. New `viewModelScope.launch { observeAuthState().collect { ... } }`
updates `uiState.authState` continuously (not the existing one-shot `first {}` used for the
sync-on-load check, which stays as-is). New `onIntent` branches:
`AccountIconClicked`/`AccountSheetDismissed` toggle `isAccountSheetVisible`;
`SignInSignUpPromptClicked` closes the sheet and emits `NavigateToAccount`;
`SignOutClicked`/`SignOutCancelled` toggle `isSignOutConfirmVisible`; `SignOutConfirmed` sets
`isSigningOut = true`, calls `SignOutUseCase`, and on success closes both the dialog and the sheet,
or on failure clears `isSigningOut` (no dedicated error surface needed beyond that — sign-out
failure just leaves the sheet open to retry).

#### 3. Main screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Add the account icon to the top bar's actions slot, the bottom sheet, and the sign-out
confirmation dialog; wire the new navigation event through a new screen-level callback parameter.

**Contract**: `MainScreen` gains an `onAccountClicked: () -> Unit` parameter, wired in its
`LaunchedEffect` `events.collect` alongside the existing `Navigate*` cases. `DsTopBar`'s `actions`
slot gets a `DsIconButton` (generic account icon, same regardless of sign-in state) firing
`MainIntent.AccountIconClicked`. A new `AccountBottomSheet` composable (built on
`DsModalBottomSheet`, shown when `uiState.isAccountSheetVisible`) renders signed-in content (email +
a `DsButtonWithLoading` "Sign out" driven by `isSigningOut`, firing `SignOutClicked`) or signed-out
content (prompt text + a button firing `SignInSignUpPromptClicked`) based on `uiState.authState`. A
`DsAlertDialog` shown when `uiState.isSignOutConfirmVisible` confirms/cancels via
`SignOutConfirmed`/`SignOutCancelled`. Extend `MainScreenPreviewStateProvider` with signed-in
sheet-open, signed-out sheet-open, and sign-out-confirm-dialog-visible states.

#### 4. Wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/mainshell/MainShellScreen.kt`

**Intent**: Thread the existing `onAccountClicked` parameter through to `MainScreen`'s `HOME` tab
(previously only `PreferencesScreen` received it).

**Contract**: `BottomNavTab.HOME -> MainScreen(..., onAccountClicked = onAccountClicked)`. (This
file is deleted wholesale in Phase 5 — this is a minimal bridging edit to keep the app buildable
and manually testable in this phase.)

#### 5. Tests

**File**: `app/src/test/java/pl/luczka/todaywas/ui/main/MainViewModelTest.kt`

**Intent**: Add coverage for the new intents.

**Contract**: Cases for: `AccountIconClicked` sets `isAccountSheetVisible = true`;
`AccountSheetDismissed` clears it; `SignInSignUpPromptClicked` emits `NavigateToAccount` and closes
the sheet; `SignOutClicked` sets `isSignOutConfirmVisible = true`; `SignOutCancelled` clears it
without signing out; `SignOutConfirmed` with a succeeding `SignOutUseCase` fake clears
`isSigningOut`/`isSignOutConfirmVisible`/`isAccountSheetVisible`; `SignOutConfirmed` with a failing
fake clears `isSigningOut` but leaves the sheet open.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Signed out: tapping the account icon shows a sign-in/up prompt; tapping it navigates to the full
  `AccountScreen`.
- Signed in: tapping the account icon shows the account email + Sign out; tapping Sign out shows a
  confirmation dialog; confirming signs out and the sheet reflects signed-out state without manually
  reopening it (live `StateFlow`, not a stale snapshot).
- Cancelling the sign-out confirmation leaves the user signed in with the sheet still open.

---

## Phase 5: Remove bottom nav & Preferences

### Overview

Delete the bottom-navigation shell and the now-redundant Preferences screen; `MainKey` renders
`MainScreen` directly.

### Changes Required:

#### 1. Bottom nav shell

**File**: `app/src/main/java/pl/luczka/todaywas/ui/mainshell/MainShellScreen.kt`

**Intent**: Delete the whole file (bottom nav bar, `BottomNavTab`, tab placeholders, and their
previews) — no bottom navigation remains.

#### 2. Preferences

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/preferences/PreferencesScreen.kt`,
`PreferencesViewModel.kt`, `PreferencesUiState.kt`, `PreferencesIntent.kt`, `PreferencesUiEvent.kt`

**Intent**: Delete the whole package — its only content (the account card) is now the top-bar
account icon on the hub.

#### 3. Root navigation

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Render `MainScreen` directly from the `MainKey` entry instead of `MainShellScreen`.

**Contract**: `entry<MainKey> { MainScreen(onAddEntryClicked = ..., onJournalEntryClicked = ...,
onCreateHabitClicked = ..., onLogCheckInsClicked = ..., onHabitClicked = ..., onAccountClicked = {
backStack.add(AccountKey) }) }`, replacing the `MainShellScreen(...)` call. Remove the now-unused
`import pl.luczka.todaywas.ui.mainshell.MainShellScreen`.

#### 4. Strings cleanup

**File**: `app/src/main/res/values/strings.xml`

**Intent**: Remove strings that no longer have any caller.

**Contract**: Remove `bottom_nav_home_label`, `bottom_nav_journal_label`, `bottom_nav_habits_label`,
`bottom_nav_preferences_label`, `main_journal_tab_placeholder`, `main_habit_tab_placeholder`,
`preferences_top_bar_title`, `preferences_account_section_title`,
`preferences_account_signed_out_subtitle`. Keep `preferences_sign_out_cta` and
`preferences_signed_in_no_email` — both are still used by `AccountScreen`/`OnboardingScreen`/the new
account sheet.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`
- A repo-wide search confirms no remaining references to `MainShellScreen`, `BottomNavTab`, or
  `pl.luczka.todaywas.ui.preferences` outside this deletion.

#### Manual Verification:

- Launching the app (post-onboarding) goes straight to the hub — no bottom nav bar is visible
  anywhere.
- All previously-reachable destinations (add journal entry, journal detail, create habit, log
  check-ins, habit detail, account) are still reachable from the hub.

---

## Phase 6: Foundation docs

### Overview

Bring `prd.md` in line with the shipped behavior: no onboarding focus pick, main screen always a
combined hub.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: Amend FR-001 (the onboarding focus-pick requirement) and any other passage describing
focus-gated main-screen behavior so the document matches what's actually built, per CLAUDE.md's
"foundation docs are edited in place" convention. A quick grep of `prd.md` for "focus" during
implementation confirms the full surface (FR-001 itself, and the Socrates counter-argument note
attached to it, are the known ones from this research pass).

**Contract**: FR-001 is removed or explicitly marked superseded with a short note on why (main
screen collapsing focus/journal/habit into one always-on hub removed the need for an upfront
choice); no other FR should need renumbering since FR-001 is additive-removal, not a dependency of
later FRs.

### Success Criteria:

#### Automated Verification:

- N/A (documentation-only phase).

#### Manual Verification:

- `prd.md` no longer describes an onboarding focus pick or a focus-gated main screen; a fresh reader
  of the PRD alone would correctly predict the app's current behavior.

---

## Testing Strategy

### Unit Tests:

- Repository/use-case level: `completeOnboarding()` replaces `saveFocus()` coverage in
  `OnboardingRepositoryImplTest`; `SelectFocusUseCaseTest` deleted.
- `OnboardingViewModelTest`: step-machine coverage updated for the 3-step flow; persist-then-advance
  and retry-on-failure coverage moves from `FOCUS_PICK` to `ACCOUNT_INFO`.
- `MainViewModelTest`: focus-parameterized fabActions cases rewritten as direct
  addable-slots/habits cases; new cases for the account sheet, sign-out confirm/cancel/confirm-fail.
- `RootViewModelTest`: drop focus from `OnboardingState` construction; behavior itself (route on
  `completed`) is unchanged, so existing routing assertions should still hold as-is.

### Integration Tests:

- None planned beyond the existing JUnit4 unit-test suite — no instrumented test changes are in
  scope for this change.

### Manual Testing Steps:

1. Fresh install → onboarding Welcome → Account → All set, no focus screen, lands on the hub.
2. From the hub, verify both Journal and Habit sections render, and the FAB offers both actions.
3. Tap the account icon while signed out → prompt card → tap it → lands on full `AccountScreen`.
4. Sign in, return to the hub, tap the account icon → email + Sign out shown.
5. Tap Sign out → confirm dialog → Cancel → still signed in, sheet still open.
6. Tap Sign out → confirm dialog → Confirm → signed out, sheet reflects it live.
7. Confirm no bottom navigation bar is visible anywhere in the app.

## Migration Notes

Bumping `TodayWasDatabase`'s version with the already-configured
`fallbackToDestructiveMigration(dropAllTables = true)` wipes all local tables (not just
`user_preferences`) on next launch. This is acceptable pre-launch (no shipped users, per
`tech-stack.md`), but anyone with existing local dev/test data will lose it after this change lands
— worth a heads-up before running the updated app on a device with data worth keeping.

## References

- Related archive: `context/archive/2026-07-25-onboarding-focus-pick/` (original Focus feature)
- Related archive: `context/archive/2026-08-03-supabase-auth-foundation/` (bottom nav + Account/auth
  foundation)
- Related archive: `context/archive/2026-08-10-account-creation-and-sync/` (data-sync review step)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Data layer & onboarding domain

#### Automated

- [x] 1.1 Unit tests pass — 388db9e
- [x] 1.2 Lint passes — 388db9e
- [x] 1.3 Debug build compiles — 388db9e

#### Manual

- [x] 1.4 Fresh install/clear-data launches past the destructive migration — 388db9e

### Phase 2: Onboarding UI — drop the focus-pick step

#### Automated

- [x] 2.1 Unit tests pass — 5f4effb
- [x] 2.2 Lint passes — 5f4effb
- [x] 2.3 Debug build compiles — 5f4effb

#### Manual

- [x] 2.4 Fresh onboarding run has no focus-pick screen — 5f4effb
- [x] 2.5 Skip from any step finishes onboarding — 5f4effb
- [x] 2.6 Kill/reopen mid-ACCOUNT_INFO still completes correctly — 5f4effb

### Phase 3: Main screen becomes the hub

#### Automated

- [x] 3.1 Unit tests pass
- [x] 3.2 Lint passes
- [x] 3.3 Debug build compiles

#### Manual

- [x] 3.4 Main screen always shows both sections
- [x] 3.5 FAB offers both actions together

### Phase 4: Account bottom sheet + top bar

#### Automated

- [ ] 4.1 Unit tests pass
- [ ] 4.2 Lint passes
- [ ] 4.3 Debug build compiles

#### Manual

- [ ] 4.4 Signed-out sheet prompts and navigates to AccountScreen
- [ ] 4.5 Signed-in sheet shows email + sign-out, live-updates after sign-out
- [ ] 4.6 Cancelling sign-out confirmation leaves user signed in

### Phase 5: Remove bottom nav & Preferences

#### Automated

- [ ] 5.1 Unit tests pass
- [ ] 5.2 Lint passes
- [ ] 5.3 Debug build compiles
- [ ] 5.4 No remaining references to MainShellScreen/BottomNavTab/ui.preferences

#### Manual

- [ ] 5.5 No bottom nav bar visible anywhere
- [ ] 5.6 All destinations still reachable from the hub

### Phase 6: Foundation docs

#### Manual

- [ ] 6.1 prd.md matches shipped behavior
