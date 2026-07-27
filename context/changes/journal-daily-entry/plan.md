# Journal Daily Entry Implementation Plan

## Overview

Implement S-02 (the project's north star): a user adds a free-text journal entry for today (or
yesterday, if not yet logged), sees it in a list on the main screen, and can open a past entry to
read it back. This is the first slice to give `MainScreen` real content and a real `MainViewModel`
— both were left stateless/deleted after S-01's onboarding redesign.

## Current State Analysis

- `MainScreen.kt` is fully stateless: `TodayWasScaffold` + `TodayWasTopBar` + a static empty-state
  `TodayWasText`. No ViewModel exists.
- `TodayWasDatabase` (`data/local/TodayWasDatabase.kt`) is at `version = 1` with a single entity,
  `UserPreferencesEntity` (the onboarding singleton row). An archived migration note requires new
  entities to extend this same database, not create a second one.
- `Focus` (`domain/model/Focus.kt`: `JOURNAL`/`HABIT`/`BOTH`, picked during onboarding) has never
  been read by any screen — `RootViewModel` only checks `OnboardingState.completed` for routing.
  S-02 is the first consumer of the actual `Focus` value.
- Root navigation (`ui/TodayWasKey.kt`, `ui/TodayWasApp.kt`) uses Navigation 3 with exactly two
  keys today (`OnboardingKey`, `MainKey`) in one root `NavDisplay`/back stack.
- `:core:designsystem` has 10 thin Material3 wrapper components (button, text, scaffold, top bar,
  text button, button-with-loading, radio option, loading indicator, icon, icon button) — no text
  input component and nothing resembling a list item exist yet.
- The established MVI pattern (from `ui/onboarding/`): one `@HiltViewModel` per screen, a single
  `fun onIntent(intent: XxxIntent)` dispatching a sealed `XxxIntent`, state in a
  `MutableStateFlow`/`asStateFlow()`, one-shot navigation/effects via a `Channel`-backed
  `XxxUiEvent` flow, every state/intent/event type in its own file under `ui/<feature>/`.
- Repository writes use a retry-once-then-fail pattern (`OnboardingRepositoryImpl`), a
  fake-repository (not mocking-framework) test pattern with `UnconfinedTestDispatcher`, and thin
  use cases (`operator fun invoke`, business rules living in the use case not the repository).

### Key Discoveries:

- `data/local/DatabaseModule.kt`: `Room.databaseBuilder(...).fallbackToDestructiveMigration(dropAllTables = true).build()` — no real `Migration` objects exist. Kept as-is per explicit
  product decision below (see Critical Implementation Details).
- `data/repository/OnboardingRepositoryImpl.kt`: the retry-once write pattern to mirror exactly
  for `JournalRepositoryImpl`.
- `ui/onboarding/OnboardingScreen.kt`: the screen-wiring convention to mirror — `hiltViewModel()`
  default param, `collectAsStateWithLifecycle()`, a `LaunchedEffect(Unit) { viewModel.events.collect { ... } }`
  for one-shot events, a stateless `XxxScreenContent(uiState, onIntent)` split out for
  previewability.
- `context/foundation/lessons.md`: "a flow gets its own ViewModel once it's a screen, not an
  overlay" — applies to the new Add-entry and detail screens (their own `ui/journal/` package,
  not folded into `MainViewModel`).

## Desired End State

A user with `Focus == JOURNAL` or `BOTH` sees a Journal section on the main screen listing their
past entries (most recent first) and an "Add entry" action (hidden once both today and yesterday
are already logged). Tapping "Add entry" opens a full screen to write today's (or, if today's
already logged, yesterday's) entry and save it; tapping a past entry in the list opens a read-only
detail screen showing its date and text. A user with `Focus == HABIT` sees a placeholder instead
of the journal section (nothing else exists to show yet). All data persists across app restarts
with no account required.

