# Android audit — TodayWas — 2026-10-31

## Summary

The architecture underneath is sound: clear UI/domain/data layers, repositories as the only data
entry point, Navigation 3 wired correctly with ID-only keys and per-entry ViewModel scoping,
cancellation handled correctly everywhere, and a genuinely strong unit-test suite (397 passing,
fakes only, every ViewModel and repository covered). The biggest risk is a **crash on Android
11–13**: three `LocalDate.ofInstant` calls need API 34 while `minSdk` is 30. It wasn't caught
because CI never runs Android Lint and JVM unit tests can't see API-level problems. The most
widespread guideline violation is the ViewModel→UI event channel used by 13 of 14 ViewModels, which
the project's own `CLAUDE.md` prescribes — so it's a convention to revisit, not just code to fix.

**Scope:** `:app` (224 Kotlin files) and `:core:designsystem` (61), all of it ·
**Method:** static review, Android Lint (`lintDebug`), unit tests (`testDebugUnitTest`) ·
**Not covered:** instrumented/UI tests (none exist beyond the template), runtime profiling,
the Supabase edge function, release-build R8 output.

## Findings

### A1 `LocalDate.ofInstant` crashes on API 30–33 — Critical
- **Rule:** minSdk compatibility; Android Lint `NewApi` error (build-performance: lint as a gate)
- **Evidence:** 3 calls, lint errors: `domain/model/ContributionWindow.kt:17`,
  `domain/model/ContributionWindow.kt:28`, `domain/util/EditWindow.kt:20`. `minSdk = 30`, core
  library desugaring not enabled (`app/build.gradle.kts`).
- **Why it matters:** `NoSuchMethodError` on Android 11, 12 and 13 — still a large share of
  devices — in the contribution grid (`ObserveJournalContributionUseCase`,
  `ObserveHabitContributionUseCase`, `HabitDetailViewModel`, `JournalListViewModel`) and in fresh
  loggable dates (`GetFreshLoggableDatesUseCase`, `SaveHabitCheckInsUseCase`). The main screen
  and check-in logging are on that path.
- **Fix:** replace with `now.atZone(ZoneId.systemDefault()).toLocalDate()` (API 26). Enabling core
  library desugaring also works but adds a dependency for one method. **Effort:** S

### A2 CI doesn't run Android Lint — High
- **Rule:** build-performance: treat Android Lint as a CI gate
- **Evidence:** `.github/workflows/ci.yml:57-61` runs `ktlintCheck`, `testDebugUnitTest`,
  `assembleDebug` only. `lintDebug` currently fails locally with 3 errors / 45 warnings.
- **Why it matters:** A1 shipped to `master` through green CI. In a mostly agent-written codebase,
  lint is the cheapest reviewer you have.
- **Fix:** add `./gradlew lintDebug` to the `android` job after A1 lands; consider
  `warningsAsErrors` later once the warnings below are cleared or baselined. **Effort:** S

