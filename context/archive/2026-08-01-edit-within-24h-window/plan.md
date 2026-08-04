# Edit within 24h window Implementation Plan

## Overview

Let users edit a journal entry or habit check-in within 24 hours of its creation; after that it
stays viewable but becomes read-only (FR-006, US-03). This is greenfield work on top of two
existing verticals — no edit path, update query, or time-window logic exists anywhere in the
codebase today.

## Current State Analysis

- `JournalEntryEntity`/`HabitCheckInEntity` both already have a `createdAt: Long` (epoch millis)
  column, populated on write, but never read back — no window logic exists anywhere
  (`app/src/main/java/pl/luczka/todaywas/data/local/JournalEntryEntity.kt:8-13`,
  `.../HabitCheckInEntity.kt:11-17`).
- `JournalEntryDao`/`HabitCheckInDao` only support insert + observe-all — no `@Update`, no
  get-by-id/get-by-key query
  (`.../JournalEntryDao.kt`, `.../HabitCheckInDao.kt`).
- `JournalEntryDetailScreen` is a bare stateless composable with no ViewModel/state machine at all
  — it doesn't even use the `createdAt` its own nav key already carries
  (`app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailScreen.kt`).
- `JournalEntryDetailKey` carries `date`/`text`/`createdAt` but not the entry's `id`
  (`app/src/main/java/pl/luczka/todaywas/ui/TodayWasKey.kt:18-23`) — no way to look the entry back
  up for an update.
- On the habit check-in date strip, an already-logged row (`HabitCheckInRowUiState.AlreadyLogged`)
  renders a permanently `enabled = false` segmented control — purely presence-based, zero time
  logic (`app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsScreen.kt:176-187`,
  `.../LogHabitCheckInsMapper.kt:10-16`). The backfill window itself is a hardcoded 7-day range
  computed inline in the ViewModel's `init` (`LogHabitCheckInsViewModel.kt:26-29`).
- `MainUiEvent.NavigateToJournalDetail` already carries the full `JournalEntryUiState` (including
  `id`) — `TodayWasApp.kt` just discards `id` today when building the nav key
  (`app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt:51-59`).
- On the Main screen, `JournalEntryListItem` is already clickable and navigates to journal detail,
  but the equivalent `HabitListItem` is not clickable at all today
  (`app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt:163-181` vs. `204-215`) — there is no
  per-habit screen to navigate to yet.
- `ObserveHabitCheckInBoardUseCase` already returns every habit and every check-in with no date
  filtering (`domain/usecase/ObserveHabitCheckInBoardUseCase.kt:9-23`) — a per-habit screen can
  reuse it as-is (filtering client-side) without any new read query.

## Desired End State

- Opening a journal entry created less than 24h ago shows an "Edit" action; tapping it turns the
  text into an editable field with Save/Cancel. Saving updates the stored text (date and
  `createdAt` never change). After 24h, no edit action is shown, and attempting to save (e.g. a
  session left open past expiry) fails with an inline error and the entry reverts to read-only.
- "Log check-ins" (Input) keeps its existing insert-only behavior exactly as it is today — an
  already-logged row still shows its label + value, non-interactive as a control — except its date
  strip narrows from 7 days to just Today and Yesterday.
- Tapping a habit on the Main screen opens a new **Habit Detail** screen showing every check-in
  ever logged for that habit (most recent first), plus Today and Yesterday even if unlogged, as a
  plain unbounded list — no pagination yet (noted as a future follow-up once real usage volume
  warrants it). The screen is read-only by default; an "Edit" top-bar action (shown only when at
  least one row is currently eligible) flips it into edit mode, where eligible rows (not-yet-logged,
  or logged within 24h) become interactive and a Cancel/Save pair appears, mirroring the journal
  detail screen's Edit/Cancel/Save shape. Saving inserts not-yet-logged rows and updates in-window
  logged rows, then exits edit mode; Cancel discards any staged changes and exits edit mode without
  writing. This list is intentionally a placeholder for the future detailed-contribution-history
  *grid* (FR-011, roadmap S-05, not part of this change) — a plain list now, a real grid later.
- Verify via: create a journal entry and a habit check-in, edit both immediately (succeeds), then
  either wait past 24h or manually push a row's `createdAt` back via a debug write and confirm both
  become read-only/locked and a save attempt fails cleanly.

### Key Discoveries:

- No DB schema change is needed — both entities already have every column an update needs. Only
  new DAO queries are added, so no Room version bump.
- Nothing in this codebase currently injects `java.time.Clock` — `Instant.now()` is always called
  directly (`JournalRepositoryImpl.kt:26`, `HabitRepositoryImpl.kt:39,66`). This change introduces
  the first `Clock` seam so the 24h boundary is testable with `Clock.fixed(...)`.
- No `@AssistedInject`/`SavedStateHandle` pattern exists for passing per-entry nav data into a Hilt
  ViewModel in this codebase yet, but `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`
  (the wrapper already in use here, distinct from `androidx.hilt.navigation.compose.hiltViewModel`)
  has a `creationCallback` overload built exactly for this, backed by Hilt's
  `@HiltViewModel(assistedFactory = ...)` + `@AssistedInject`/`@Assisted`. `id` is injected
  directly into `JournalEntryDetailViewModel`'s constructor and loaded in `init {}` — no
  `LaunchedEffect`-dispatched `Load` intent needed. Verified by reading both libraries' sources
  jars (`hilt-lifecycle-viewmodel-compose:1.3.0`, `hilt-android:2.60.1`) rather than assuming, per
  this area's own "verify the current API shape before coding" caveat. This is the first assisted
  Hilt ViewModel in the codebase; `HabitDetailViewModel` (Phase 8) follows the same pattern for
  `habitId`.