**Verification**: fresh install → pick Journal or Both focus → main screen shows an empty Journal
section + "Add entry" → add today's entry → it appears in the list immediately → relaunch → it's
still there → tap it → detail screen shows the same text → back → "Add entry" now offers only
"Yesterday" → add it → both slots gone → "Add entry" disappears until tomorrow.

## What We're NOT Doing

- **Editing entries** (S-04) — the detail view is read-only; no edit controls, no 24h-window logic
  yet. `createdAt` is stored now so S-04 doesn't need a schema migration just to add it.
- **Habit tracking** (S-03) — the `HABIT`-only main-screen placeholder has no real content behind
  it; nothing habit-related is built.
- **Bottom navigation / multi-tab shell** — deferred per explicit product decision. `MainScreen`
  stays the single root destination; the Journal section is built as its own composable so it can
  be promoted to a tab later with minimal rework.
- **Contribution-style intensity grid** (FR-011/S-05) — no GitHub-style heatmap in this slice; the
  main screen shows a plain list, not a grid.
- **Rich text formatting** — plain multi-line text only.
- **Backfilling beyond yesterday** — no open date picker; only "today" and "yesterday" are ever
  addable, and only while unlogged.
- **A real Room `Migration`** — destructive migration is kept (see Critical Implementation
  Details).
- Account creation/sync (F-01/S-06) and AI-assist (F-02/S-07/S-08) — untouched, out of scope.

## Implementation Approach

Mirror S-01's layering exactly: Data → Domain → Presentation → UI, one phase each. The main new
architectural piece is `MainViewModel` combining two existing/new observation sources
(`ObserveOnboardingStateUseCase` for `Focus`-gating, `ObserveJournalEntriesUseCase` for the list)
into one `MainUiState`, and computing which dates are still addable itself (no separate use case —
this is simple presentation-layer derivation, not a business rule). The Add-entry and detail
screens are reached via two new `TodayWasKey` entries pushed onto the *same* root back stack
`TodayWasApp` already manages — no nested navigation needed yet.

## Critical Implementation Details

- **The detail screen carries a data snapshot, not an ID.** `JournalEntryDetailKey` holds
  `date`/`text`/`createdAt` directly rather than an entry ID the destination re-fetches. This is
  intentional and safe *because* entries are immutable in this slice (no edit capability exists
  yet) — `MainScreen` already has the full entry in memory when the user taps it, so re-querying
  Room for read-only, unchanging data would be pure overhead. Revisit this once S-04 adds editing
  (a live entry could change while its detail screen is open) — an ID-based, observed approach
  will be worth the switch then.
- **Addable dates resolve to actual calendar dates only inside `AddJournalEntryUseCase`, not
  earlier.** `MainViewModel`, the nav key, and `AddJournalEntryUiState` all carry a
  `JournalDateSlot` (`TODAY`/`YESTERDAY`) enum, never a concrete `LocalDate` — the enum is resolved
  to `LocalDate.now()`/`.minusDays(1)` only at the moment of insert, inside the use case. This
  avoids a stale date surviving navigation (e.g. a user opening the add-entry screen right before
  midnight and saving just after it) and keeps "what date does 'today' mean" defined in exactly one
  place.
- **Destructive migration is a deliberate, revisited-and-kept decision, not an oversight.**
  `TodayWasDatabase` bumps to `version = 2` with `JournalEntryEntity` added, but
  `fallbackToDestructiveMigration` stays as the resolution strategy — this now risks wiping real
  user journal content on a future schema change (unlike S-01's onboarding singleton), which was
  weighed explicitly against writing a real `Migration` and decided against for now, given the
  3-week timeline. Flag this again before the app ships.

## Phase 1: Data layer (Room)

### Overview

Add the journal entity/DAO to the existing database, and a repository following the established
retry-once-then-fail write pattern.

### Changes Required:

#### 1. Entity + DAO

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/JournalEntryEntity.kt`,
`app/src/main/java/pl/luczka/todaywas/data/local/JournalEntryDao.kt`

**Intent**: One row per logged day. `date` is stored as an ISO-8601 string
(`LocalDate.toString()`), `createdAt` as epoch millis (for S-04's future edit window).

**Contract**: `@Entity(tableName = "journal_entries", indices = [Index(value = ["date"], unique = true)]) data class JournalEntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String, val text: String, val createdAt: Long)`.
`JournalEntryDao`: `fun observeAll(): Flow<List<JournalEntryEntity>>` (`@Query("SELECT * FROM journal_entries ORDER BY date DESC")`), `suspend fun insert(entity: JournalEntryEntity)` (`@Insert`) — a plain insert, not an upsert, since the UI never offers re-adding an already-logged date (see `MainUiState.addableSlots` in Phase 3); the unique index on `date` is a DB-level safety net, not the primary enforcement mechanism.

#### 2. Database + DI wiring

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/TodayWasDatabase.kt`,
`app/src/main/java/pl/luczka/todaywas/data/local/DatabaseModule.kt`

