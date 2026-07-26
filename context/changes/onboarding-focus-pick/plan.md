# Onboarding Focus Pick Implementation Plan

## Overview

Implement FR-001 (roadmap `S-01`): a one-time, four-step onboarding flow (Welcome → Focus selection
→ Account → All set) via a non-dismissible popup over the main screen, persisted locally via Room,
with a minimal "Change focus" re-pick entry point. A persistent "Skip" action is available on every
step; the hardware back button navigates to the previous step (exiting the app from Welcome, since
there's no earlier step). This is the first feature ever built in this repo, so it also establishes
the app's data/domain/presentation layering, Hilt-based DI, and a **new Gradle module**
(`:core:designsystem`) housing `TodayWas*`-prefixed wrappers around every Material3 component this
slice touches — that later roadmap slices (`S-02` onward) will follow and extend. This is the first
multi-module split in the repo; `:app` depends on `:core:designsystem`, never the reverse.

## Current State Analysis

The codebase is the unmodified Android Studio Compose template:

- `MainActivity.kt` renders a static `Greeting("Android")` inside a `Scaffold` — no navigation, no
  ViewModel, no persistence, no DI.
- `gradle/libs.versions.toml` has no Room, KSP, Hilt, lifecycle-viewmodel-compose, coroutines-test,
  or Robolectric entries.
- `app/src/test/` and `app/src/androidTest/` contain only the default template tests (not yet read
  in detail — irrelevant, they'll be superseded).
- No `data/`, `domain/`, or `ui/main/` packages exist under `pl.luczka.todaywas/` yet — `ui/theme/`
  (Color.kt, Theme.kt, Type.kt) is the only non-root package, and it already establishes the
  `TodayWasTheme` naming precedent this plan's `TodayWas*` component wrappers follow.
- `settings.gradle.kts` declares a single module (`include(":app")`) — this is a single-module
  project today. `gradle/libs.versions.toml`'s `[plugins]` block has only `android-application`
  (`com.android.application`); there is no `android-library` (`com.android.library`) entry yet,
  which a new library module requires.

## Desired End State

On first launch, a non-dismissible dialog appears over a minimal main-screen shell, presenting four
steps in order: **Welcome** (orientation, "Get started") → **Focus selection** (the FR-001 decision)
→ **Account** (an informational nudge; "Create account" is an intentional no-op for now) → **All
set** (confirmation, "Get started" into the app). A "Skip" action is visible on every step: tapped
before a focus is confirmed, it defaults to "Both" and closes immediately; tapped after, it keeps
the already-chosen focus and just closes immediately. The hardware back button always moves to the
previous step (Welcome → exits the app). The dialog never reappears on that install once completed.
A "Change focus" action in the main screen's top bar reopens just the Focus-selection step, in a
normally-dismissible mode, with no Welcome/Account/All-set steps and no Skip action.

Verification: fresh install walks through all four steps in order; Skip and back behave as
specified at every step; the choice persists across app restarts; "Change focus" lets the user
revise it afterward without re-entering the full flow.

### Key Discoveries:

- No Room/DI/ViewModel infrastructure exists — this plan introduces all of it for the first time,
  scoped to exactly what onboarding needs (`pl.luczka.todaywas/data`, `domain`, `ui/main`).
- Room's DAO layer needs an Android `Context`, which plain JVM unit tests can't provide — Robolectric
  (runs under `src/test`, not `src/androidTest`) resolves this without adding an instrumented test.
- Compose `Dialog(properties = DialogProperties(dismissOnBackPress = false, ...))` only disables the
  *default* back-dismiss behavior — intercepting back press for step-navigation requires an explicit
  `BackHandler` inside the dialog's content (see Critical Implementation Details).
- A `Dialog` runs in its own window with its own back-press scope — a `BackHandler` inside it does
  NOT automatically bubble up to close the host `Activity`. Exiting the app from the Welcome step
  needs an explicit signal from the ViewModel to the Activity (see Critical Implementation Details).

## What We're NOT Doing

- No settings *screen* — "Change focus" is a single top-bar menu action that reopens the same
  `OnboardingDialog` Composable (Focus-selection step only), not a new destination.
- No real account creation — the Account step's "Create account" button is an intentional no-op.
  Real Supabase/Google sign-in is `F-01`'s job (not yet built, blocked on a human-only Supabase
  project setup step); wiring it up is explicitly deferred, not part of this slice.
- No Welcome/Account/All-set steps on the "Change focus" re-pick path — re-pick shows only the
  Focus-selection step.
- No confirmation dialog on Skip — a deliberate tap on a labeled "Skip" button is already an
  explicit choice; layering a confirmation on top of an explicit button (rather than an accidental
  back-press) would be over-confirming.
- No Room `Migration` objects — schema starts at `version = 1` with `fallbackToDestructiveMigration()`,
  acceptable pre-release since no shipped user data exists yet. Revisit once the app ships, and
  whenever `S-02`/`S-03` add their own entities to this database.
- No cloud sync of the focus preference — this slice only makes the choice available in the same
  Room database `S-06` (account creation + sync) will later read from; it does not implement syncing.
- No instrumented Compose UI test — the "unit tests only" testing-scope decision covers the
  persistence layer (via Robolectric) and ViewModel/use-case logic (via fakes), not the rendered UI.
- No dedicated test source set for `:core:designsystem` — its wrappers are stateless passthroughs
  with no logic to unit test, consistent with the "unit tests only, no instrumented Compose test"
  decision above.
- `ui/theme/` (`TodayWasTheme`, `Color`, `Type`) stays in `:app` — not moved into
  `:core:designsystem`, per explicit decision. One consequence worth naming plainly: the module is
  called "designsystem" but doesn't contain the theme, only the components — a deliberate scope
  choice, not an oversight.
- No further module splitting (e.g., extracting `data`/`domain` into their own modules) — this
  slice introduces exactly one new module, for components only.

## Implementation Approach

Standard three-layer Android architecture, introduced here for the first time:

- **Data**: `TodayWasDatabase` (Room, single entity for now) → `UserPreferencesDao` → `OnboardingRepository`
  (interface) / `OnboardingRepositoryImpl` (retry-once-then-fail write semantics).
- **Domain**: `ObserveOnboardingStateUseCase` (thin passthrough) and `SelectFocusUseCase` /
  `SkipOnboardingUseCase` (the latter carries the "default to Both" business rule) — each wraps a
  single repository operation. Unchanged by the 4-step/Skip/back rework below: Welcome and All-set
  are pure UI navigation with no new persisted state, and "Skip after a focus is already chosen"
  needs no use-case call at all (see Phase 3).
- **Presentation**: one `MainViewModel` owns both main-screen state and onboarding/dialog state
  (no separate `OnboardingViewModel`, since onboarding is an overlay on the one screen that exists),
  exposing a single `StateFlow<MainUiState>` to `MainScreen` and `OnboardingDialog`, plus a one-shot
  `exitAppEvent` flow for the Welcome-step back-press case.

Dependencies wired via **Hilt** (first-ever DI framework in this repo, introduced here since it's
established as project convention going forward): a `@HiltAndroidApp` `Application` class bootstraps
the graph; a `DatabaseModule` provides `TodayWasDatabase`/`UserPreferencesDao`; `OnboardingRepositoryImpl`
is `@Inject`-constructed and bound to the `OnboardingRepository` interface via `@Binds`; the two use
cases and `MainViewModel` are `@Inject`-constructed directly (no manual factory).

**Common components module**: `MainScreen` and `OnboardingDialog` (in `:app`) are built entirely from
`pl.luczka.todaywas.core.designsystem.components` wrappers (`TodayWasButton`, `TodayWasTextButton`,
`TodayWasText`, `TodayWasScaffold`, `TodayWasTopBar`, `TodayWasIconButton`, `TodayWasIcon`,
`TodayWasDialog`, `TodayWasRadioOption`, `TodayWasLoadingIndicator`) living in a new **`:core:designsystem`**
Gradle module, rather than raw Material3 composables directly — every Material3 component this
slice touches gets one. Naming follows the `TodayWasTheme` precedent already in `:app`'s `ui/theme/`,
not a generic `App*` prefix. Each wrapper is a thin passthrough for now (no custom styling beyond
default Material3) — the point of introducing them this early is to centralize the call site so
`S-02` onward reuse them by default and so future styling changes touch one module instead of every
screen, not to invent design tokens that don't exist yet.

`:core:designsystem` depends only on the Compose Material3/UI libraries (external AndroidX
artifacts) — it has no dependency on `:app`, `TodayWasTheme`, Room, Hilt, or the domain layer, and
`:app` depends on it (never the reverse). It does *not* need to import `TodayWasTheme` to render
correctly: Compose theming is ambient (`CompositionLocal`-based), so any composable inside
`TodayWasTheme { ... }` — including ones from a completely separate module — automatically reads its
`MaterialTheme.colorScheme`/`typography` without a compile-time reference to `TodayWasTheme` itself
(see Critical Implementation Details). This is what keeps the module graph acyclic without needing
to move `ui/theme/` anywhere.

## Critical Implementation Details

- **Non-dismissible dialog + back-press interception**: `dismissOnBackPress = false` in
  `DialogProperties` only suppresses the *default* dismiss-on-back. To drive step-navigation
  instead, nest a `BackHandler(enabled = true) { viewModel.onStepBack() }` inside the dialog's
  content (MANDATORY mode only) — without it, back press during onboarding does nothing at all (a
  silent gap, not an error, easy to miss in review).
- **Exiting the app from the Welcome step needs a one-shot event, not state**: a Compose `Dialog`
  has its own back-press scope, so a `BackHandler` inside it can't just call `Activity.finish()`
  directly from the ViewModel (no Activity reference there) and can't rely on the event "falling
  through" to the host Activity's dispatcher. Expose `MainViewModel.exitAppEvent: Flow<Unit>` (a
  `Channel`/`SharedFlow`, not a boolean in `MainUiState`) that `onStepBack()` emits to when called
  on the Welcome step; `MainScreen` collects it in a `LaunchedEffect` and calls
  `(LocalContext.current as Activity).finish()`. A boolean flag would work but risks re-firing on
  configuration change; a one-shot event flow is the correct tool here.
