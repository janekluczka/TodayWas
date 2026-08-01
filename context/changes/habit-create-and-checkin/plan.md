# Habit Creation and Check-in Implementation Plan

## Overview

Implement S-03: a user creates a habit (binary done/not-done, or a user-defined numeric scale) and
logs check-ins for one or more habits at once, for today or any of the last 7 days, independent of
journaling. Habits and their today-status appear on the main screen alongside the existing Journal
section. This slice also retrofits the journal Add-entry flow's data-passing pattern (see
"Architecture change" below) so both flows share one consistent approach going forward.

## Current State Analysis

- `TodayWasDatabase` is at `version = 2` with `UserPreferencesEntity` + `JournalEntryEntity`.
  `fallbackToDestructiveMigration(dropAllTables = true)` is still the migration strategy — no real
  `Migration` exists.
- `JournalEntryDao.insert` relies on a unique index on `date` and throws on conflict (no upsert).
  The retry-once-then-fail write pattern (`OnboardingRepositoryImpl`, `JournalRepositoryImpl`) is
  the established resilience contract for all repository writes.
- `Focus`/`FocusUiState` already model `JOURNAL`/`HABIT`/`BOTH`, but the main screen's `HABIT`
  branch is currently a placeholder string, and the `BOTH` branch renders the Journal section only
  — no habit content exists anywhere yet.
- `MainFab` (`ui/main/MainScreen.kt`) is genuinely expand-ready: it already renders one
  `TodayWasExtendedFloatingActionButton` per entry in `FabActionUiState` when there's more than
  one, with an explicit comment noting it activates automatically once a second action exists.
  `FabActionUiState` currently has exactly one value, `ADD_JOURNAL_ENTRY`.
- `AddJournalEntryKey(availableSlots: List<JournalDateSlot>)` carries pre-computed data from
  `MainViewModel` into `AddJournalEntryViewModel` via `@AssistedInject`/`@AssistedFactory`.
  `MainViewModel` derives `addableSlots` from `ObserveJournalEntriesUseCase`'s output itself, today.
- The journal date picker (`DayStrip`/`DayCard` in `AddJournalEntryScreen.kt`) is private to that
  file and hardcoded to a 2-value `JournalDateSlotUiState` (`TODAY`/`YESTERDAY`) universe, using an
  infinite (`Int.MAX_VALUE`-page) `HorizontalPager` keyed by epoch-day to get a centered,
  swipeable "clock pager" feel. Not reusable as-is for a 7-day habit backfill window.
- No dialog, slider, stepper, or number-input component exists anywhere in `:core:designsystem`.
- No mocking framework is used anywhere in the test suite — all repository/use-case tests use
  hand-written fakes (`FakeDao`/`FakeRepository` implementing the real interface) with
  `kotlinx.coroutines.test.runTest`; Room DAO tests use Robolectric with a real, file-backed
  `TodayWasDatabase` instance (unique filename per test), not an in-memory/fake builder.

### Key Discoveries:

- `data/repository/JournalRepositoryImpl.kt`, `OnboardingRepositoryImpl.kt`: identical
  try/retry-once/catch-`Result.failure` shape — the exact structure `HabitRepositoryImpl` mirrors
  for every write method.
- `ui/main/MainScreen.kt`'s `MainFab` composable and `FabActionUiState` enum: the only two places
  that need touching to add new FAB actions — `MainFab` itself needs no changes.
- `app/src/main/res/values/strings.xml`: confirmed `<screen>_<element>` naming convention
  (`main_*`, `journal_*`, `focus_*`) — `habit_*` and `main_habit_*` follow the same shape.
- Room supports an atomic multi-row insert via a `@Transaction`-annotated default-body DAO method
  that calls a single-row `@Insert` method in a loop — this is the documented pattern for
  all-or-nothing batch writes and is what `HabitCheckInDao.insertAll` uses (see Critical
  Implementation Details).

## Desired End State

A user with `Focus == HABIT` or `BOTH` sees a Habit section on the main screen listing every habit
with its today status (done/not-done, a scale value, or not yet logged). The FAB offers "Create
habit" (always available when focus includes habits) and, once at least one habit exists, "Log
check-ins" — which opens a single screen with a 7-day date strip (today back to 6 days prior) and
one row per habit for the selected date: unlogged habits get an input (a stepper for scale habits,
a done/not-done toggle for binary habits), already-logged habits show read-only with a green "Done"
label. Saving writes all newly-entered values for that day in one all-or-nothing batch. A user with
`Focus == JOURNAL` sees no habit UI at all. All data persists locally, no account required.

**Verification**: fresh install → pick Habit (or Both) focus → main screen shows an empty Habit
section + "Create habit" (no "Log check-ins" yet, no habits exist) → create a binary habit and a
scale habit (range 1–5) → both appear in the Habit list as not-logged-today → tap "Log check-ins" →
today's strip shows both habits with inputs → toggle the binary one, set the scale one to 3 → save
→ both now show today's value on the main screen immediately → relaunch → still there → tap "Log
check-ins" again → today's row for both habits now reads read-only "Done" → swipe the strip back
one day → log a backfilled value for one habit → save → confirm it appears in that habit's history
(no detail view yet — verified via re-opening "Log check-ins" on that date, which now shows it
read-only too).

## What We're NOT Doing

- **Editing or deleting a habit, or an existing check-in** (deferred; S-04 owns the 24h edit window
  for both journal and habits). Once a habit is created its name/type/range are fixed; once a
  check-in is logged for a habit+date it can't be changed in this slice.
- **Backfill beyond 7 days** — the date strip only ever covers today and the 6 preceding days.
- **A detailed contribution-style history view per habit** (FR-011/S-05) — the main screen shows
  only today's status per habit, not a heatmap or list of past values.