**Intent**: Register the new entity on the existing database (per the S-01 migration note) and
provide its DAO.

**Contract**: `@Database(entities = [UserPreferencesEntity::class, JournalEntryEntity::class], version = 2, exportSchema = false)`; add `abstract fun journalEntryDao(): JournalEntryDao`. `DatabaseModule` gains `@Provides fun provideJournalEntryDao(database: TodayWasDatabase): JournalEntryDao = database.journalEntryDao()`, mirroring the existing `provideUserPreferencesDao`.

#### 3. Domain model + Repository

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/JournalEntry.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepository.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/RepositoryModule.kt`

**Intent**: `JournalEntry(id: Long, date: LocalDate, text: String, createdAt: Instant)` — the
domain-facing shape, mapped from `JournalEntryEntity` (`LocalDate.parse`/`Instant.ofEpochMilli`).
`JournalRepository` exposes `observeEntries()`/`addEntry(date, text)`; `JournalRepositoryImpl`
reuses the exact retry-once-then-fail write pattern from `OnboardingRepositoryImpl` (try insert,
catch `CancellationException` and rethrow, catch other `Exception` and retry once, `Result.failure`
if the retry also fails). `RepositoryModule` gains a second `@Binds` method binding
`JournalRepositoryImpl` to `JournalRepository`.

**Contract**: `interface JournalRepository { fun observeEntries(): Flow<List<JournalEntry>>; suspend fun addEntry(date: LocalDate, text: String): Result<Unit> }`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`
- Robolectric round-trip test passes for `JournalEntryDao` (two `TodayWasDatabase` instances
  sharing a file, mirroring `TodayWasDatabaseTest`'s existing pattern)
- Repository retry-once-then-fail test passes for `JournalRepositoryImpl` (fake DAO, same shape as
  `OnboardingRepositoryImplTest`)

#### Manual Verification:

- None — pure data-layer phase, fully covered by automated tests.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Phase 2: Domain (use cases)

### Overview

Thin use cases wrapping the repository, with the slot→date resolution rule living here (see
Critical Implementation Details).

### Changes Required:

#### 1. Use cases

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/JournalDateSlot.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/usecase/ObserveJournalEntriesUseCase.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/usecase/AddJournalEntryUseCase.kt`

**Intent**: `JournalDateSlot` (`TODAY`, `YESTERDAY`) is the domain concept both `MainViewModel`
and the add-entry flow reason about — never a raw `LocalDate` outside this use case.
`ObserveJournalEntriesUseCase` is a thin passthrough (no dedicated unit test, matching
`ObserveOnboardingStateUseCase`'s precedent). `AddJournalEntryUseCase` resolves the slot to
`LocalDate.now()` or `LocalDate.now().minusDays(1)` before calling `repository.addEntry(...)`.

**Contract**: `class AddJournalEntryUseCase @Inject constructor(private val repository: JournalRepository) { suspend operator fun invoke(slot: JournalDateSlot, text: String): Result<Unit> }`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `AddJournalEntryUseCase` test verifies `TODAY`/`YESTERDAY` resolve to the correct `LocalDate`
  and pass through to `repository.addEntry`

#### Manual Verification:

- None — pure domain-layer phase, fully covered by automated tests.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Phase 3: Presentation (ViewModels)

### Overview

A real `MainViewModel` for the first time, plus a new `AddJournalEntryViewModel` in its own
`ui/journal/` package per the "own screen, own ViewModel" lesson. The detail screen needs no
ViewModel (see Critical Implementation Details).

### Changes Required:

#### 1. Main screen state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/main/MainIntent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/main/MainUiEvent.kt`

**Intent**: Recreates these three files (deleted in S-01's cleanup once `MainScreen` went
stateless), this time with real content.

**Contract**: `MainUiState(focus: Focus?, journalEntries: List<JournalEntry>, addableSlots: List<JournalDateSlot>)`. `MainIntent`: `AddEntryClicked`, `JournalEntryClicked(entry: JournalEntry)`. `MainUiEvent`: `NavigateToAddEntry(availableSlots: List<JournalDateSlot>)`, `NavigateToJournalDetail(entry: JournalEntry)`.

#### 2. MainViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Combines `ObserveOnboardingStateUseCase` (for `focus`) and
`ObserveJournalEntriesUseCase` (for the list) into one reactive `MainUiState`, deriving
`addableSlots` from the entries list each time it updates (`TODAY` included unless an entry dated
today already exists; `YESTERDAY` included unless one dated yesterday already exists).
`onIntent` only ever sends navigation events — no local state to mutate on click.

**Contract**: `@HiltViewModel class MainViewModel @Inject constructor(private val observeOnboardingState: ObserveOnboardingStateUseCase, private val observeJournalEntries: ObserveJournalEntriesUseCase) : ViewModel()`, exposing `uiState: StateFlow<MainUiState>` and `events: Flow<MainUiEvent>` (`Channel`-backed), same shape as `OnboardingViewModel`.

#### 3. Add-entry screen state + ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryIntent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryUiEvent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModel.kt`

**Intent**: `availableSlots` arrives from the nav key (computed by `MainViewModel` before
navigating — the add-entry flow never independently re-derives it). `selectedSlot` defaults to
the first available slot. Saving calls `AddJournalEntryUseCase`, toggling `isSaving`/`saveError`
around the call exactly like `OnboardingViewModel.onConfirmFocus()`; success sends `Saved`, cancel
sends `Cancelled` — both close the screen from the caller side.

**Contract**: `AddJournalEntryUiState(availableSlots: List<JournalDateSlot>, selectedSlot: JournalDateSlot, text: String, isSaving: Boolean, saveError: Boolean)`. `AddJournalEntryIntent`: `SlotSelected(slot)`, `TextChanged(text)`, `SaveClicked`, `CancelClicked`. `AddJournalEntryUiEvent`: `Saved`, `Cancelled`. `@HiltViewModel class AddJournalEntryViewModel @Inject constructor(private val addJournalEntry: AddJournalEntryUseCase) : ViewModel()` — takes `availableSlots` as a plain constructor parameter via Hilt's assisted-injection (`@AssistedInject`/`@AssistedFactory`), the standard pattern for passing Navigation 3 key data into a Hilt-provided ViewModel in this codebase (no `SavedStateHandle` auto-wiring exists here, unlike AndroidX Navigation-Compose).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `MainViewModelTest` covers: focus-gating value passthrough, `addableSlots` derivation (both
  slots open / one open / none open), `AddEntryClicked` and `JournalEntryClicked` emit the correct
  events