- **Dialog has two dismissal modes, same Composable**: mandatory first-run mode (non-dismissible,
  `BackHandler` → step navigation, Skip action visible) vs. voluntary re-pick mode (opened via
  "Change focus": normal `dismissOnBackPress = true`/`dismissOnClickOutside = true`, no Skip action,
  always the Focus-selection step). Drive this from `MainUiState.onboardingMode: MANDATORY | REPICK`
  — don't duplicate the Composable.
- **Write-retry lives in the Repository, not the UseCase**: `SelectFocusUseCase`/`SkipOnboardingUseCase`
  just call `repository.saveFocus(...)`. The retry-once-then-`Result.failure` behavior is I/O
  resilience, not a business rule, so it belongs in `OnboardingRepositoryImpl`, not the domain layer.
- **Mandatory onboarding is four steps**: `WELCOME` → `FOCUS_PICK` (the 3-option picker) →
  `ACCOUNT_INFO` (informational, "Create account" no-op) → `ALL_SET` (confirmation) — only in
  `MANDATORY` mode; `REPICK` never leaves `FOCUS_PICK`. The focus choice is persisted (and
  `onboardingCompleted` set) as soon as `FOCUS_PICK` is confirmed, *before* `ACCOUNT_INFO`/`ALL_SET`
  show — so if the app is killed between steps, the dialog will not reappear on relaunch and the
  user simply never sees the remaining steps. This is an accepted edge case, not a bug: adding a
  second persisted "onboarding fully finished" flag just to guarantee two informational screens are
  shown isn't worth the complexity.