- **A real Room `Migration`** — destructive migration is kept (`version` bumps 2 → 3); see
  Migration Notes.
- **Foreign-key constraint between `habit_check_ins` and `habits`** — with no delete/edit in scope,
  referential-integrity enforcement has no failure mode to guard against yet; add it if/when habit
  deletion is built.
- Account creation/sync and AI-assist — untouched, out of scope.

## Implementation Approach

Mirror S-02's layering (Data → Domain → Presentation → UI), one phase each, using the exact
retry-once-then-fail write pattern and thin-use-case shape already established. The one
architectural departure from S-02, applied consistently to both the new habit flow and a retrofit
of the existing journal flow, is described next.

## Critical Implementation Details

- **Screens that need "what's still loggable" derive it themselves via a use case, never receive
  it pre-computed through a nav key.** In S-02, `MainViewModel` computed `addableSlots` and passed
  it into `AddJournalEntryKey`, requiring `AddJournalEntryViewModel` to take it via
  `@AssistedInject`. This slice removes that: a new `ObserveAddableJournalDateSlotsUseCase`
  (mapping `JournalRepository.observeEntries()` directly into a derived `Flow<List<JournalDateSlot>>`
  of not-yet-logged slots) is injected directly into **both** `MainViewModel` (to decide whether the
  "Add journal entry" FAB action shows) and the retrofitted `AddJournalEntryViewModel` (to decide
  which slots to render as inputs). Neither passes data to the other through navigation.
  `AddJournalEntryKey` becomes a plain `data object` with no fields, and
  `AddJournalEntryViewModel` becomes a plain `@HiltViewModel` (no assisted injection at all).
  The same shape applies to habits: a new `ObserveHabitCheckInBoardUseCase` (combining
  `HabitRepository.observeHabits()` + `.observeCheckIns()` directly into one `Flow<HabitCheckInBoard>`)
  is injected into both `MainViewModel` (derives each habit's *today* status) and
  `LogHabitCheckInsViewModel` (derives, per whichever date is currently selected on its date strip,
  which habits are logged vs. still need input) — each ViewModel does its own date-specific
  derivation from the same shared observational use case, since that derivation depends on
  transient UI state (the selected date) the use case itself can't own. Both combinator use cases
  depend directly on their repository, not on another use case, so there's no
  use-case-wrapping-use-case chain — `ObserveHabitsUseCase`/`ObserveHabitCheckInsUseCase` were
  considered but skipped since nothing needs the `Habit`/`HabitCheckIn` flows individually.
  `CreateHabitKey` and `LogHabitCheckInsKey` are likewise plain `data object`s, and
  `CreateHabitViewModel`/`LogHabitCheckInsViewModel` are plain `@HiltViewModel`s with no
  assisted-injected constructor parameters.
- **All-or-nothing batch check-in save uses Room's `@Transaction`-default-method pattern, not a
  bare `@Insert(list)`.** A plain `@Insert suspend fun insertAll(items: List<HabitCheckInEntity>)`
  does not guarantee that a mid-batch failure leaves zero rows committed. `HabitCheckInDao` instead
  defines a single-row `@Insert` and a `@Transaction`-annotated function with a default body that
  loops over it:
  ```kotlin
  @Dao
  interface HabitCheckInDao {
      @Insert
      suspend fun insertOne(entity: HabitCheckInEntity)

      @Transaction
      suspend fun insertAll(entities: List<HabitCheckInEntity>) {
          entities.forEach { insertOne(it) }
      }
  }
  ```
  `HabitRepositoryImpl.addCheckIns` calls `insertAll` once inside the existing retry-once-then-fail
  shape (retry re-attempts the whole batch, not individual rows).
- **The generalized date-strip component keeps the infinite (epoch-day-indexed) pager — this was
  tried as a finite `HorizontalPager(pageCount = { dates.size })` first and reverted.** A finite
  pager over just the selectable dates (2 for journal, 7 for habits) breaks the centered feel
  whenever the selected date sits at an edge of that set — e.g. selecting YESTERDAY has nothing
  before it to page 0, so it can't visually center; same for day 7 of the habit backfill window.
  `TodayWasDateStrip` therefore keeps `DayStrip`'s original `Int.MAX_VALUE`-page,
  `LocalDate.ofEpochDay(page)`-indexed approach: it always renders however many context days fit
  the available width around `selectedDate`, all disabled except the ones `isSelectable` accepts,
  so a selectable date at the edge of its "real" window still has flanking (non-interactive) days
  either side. The generalization that *is* real: `isSelectable`/`onDateSelected` take an arbitrary
  `LocalDate` predicate/callback instead of a hardcoded `JournalDateSlotUiState` — no `dates: List<LocalDate>`
  parameter exists on the component at all.
- **Scale-range validation is UI-only, not a use-case business rule** — matching the existing
  precedent where `AddJournalEntryScreen` disables Save on blank text with no use-case-level check.
  `CreateHabitUseCase` is a thin passthrough; `CreateHabitScreen` disables Save until name is
  non-blank and, for scale habits, `scaleMin`/`scaleMax` both parse as integers with
  `scaleMin < scaleMax`.

## Phase 1: Data layer (Room)

### Overview

Add `HabitEntity`/`HabitCheckInEntity` + DAOs to the existing database, domain models, and a
`HabitRepository` following the established retry-once-then-fail pattern (plus the all-or-nothing
batch-insert pattern from Critical Implementation Details for check-ins).

### Changes Required:

#### 1. Entities + DAOs

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/HabitEntity.kt`,
`app/src/main/java/pl/luczka/todaywas/data/local/HabitCheckInEntity.kt`,
`app/src/main/java/pl/luczka/todaywas/data/local/HabitDao.kt`,
`app/src/main/java/pl/luczka/todaywas/data/local/HabitCheckInDao.kt`

**Intent**: One row per habit; one row per habit+date check-in, rejecting duplicate re-logs at the
DB level (mirroring journal's unique-date approach, not upsert — see plan-brief for the reasoning).

**Contract**: `@Entity(tableName = "habits") data class HabitEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val description: String?, val type: String, val scaleMin: Int?, val scaleMax: Int?, val createdAt: Long)` — `type` stores `HabitType.name`. `@Entity(tableName = "habit_check_ins", indices = [Index(value = ["habitId", "date"], unique = true)]) data class HabitCheckInEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val habitId: Long, val date: String, val value: Int, val createdAt: Long)` — `date` is `LocalDate.toString()`; `value` is `0`/`1` for binary, the raw scale number otherwise.
`HabitDao`: `fun observeAll(): Flow<List<HabitEntity>>` (`ORDER BY createdAt ASC`), `suspend fun insert(entity: HabitEntity)` (`@Insert`).
`HabitCheckInDao`: `fun observeAll(): Flow<List<HabitCheckInEntity>>` (`SELECT * FROM habit_check_ins`), plus the `insertOne`/`insertAll` pair from Critical Implementation Details.

#### 2. Database + DI wiring

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/TodayWasDatabase.kt`,
`app/src/main/java/pl/luczka/todaywas/data/local/DatabaseModule.kt`