### A3 ViewModel → UI events via `Channel` — High
- **Rule:** ui-layer — **[SR]** "Do not send events from the ViewModel to the UI"
  ([UI events](https://developer.android.com/topic/architecture/ui-layer/events))
- **Evidence:** 13 of 14 ViewModels, e.g. `ui/main/MainViewModel.kt:66`,
  `ui/habit/create/CreateHabitViewModel.kt:34`, `ui/account/AccountViewModel.kt:61`; collected in
  `LaunchedEffect(Unit)`, e.g. `ui/account/AccountScreen.kt:56`. Prescribed by `CLAUDE.md`
  ("one-shot effects via `val events: Flow<XxxUiEvent>` backed by a buffered `Channel`") and
  `lessons.md`.
- **Why it matters:** delivery isn't guaranteed if the collector is cancelled mid-handling
  (configuration change, leaving composition), so a "Saved → navigate back" or error snackbar can
  be lost. Many events carry no logic at all — e.g. `MainIntent.JournalViewAllClicked` →
  `MainUiEvent.NavigateToJournalList` (`MainViewModel.kt` `onIntent`) — and just add a ViewModel
  round-trip to a click.
- **Fix:** per screen, (1) navigation-only clicks call the screen's navigation lambdas directly
  from the UI; (2) outcomes become state (`isSaved`, `userMessage: AuthErrorUiState?`) that the
  screen reacts to in `LaunchedEffect(state.x)` and acknowledges with an intent that clears it.
  **Effort:** M overall, S per screen.

### A4 24h edit window enforced against a caller-supplied timestamp — High
- **Rule:** project hard rule FR-006 ("enforce at write time, not just in the UI"); data-domain:
  single source of truth
- **Evidence:** `domain/usecase/UpdateJournalEntryUseCase.kt` and
  `domain/usecase/UpdateHabitCheckInUseCase.kt` check `EditWindow.isEditable(createdAt, …)` on a
  `createdAt` **parameter** (passed from ViewModel state, `ui/journal/edit/EditJournalEntryViewModel.kt:139`);
  `data/repository/JournalRepositoryImpl.kt:57-64` `updateEntry` writes without any check.
- **Why it matters:** the rule holds only as long as every caller passes the true stored
  `createdAt`. Any future caller (sync, a widget, a new screen) or a stale in-memory value can
  edit an entry past its window.
- **Fix:** have the use case (or repository) read the stored entry by ID and check its persisted
  `createdAt`, or make the DAO update conditional (`WHERE id = :id AND createdAt > :cutoff`) and
  report "expired" when no row changes. Add a test that a stale `createdAt` can't bypass it.
  **Effort:** S–M

### A5 UI state collected in `init` instead of `stateIn` — Medium
- **Rule:** ui-layer — **[R]** build stream-derived state with
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`
- **Evidence:** most ViewModels launch collectors in `init` and push into a private
  `MutableStateFlow`, e.g. `ui/main/MainViewModel.kt:69-95`; only
  `ui/habit/detail/HabitDetailViewModel.kt:112` uses `stateIn`.
- **Why it matters:** upstream Room/auth flows keep running for the ViewModel's whole life,
  including while the app is backgrounded; state is spread across imperative `update` calls.
- **Fix:** migrate screen by screen, together with A3 (both touch the same ViewModels). Tests then
  need an active collector — see `testing.md`. **Effort:** M

### A6 Re-sortable lazy lists without item keys — Medium
- **Rule:** compose — lazy lists provide stable `key`s
- **Evidence:** `ui/habit/list/HabitListScreen.kt:113`, `ui/journal/list/JournalListScreen.kt:129`,
  `ui/habit/logcheckin/LogHabitCheckInsScreen.kt:117` (`items(list) { … }` with no `key`).
- **Why it matters:** these lists change order (sort chips), so item state and animations attach to
  positions rather than items, and every reorder recomposes all rows.
- **Fix:** `items(uiState.habits, key = { it.id }) { … }` (and equivalents). **Effort:** S

### A7 No UI or navigation tests — Medium
- **Rule:** testing — **[SR]** UI navigation tests unless the app is trivial
- **Evidence:** `app/src/androidTest/` contains only the template `ExampleInstrumentedTest.kt`.
- **Why it matters:** the flows most likely to regress from agent edits — onboarding routing,
  edit/read-only switching, back navigation after save — have no automated check above the
  ViewModel.
- **Fix:** a few Compose UI tests for the critical paths (onboarding → main; add entry → appears on
  main; entry past 24h shows read-only). **Effort:** M

### A8 No Baseline Profile / Macrobenchmark — Medium (defer until store release)
- **Rule:** build-performance — **[SR]** Baseline Profiles and Macrobenchmark
- **Evidence:** no `baselineprofile`/`profileinstaller` dependency or module.
- **Why it matters:** slower cold start and early-session jank; low priority while the app is
  sideload-only.
- **Fix:** add a `:baselineprofile` module before a Play Store release. **Effort:** M

### A9 Data layer reads the wall clock directly — Low
- **Rule:** data-domain — inject time/dispatcher dependencies so tests control them
- **Evidence:** `Instant.now()` at `data/repository/HabitRepositoryImpl.kt:51, 79, 100, 106, 116, 150`
  and `data/repository/JournalRepositoryImpl.kt:44, 61, 67, 87`, while use cases inject `Clock`.
- **Fix:** inject the existing `Clock` into both repositories. **Effort:** S

### A10 Modifier not the first optional parameter — Low
- **Rule:** compose — **[API]** `modifier` is the first optional parameter (lint `ModifierParameter`)
- **Evidence:** `ui/auth/GoogleSignInLaunchButton.kt:32`,
  `core/designsystem/…/pickers/DsDateStrip.kt:44`.
- **Fix:** move `modifier` before `text` / `interactive`. **Effort:** S

### A11 "No Google account on device" shown as a generic failure — Low
- **Rule:** lint `CredentialManagerMisuse`
- **Evidence:** `ui/auth/GoogleSignInLaunchButton.kt:61` — `NoCredentialException` falls into the
  generic `GetCredentialException` → `onFailed()` branch.
- **Fix:** catch `NoCredentialException` first and show a specific message (or fall back to
  adding an account). **Effort:** S

### A12 Lint housekeeping — Low
- **Evidence (lint):** 17 `UnusedResources`, 13 `GradleDependency` + 7 `NewerVersionAvailable`,
  `OldTargetApi` (targetSdk 36 vs compileSdk 37), `ObsoleteSdkInt`, `PluralsCandidate`,
  `RedundantLabel`, `AndroidGradlePluginVersion`.
- **Fix:** remove unused resources; batch dependency bumps via Dependabot PRs; decide on targetSdk 37.
  **Effort:** S

### A13 `@Immutable` on every UI state, including list holders — Low
- **Rule:** compose — strong skipping is default since Kotlin 2.0.20; be cautious annotating models
  with large collections
- **Evidence:** 21 annotated classes, e.g. `ui/habit/list/HabitListUiState.kt:7`,
  `ui/journal/list/JournalListUiState.kt:8` (lists rendered in `LazyColumn`).
- **Fix:** no urgent change; stop adding it by default, keep it only where value-equality skipping
  is measured to help. **Effort:** S

## Declared conventions worth revisiting

These come from `CLAUDE.md`, `lessons.md` and agent memory. None is declared under an
`Android guideline overrides` heading yet.

- **Channel-based `UiEvent` flow** (`CLAUDE.md`, `lessons.md`) — contradicts an **[SR]** rule; see A3.
  Recommend replacing the convention, not declaring it.
- **"`modifier: Modifier = Modifier` is always the last parameter"** (`CLAUDE.md`) — contradicts the
  Compose API guideline *and* the code itself, which mostly puts `modifier` first among optional
  params (lint enforces it). Recommend correcting the `CLAUDE.md` line.
- **`@Immutable` on every UI state** (memory) — contradicts strong-skipping guidance; see A13.
- **One use case per action, always** (`CLAUDE.md`) — official guidance makes the domain layer
  optional; 9 of 30 use cases are one-line pass-throughs (`CompleteOnboardingUseCase`,
  `DeleteHabitUseCase`, `DeleteJournalEntryUseCase`, `GetFreshLoggableDatesUseCase`,
  `GetJournalEntryUseCase`, `MarkLocalDataSyncedUseCase`, `ObserveAuthStateUseCase`,
  `ObserveJournalEntriesUseCase`, `ObserveOnboardingStateUseCase`). A legitimate choice — declare it
  as an override if kept.
- **Use cases depend on repositories only** (memory) — official guidance calls use-case-to-use-case
  dependencies normal. Fine as a preference; declare it.
- **MVI `onIntent(Intent)`** (`CLAUDE.md`) — a valid alternative to per-action methods; declare it.

## What's in good shape

- **Navigation 3:** keys carry IDs only (`ui/TodayWasKey.kt`); both entry decorators wired
  (`ui/TodayWasApp.kt:45-47`); ViewModels only at screen level; screens navigate via lambdas.
- **Coroutines:** every `catch (e: Exception)` rethrows `CancellationException` first
  (`data/util/RemoteCall.kt`, `data/util/SafeDbCall.kt`, `AuthRepositoryImpl`, `AiAssistRepositoryImpl`,
  `OnboardingRepositoryImpl`); no `GlobalScope`, `runBlocking`, or hardcoded dispatchers; Room's
  suspend APIs used directly.
- **ViewModels:** no `Context`/`Application`/`AndroidViewModel`; state collected with
  `collectAsStateWithLifecycle` (no `collectAsState()` anywhere).
- **Layers:** no `data.*` imports from `ui/`; data sources named by role and used only by repositories.
- **Tests:** every ViewModel and repository implementation has a test class; 397 tests pass;
  no mocking library — fakes only; Robolectric confined to Room DAO/integration tests.
- **Compose:** user-facing strings in resources (literals only in previews); previews in `DsTheme`;
  `modifier` placement correct in all but two composables.
- **Release:** R8 optimization enabled for release builds.

## Suggested fix order

Each item is one small PR you can review on its own.

1. **A1** — replace the three `LocalDate.ofInstant` calls. Verify: `lintDebug` shows 0 errors; open
   the main screen and log a check-in on an API 30 emulator.
2. **A2** — add `lintDebug` to CI. Verify: the CI run shows the lint step green.
3. **A4** — enforce the edit window against the stored `createdAt`. Verify: new unit tests for a
   stale/forged `createdAt` on both journal entries and check-ins.
4. **A6 + A10** — lazy list keys and the two modifier positions. Verify: lint `ModifierParameter`
   gone; re-sort the lists manually.
5. **Conventions** — update `CLAUDE.md`/`lessons.md` for the items above and add an
   `Android guideline overrides` section for the ones you keep (MVI, use-case policy), so new code
   stops reproducing A3/A13.
6. **A3 + A5**, one screen per PR, simplest first (`OnboardingChoice`, `CreateHabit`), `Main` last.
   Verify: ViewModel tests updated per screen; manual rotate-during-save check.
7. **A9, A11, A12** — small cleanups, batchable.
8. **A7** UI tests for the critical paths; **A8** baseline profile before any store release.

## Status — fixed on branch `fix/android-audit`

| Finding | Status |
|---|---|
| A1 `LocalDate.ofInstant` crash | Fixed — `atZone(...).toLocalDate()`; lint reports 0 errors |
| A2 Lint not in CI | Fixed — `lintDebug` step added to the `android` job |
| A3 Channel events | Fixed — all 13 ViewModels; navigation-only taps call lambdas, outcomes are `pendingNavigation` / `userMessage` state |
| A4 Edit window vs caller timestamp | Fixed — both use cases read stored data; dead `UpdateHabitCheckInUseCase` removed; `SaveHabitCheckInsUseCaseTest` added |
| A5 `stateIn` | Fixed for the stream-derived screens (Main, JournalList, HabitList, LogHabitCheckIns; HabitDetail already was). Form-driven screens (Account, onboarding, Add/Edit entry, JournalEntryDetail) keep a `MutableStateFlow` — their state is mostly local input, which the guidance allows |
| A6 Lazy list keys | Fixed |
| A7 UI/navigation tests | **Open** — needs an emulator or a Robolectric Compose test setup |
| A8 Baseline Profile | **Deferred** until a store release |
| A9 Clock in repositories | Fixed |
| A10 Modifier order | Fixed |
| A11 No Google account message | Fixed — new `NO_GOOGLE_ACCOUNT` error |
| A12 Lint housekeeping | Fixed — unused colors/strings removed, plurals for the sync summary, redundant label dropped. Left: launcher concept drawables (kept on purpose), dependency bumps (Dependabot), targetSdk 37 decision, `mipmap-anydpi-v26` (Image Asset regenerates it) |
| A13 `@Immutable` everywhere | Convention changed in `CLAUDE.md`; existing annotations left in place |
| Conventions | `CLAUDE.md` updated (events, modifier order, stability) with an `Android guideline overrides` section; three lessons appended to `lessons.md` |