- **`:core:designsystem` never imports `TodayWasTheme`, by design**: it would be natural to assume
  the components module needs `TodayWasTheme` to style itself, but Compose theming works via
  ambient `CompositionLocal`s — `MaterialTheme.colorScheme`/`typography` resolve to whatever theme
  is active *above* a composable in the tree, regardless of which module defined that composable.
  Since `MainActivity` applies `TodayWasTheme` once at the root (`setContent { TodayWasTheme {
  MainScreen() } }`), every `TodayWas*` wrapper picks it up automatically. Do NOT add a dependency
  from `:core:designsystem` back to `:app` (or move `TodayWasTheme` into the new module) to "fix" a
  missing-theme problem that doesn't actually exist — that would create the exact circular
  dependency the module split is meant to avoid.
- **Skip's effect depends on whether a focus is already confirmed**: `onSkipOnboarding()` checks
  `currentFocus` (the *persisted* value, not the transient in-dialog selection). If `null`
  (Welcome/Focus-pick, nothing confirmed yet), it calls `SkipOnboardingUseCase` (defaults to
  "Both") before closing. If non-null (Account/All-set, a real choice already landed), it closes
  directly with no use-case call — Skip must never silently overwrite a choice the user already
  made. Back-navigating from `ACCOUNT_INFO` to `FOCUS_PICK` pre-fills `selectedFocusInDialog =
  currentFocus`, same as the "Change focus" re-pick pre-fill.

## Phase 1: Data layer (Room)

### Overview

Stand up the Room database, the user-preferences entity/DAO, and the repository that will back all
onboarding state — including retry-once write resilience — plus the Hilt DI bootstrap the whole app
will build on.

### Changes Required:

#### 1. Version catalog

**File**: `gradle/libs.versions.toml`

**Intent**: Add Room (runtime, ktx, compiler), Hilt (android, compiler) + Hilt Navigation Compose,
the KSP plugin, and Robolectric + `kotlinx-coroutines-test` as test-only dependencies.

**Contract**: New `[versions]` entries for `room`, `hilt`, `ksp`, `robolectric`, `coroutinesTest`; new
`[libraries]` entries `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler`,
`hilt-android`, `hilt-compiler`, `androidx-hilt-navigation-compose`, `robolectric`,
`kotlinx-coroutines-test`; new `[plugins]` entries `ksp` (`com.google.devtools.ksp`) and `hilt`
(`com.google.dagger.hilt.android`). Resolve current stable versions at implementation time rather
than pinning guessed numbers here.

#### 2. Root and app Gradle plugin wiring

**File**: `build.gradle.kts`, `app/build.gradle.kts`

**Intent**: Apply the KSP and Hilt plugins; wire `libs.androidx.room.compiler` and `libs.hilt.compiler`
via `ksp(...)` in `app/build.gradle.kts`; add `testImplementation` entries for `robolectric` and
`kotlinx-coroutines-test`.