**Intent**: Register both new entities on the existing database and provide their DAOs.

**Contract**: `@Database(entities = [UserPreferencesEntity::class, JournalEntryEntity::class, HabitEntity::class, HabitCheckInEntity::class], version = 3, exportSchema = false)`; add `abstract fun habitDao(): HabitDao` and `abstract fun habitCheckInDao(): HabitCheckInDao`. `DatabaseModule` gains matching `@Provides` functions, mirroring `provideJournalEntryDao`.

#### 3. Domain models + Repository

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/HabitType.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/model/Habit.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/model/HabitCheckIn.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepository.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/RepositoryModule.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/HabitEntityMapper.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/HabitCheckInEntityMapper.kt`

**Intent**: `Habit`/`HabitCheckIn` are the domain-facing shapes, mapped from their entities in
dedicated mapper files (never inline in the repository, per lessons.md). `HabitRepositoryImpl`
builds entities (stamping `createdAt = System.currentTimeMillis()`) and reuses the exact
retry-once-then-fail shape from `JournalRepositoryImpl` for both `createHabit` and `addCheckIns`.
`RepositoryModule` gains a `@Binds` method binding `HabitRepositoryImpl` to `HabitRepository`.

**Contract**: `enum class HabitType { BINARY, SCALE }`. `data class Habit(val id: Long, val name: String, val description: String?, val type: HabitType, val scaleMin: Int?, val scaleMax: Int?, val createdAt: Instant)`. `data class HabitCheckIn(val id: Long, val habitId: Long, val date: LocalDate, val value: Int, val createdAt: Instant)`.
`interface HabitRepository { fun observeHabits(): Flow<List<Habit>>; suspend fun createHabit(name: String, description: String?, type: HabitType, scaleMin: Int?, scaleMax: Int?): Result<Unit>; fun observeCheckIns(): Flow<List<HabitCheckIn>>; suspend fun addCheckIns(date: LocalDate, values: Map<Long, Int>): Result<Unit> }`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`
- Robolectric round-trip test passes for `HabitDao` and `HabitCheckInDao` (two `TodayWasDatabase`
  instances sharing a file, mirroring `JournalEntryDaoTest`)
- A Robolectric test proves `HabitCheckInDao.insertAll` is atomic: a batch containing one row that
  violates the unique `(habitId, date)` index leaves **zero** rows from that batch committed
- Repository retry-once-then-fail tests pass for `HabitRepositoryImpl.createHabit` and
  `.addCheckIns` (fake DAOs, same shape as `JournalRepositoryImplTest`)

#### Manual Verification:

- None — pure data-layer phase, fully covered by automated tests.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Phase 2: Domain (use cases)

### Overview

Thin use cases wrapping the repository, plus two combinator use cases that let `MainViewModel` and
the input-flow ViewModels each derive their own state from the same shared observational source.
Every use case in this phase depends directly on its repository — never on another use case — so
there's no use-case-wrapping-use-case chain to reason about.

### Changes Required:

#### 1. Habit use cases

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/CreateHabitUseCase.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/usecase/LogHabitCheckInsUseCase.kt`

**Intent**: Thin passthroughs — validation lives in the UI layer per Critical Implementation
Details. (No standalone `ObserveHabitsUseCase`/`ObserveHabitCheckInsUseCase` — nothing needs the
`Habit`/`HabitCheckIn` flows individually, only combined via `ObserveHabitCheckInBoardUseCase`
below, so those two would-be passthroughs are skipped rather than left unused.)

**Contract**: `class CreateHabitUseCase @Inject constructor(private val repository: HabitRepository) { suspend operator fun invoke(name: String, description: String?, type: HabitType, scaleMin: Int?, scaleMax: Int?): Result<Unit> }`. `class LogHabitCheckInsUseCase @Inject constructor(private val repository: HabitRepository) { suspend operator fun invoke(date: LocalDate, values: Map<Long, Int>): Result<Unit> }`.

#### 2. Combinator use cases

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/HabitCheckInBoard.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/usecase/ObserveHabitCheckInBoardUseCase.kt`,
`app/src/main/java/pl/luczka/todaywas/domain/usecase/ObserveAddableJournalDateSlotsUseCase.kt`