## What We're NOT Doing

- No change to which date a journal entry belongs to — editing only changes the text, not the date
  (the date is unique-indexed and re-dating isn't required by FR-006).
- No editing inside "Log check-ins" itself — that screen stays insert-only; all editing of existing
  habit check-ins happens on the new Habit Detail screen.
- No full GitHub-style contribution *grid* for the Habit Detail screen — that's FR-011/roadmap S-05,
  a separate future change. This change's Habit Detail screen shows the full history as a plain
  list, just enough to host editing; a future change can turn the same screen into a real grid.
- No pagination on the Habit Detail history list — a plain unbounded `LazyColumn` over whatever
  check-ins exist. Flagged as a follow-up once real usage volume warrants it, not built now.
- No "habit list" screen separate from Main — Main's existing Habit section already lists every
  habit; it just becomes tappable.
- No extraction of a shared "Today/Yesterday" date-slot abstraction, even though this plan now has
  three independent places computing it (journal's addable slots, Log check-ins' narrowed window,
  Habit Detail's always-present Today/Yesterday rows). Each stays a small local computation;
  unifying them is a nice-to-have refactor, not required by this change.
- No DB migration / schema version bump — no entity columns change.
- No continuous re-evaluation of the 24h boundary while a screen sits open (e.g. no periodic timer
  ticking the UI from editable to locked). The window is (re-)checked on screen load and again at
  save time, which is sufficient to satisfy "enforce at write time, not just in the UI."

## Implementation Approach

Two verticals (journal, habit), each getting the same shape of change — data → domain →
presentation → UI — plus one shared foundation phase first (`Clock` DI + a pure `EditWindow` policy
object) that both verticals depend on. The 24h check lives in the domain layer (new
`UpdateJournalEntryUseCase`/`UpdateHabitCheckInUseCase`), matching how `AddJournalEntryUseCase`
already owns business policy while repositories stay pure persistence. Both use cases receive the
row's `createdAt` from their caller (already available from data already loaded on screen) rather
than re-fetching it, avoiding a redundant DB read purely for the window check. The habit vertical's
new Habit Detail screen reads via the existing `ObserveHabitCheckInBoardUseCase` (filtering
client-side to one habit and two dates) rather than any new query, and writes via the existing
`LogHabitCheckInsUseCase` (for not-yet-logged rows) plus the new `UpdateHabitCheckInUseCase` (for
in-window already-logged rows) — see Critical Implementation Details.

## Critical Implementation Details

**Mixed insert+update in the Habit Detail screen's Save, bounded to at most two rows.** The new
Habit Detail screen shows exactly two rows (Today, Yesterday) for one habit. A not-yet-logged row's
saved value needs `LogHabitCheckInsUseCase` (insert; it takes a date + a habitId→value map, so it's
called with a single-entry map here); an already-logged, in-window row's saved value needs
`UpdateHabitCheckInUseCase` (update). A single Save press can touch either or both rows at once, so
`HabitDetailViewModel.onSaveClicked` must call the right use case per row (using each row's
`alreadyLogged` flag to decide) and combine both outcomes into one saving/error state — the same
kind of partitioning that would have been needed on the full multi-habit board, but bounded to at
most 2 calls instead of N, since this screen only ever shows one habit.

**Loading via assisted injection, not a `Load` intent.** The nav-key `id` is the ViewModel's own
identity, not runtime UI state — so it's injected directly into the constructor via Hilt's
assisted-injection support (`@HiltViewModel(assistedFactory = ...)` + `@AssistedInject`/
`@Assisted`, surfaced to Compose via `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`'s
`creationCallback` overload) rather than passed as a `Load(id)` intent dispatched from a
`LaunchedEffect(id)`. The `init {}` block loads immediately on construction. This works safely
with per-entry `ViewModelStore` scoping (`rememberViewModelStoreNavEntryDecorator()`, already
wired in `TodayWasApp.kt`): each nav-key push gets its own back-stack entry and thus its own
`ViewModelStore`, so a fresh, correctly-`id`'d ViewModel is created per navigation, not reused
across different ids. `HabitDetailViewModel` (Phase 8) uses the same pattern for `habitId`.

**Distinguishing "window just expired" from "transient failure" on save.** A generic write failure
(e.g. a flaky DB write) should leave the journal edit screen in edit mode so the user can retry
without losing their typed text. A window-expired failure should instead flip the screen back to
read-only, since retrying is pointless. `UpdateJournalEntryUseCase`/`UpdateHabitCheckInUseCase`
signal this by returning `Result.failure(EditWindowExpiredException())` specifically for the
expired case (vs. any other exception for a generic failure); the ViewModel checks
`result.exceptionOrNull() is EditWindowExpiredException` to decide which UI transition to make.

## Phase 1: Shared foundation (Clock + edit-window policy)

### Overview

Introduce the one time-source seam and the one pure boundary check both verticals will use.

### Changes Required:

#### 1. Clock DI

**File**: `app/src/main/java/pl/luczka/todaywas/di/ClockModule.kt` (new)

**Intent**: Provide a Hilt-injectable `java.time.Clock` so production code uses
`Clock.systemDefaultZone()` while tests can substitute `Clock.fixed(...)` for deterministic
boundary tests.

**Contract**: `@Module @InstallIn(SingletonComponent::class) object ClockModule` with a single
`@Provides @Singleton fun provideClock(): Clock` — same shape as the existing
`DatabaseModule` (`app/src/main/java/pl/luczka/todaywas/data/local/DatabaseModule.kt:12-14`).

#### 2. Edit-window policy

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/EditWindow.kt` (new)

**Intent**: One place defining "is this row still editable," used identically by both verticals'
update use cases and by the habit row mapper.

**Contract**: An `object EditWindow` with a `24`-hour constant and a pure function
`isEditable(createdAt: Instant, now: Instant): Boolean` — editable while
`Duration.between(createdAt, now) < Duration.ofHours(24)` (strictly less-than, so an entry is
locked at exactly the 24h mark, not one tick after). Also add
`app/src/main/java/pl/luczka/todaywas/domain/model/EditWindowExpiredException.kt` (its own file) —
`class EditWindowExpiredException : Exception()` — it's the signal the two update use cases
(Phase 3, Phase 7) return to let ViewModels distinguish an expired-window failure from any other
failure.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`
- `EditWindowTest` passes covering: just-created (editable), 23h59m59s after creation (editable),
  exactly 24h00m00s after creation (locked), 24h00m01s after creation (locked)

---

## Phase 2: Journal — Data layer

### Overview

Add the read-by-id and update capabilities the journal edit flow needs.

### Changes Required:

#### 1. DAO

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/JournalEntryDao.kt`

**Intent**: Support looking up one entry by id (for the detail screen's initial load) and updating
its text.

**Contract**: Add `suspend fun getById(id: Long): JournalEntryEntity?` (`@Query("SELECT * FROM
journal_entries WHERE id = :id")`) and `suspend fun update(entity: JournalEntryEntity)`
(`@Update`).

#### 2. Repository

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepository.kt`,
`JournalRepositoryImpl.kt`

**Intent**: Expose a get-by-id read and a text-only update write, both pure persistence — no
window/policy logic here (that lives in the domain use case, Phase 3).

**Contract**: Interface gains `suspend fun getEntry(id: Long): JournalEntry?` and `suspend fun
updateEntry(id: Long, text: String): Result<Unit>`. `getEntry` is a thin `dao.getById(id)
?.toDomain()`. `updateEntry` fetches the existing entity via `dao.getById(id)` (returns
`Result.failure` if not found), copies in the new `text` (date/createdAt untouched), and calls
`dao.update(...)` wrapped in the same retry-once-on-failure pattern as `addEntry`
(`JournalRepositoryImpl.kt:28-44`).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`
- Robolectric round-trip test passes for `JournalEntryDao.getById`/`update`
- `JournalRepositoryImplTest` passes: `updateEntry` retry-once-then-fail behavior, `updateEntry`
  on a missing id returns failure, `getEntry` returns the mapped domain entry or null

---

## Phase 3: Journal — Domain

### Overview

Add the use cases the detail screen needs: load-by-id, and a window-checked update.

### Changes Required:

#### 1. Get use case

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/GetJournalEntryUseCase.kt` (new)

**Intent**: One-shot fetch of a single entry for the detail screen's initial load.

**Contract**: `class GetJournalEntryUseCase @Inject constructor(private val repository:
JournalRepository)` with `suspend operator fun invoke(id: Long): JournalEntry? =
repository.getEntry(id)`.

#### 2. Update use case

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/UpdateJournalEntryUseCase.kt` (new)

**Intent**: Own the "is this still within the edit window" policy before allowing a write, per the
project's hard rule to enforce this at write time, not just in the UI.

**Contract**: `class UpdateJournalEntryUseCase @Inject constructor(private val repository:
JournalRepository, private val clock: Clock)` with `suspend operator fun invoke(id: Long, text:
String, createdAt: Instant): Result<Unit>` — returns `Result.failure(EditWindowExpiredException())`
if `EditWindow.isEditable(createdAt, clock.instant())` is false, otherwise delegates to
`repository.updateEntry(id, text)`. `createdAt` is supplied by the caller (already loaded from
`GetJournalEntryUseCase`) rather than re-fetched here.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `GetJournalEntryUseCaseTest` passes (found id, missing id)
- `UpdateJournalEntryUseCaseTest` passes using `Clock.fixed(...)`: within-window succeeds and
  delegates to the repository with unchanged id/date; at/past the 24h boundary returns
  `Result.failure` with `EditWindowExpiredException` and never calls the repository; a repository
  failure passes through unchanged

---

## Phase 4: Journal — Presentation

### Overview

Give the journal detail screen its own MVI stack (today it has none), with a load-then-edit state
machine.

### Changes Required:

#### 1. UI state / intent / event

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailUiState.kt`,
`JournalEntryDetailIntent.kt`, `JournalEntryDetailUiEvent.kt` (all new)

**Intent**: Standard per-screen MVI trio, per the project's ViewModel convention.

**Contract**: `JournalEntryDetailUiState` holds `isLoading: Boolean`, `entry: JournalEntryUiState?`
(null while loading — reuses the existing `ui.model.JournalEntryUiState`/`JournalEntryMapper`
rather than re-deriving `formattedDate` locally), `editedText: String`, `isEditable: Boolean`,
`isEditing: Boolean`, `isSaving: Boolean`, `saveError: Boolean`. `JournalEntryDetailIntent` is a
sealed interface: `EditClicked`, `TextChanged(text: String)`, `SaveClicked`, `CancelEditClicked`,
`BackClicked` (no `Load` — see ViewModel below). `JournalEntryDetailUiEvent` is a sealed interface
with one member: `NavigatedBack`.

#### 2. ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailViewModel.kt` (new)

**Intent**: Load the entry once on construction, compute initial editability, and drive the
edit/save/cancel state machine described in Critical Implementation Details.

**Contract**: `@HiltViewModel(assistedFactory = JournalEntryDetailViewModel.Factory::class)` with
an `@AssistedInject` constructor taking `@Assisted id: Long` alongside `GetJournalEntryUseCase`,
`UpdateJournalEntryUseCase`, `Clock` (see Critical Implementation Details for why `id` is assisted
rather than passed via a `Load` intent). `init` fetches via `GetJournalEntryUseCase`, and sets
`entry`/`isEditable` (via `EditWindow.isEditable(entry.createdAt, clock.instant())`).
`onSaveClicked` calls
`UpdateJournalEntryUseCase(id, editedText, entry.createdAt)`; on success updates `entry.text` and
exits edit mode; on failure sets `saveError = true`, and additionally exits edit mode and sets
`isEditable = false` only when the failure is `EditWindowExpiredException` (a generic failure
keeps `isEditing = true` so the user's draft isn't lost). `onBackClicked` sends `NavigatedBack`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `JournalEntryDetailViewModelTest` passes: load populates `entry`/`isEditable`; edit→type→save
  success updates `entry.text` and clears `isEditing`; save failure (generic) keeps `isEditing`
  true and sets `saveError`; save failure (`EditWindowExpiredException`) clears `isEditing`, sets
  `isEditable = false`, and sets `saveError`

---

## Phase 5: Journal — UI

### Overview

Wire the new ViewModel into the screen and nav graph, replacing the stateless composable.

### Changes Required:

#### 1. Nav key

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasKey.kt`

**Intent**: Carry only the entry id, so the screen always loads current DB state instead of a
nav-key snapshot.

**Contract**: Replace `JournalEntryDetailKey(date, text, createdAt)` with `JournalEntryDetailKey(val
id: Long)`.

#### 2. Nav wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Pass the id through instead of the discarded snapshot fields.

**Contract**: `onJournalEntryClicked` builds `JournalEntryDetailKey(id = entry.id)`
(`entry: JournalEntryUiState` already carries `id`, just unused today — line 51-59). The
`entry<JournalEntryDetailKey>` block passes `id = key.id` into the screen.

#### 3. Screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailScreen.kt`

**Intent**: Replace the stateless read-only composable with a stateful one that loads by id and
supports the edit flow.

**Contract**: `JournalEntryDetailScreen(id: Long, onBack: () -> Unit, viewModel:
JournalEntryDetailViewModel = hiltViewModel<JournalEntryDetailViewModel,
JournalEntryDetailViewModel.Factory> { it.create(id) })` — the assisted-injection `hiltViewModel`
overload creates the ViewModel with `id` already wired in, so it loads itself; the screen only
collects `viewModel.events` for `NavigatedBack -> onBack()`. Top bar: back nav icon dispatches
`BackClicked`; actions show nothing while `!isEditable`, an "Edit" `TodayWasIconButton` when
`isEditable && !isEditing`, and Save (`TodayWasButtonWithLoading`, loading = `isSaving`) +
Cancel icon buttons when `isEditing`. Body: date (`entry.formattedDate`, always read-only) plus
either `entry.text` via `TodayWasText` (not editing) or a multi-line `TodayWasTextField` bound to
`editedText` (editing), following `AddJournalEntryScreen`'s field usage
(`app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt:105-114`). `saveError`
drives a `TodayWasSnackbarHost`, matching the pattern in `LogHabitCheckInsScreen.kt:76-82`. New
strings: `journal_detail_edit_action`, `journal_detail_save_cta`,
`journal_detail_cancel_edit_action`, `journal_detail_error`,
`journal_detail_edit_window_expired_error`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Opening a just-created entry shows an "Edit" action; tapping it, changing the text, and saving
  updates the entry and returns to read-only display with the new text
- Cancelling an in-progress edit discards the typed change and shows the original text
- Opening an entry created more than 24h ago shows no "Edit" action at all
- (Debug-assisted) an entry whose window expires while the edit screen is open shows an inline
  error on Save and reverts to read-only

**Implementation Note**: Pause here for manual confirmation before starting the habit vertical.

---

## Phase 6: Habit — Data layer

### Overview

Add the lookup-by-key and update capability the habit check-in edit needs.

### Changes Required:

#### 1. DAO

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/HabitCheckInDao.kt`

**Intent**: Support looking up one check-in by its natural key and updating its value.

**Contract**: Add `suspend fun getByHabitAndDate(habitId: Long, date: String):
HabitCheckInEntity?` (`@Query("SELECT * FROM habit_check_ins WHERE habitId = :habitId AND date =
:date")`) and `suspend fun update(entity: HabitCheckInEntity)` (`@Update`).

#### 2. Repository

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepository.kt`,
`HabitRepositoryImpl.kt`

**Intent**: Expose a value-only update write. No public get-by-key method is needed on the
interface — the habit vertical's read side is already covered by the existing
`observeCheckIns()`/`ObserveHabitCheckInBoardUseCase`, which already carries each check-in's
`createdAt`; the update use case (Phase 7) receives `createdAt` from its caller instead of
re-fetching it.

**Contract**: Interface gains `suspend fun updateCheckIn(habitId: Long, date: LocalDate, value:
Int): Result<Unit>`. Implementation looks up the existing entity via
`habitCheckInDao.getByHabitAndDate(habitId, date.toString())` (returns `Result.failure` if not
found — this is internal plumbing to preserve the row's `id`/`createdAt` for `@Update`, not a
window check), copies in the new `value`, and calls `habitCheckInDao.update(...)` via the shared
`safeDbCall` helper (`data/repository/SafeDbCall.kt`, extracted in Phase 2) rather than duplicating
the retry-once try/catch inline. While in this file, also refactor `createHabit` and `addCheckIns`
(`HabitRepositoryImpl.kt:26-57,62-91`) to use `safeDbCall` too, so the whole file is consistent.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`
- Robolectric round-trip test passes for `HabitCheckInDao.getByHabitAndDate`/`update`
- `HabitRepositoryImplTest` passes: `updateCheckIn` retry-once-then-fail behavior, `updateCheckIn`
  for a non-existent (habitId, date) returns failure

---

## Phase 7: Habit — Domain

### Overview

Add the window-checked update use case for a single habit check-in.

### Changes Required:

#### 1. Update use case

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/UpdateHabitCheckInUseCase.kt` (new)

**Intent**: Same policy role as `UpdateJournalEntryUseCase` (Phase 3), for a single habit/date.

**Contract**: `class UpdateHabitCheckInUseCase @Inject constructor(private val repository:
HabitRepository, private val clock: Clock)` with `suspend operator fun invoke(habitId: Long, date:
LocalDate, value: Int, createdAt: Instant): Result<Unit>` — same
`EditWindow.isEditable`/`EditWindowExpiredException` contract as `UpdateJournalEntryUseCase`,
delegating to `repository.updateCheckIn(habitId, date, value)` when still within window.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `UpdateHabitCheckInUseCaseTest` passes using `Clock.fixed(...)`: within-window succeeds and
  delegates to the repository; at/past the 24h boundary returns `Result.failure` with
  `EditWindowExpiredException` and never calls the repository; a repository failure passes through
  unchanged

---

## Phase 8: Habit — Presentation

### Overview

Narrow Log check-ins' window to Today/Yesterday (no other change to that ViewModel), add Main's
navigation into a new per-habit screen, and build that new screen's state machine.

### Changes Required:

#### 1. Log check-ins date window

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsViewModel.kt`

**Intent**: Match the journal vertical's "today or yesterday" backfill scope (per this session's
decision to narrow it from the original 7-day window) — this is the only change this ViewModel
gets; its insert-only Save behavior and `AlreadyLogged` row rendering stay exactly as they are
today.

**Contract**: Replace the `(6 downTo 0)` range in `selectableDates` (`LogHabitCheckInsViewModel.kt:
26-29`) with `(1 downTo 0)`, producing exactly `[yesterday, today]`.

#### 2. Main navigation

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainIntent.kt`,
`MainUiEvent.kt`, `MainViewModel.kt`

**Intent**: Let tapping a habit on the Main screen navigate to its detail, mirroring
`JournalEntryClicked`/`NavigateToJournalDetail` exactly (`MainIntent.kt`, `MainUiEvent.kt:9`,
`MainViewModel.kt:99`).

**Contract**: Add `MainIntent.HabitClicked(habit: HabitUiState)` and
`MainUiEvent.NavigateToHabitDetail(habitId: Long)`; `MainViewModel` dispatches the event with
`habit.id` on that intent.

#### 3. Habit Detail state / intent / event

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailUiState.kt`,
`HabitDetailIntent.kt`, `HabitDetailUiEvent.kt` (all new)

**Intent**: Standard per-screen MVI trio for the new minimal per-habit screen.

**Contract**: `HabitDetailUiState` holds `isLoading: Boolean`, `habitName: String`, `type:
HabitTypeUiState`, `range: IntRange`, `rows: List<HabitDetailRowUiState>` (every logged date plus
Today/Yesterday even if unlogged, most-recent-first — not capped to 2, see the redesign note
below), `isEditMode: Boolean`, `isSaving: Boolean`, `saveError: Boolean`. `HabitDetailRowUiState`
holds `date: LocalDate`, `value: Int?`, `eligibleForEdit: Boolean` (true if not yet logged, or
logged and within the 24h window — named `eligibleForEdit` rather than `editable` since actual
interactivity also depends on `isEditMode`, a separate screen-level gate), `alreadyLogged: Boolean`
(distinguishes an insert-on-save row from an update-on-save row for the partitioning described in
Critical Implementation Details). No `label` field; the screen (Phase 9) derives "Today"/
"Yesterday"/formatted-date text from `date` itself via `stringResource`, keeping all UI copy in
`strings.xml` per project convention rather than baking it into UI state. `HabitDetailIntent` is a
sealed interface: `EditClicked`, `ValueChanged(date: LocalDate, value: Int?)`, `SaveClicked`,
`CancelEditClicked`, `BackClicked` (no `Load` — `habitId` is assisted-injected, same as journal's
`JournalEntryDetailViewModel`). `HabitDetailUiEvent` is a sealed interface with one member:
`NavigatedBack`.

**Redesign note (mid-Phase-9 steer)**: the screen was originally scoped to a fixed 2-row
Today/Yesterday shape with inline editing. Before Phase 9's manual verification completed, this was
revised to show the *full* check-in history (foreshadowing the future S-05 contribution grid) with
a separate read-only/edit-mode split — editing only becomes possible after an explicit "Edit"
action, mirroring the journal detail screen's Edit/Cancel/Save shape at the whole-screen level
instead of always-on inline controls. This section and Phase 9 describe the shape that shipped.

#### 4. Habit Detail ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailViewModel.kt` (new)

**Intent**: Load one habit's full check-in history from the existing board use case, and drive the
edit-mode + mixed-save state machine.

**Contract**: `@HiltViewModel(assistedFactory = HabitDetailViewModel.Factory::class)` with an
`@AssistedInject` constructor taking `@Assisted habitId: Long` alongside
`ObserveHabitCheckInBoardUseCase`, `LogHabitCheckInsUseCase`, `UpdateHabitCheckInUseCase`, `Clock`
— same pattern as `JournalEntryDetailViewModel` (Phase 4). Internal state follows the
"Now in Android"-style shape: a top-level (not nested — file-private, not inside the ViewModel
class body) `HabitDetailViewModelState` data class (`isLoading`, `habitName`, `type`, `range`,
`checkIns: List<HabitCheckIn>`, `pendingValues: Map<LocalDate, Int>`, `isEditMode`, `isSaving`,
`saveError`) held in a private `MutableStateFlow`, with a `toUiState(now)` method on that class
doing the mapping (via `HabitDetailMapper.kt`'s `List<HabitCheckIn>.toHabitDetailRows(pendingValues,
now)` — this function derives its own date set internally: every date with a check-in, unioned with
Today/Yesterday, sorted descending; no `dates` parameter). The public `val uiState:
StateFlow<HabitDetailUiState>` is *derived*, not separately `.update{}`-driven:
`viewModelState.map { it.toUiState(clock.instant()) }.stateIn(scope = viewModelScope, started =
SharingStarted.WhileSubscribed(5_000), initialValue = ...)` — there is no private `_uiState` written
to directly. `init` collects the board `Flow`, finds the matching `Habit`, and updates
`viewModelState` with `habitName`/`type`/`range`/`checkIns` (raw, unmerged with pending edits — the
mapping happens once, at the `uiState` derivation point). `onEditClicked` sets `isEditMode = true`.
`onValueChanged` updates `viewModelState.pendingValues`. `onCancelEditClicked` clears
`pendingValues` and sets `isEditMode = false` (discards, does not write). `onSaveClicked` partitions
the pending entries by looking each date up in `viewModelState.value.checkIns` (associated by
date): a hit means `updateHabitCheckIn(habitId, date, value, existing.createdAt)`; a miss means
`logHabitCheckIns(date, mapOf(habitId to value))` (one call per such row, since the use case takes
one date at a time). If `pendingValues` is empty, `onSaveClicked` just exits edit mode without
calling either use case. `saveError = true` unless every call in the batch succeeds; on success
`isEditMode` is also cleared — the board's own live `Flow` naturally refreshes `checkIns` with the
new persisted state, so no manual state patching is needed (unlike the journal detail screen, which
has no live Flow and must patch `entry` locally on save success). `onBackClicked` sends
`NavigatedBack`.

**Test note**: `SharingStarted.WhileSubscribed` means `uiState.value` only updates while something
is actively subscribed to it — a test that reads `.value` without first `launch { uiState.collect
{} }`ing would silently observe the stale `initialValue` forever. Every `HabitDetailViewModelTest`
case launches and cancels such a collector job, the same subscribe-then-read shape already used
elsewhere in this test suite for one-shot `events` Flow assertions.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `LogHabitCheckInsViewModelTest` passes (existing cases still pass; `selectableDates` is now
  exactly `[yesterday, today]`)
- `MainViewModelTest` passes (`HabitClicked` → `NavigateToHabitDetail` with the habit's id)
- `HabitDetailViewModelTest` passes: rows include every logged date plus Today/Yesterday even when
  unlogged; correct `eligibleForEdit`/`alreadyLogged` derivation for a not-yet-logged day, an
  in-window logged day, and an expired logged day; `EditClicked`/`CancelEditClicked` toggle
  `isEditMode` and Cancel discards pending values; Save with nothing pending exits edit mode without
  calling either use case; Save with only a not-yet-logged row touched calls only
  `logHabitCheckIns`; Save with only an in-window logged row touched calls only
  `updateHabitCheckIn`; Save touching both calls both and only clears pending/exits edit mode if
  both succeed

---

## Phase 9: Habit — UI

### Overview

Make Main's habit rows tappable, and build the new Habit Detail screen + nav wiring. Log
check-ins' own screen file is untouched in this phase (Phase 8 only touched its ViewModel's date
range).

### Changes Required:

#### 1. Main screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Make habit rows navigable, mirroring `JournalEntryListItem`'s existing `.clickable`
(line 171).

**Contract**: `HabitListItem` (lines 204-215) gains an `onClick: () -> Unit` parameter and a
`Modifier.clickable(onClick = onClick)`, wired from `HabitSection`'s `items(uiState.habits)` to
`onIntent(MainIntent.HabitClicked(habit))`.

#### 2. Nav key

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasKey.kt`

**Intent**: Carry the habit id into the new detail screen.

**Contract**: Add `data class HabitDetailKey(val habitId: Long) : TodayWasKey`.

#### 3. Nav wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Wire `MainScreen`'s new navigation event and add the new screen's nav entry, following
the existing `onJournalEntryClicked`/`entry<JournalEntryDetailKey>` pattern.

**Contract**: `MainScreen` gains `onHabitClicked = { habitId -> backStack.add(HabitDetailKey(habitId))
}`, wired from `MainUiEvent.NavigateToHabitDetail`. A new `entry<HabitDetailKey> { key ->
HabitDetailScreen(habitId = key.habitId, onBack = { backStack.removeLastOrNull() }) }` block is
added.

#### 4. Habit Detail screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailScreen.kt` (new)

**Intent**: Read-only-by-default history list with an explicit edit mode. This is the placeholder
described in "What We're NOT Doing" for the future contribution-grid screen (S-05) — a list now,
a grid later.

**Contract**: `HabitDetailScreen(habitId: Long, onBack: () -> Unit, viewModel: HabitDetailViewModel
= hiltViewModel<HabitDetailViewModel, HabitDetailViewModel.Factory> { it.create(habitId) })`
(assisted-injection `hiltViewModel` overload, same as the journal detail screen) collects events for
`NavigatedBack -> onBack()`. Top bar: back nav icon dispatches `BackClicked`; actions are
mode-dependent, mirroring `JournalEntryDetailScreen`'s Edit/Cancel/Save shape at the whole-screen
level — when `!isEditMode`, a single Edit `TodayWasIconButton` (shown only if `rows.any {
it.eligibleForEdit }`, else no action at all); when `isEditMode`, a Cancel (`Icons.Filled.Close`)
icon dispatching `CancelEditClicked` plus a Save `TodayWasButtonWithLoading` (loading = `isSaving`)
dispatching `SaveClicked`. Body: `habitName` as a heading, then a `LazyColumn` over `uiState.rows`
(not a plain `Column` — the list is now unbounded) — each row rendered via the same
`TodayWasSegmentedRow` pattern `LogHabitCheckInsScreen.HabitCheckInRow` already uses
(`LogHabitCheckInsScreen.kt:134-190`), with `enabled = uiState.isEditMode && row.eligibleForEdit`
(both conditions, not `eligibleForEdit` alone) and `onItemSelected` dispatching
`ValueChanged(row.date, it)`. Each row's date label is `stringResource(R.string
.habit_detail_today_label)` / `..._yesterday_label` for those two dates, and
`date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))` (same formatter the journal
detail screen already uses) for every other date. `saveError` drives a `TodayWasSnackbarHost`,
matching `LogHabitCheckInsScreen.kt:76-82`. New strings: `habit_detail_title`,
`habit_detail_edit_action`, `habit_detail_save_cta`, `habit_detail_cancel_edit_action`,
`habit_detail_error`, `habit_detail_today_label`, `habit_detail_yesterday_label`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Tapping a habit on the Main screen opens its Habit Detail screen showing its full history
  (Today/Yesterday always present even unlogged, plus any older logged dates), read-only, with an
  Edit action
- Tapping Edit reveals interactive controls only on eligible rows (not-yet-logged, or logged within
  24h); other rows stay visibly non-interactive
- Setting a value for a not-yet-logged day, then Save, inserts it, exits edit mode, and persists
  across relaunch
- Changing an already-logged, in-window day's value, then Save, updates it, exits edit mode, and
  persists
- Entering edit mode, changing a value, then Cancel discards the change and exits edit mode without
  writing anything
- (Debug-assisted) a day whose window has expired never becomes interactive even in edit mode
- Regression: journal's add-entry flow, Log check-ins' insert-only flow (now Today/Yesterday only),
  and first-time habit creation still work end-to-end

**Implementation Note**: Pause here for manual confirmation before wrapping up the change.

---

## Testing Strategy

### Unit Tests:

- `EditWindow.isEditable` boundary behavior (Phase 1) — the one place the 24h cutoff is defined,
  so its correctness gates everything downstream.
- Repository retry-once-on-failure and not-found handling for both new update paths (Phases 2, 6).
- Use-case-level boundary tests via `Clock.fixed(...)` for both update use cases (Phases 3, 7) —
  this is the actual enforcement point per the project's write-time-enforcement hard rule.
- ViewModel-level state transitions: journal's edit/save/cancel and expired-vs-generic-failure
  branching (Phase 4); Habit Detail's per-row editable/alreadyLogged derivation and mixed
  insert+update Save partitioning, bounded to its 2 rows (Phase 8).

### Integration Tests:

- None — out of scope per this repo's "unit tests only" testing-scope convention (established in
  prior slices).

### Manual Testing Steps:

1. Create a journal entry → open it → confirm "Edit" is shown, editing text and saving updates it
   and returns to read-only.
2. Cancel an in-progress journal edit → confirm the original text is shown, unchanged.
3. Log a habit check-in for today via "Log check-ins" → confirm the row is now non-interactive
   there → tap the habit from the Main screen → confirm Habit Detail shows the full history
   read-only with an Edit action → tap Edit, change today's value, Save → confirm it exits edit
   mode, persists across relaunch, and shows correctly everywhere.
4. Using a debug-only backdated write (or system clock manipulation) push an entry's/check-in's
   `createdAt` more than 24h in the past → confirm the journal entry shows no "Edit" action and the
   habit's Habit Detail row for that day renders locked.
5. With a journal entry's edit screen open and its window artificially expired mid-session, attempt
   Save → confirm an inline error and the screen reverts to read-only.
6. Confirm "Log check-ins" only ever offers Today and Yesterday (no further backfill).
7. Regression: add-entry (journal), Log check-ins' insert flow, and habit creation from the prior
   two slices still work end-to-end.

## Performance Considerations

Negligible at MVP scale — single-row lookups by indexed/primary key, no new list-scale queries.

## References

- Roadmap: `context/foundation/roadmap.md` (`S-04: Edit within the 24-hour window`)
- PRD: `context/foundation/prd.md` (US-03, FR-006)
- Prior slices: `context/archive/2026-07-27-journal-daily-entry/plan.md` (S-02 — data/domain/
  presentation/UI layering, retry-once write pattern, MVI shape, reused as-is),
  `context/archive/2026-07-31-habit-create-and-checkin/plan.md` (S-03 — same layering for habit
  check-ins, batch-insert atomicity pattern reused for the insert half of Phase 8's mixed save)
- Related future work (not part of this change): roadmap `S-05: Detailed contribution history`
  (FR-011) — the Habit Detail screen built in Phases 8-9 is a deliberately minimal placeholder for
  what S-05 will later turn into a full contribution grid.
- Project conventions: `CLAUDE.md`, `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: Shared foundation (Clock + edit-window policy)