**Contract**: `alias(libs.plugins.ksp) apply false` and `alias(libs.plugins.hilt) apply false` at
root; `alias(libs.plugins.ksp)` and `alias(libs.plugins.hilt)` in `app/`; `app/build.gradle.kts`'s
`dependencies` block gains `implementation(libs.androidx.room.runtime)`,
`implementation(libs.androidx.room.ktx)`, `ksp(libs.androidx.room.compiler)`,
`implementation(libs.hilt.android)`, `ksp(libs.hilt.compiler)`,
`implementation(libs.androidx.hilt.navigation.compose)`, `testImplementation(libs.robolectric)`,
`testImplementation(libs.kotlinx.coroutines.test)`.

#### 3. Hilt bootstrap

**File**: `app/src/main/java/pl/luczka/todaywas/TodayWasApplication.kt`,
`app/src/main/AndroidManifest.xml`

**Intent**: The `@HiltAndroidApp` Application class Hilt needs to generate its root component. No
custom `Application` exists yet — `AndroidManifest.xml`'s `<application>` element currently has no
`android:name`.

**Contract**: `@HiltAndroidApp class TodayWasApplication : Application()`; add
`android:name=".TodayWasApplication"` to the manifest's `<application>` element.

#### 4. Database module

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/DatabaseModule.kt`

**Intent**: Provide the Room database and DAO through Hilt instead of a manual singleton accessor.

**Contract**: `@Module @InstallIn(SingletonComponent::class) object DatabaseModule` with
`@Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): TodayWasDatabase`
(built with `Room.databaseBuilder(...).fallbackToDestructiveMigration().build()`) and
`@Provides fun provideUserPreferencesDao(db: TodayWasDatabase): UserPreferencesDao`.

#### 5. Entity + Database

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/UserPreferencesEntity.kt`,
`app/src/main/java/pl/luczka/todaywas/data/local/TodayWasDatabase.kt`

**Intent**: A singleton-row table holding the onboarding/focus state, and the app's single Room
database (future slices add entities here, not a new database). Instantiated only through the
`DatabaseModule` Hilt provider above — no manual singleton accessor.

**Contract**: `@Entity(tableName = "user_preferences")` with `@PrimaryKey val id: Int = 0` (always
0 — enforces a single row), `val focus: String` (stores `Focus.name`), `val onboardingCompleted: Boolean`.
`@Database(entities = [UserPreferencesEntity::class], version = 1)` abstract class exposing
`abstract fun userPreferencesDao(): UserPreferencesDao`.