**Intent**: `ObserveHabitCheckInBoardUseCase` combines `HabitRepository.observeHabits()` +
`.observeCheckIns()` directly into one reactive `Flow<HabitCheckInBoard>`, the single source both
`MainViewModel` and `LogHabitCheckInsViewModel` observe. `ObserveAddableJournalDateSlotsUseCase`
maps `JournalRepository.observeEntries()` directly into a derived `Flow<List<JournalDateSlot>>` (a
slot is addable unless an entry already exists for that resolved date) — the single source both
`MainViewModel` and `AddJournalEntryViewModel` observe, replacing the derivation logic that
currently lives only inside `MainViewModel`. Both inject their repository directly rather than
routing through another use case.

**Contract**: `data class HabitCheckInBoard(val habits: List<Habit>, val checkIns: List<HabitCheckIn>)`. `class ObserveHabitCheckInBoardUseCase @Inject constructor(private val repository: HabitRepository) { operator fun invoke(): Flow<HabitCheckInBoard> }` (built with `combine`). `class ObserveAddableJournalDateSlotsUseCase @Inject constructor(private val repository: JournalRepository) { operator fun invoke(): Flow<List<JournalDateSlot>> }`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `ObserveHabitCheckInBoardUseCase` test verifies the combined flow emits whenever either source
  flow emits, with correct `habits`/`checkIns` contents
- `ObserveAddableJournalDateSlotsUseCase` test verifies slot derivation in all three states (both
  addable, one addable, none addable) — the same three cases `MainViewModelTest` covered in S-02,
  now covered here instead since the derivation moved

#### Manual Verification:

- None — pure domain-layer phase, fully covered by automated tests.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Phase 3: Presentation (ViewModels)

### Overview

Update `MainViewModel` to show habits and gate two new FAB actions, add `CreateHabitViewModel` and
`LogHabitCheckInsViewModel` in a new `ui/habit/` package, and retrofit `AddJournalEntryViewModel` to
self-derive its addable slots instead of receiving them via assisted injection.

**As built**: since `MainScreen.kt`, `AddJournalEntryScreen.kt`, `TodayWasKey.kt`, and
`TodayWasApp.kt` already exist (built in S-02) and reference the contracts changed in this phase
(the old `NavigateToAddEntry(slots)` payload, `MainUiState.addableSlots`, the assisted-injection
factory), this single-module project can't compile — let alone run `testDebugUnitTest` — without
also mechanically updating those four files' call sites. Minimal fixes only were made here (updated
signatures, `when` branches, temporary no-op `onCreateHabitClicked`/`onLogCheckInsClicked` stubs in
`TodayWasApp.kt`); the real UI (habit section rendering, the two new screens, and wiring the stubs
to real nav entries) is still built in Phase 4 as planned.

### Changes Required:

#### 1. Main screen state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/main/MainUiEvent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/model/FabActionUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/model/HabitUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/model/HabitMapper.kt`

**Intent**: `MainUiState` gains a `habits` list (each with its derived today-status). `FabActionUiState`
gains two values so `MainFab`'s existing expand-ready mechanism activates. `HabitMapper` maps a
`Habit` + that habit's check-ins to a `HabitUiState`, deriving today's status in its own dedicated
mapper file per lessons.md (never inline in the ViewModel).

**Contract**: `MainUiState(focus: FocusUiState?, journalEntries: List<JournalEntryUiState>, habits: List<HabitUiState>, fabActions: List<FabActionUiState>, fabExpanded: Boolean)`. `FabActionUiState`: adds `CREATE_HABIT(R.string.main_fab_create_habit)`, `LOG_HABIT_CHECK_INS(R.string.main_fab_log_check_ins)`. `MainUiEvent` gains `NavigateToCreateHabit`, `NavigateToLogHabitCheckIns` (no payload — both destination ViewModels self-derive). `HabitUiState(id: Long, name: String, type: HabitTypeUiState, todayStatus: HabitCheckInStatusUiState)` where `HabitCheckInStatusUiState` is a sealed interface: `NotLogged`, `LoggedBinary(done: Boolean)`, `LoggedScale(value: Int)`.