- `AddJournalEntryViewModelTest` covers: default `selectedSlot`, `TextChanged`, save
  success/failure (`isSaving`/`saveError`), `Saved`/`Cancelled` events

#### Manual Verification:

- None — pure presentation-layer phase, fully covered by automated tests.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Phase 4: UI

### Overview

One new design-system component (`TodayWasTextField`), the Journal section on `MainScreen`, the
two new screens, and the Navigation 3 wiring connecting them all.

### Changes Required:

#### 1. Design system

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasTextField.kt`

**Intent**: Multi-line text input wrapper — nothing like it exists yet.

**Contract**: `TodayWasTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, enabled: Boolean = true, minLines: Int = 1)` wrapping `OutlinedTextField`. `@PreviewLightDark` per the established lesson.

#### 2. Root navigation

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasKey.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Two new keys pushed onto the existing root back stack; `MainScreen` gains two
callback params wired from `TodayWasApp`'s `entryProvider`, mirroring how `OnboardingScreen`
already receives `onFinished`.

**Contract**: `@Serializable data class AddJournalEntryKey(val availableSlots: List<JournalDateSlot>) : TodayWasKey`; `@Serializable data class JournalEntryDetailKey(val date: String, val text: String, val createdAt: Long) : TodayWasKey` (snapshot fields, not an ID — see Critical Implementation Details). `entry<MainKey>` now passes `onAddEntryClicked`/`onJournalEntryClicked` lambdas that `backStack.add(...)` the two new keys; `entry<AddJournalEntryKey>`/`entry<JournalEntryDetailKey>` both pop via `backStack.removeLastOrNull()` on their respective close callbacks.

#### 3. Main screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Replaces the static empty state with: a Journal section (title, `LazyColumn` list of
entries most-recent-first — each row showing the date and a truncated text preview, tappable — and
an "Add entry" `TodayWasButton` shown only when `addableSlots` isn't empty) when `focus` is
`JOURNAL`/`BOTH`; a placeholder `TodayWasText` when `focus == HABIT`. Handles `viewModel.events` in
a `LaunchedEffect` exactly like `OnboardingScreen`, calling the new `onAddEntryClicked`/
`onJournalEntryClicked` params.

**Contract**: `MainScreen(onAddEntryClicked: (List<JournalDateSlot>) -> Unit, onJournalEntryClicked: (JournalEntry) -> Unit, viewModel: MainViewModel = hiltViewModel())`.

#### 4. Add-entry screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt`