#### 6. DAO

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/UserPreferencesDao.kt`

**Intent**: Observe and upsert the single preferences row.

**Contract**: `@Dao interface` with `fun observe(): Flow<UserPreferencesEntity?>` (`@Query("SELECT *
FROM user_preferences WHERE id = 0")`) and `suspend fun upsert(entity: UserPreferencesEntity)`
(`@Upsert`).

#### 7. Domain model + Repository

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/Focus.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/model/OnboardingState.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/OnboardingRepository.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/OnboardingRepositoryImpl.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/RepositoryModule.kt`

**Intent**: `Focus` is the domain enum (`JOURNAL`, `HABIT`, `BOTH`). `OnboardingState` is
`data class(val completed: Boolean, val focus: Focus?)`. `OnboardingRepository` exposes
`fun observeState(): Flow<OnboardingState>` and `suspend fun saveFocus(focus: Focus): Result<Unit>`.
`OnboardingRepositoryImpl` (`@Inject constructor(private val dao: UserPreferencesDao)`) maps entity
↔ domain model and implements the write path: attempt the DAO upsert, on failure retry once, on
second failure return `Result.failure` without throwing. `RepositoryModule` binds the interface to
the impl: `@Module @InstallIn(SingletonComponent::class) abstract class RepositoryModule { @Binds
abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository }`.

**Contract**: No code snippet needed beyond the `@Binds` shape above — this is a standard repository
mapping/retry pattern; the retry-once-then-fail behavior is the only load-bearing rule (see Critical
Implementation Details).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles (confirms Room/KSP/Hilt wiring): `./gradlew.bat assembleDebug`
- Robolectric test verifies a `saveFocus` call followed by `observeState()` returns the persisted
  value, and that it survives recreating the database instance from the same file (simulates process
  restart).
- Repository-level fake-DAO test verifies retry-once-then-`Result.failure` on repeated write failure.

#### Manual Verification:

- N/A for this phase (no UI yet) — covered end-to-end in Phase 4.

---

## Phase 2: Domain (use cases)

### Overview

Wrap the repository behind two use cases, placing the skip-defaults-to-Both business rule at the
domain layer.

### Changes Required:

#### 1. Use cases

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/ObserveOnboardingStateUseCase.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/usecase/SelectFocusUseCase.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/usecase/SkipOnboardingUseCase.kt`

**Intent**: `ObserveOnboardingStateUseCase` passes `repository.observeState()` through unchanged.
`SelectFocusUseCase(focus: Focus)` passes the user's explicit choice through to
`repository.saveFocus(focus)` — used for both first-time selection and "Change focus" re-pick.
`SkipOnboardingUseCase` calls `repository.saveFocus(Focus.BOTH)` — the skip-defaults-to-Both product
rule lives here, not in the ViewModel or Repository. (The ViewModel decides *whether* to call
`SkipOnboardingUseCase` at all based on `currentFocus`, per Critical Implementation Details — this
use case itself is unchanged by that logic.)

**Contract**: Each use case is `@Inject constructor(private val repository: OnboardingRepository)`
(Hilt-injectable, no module needed for plain `@Inject`-constructed classes) and exposes a single
`operator fun invoke(...)` (idiomatic Kotlin use-case shape). No code snippet needed — trivial
delegation except for `SkipOnboardingUseCase`'s hardcoded `Focus.BOTH`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `SkipOnboardingUseCase` test verifies it calls `saveFocus(Focus.BOTH)` regardless of prior state,
  against a fake `OnboardingRepository`.
- `SelectFocusUseCase` test verifies it passes the given `Focus` through unchanged.

#### Manual Verification:

- N/A for this phase — covered end-to-end in Phase 4.

---

## Phase 3: Presentation (MainViewModel)

### Overview

One ViewModel owning both main-screen and onboarding/dialog state, driving the 4-step flow, Skip,
and back-navigation, calling the Phase 2 use cases.

### Changes Required:

#### 1. UI state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiState.kt`

**Intent**: The single state shape `MainScreen` and `OnboardingDialog` render from.

**Contract**: `data class MainUiState(val currentFocus: Focus?, val showOnboardingDialog: Boolean,
val onboardingMode: OnboardingMode, val onboardingStep: OnboardingStep, val selectedFocusInDialog:
Focus?, val isSaving: Boolean, val saveError: Boolean)` where `OnboardingMode` is an enum
(`MANDATORY`, `REPICK`) and `OnboardingStep` is an enum (`WELCOME`, `FOCUS_PICK`, `ACCOUNT_INFO`,
`ALL_SET`) — `onboardingStep` is only meaningful when `onboardingMode == MANDATORY`; `REPICK` never
leaves `FOCUS_PICK`. (No `showSkipConfirmation` field — that dialog no longer exists.)

#### 2. ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Bridges the use cases to `MainUiState`. Collects `ObserveOnboardingStateUseCase` on init
to decide whether the dialog should show (`completed == false` → `showOnboardingDialog = true`,
`onboardingMode = MANDATORY`, `onboardingStep = WELCOME`). Exposes:

- `onWelcomeContinue()` — `WELCOME` → `FOCUS_PICK` (pure UI navigation, no persistence).
- `onFocusOptionSelected(focus)` — updates `selectedFocusInDialog`, transient.
- `onConfirmSelection()` — calls `SelectFocusUseCase(selectedFocusInDialog)`; on success in
  MANDATORY mode advances `onboardingStep = ACCOUNT_INFO`, in REPICK mode closes the dialog
  directly; sets `isSaving`/`saveError` around the call.
- `onCreateAccountClicked()` — intentional no-op (see Critical Implementation Details).
- `onAccountContinue()` — `ACCOUNT_INFO` → `ALL_SET` (MANDATORY only; pure UI navigation).
- `onFinishOnboarding()` — `ALL_SET` → closes the dialog (`showOnboardingDialog = false`).
- `onStepBack()` — MANDATORY-mode back navigation: `FOCUS_PICK` → `WELCOME`; `ACCOUNT_INFO` →
  `FOCUS_PICK` (also pre-fills `selectedFocusInDialog = currentFocus`); `ALL_SET` → `ACCOUNT_INFO`;
  `WELCOME` → emits to `exitAppEvent` instead of changing `onboardingStep`.
- `onSkipOnboarding()` — MANDATORY-mode Skip, available on any step: if `currentFocus == null`,
  calls `SkipOnboardingUseCase` then closes the dialog; if non-null, closes the dialog directly with
  no use-case call (see Critical Implementation Details for why).
- `onChangeFocusRequested()` — opens dialog with `onboardingMode = REPICK`, `onboardingStep =
  FOCUS_PICK`, `selectedFocusInDialog = currentFocus`.
- `onRetrySave()` — re-invokes the last selection attempt after a `saveError`.

**Contract**: `@HiltViewModel class MainViewModel @Inject constructor(private val
observeOnboardingState: ObserveOnboardingStateUseCase, private val selectFocus: SelectFocusUseCase,
private val skipOnboarding: SkipOnboardingUseCase) : ViewModel()`, exposing `StateFlow<MainUiState>`
(`MutableStateFlow` internally) and `val exitAppEvent: Flow<Unit>` (backed by a `Channel<Unit>`,
per Critical Implementation Details). No manual factory — Hilt generates the wiring via
`hiltViewModel()` at the call site (Phase 4).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `MainViewModel` tests (against fake use cases) cover: initial state is `MANDATORY`/`WELCOME` when
  onboarding incomplete; `onWelcomeContinue()` advances to `FOCUS_PICK`; `onConfirmSelection()`
  success in MANDATORY mode updates `currentFocus`, advances to `ACCOUNT_INFO`, does NOT close the
  dialog; `onConfirmSelection()` success in REPICK mode closes the dialog directly; `onConfirmSelection()`
  failure sets `saveError` and stays on `FOCUS_PICK`; `onAccountContinue()` advances `ACCOUNT_INFO`
  → `ALL_SET`; `onCreateAccountClicked()` is a true no-op (no state change, no use-case call);
  `onFinishOnboarding()` closes the dialog; `onStepBack()` covers all four MANDATORY steps
  (`FOCUS_PICK`→`WELCOME`, `ACCOUNT_INFO`→`FOCUS_PICK` incl. pre-fill, `ALL_SET`→`ACCOUNT_INFO`,
  `WELCOME`→emits `exitAppEvent` without changing `onboardingStep`); `onSkipOnboarding()` calls
  `SkipOnboardingUseCase` and closes when `currentFocus == null`, but closes with NO use-case call
  when `currentFocus` is already set; `onChangeFocusRequested()` sets `onboardingMode = REPICK`,
  `onboardingStep = FOCUS_PICK`, dialog pre-filled to `currentFocus`.

#### Manual Verification:

- N/A for this phase — covered end-to-end in Phase 4.

---

## Phase 4: UI

### Overview

Stand up the new `:core:designsystem` module and its common `TodayWas*` component wrappers, then
`MainScreen` (the shell) and `OnboardingDialog` (4 steps + persistent Skip) from them, wire both into
`MainActivity` via the Phase 3 ViewModel, and remove the default template content.

### Changes Required:

#### 1. Module setup

**File**: `settings.gradle.kts`, `gradle/libs.versions.toml`, `core/designsystem/build.gradle.kts`,
`app/build.gradle.kts`

**Intent**: Register the new library module and give `:app` a dependency on it. This is the repo's
first multi-module split — `settings.gradle.kts` currently declares only `:app`.

**Contract**: `settings.gradle.kts` gains `include(":core:designsystem")`. `gradle/libs.versions.toml`
gains a new `[plugins]` entry `android-library` (`com.android.library`, same `version.ref = "agp"`
as the existing `android-application` entry). New `core/designsystem/build.gradle.kts`: `plugins {
alias(libs.plugins.android.library); alias(libs.plugins.kotlin.compose); alias(libs.plugins.ktlint)
}`; `android { namespace = "pl.luczka.todaywas.core.designsystem"; compileSdk = ...; defaultConfig
{ minSdk = 30 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17;
targetCompatibility = JavaVersion.VERSION_17 }; buildFeatures { compose = true } }` (mirror `:app`'s
`compileSdk`/Java-version config); `dependencies` block: `implementation(platform(libs.androidx.compose.bom))`,
`implementation(libs.androidx.compose.material3)`, `implementation(libs.androidx.compose.ui)`,
`implementation(libs.androidx.compose.ui.graphics)`, `implementation(libs.androidx.compose.ui.tooling.preview)`,
`debugImplementation(libs.androidx.compose.ui.tooling)` — Compose/Material3 only, no Room, no Hilt,
no domain-layer dependency, keeping the module a pure UI-presentation layer. `app/build.gradle.kts`
gains `implementation(project(":core:designsystem"))`.

#### 2. Common components

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasButton.kt`,
`.../TodayWasTextButton.kt`, `.../TodayWasText.kt`, `.../TodayWasScaffold.kt`,
`.../TodayWasTopBar.kt`, `.../TodayWasIconButton.kt`, `.../TodayWasIcon.kt`,
`.../TodayWasDialog.kt`, `.../TodayWasRadioOption.kt`, `.../TodayWasLoadingIndicator.kt`

**Intent**: One thin wrapper per Material3 component `MainScreen`/`OnboardingDialog` use, living in
the new module — every call site in this slice goes through these (imported from
`pl.luczka.todaywas.core.designsystem.components`), not `androidx.compose.material3` directly.
`TodayWasButton` wraps `Button` (primary actions: Welcome's "Get started", Focus-pick's
confirm/retry, Account's "Continue", All-set's "Get started"). `TodayWasTextButton` wraps
`TextButton` (Skip, "Create account"). `TodayWasText` wraps `Text` (all copy). `TodayWasScaffold`
wraps `Scaffold`. `TodayWasTopBar` wraps `TopAppBar`. `TodayWasIconButton` wraps `IconButton` and
`TodayWasIcon` wraps `Icon` (the "Change focus" top-bar action). `TodayWasDialog` wraps `Dialog`
(the outer container `OnboardingDialog` builds on — distinct from `OnboardingDialog` itself, which
stays in `:app` as the feature composable). `TodayWasRadioOption` is a composite (`RadioButton` +
`TodayWasText` in a selectable `Row`) for the three focus choices — not a raw `RadioButton` wrapper
alone, since the picker always needs the pair together. `TodayWasLoadingIndicator` wraps
`CircularProgressIndicator`, shown while `uiState.isSaving` is true.

**Contract**: Each wrapper accepts the same essential parameters as its Material3 counterpart
(text/label, `onClick`, `enabled`, `modifier`, etc.) and forwards them, applying no styling beyond
default Material3 — no reference to `TodayWasTheme` anywhere in this module (see Critical
Implementation Details). No code snippet needed — these are declarative passthroughs; the value is
the call-site indirection, not novel behavior.

#### 3. Main screen shell

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: `TodayWasScaffold` with a `TodayWasTopBar` (title "TodayWas", a "Change focus"
`TodayWasIconButton` action visible only once onboarding is complete) and an empty-state body
(`TodayWasText`, "No entries yet" placeholder) — the foundation `S-02`/`S-03` extend with real
content later. Hosts `OnboardingDialog` when `uiState.showOnboardingDialog` is true. Collects
`viewModel.exitAppEvent` in a `LaunchedEffect` and calls `(LocalContext.current as Activity).finish()`.

**Contract**: `@Composable fun MainScreen(viewModel: MainViewModel = hiltViewModel())` collecting
`uiState` via `collectAsStateWithLifecycle()`. The "Change focus" `TodayWasTopBar` action calls
`viewModel.onChangeFocusRequested()`. Imports the `TodayWas*` components from
`pl.luczka.todaywas.core.designsystem.components` (cross-module import — `:app` depends on
`:core:designsystem` per item #1).

#### 4. Onboarding dialog

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/OnboardingDialog.kt`

**Intent**: Built on `TodayWasDialog`, renders one of four steps based on `uiState.onboardingStep`
(MANDATORY mode only — REPICK stays on `FOCUS_PICK`): `WELCOME` shows app name + one-line value prop
(`TodayWasText`) + "Get started" (`TodayWasButton`, `onWelcomeContinue()`); `FOCUS_PICK` shows three
`TodayWasRadioOption`s plus a confirm `TodayWasButton`; `ACCOUNT_INFO` shows a short explanation
(`TodayWasText`) of the benefit of an account, a "Create account" `TodayWasTextButton`
(`onCreateAccountClicked()` — no-op) and a "Continue" `TodayWasButton` (`onAccountContinue()`);
`ALL_SET` shows a confirmation message (`TodayWasText`) plus "Get started" (`TodayWasButton`,
`onFinishOnboarding()`). A persistent "Skip" `TodayWasTextButton` (top-end corner) is shown on all
four steps in MANDATORY mode only, calling `viewModel.onSkipOnboarding()`. `TodayWasLoadingIndicator`
shows while `uiState.isSaving`. Mode-dependent dismissal per Critical Implementation Details; inline
error + "Try again" (`TodayWasText` + `TodayWasButton`) on `FOCUS_PICK` when `uiState.saveError` is
true.

**Contract**:

```kotlin
TodayWasDialog(
    onDismissRequest = { if (uiState.onboardingMode == OnboardingMode.REPICK) viewModel.onDismiss() },
    dismissOnBackPress = uiState.onboardingMode == OnboardingMode.REPICK,
    dismissOnClickOutside = uiState.onboardingMode == OnboardingMode.REPICK,
) {
    if (uiState.onboardingMode == OnboardingMode.MANDATORY) {
        // Single callback for all four steps — the ViewModel's onStepBack() branches on
        // onboardingStep internally (WELCOME emits exitAppEvent instead of changing the step).
        BackHandler(enabled = true) { viewModel.onStepBack() }
    }
    Box {
        if (uiState.onboardingMode == OnboardingMode.MANDATORY) {
            TodayWasTextButton(text = "Skip", onClick = viewModel::onSkipOnboarding, modifier = Modifier.align(Alignment.TopEnd))
        }
        // WELCOME / FOCUS_PICK / ACCOUNT_INFO / ALL_SET content, switched on uiState.onboardingStep
    }
}
```

`TodayWasDialog` itself wraps the underlying `Dialog`/`DialogProperties` (per item #2 above) —
`OnboardingDialog` never touches `androidx.compose.material3.Dialog` directly.

#### 5. MainActivity

**File**: `app/src/main/java/pl/luczka/todaywas/MainActivity.kt`

**Intent**: `@AndroidEntryPoint` so Hilt can inject into the Activity's Compose tree (required for
`hiltViewModel()` to resolve); replace the default `Greeting` content with `MainScreen`.

**Contract**: `@AndroidEntryPoint class MainActivity : ComponentActivity()`; `setContent {
TodayWasTheme { MainScreen() } }`; delete `Greeting`/`GreetingPreview`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug` (also confirms the new
  `:core:designsystem` module resolves and `:app`'s dependency on it builds cleanly)

#### Manual Verification:

- Fresh install: Welcome step appears immediately, non-dismissible (tapping outside / back does not
  close it outright); "Skip" is visible.
- Tapping "Get started" on Welcome advances to Focus-pick; picking an option and confirming advances
  to Account; "Create account" does nothing observable, "Continue" advances to All-set; "Get
  started" on All-set closes the dialog and the main screen shows the empty-state shell. Relaunching
  does not show the dialog again.
- From each of the four steps in turn (across separate fresh installs / cleared app data), pressing
  back moves to the previous step; from Welcome specifically, pressing back exits the app.
- From Focus-pick (nothing confirmed yet), tapping "Skip" defaults focus to "Both" and closes the
  dialog immediately; relaunching does not show the dialog again.
- From Account or All-set (a focus already confirmed), tapping "Skip" keeps the already-chosen focus
  (not overwritten to "Both") and closes the dialog immediately.
- "Change focus" in the top bar reopens the dialog pre-filled with the current selection, showing
  only the Focus-pick step (no Welcome/Account/All-set, no Skip button), in dismissible mode.
- Forcing a write failure (e.g., a temporary throw in the DAO during manual testing) shows the
  inline error with "Try again" on Focus-pick, and retrying succeeds once the forced failure is
  removed.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- Room DAO/database round-trip via Robolectric (Phase 1).
- Repository retry-once-then-fail behavior against a fake DAO (Phase 1).
- Use-case delegation and the skip-defaults-to-Both rule (Phase 2).
- ViewModel state transitions for all four steps, Skip's two distinct behaviors, back-navigation
  (including the Welcome-step `exitAppEvent` emission), retry, and re-pick, against fake use cases
  (Phase 3).

### Integration Tests:

- None — out of scope per the "unit tests only" testing-scope decision for this slice.

### Manual Testing Steps:

1. Fresh install → confirm Welcome step appears, non-dismissible, "Skip" visible.
2. Tap "Get started" → Focus-pick → pick "Journaling" → confirm → Account step appears → tap
   "Create account" → confirm nothing happens → tap "Continue" → All-set step appears → tap "Get
   started" → confirm main screen shell appears, relaunch → dialog does not reappear.
3. Clear app data → repeat with "Habit tracking", using back press instead of "Continue" on the
   Account step (should behave the same as "Continue") → same persistence check.
4. Clear app data → repeat with "Both", using back press at each step in turn to confirm it always
   returns to the previous step (All-set → Account → Focus-pick → Welcome → back once more exits
   the app).
5. Clear app data → on Focus-pick (before confirming anything), tap "Skip" → confirm focus is
   "Both", dialog closes immediately, relaunch → dialog does not reappear.
6. Clear app data → pick "Journaling", confirm → on the Account step, tap "Skip" → confirm focus is
   still "Journaling" (NOT overwritten to "Both"), dialog closes immediately.
7. From a completed state, tap "Change focus" → dialog reopens pre-filled, Focus-pick step only, no
   Skip button → cancel via back → confirm nothing changed → reopen and confirm a different option
   → confirm it persists.
8. Force a write failure on Focus-pick → confirm inline error + "Try again" → remove the forced
   failure → retry → confirm it succeeds and advances to the Account step.

## Performance Considerations

Negligible — a single-row Room query via `Flow`. No specific performance budget applies.

## Migration Notes

Schema starts at `version = 1` with one entity. `fallbackToDestructiveMigration()` is acceptable
pre-release since no shipped user data exists yet — revisit before the app ships, and whenever
`S-02`/`S-03` add their own entities to this same database (they should extend `TodayWasDatabase`'s
`entities` list rather than create a new database, since `S-06`'s later account-sync work needs one
database to read the full local dataset from).

## References

- Roadmap: `context/foundation/roadmap.md` (`S-01: Onboarding and focus pick`)
- PRD: `context/foundation/prd.md` (FR-001, FR-008)
- Project conventions: `AGENTS.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: Data layer (Room)

#### Automated

- [ ] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 1.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 1.3 Debug build compiles: `./gradlew.bat assembleDebug`
- [ ] 1.4 Robolectric persistence round-trip test passes
- [ ] 1.5 Repository retry-once-then-fail test passes

### Phase 2: Domain (use cases)

#### Automated

- [ ] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 2.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 2.3 SkipOnboardingUseCase test passes
- [ ] 2.4 SelectFocusUseCase test passes

### Phase 3: Presentation (MainViewModel)

#### Automated

- [ ] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 3.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 3.3 MainViewModel state-transition tests pass (4-step flow, Skip, back-navigation, retry, re-pick)

### Phase 4: UI

#### Automated

- [ ] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 4.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 4.3 Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 4.4 Fresh install shows Welcome step, non-dismissible, Skip visible
- [ ] 4.5 All four steps flow in order (Welcome → Focus-pick → Account → All-set) and persist
      across relaunch
- [ ] 4.6 Back press at each step moves to the previous step; back on Welcome exits the app
- [ ] 4.7 Skip on Welcome/Focus-pick defaults to "Both"; Skip on Account/All-set preserves the
      already-chosen focus
- [ ] 4.8 "Change focus" reopens the dialog pre-filled, in dismissible mode, Focus-pick step only,
      no Skip button
- [ ] 4.9 Write-failure retry flow works as specified