#### 2. MainViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Adds `ObserveHabitCheckInBoardUseCase` and `ObserveAddableJournalDateSlotsUseCase` to the
existing `combine(...)` alongside `ObserveOnboardingStateUseCase`. Derives `habits` (via
`HabitMapper`, filtering `checkIns` to today's date per habit). Derives `fabActions`: `ADD_JOURNAL_ENTRY`
included when focus includes journal and the addable-slots flow is non-empty (as today, now sourced
from the new use case instead of computing it locally); `CREATE_HABIT` included whenever focus
includes habits; `LOG_HABIT_CHECK_INS` included when focus includes habits **and** `habits` is
non-empty. `onFabActionClicked` gains the two new `when` branches emitting the two new events.

**Contract**: `MainViewModel` constructor gains `observeAddableJournalDateSlots: ObserveAddableJournalDateSlotsUseCase` and `observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase` alongside the existing two dependencies.

#### 3. Create-habit screen state + ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/CreateHabitUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/habit/CreateHabitIntent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/habit/CreateHabitUiEvent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/habit/CreateHabitViewModel.kt`

**Intent**: Plain `@HiltViewModel`, no assisted injection (nothing to receive from the nav key).
Save is valid when name is non-blank and, for `SCALE` type, both range fields parse as integers
with min < max (see Critical Implementation Details) — `CreateHabitScreen` disables its save action
until then, same `isSaving`/`saveError` toggling as `AddJournalEntryViewModel.onConfirmFocus()`-style
save flows.

**Contract**: `CreateHabitUiState(name: String, description: String, type: HabitTypeUiState, scaleMin: String, scaleMax: String, isSaving: Boolean, saveError: Boolean)`. `CreateHabitIntent`: `NameChanged(name)`, `DescriptionChanged(description)`, `TypeChanged(type)`, `ScaleMinChanged(text)`, `ScaleMaxChanged(text)`, `SaveClicked`, `CancelClicked`. `CreateHabitUiEvent`: `Saved`, `Cancelled`. `@HiltViewModel class CreateHabitViewModel @Inject constructor(private val createHabit: CreateHabitUseCase) : ViewModel()`.

#### 4. Log-check-ins screen state + ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsIntent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsUiEvent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsViewModel.kt`

**Intent**: Plain `@HiltViewModel`, observes `ObserveHabitCheckInBoardUseCase()` combined with an
internal `selectedDate` (`MutableStateFlow`, defaulting to today) to derive, per habit, whether the
selected date is already logged (read-only row) or needs input (editable row). Input values for the
currently selected date are held in local state until save. `SaveClicked` calls
`LogHabitCheckInsUseCase(selectedDate, enteredValues)` for whatever's been entered among the
unlogged rows; success re-derives the board reactively (newly-logged rows flip to read-only) and
clears the in-progress input state; failure sets `saveError`.

**Contract**: `LogHabitCheckInsUiState(selectableDates: List<LocalDate>, selectedDate: LocalDate, rows: List<HabitCheckInRowUiState>, isSaving: Boolean, saveError: Boolean)` where `selectableDates` is always the fixed 7-entry today-back-to-6-days-prior list. `HabitCheckInRowUiState` sealed interface: `Editable(habitId, name, value: Int?, range: IntRange, type: HabitTypeUiState)`, `AlreadyLogged(habitId, name, status: HabitCheckInStatusUiState)`. `LogHabitCheckInsIntent`: `DateSelected(date)`, `ValueChanged(habitId, value: Int?)`, `SaveClicked`, `CancelClicked`. `LogHabitCheckInsUiEvent`: `Saved`, `Cancelled`. `@HiltViewModel class LogHabitCheckInsViewModel @Inject constructor(private val observeHabitCheckInBoard: ObserveHabitCheckInBoardUseCase, private val logHabitCheckIns: LogHabitCheckInsUseCase) : ViewModel()`.

**As built** (superseded during Phase 4 manual verification, per live UI feedback): `Editable.Binary`/`Editable.Scale` collapsed into one `Editable(value: Int?, range: IntRange, type: HabitTypeUiState)` — a binary habit is just a `0..1` range, so one segmented-row input (see Phase 4 item 1) renders both variants instead of a toggle widget for one and a stepper for the other; `type` is kept (rather than inferred from `range == 0..1`) purely so the screen can pick "Done"/"Not done" labels for binary segments instead of literal "0"/"1" digits, without guessing from the range's shape. `BinaryValueChanged`/`ScaleValueChanged` likewise collapsed into one `ValueChanged(habitId, value: Int?)` — nullable so a segment can be deselected (tapping the already-selected segment clears it back to unselected, per further live feedback), not just selected; the ViewModel removes the habit's entry from its pending-values map on `null` instead of writing one. `AlreadyLogged.displayValue: String` first became `status: HabitCheckInStatusUiState`, then (per one more round of live feedback) `range: IntRange`/`type: HabitTypeUiState`/`value: Int` — the same three fields `Editable` carries (`range`/`type` promoted onto the sealed interface itself since both variants need them), so an already-logged habit renders the exact same (disabled, `enabled = false`) `TodayWasSegmentedRow` as an editable one, with a status chip (new `TodayWasChip` design-system component, wrapping M3 `SuggestionChip`) next to the habit name, instead of a plain "Done" text substitute or a hand-rolled colored `Box`. `TodayWasSegmentedRow`'s disabled (`enabled = false`) colors follow M3's standard disabled-content convention (`onSurface` at 12%/38% alpha for container/content) rather than dimmed selected/unselected colors, matching how M3's own chips and buttons render disabled state. The original plan's `value = row.value ?: row.range.first` fallback (shown in an earlier build of the screen) was a bug, not a feature — it visually pre-selected the range minimum before the user had chosen anything; the fix is `selectedValue: Int?` staying genuinely null until the user taps a segment.

#### 5. Journal retrofit

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModel.kt`

**Intent**: Removes the `@AssistedInject`/`@AssistedFactory` machinery entirely.
`AddJournalEntryViewModel` becomes a plain `@HiltViewModel` injecting
`ObserveAddableJournalDateSlotsUseCase` directly and deriving `availableSlots`/default
`selectedSlot` itself, reactively, instead of receiving a fixed snapshot at construction time —
this also fixes the latent staleness the fixed-snapshot approach had (a slot logged in a second app
window, or right at midnight, wouldn't have been reflected).

**Contract**: `AddJournalEntryUiState` keeps its existing shape; `availableSlots` is now populated
from the injected use case's latest emission rather than a constructor parameter.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `MainViewModelTest` covers: habit list mapping (today status: not-logged / binary / scale),
  `CREATE_HABIT`/`LOG_HABIT_CHECK_INS` fab-gating in all relevant focus/habits-empty combinations,
  the two new intents emit the correct events
- `CreateHabitViewModelTest` covers: field updates, save success/failure, `Saved`/`Cancelled` events
- `LogHabitCheckInsViewModelTest` covers: row derivation per selected date (all-unlogged /
  mixed / all-logged), date switching, all-or-nothing save success/failure
- `AddJournalEntryViewModelTest` updated: verifies self-derived `availableSlots` reacts to the
  injected use case's flow instead of a constructor snapshot

#### Manual Verification:

- None — pure presentation-layer phase, fully covered by automated tests.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Phase 4: UI

### Overview

Two new design-system components (`TodayWasSegmentedRow`, `TodayWasDateStrip`), the Habit section on
`MainScreen`, the two new habit screens, the journal screen's switch to the shared date-strip, and
Navigation 3 wiring for it all.

### Changes Required:

#### 1. Design system

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasSegmentedRow.kt`,
`core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasDateStrip.kt`

**Intent**: `TodayWasSegmentedRow` renders one tappable segment per value in an `IntRange` (2
segments for a binary habit's `0..1`, N segments for a scale habit's range), with `selectedValue`
staying `null` until the user taps one — no value is pre-selected. This replaced an earlier
`TodayWasStepper` (+/- stepper) design once live UI feedback asked for one unified segmented
control instead of a stepper for scale habits and a separate toggle for binary ones — see Phase 3
item 4's As-built note. `TodayWasDateStrip`
generalizes the existing journal `DayStrip`/`DayCard` into a reusable, arbitrary-date component,
keeping the original `Int.MAX_VALUE`-page, epoch-day-indexed `HorizontalPager` per Critical
Implementation Details (needed to keep the selected date visually centered even at the edge of its
selectable window).

**Contract**: `TodayWasSegmentedRow<T>(items: List<T>, selectedItem: T?, onItemSelected: (T?) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, allowDeselect: Boolean = true, label: (T) -> String = { it.toString() })` — one segment per item; none highlighted when `selectedItem == null`; tapping the already-selected segment calls `onItemSelected(null)` when `allowDeselect` (default); `enabled = false` renders a non-interactive preview (used by `CreateHabitScreen`'s check-in preview, see Phase 4 item 4); `label` overrides the default `toString()` label (e.g. binary habits show "Done"/"Not done" instead of "1"/"0", or "Yes/No"/"Range" for the habit-type picker). Generic rather than `IntRange`-only so it serves both the numeric check-in value picker and the non-numeric habit-type picker. `TodayWasDateStrip(selectedDate: LocalDate, isSelectable: (LocalDate) -> Boolean, onDateSelected: (LocalDate) -> Unit, modifier: Modifier = Modifier)` — no `dates` list parameter; `isSelectable` alone determines which of the (many, rendered-for-context) days are interactive. Both ship `@PreviewLightDark` per the established lesson.

**As built** (superseded during Phase 4 manual verification, per live UI feedback): both components' per-item spacing changed from per-card `.padding()` to `Arrangement.spacedBy()` (segmented row's `Row`) / `HorizontalPager`'s native `pageSpacing` param (date strip), and both now delegate their card rendering to a new shared `TodayWasSelectableCard` component (`enum class TodayWasCardVariant { NEUTRAL, PRIMARY, SECONDARY, TERTIARY }`; wraps M3 `Card`) instead of each hand-rolling `Box`/`Column` + `.background()`/`.clip()`/`.clickable()` — for a unified look as more selectable-card UI gets added later. `TodayWasSegmentedRow` passes `PRIMARY` for the selected segment and `NEUTRAL` otherwise; `TodayWasDateStrip` passes `PRIMARY` for the selected date and `SECONDARY` for other selectable-but-unselected dates (`TERTIARY` isn't consumed yet, added for future reuse). Disabled-state colors live once in `TodayWasSelectableCard` (the M3 `onSurface` 12%/38%-alpha convention) rather than being duplicated per component.