**Intent**: `TodayWasTopBar` with a back icon (`onCancelled`), a `TodayWasRadioOption` pair for
Today/Yesterday shown only when both slots are available (single-slot case just labels which day
it's for, no toggle), a `TodayWasTextField` for the entry text, and a `TodayWasButtonWithLoading`
save action (disabled while `text` is blank or `isSaving`).

**Contract**: `AddJournalEntryScreen(availableSlots: List<JournalDateSlot>, onSaved: () -> Unit, onCancelled: () -> Unit, viewModel: AddJournalEntryViewModel = ...)`.

#### 5. Detail screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailScreen.kt`

**Intent**: Stateless (no ViewModel) — `TodayWasTopBar` with a back icon, the date formatted for
display, and the entry text.

**Contract**: `JournalEntryDetailScreen(date: String, text: String, onBack: () -> Unit)`.

#### 6. Strings

**File**: `app/src/main/res/values/strings.xml`

**Intent**: New `main_journal_*`/`journal_*` keys per the established `<screen>_<element>`
naming convention — section title, empty-state text, add-entry CTA, habit-focus placeholder,
Today/Yesterday labels, text field label, save CTA, detail screen title.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Fresh install, focus = Journal or Both: main screen shows an empty Journal section + "Add
  entry"; focus = Habit: placeholder shown instead, no "Add entry".
- Tapping "Add entry" with neither slot logged shows a Today/Yesterday choice defaulting to
  Today; saving adds it to the list immediately.
- Relaunching the app shows the same entries (persistence).
- Tapping a list entry opens the detail screen showing the same date/text, with no edit controls;
  back returns to the main screen.
- After today is logged, "Add entry" offers only Yesterday (no toggle); after both are logged,
  "Add entry" disappears entirely.
- Forcing a write failure on save shows an inline error and the save button re-enables for retry.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- Room round-trip for `JournalEntryDao` (Robolectric, two DB instances sharing a file).
- `JournalRepositoryImpl` retry-once-then-fail (fake DAO, failure-count-controlled).
- `AddJournalEntryUseCase` slot→date resolution.
- `MainViewModel`: focus passthrough, `addableSlots` derivation in all three states (both open,
  one open, none open), intent→event mapping.
- `AddJournalEntryViewModel`: default slot selection, text updates, save success/failure, saved/
  cancelled events.

### Integration Tests:

- None — out of scope per this repo's "unit tests only" testing-scope convention (established in
  S-01).