#### Automated

- [x] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 6348384
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck` — 6348384
- [x] 1.3 Debug build compiles: `./gradlew.bat assembleDebug` — 6348384
- [x] 1.4 `EditWindowTest` passes covering just-created / 23h59m59s / exactly-24h / past-24h — 6348384

### Phase 2: Journal — Data layer

#### Automated

- [x] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 30aaa9f
- [x] 2.2 Lint passes: `./gradlew.bat ktlintCheck` — 30aaa9f
- [x] 2.3 Debug build compiles: `./gradlew.bat assembleDebug` — 30aaa9f
- [x] 2.4 Robolectric round-trip test passes for `JournalEntryDao.getById`/`update` — 30aaa9f
- [x] 2.5 `JournalRepositoryImplTest` passes (updateEntry retry-once, missing-id, getEntry) — 30aaa9f

### Phase 3: Journal — Domain

#### Automated

- [x] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — c75b1be
- [x] 3.2 Lint passes: `./gradlew.bat ktlintCheck` — c75b1be
- [x] 3.3 `GetJournalEntryUseCaseTest` passes — c75b1be
- [x] 3.4 `UpdateJournalEntryUseCaseTest` passes (window-boundary cases via `Clock.fixed`) — c75b1be

### Phase 4: Journal — Presentation

#### Automated

- [x] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 7b77d04
- [x] 4.2 Lint passes: `./gradlew.bat ktlintCheck` — 7b77d04
- [x] 4.3 `JournalEntryDetailViewModelTest` passes (load, edit/save success, generic failure,
      expired failure) — 7b77d04

### Phase 5: Journal — UI

#### Automated

- [x] 5.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 0939fb9
- [x] 5.2 Lint passes: `./gradlew.bat ktlintCheck` — 0939fb9
- [x] 5.3 Debug build compiles and installs: `./gradlew.bat assembleDebug` — 0939fb9

#### Manual

- [x] 5.4 Editing a just-created entry updates it and returns to read-only with new text — 0939fb9
- [x] 5.5 Cancelling an in-progress edit discards the change — 0939fb9
- [x] 5.6 An entry older than 24h shows no "Edit" action — 0939fb9
- [x] 5.7 A mid-session expiry shows an inline error on Save and reverts to read-only — 0939fb9

### Phase 6: Habit — Data layer

#### Automated

- [x] 6.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 3a996ab
- [x] 6.2 Lint passes: `./gradlew.bat ktlintCheck` — 3a996ab
- [x] 6.3 Debug build compiles: `./gradlew.bat assembleDebug` — 3a996ab
- [x] 6.4 Robolectric round-trip test passes for `HabitCheckInDao.getByHabitAndDate`/`update` — 3a996ab
- [x] 6.5 `HabitRepositoryImplTest` passes (updateCheckIn retry-once, not-found) — 3a996ab

### Phase 7: Habit — Domain

#### Automated

- [x] 7.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 59cbe53
- [x] 7.2 Lint passes: `./gradlew.bat ktlintCheck` — 59cbe53
- [x] 7.3 `UpdateHabitCheckInUseCaseTest` passes (window-boundary cases via `Clock.fixed`) — 59cbe53

### Phase 8: Habit — Presentation

#### Automated

- [x] 8.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — b9900b1
- [x] 8.2 Lint passes: `./gradlew.bat ktlintCheck` — b9900b1
- [x] 8.3 `LogHabitCheckInsViewModelTest` passes (`selectableDates` now `[yesterday, today]`,
      existing cases still pass) — b9900b1
- [x] 8.4 `MainViewModelTest` passes (`HabitClicked` → `NavigateToHabitDetail`) — b9900b1
- [x] 8.5 `HabitDetailViewModelTest` passes (row derivation, partitioned Save) — b9900b1

### Phase 9: Habit — UI

#### Automated

- [x] 9.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — b8c2965
- [x] 9.2 Lint passes: `./gradlew.bat ktlintCheck` — b8c2965
- [x] 9.3 Debug build compiles and installs: `./gradlew.bat assembleDebug` — b8c2965

#### Manual

- [x] 9.4 Tapping a habit on Main opens Habit Detail showing Today/Yesterday — b8c2965
- [x] 9.5 Setting a not-yet-logged day's value and saving inserts it and persists across relaunch — b8c2965
- [x] 9.6 Changing an in-window already-logged day's value and saving updates it and persists — b8c2965
- [x] 9.7 (Debug-assisted) an expired day renders locked on Habit Detail — b8c2965
- [x] 9.8 Regression: add-entry, Log check-ins' insert flow (now Today/Yesterday only), and habit
      creation still work end-to-end — b8c2965