One further round of live feedback changed how a scale habit's range is defined: `CreateHabitUiState.scaleMin: String`/`scaleMax: String` (free-text, independently validated) became a single `scaleSteps: Int` (bounded to a new `HabitScaleStepsRange = 2..7`, default `2`), adjusted via a revived `TodayWasStepper` (`[-][value][+]`, the same `[minus icon button][value][plus icon button]` shape planned for the check-in value picker before that was replaced by `TodayWasSegmentedRow` — it found a real use here instead). `CreateHabitIntent.ScaleMinChanged`/`ScaleMaxChanged` collapsed into one `ScaleStepsChanged(steps: Int)`, coerced into range in the ViewModel. The resulting habit's actual `scaleMin`/`scaleMax` sent to `CreateHabitUseCase` are always `1`/`scaleSteps` — the domain/data-layer contract (Phase 1) is untouched, only how the UI arrives at those two numbers changed. This also removed `CreateHabitScreen`'s scale-range validation entirely (`isSaveEnabled` no longer parses/checks min<max) since a stepper bounded to `2..7` can never produce an invalid range, and removed the "Enter a valid range to preview" placeholder from the check-in-input preview for the same reason.

One final simplification removed the explicit Yes/No-vs-Range type picker entirely: `CreateHabitUiState.type: HabitTypeUiState` was dropped in favor of a computed `val isBinary: Boolean get() = scaleSteps == HabitScaleStepsRange.first` — a 2-step habit *is* binary, anything higher *is* a scale, with no separate choice for the user to make. `CreateHabitIntent.TypeChanged` was removed; the `TodayWasStepper` (previously only shown via `AnimatedVisibility` when Range was picked) is now always visible and is the only control the user interacts with for this. `TodayWasSegmentedRow` gained a `.animateContentSize()` on its `Row` so the check-in-input preview grows/shrinks smoothly (new segments ease in on the right) as the step count changes, instead of resizing abruptly — this benefits every consumer of the component, not just this preview.

#### 2. Root navigation

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasKey.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: `AddJournalEntryKey` loses its `availableSlots` field (becomes a plain `data object`);
two new plain `data object` keys are added for the habit screens.