### Manual Testing Steps:

1. Fresh install → pick Journal (or Both) focus during onboarding → main screen shows empty
   Journal section + "Add entry".
2. Tap "Add entry" → Today is the only/default option → write an entry → save → confirm it
   appears in the list immediately.
3. Relaunch the app → confirm the entry is still there.
4. Tap the entry in the list → confirm the detail screen shows the same date and text, no edit
   controls → back → confirm still on the main screen.
5. Tap "Add entry" again → confirm only "Yesterday" is offered (no toggle, since Today is now
   logged) → save it → confirm both entries show in the list.
6. Tap "Add entry" a third time → confirm the button/entry point is gone entirely (both slots
   logged).
7. Force a write failure on save (e.g. a temporary throw in the DAO) → confirm an inline error
   appears and retry succeeds once the forced failure is removed.
8. Pick Habit-only focus on a separate fresh install → confirm the main screen shows the
   habit-placeholder instead of the Journal section, with no "Add entry" anywhere.

## Performance Considerations

Negligible at MVP scale — a small, locally-stored list via Room `Flow`, no pagination needed yet.

## Migration Notes

`TodayWasDatabase` moves from `version = 1` to `version = 2` via `fallbackToDestructiveMigration`
(no real `Migration` object) — a deliberate, explicitly-revisited decision given the 3-week
timeline; see Critical Implementation Details. Revisit before the app ships, and again whenever
S-03 adds its own entity to this same database.

## References

- Roadmap: `context/foundation/roadmap.md` (`S-02: Daily journal entry (north star)`)
- PRD: `context/foundation/prd.md` (US-01, FR-003, FR-005, FR-008)
- Prior slice: `context/archive/2026-07-25-onboarding-focus-pick/plan.md` (S-01 — data/domain/
  presentation/UI layering, retry-once write pattern, MVI shape, all reused as-is)
- Project conventions: `AGENTS.md`, `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: Data layer (Room)

#### Automated

- [x] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 9eed819
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck` — 9eed819
- [x] 1.3 Debug build compiles: `./gradlew.bat assembleDebug` — 9eed819
- [x] 1.4 Robolectric round-trip test passes for `JournalEntryDao` — 9eed819
- [x] 1.5 Repository retry-once-then-fail test passes for `JournalRepositoryImpl` — 9eed819

### Phase 2: Domain (use cases)

#### Automated

- [x] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — f420d67
- [x] 2.2 Lint passes: `./gradlew.bat ktlintCheck` — f420d67
- [x] 2.3 `AddJournalEntryUseCase` test passes — f420d67

### Phase 3: Presentation (ViewModels)

#### Automated

- [x] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [x] 3.2 Lint passes: `./gradlew.bat ktlintCheck`
- [x] 3.3 `MainViewModelTest` passes (focus-gating, addableSlots derivation, intent→event mapping)
- [x] 3.4 `AddJournalEntryViewModelTest` passes (slot selection, save success/failure, events)

### Phase 4: UI

#### Automated

- [ ] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 4.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 4.3 Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 4.4 Main screen shows Journal section + "Add entry" (Journal/Both focus) or placeholder
      (Habit focus)
- [ ] 4.5 Add-entry flow (Today default) saves and appears in the list immediately
- [ ] 4.6 Entries persist across relaunch
- [ ] 4.7 Tapping a list entry opens the read-only detail screen with matching date/text
- [ ] 4.8 "Add entry" narrows to Yesterday-only after Today is logged, then disappears after both
      are logged
- [ ] 4.9 Forced write-failure shows an inline error and retry succeeds