**Contract**: `data object AddJournalEntryKey : TodayWasKey` (was a `data class` with a field). `data object CreateHabitKey : TodayWasKey`. `data object LogHabitCheckInsKey : TodayWasKey`. `entry<MainKey>` gains `onCreateHabitClicked`/`onLogCheckInsClicked` lambdas that `backStack.add(...)` the two new keys; their `entry<...Key>` blocks pop via `backStack.removeLastOrNull()` on `Saved`/`Cancelled`, same shape as the existing journal entries.

#### 3. Main screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Replaces the `HABIT`-focus placeholder (and adds to the `BOTH`-focus branch) a Habit
section: title, a list of habit rows (name + today-status badge — "Done"/a scale value/nothing).
`MainFab` itself is unchanged (already expand-ready); `MainScreen` gains the two new callback params
and wires them the same way `onAddEntryClicked`/`onJournalEntryClicked` already are.

**Contract**: `MainScreen(onAddEntryClicked: () -> Unit, onJournalEntryClicked: (JournalEntry) -> Unit, onCreateHabitClicked: () -> Unit, onLogCheckInsClicked: () -> Unit, viewModel: MainViewModel = hiltViewModel())` — note `onAddEntryClicked` drops its `List<JournalDateSlot>` parameter now that the destination self-derives.

#### 4. Create-habit screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/CreateHabitScreen.kt`

**Intent**: `TodayWasTopBar` with a back icon (`onCancelled`) and a `TodayWasButtonWithLoading` (or
top-bar action, matching journal's as-built pattern) save action; `TodayWasTextField` for name and
description; a `TodayWasRadioOption` pair for Binary/Scale type; two `TodayWasTextField`s (numeric
keyboard) for scale min/max, shown only when Scale is selected.

**Contract**: `CreateHabitScreen(onSaved: () -> Unit, onCancelled: () -> Unit, viewModel: CreateHabitViewModel = hiltViewModel())`.

**As built** (superseded during Phase 4 manual verification, per live UI feedback): top-to-bottom
order is Title (renamed from "Name") → "Scale type" section label + a `TodayWasSegmentedRow<HabitTypeUiState>`
(2 segments, "Yes/No" / "Range", `allowDeselect = false` since a habit always has exactly one type)
replacing the `TodayWasRadioOption` pair → the scale min/max fields, now wrapped in
`AnimatedVisibility` (fade + expand/shrink vertically) instead of an abrupt `if` block → a "Preview"
section showing a live, disabled (`enabled = false`) `TodayWasSegmentedRow` of what the check-in
input will actually look like (Yes/No labels for binary; the typed min..max range for scale, or a
placeholder string until the range is valid) — visible for both types, so the user sees the
check-in UX before saving → Description moved to last position. `TodayWasSegmentedRow` itself
became generic (`TodayWasSegmentedRow<T>(items: List<T>, ...)` instead of `IntRange`-only) plus
`enabled`/`allowDeselect` params, so this screen's type-picker and preview reuse the same component
as the log-check-ins screen's value picker.

#### 5. Log-check-ins screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsScreen.kt`

**Intent**: `TodayWasTopBar` with a back icon and save action, `TodayWasDateStrip` bound to
`selectedDate` with `isSelectable = { it in uiState.selectableDates }`, then a `LazyColumn` of habit rows: `Editable` rows render a
`TodayWasSegmentedRow` (2 segments for a binary habit's `0..1` range, N segments for a scale
habit's range), `AlreadyLogged` rows render the habit name plus a green "Done" label,
non-interactive. `TodayWasSnackbarHost` reports `saveError`, matching `AddJournalEntryScreen`'s established pattern.

**Contract**: `LogHabitCheckInsScreen(onSaved: () -> Unit, onCancelled: () -> Unit, viewModel: LogHabitCheckInsViewModel = hiltViewModel())`.

#### 6. Journal screen update

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt`

**Intent**: Removes the private `DayStrip`/`DayCard` composables in favor of the shared
`TodayWasDateStrip`, mapping `uiState.availableSlots` to the 2-date list
(`{LocalDate.now(), LocalDate.now().minusDays(1)}`) and `isSelectable` to "is this date's slot in
`availableSlots`."

**Contract**: No change to `AddJournalEntryScreen`'s own function signature.

#### 7. Strings

**File**: `app/src/main/res/values/strings.xml`

**Intent**: New `habit_*` keys (create-habit form labels/CTA, log-check-ins screen labels/CTA,
Done/Not-done labels) and `main_habit_*` keys (section title, empty state, FAB labels), replacing
the now-unused `main_habit_placeholder`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Fresh install, focus = Habit or Both: main screen shows an empty Habit section + "Create habit"
  (no "Log check-ins" yet).
- Creating a binary habit and a scale habit (e.g. range 1–5) shows both in the Habit list as
  not-logged-today; "Log check-ins" now appears.
- Tapping "Log check-ins" shows today's strip with both habits as editable inputs; saving both
  updates the main screen immediately.
- Relaunching the app shows the same habits and today's logged values (persistence).
- Re-opening "Log check-ins" shows today's row for both habits as read-only "Done".
- Swiping the date strip back to a prior day and logging a backfilled value succeeds; that date
  now shows read-only for that habit on re-open.
- Forcing a write failure on a check-in save shows an inline error and no partial state (none of
  that save's rows got committed) — retry succeeds once the forced failure is removed.
- Focus = Journal only: no Habit section, no "Create habit"/"Log check-ins" FAB actions anywhere.
- Journal's "Add entry" flow still works end-to-end after the retrofit (today/yesterday narrowing,
  save, persistence) — regression check on Phase 3's architecture change.

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- Room round-trip for `HabitDao`/`HabitCheckInDao` (Robolectric, two DB instances sharing a file),
  plus the batch-insert atomicity test.
- `HabitRepositoryImpl` retry-once-then-fail (fake DAOs, failure-count-controlled) for both
  `createHabit` and `addCheckIns`.
- `ObserveHabitCheckInBoardUseCase` and `ObserveAddableJournalDateSlotsUseCase` combinator
  correctness.
- `MainViewModel`: habit list mapping, fab-gating across focus/habits-empty states, intent→event
  mapping.
- `CreateHabitViewModel`: field updates, save success/failure, saved/cancelled events.
- `LogHabitCheckInsViewModel`: row derivation per date, date switching, all-or-nothing save.
- `AddJournalEntryViewModel`: self-derived addable slots reacting to the injected use case.

### Integration Tests:

- None — out of scope per this repo's "unit tests only" testing-scope convention (established in
  S-01).

### Manual Testing Steps:

1. Fresh install → pick Habit (or Both) focus → main screen shows empty Habit section + "Create
   habit".
2. Create a binary habit and a scale habit (range 1–5) → both appear as not-logged-today; "Log
   check-ins" now shows.
3. Tap "Log check-ins" → today's strip, both habits editable → log both → save → confirm both show
   updated today-status on the main screen immediately.
4. Relaunch → confirm habits and today's values persist.
5. Re-open "Log check-ins" → confirm today's rows now read-only "Done" for both habits.
6. Swipe the date strip to yesterday → log a backfilled value for one habit → save → re-open on
   that date → confirm it's read-only.
7. Force a write failure mid-batch (e.g. a temporary throw in the DAO) → confirm an inline error
   and zero partial rows committed → retry succeeds once removed.
8. Pick Journal-only focus on a separate fresh install → confirm no Habit section or FAB actions.
9. Regression: journal's Add-entry flow (today/yesterday narrowing, save, persistence) still works
   after the Phase 3 retrofit.

## Performance Considerations

Negligible at MVP scale — small, locally-stored lists via Room `Flow`, no pagination needed. The
7-day backfill window keeps `LogHabitCheckInsViewModel`'s per-date derivation over a small, bounded
dataset (habit count × 7).

## Migration Notes

`TodayWasDatabase` moves from `version = 2` to `version = 3` via `fallbackToDestructiveMigration`
(no real `Migration` object) — the same deliberate, explicitly-revisited decision as S-02. Revisit
before the app ships; this is now the second schema bump wiping local data on this same fallback
strategy.

## References

- Roadmap: `context/foundation/roadmap.md` (`S-03: Habit creation and check-in`)
- PRD: `context/foundation/prd.md` (US-02, FR-002, FR-004, FR-005, FR-008)
- Prior slice: `context/changes/journal-daily-entry/plan.md` (S-02 — data/domain/presentation/UI
  layering, retry-once write pattern, MVI shape, FAB expand mechanism, all reused as-is except the
  nav-key data-passing pattern this plan retrofits)
- Project conventions: `CLAUDE.md`, `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: Data layer (Room)

#### Automated

- [x] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 87e19eb
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck` — 87e19eb
- [x] 1.3 Debug build compiles: `./gradlew.bat assembleDebug` — 87e19eb
- [x] 1.4 Robolectric round-trip test passes for `HabitDao` and `HabitCheckInDao` — 87e19eb
- [x] 1.5 Batch-insert atomicity test passes for `HabitCheckInDao.insertAll` — 87e19eb
- [x] 1.6 Repository retry-once-then-fail tests pass for `HabitRepositoryImpl` — 87e19eb

### Phase 2: Domain (use cases)

#### Automated

- [x] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 0a98403
- [x] 2.2 Lint passes: `./gradlew.bat ktlintCheck` — 0a98403
- [x] 2.3 `ObserveHabitCheckInBoardUseCase` test passes — 0a98403
- [x] 2.4 `ObserveAddableJournalDateSlotsUseCase` test passes (three availability states) — 0a98403

### Phase 3: Presentation (ViewModels)

#### Automated

- [x] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — cf90451
- [x] 3.2 Lint passes: `./gradlew.bat ktlintCheck` — cf90451
- [x] 3.3 `MainViewModelTest` passes (habit mapping, fab-gating, intent→event mapping) — cf90451
- [x] 3.4 `CreateHabitViewModelTest` passes (field updates, save success/failure, events) — cf90451
- [x] 3.5 `LogHabitCheckInsViewModelTest` passes (row derivation, date switching, batch save) — cf90451
- [x] 3.6 `AddJournalEntryViewModelTest` passes (self-derived addable slots) — cf90451

### Phase 4: UI

#### Automated

- [x] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 58783ca
- [x] 4.2 Lint passes: `./gradlew.bat ktlintCheck` — 58783ca
- [x] 4.3 Debug build compiles and installs: `./gradlew.bat assembleDebug` — 58783ca

#### Manual

- [x] 4.4 Main screen shows Habit section + "Create habit" (empty state) for Habit/Both focus
- [x] 4.5 Creating binary + scale habits shows them as not-logged-today; "Log check-ins" appears
- [x] 4.6 Logging both habits for today saves and updates the main screen immediately
- [x] 4.7 Habits and today's values persist across relaunch
- [x] 4.8 Already-logged rows show read-only "Done" on re-open
- [x] 4.9 Backfilling a prior day via the date strip works and shows read-only on re-open
- [x] 4.10 Forced write failure shows an inline error with zero partial rows committed; retry works
- [x] 4.11 Journal-only focus shows no habit UI anywhere
- [x] 4.12 Journal's Add-entry flow still works end-to-end after the Phase 3 retrofit