# Main Screen Improvements Implementation Plan

## Overview

Rework the Main/Hub screen's journal and habit sections: a unified empty state for brand-new
users, capped lists (5 items) with "View all" screens once there's more, a new reusable
`:core:designsystem` sectioned-list component backing both sections, and a handful of adjacent
fixes (loading state, habit ordering, section sizing, a display bug) surfaced while researching
this screen.

## Current State Analysis

`MainScreen.kt`/`MainViewModel.kt` (see `context/changes/ui-improvements/research.md`, Main/Hub
section) currently:

- Renders `JournalSection`/`HabitSection` as raw `Column`/`Row` + `.clickable()` rows — no
  `DsCard`, no `DsHorizontalDivider`, no tap affordance.
- Shows **every** journal entry and habit with no cap — `MainViewModel`'s `combine()`
  (`MainViewModel.kt:92-129`) passes the full `List<JournalEntryUiState>`/`List<HabitUiState>`
  straight through from `ObserveJournalEntriesUseCase`/`ObserveHabitCheckInBoardUseCase`.
- Has **no loading state** — `MainUiState`'s seeded initial value
  (`MainViewModel.kt:58-75`) is empty lists, so a returning user can see a false "No entries
  yet"/"No habits yet" flash before the first `combine()` emission lands.
- Splits the two sections 50/50 via `Modifier.weight(1f)` on both
  (`MainScreen.kt:131-132`), regardless of how much content either has.
- Renders `HabitCheckInStatusUiState.NotLogged` as an empty string
  (`MainScreen.kt:380-381`), which reads as a rendering glitch next to a habit that does have a
  status.
- Habits render in `createdAt ASC` order (`HabitDao.kt:13`, unchanged since habit creation) with
  no other ordering option.

## Desired End State

- A brand-new user (zero journal entries, zero habits) sees one centered empty state with two
  CTAs ("Add journal entry", "Create habit") instead of two separate weak empty-state lines.
- Each section shows at most 5 items in a bordered, divided "section card" (the new
  `DsSectionedList` component); when there are more, the section ends with a "View all" row that
  opens a dedicated list screen for that content type.
- Both view-all screens support reordering via `DsChip` sort options.
- The screen shows a loading skeleton instead of a false-empty flash before the first data
  emission.
- Section heights are content-aware instead of a fixed 50/50 split.
- `NotLogged` shows a real label instead of an empty string.

Verified by: `./gradlew.bat ktlintCheck testDebugUnitTest` all green, plus manual verification
steps listed per phase.

### Key Discoveries:

- `HabitCheckInBoard` (`domain/model/HabitCheckInBoard.kt`) already carries **all** check-ins, not
  just today's — "most recently checked-in" ordering needs no new domain use case, it's a pure
  mapper-layer computation over data `MainViewModel` already collects.
- `Habit.createdAt: Instant` (`domain/model/Habit.kt`) is already available for the "Date created"
  sort option and the recency tiebreak — no domain model change needed.
- The existing `ContributionWindowUiState` chip-selection pattern in `MainViewModel`
  (`selectedJournalWindow`, `JournalWindowSelected` intent) is the established precedent for
  chip-driven sort/filter state — the two new view-all ViewModels follow the same shape.
- No skeleton/shimmer component exists in `:core:designsystem` yet — this plan adds a minimal
  static (non-animated) placeholder as part of the new component, not a separate one.

## What We're NOT Doing

- Search or date-range filtering on the view-all screens (sort chips only, per scope decision).
- A contribution-grid visualization for the Habit section (deferred to its own future change —
  which habit's grid would show with multiple habits is an open design question, not a quick add).
- Changing habit-row tap behavior (still navigates to habit detail, not a quick-log toggle) — only
  the `NotLogged` display bug is fixed, the deeper interaction redesign is out of scope.
- Any change to the account bottom sheet, FAB, or contribution grid rendering.
- Search/filter/pagination beyond a flat capped-then-full list (no infinite scroll, no page size
  tuning — local-first app, list sizes are expected to stay modest for the foreseeable future).

## Implementation Approach

Bottom-up: fix the data layer first (loading flag + habit ordering, Phase 1), build the reusable
UI primitive next since both Main and the two new screens depend on it (Phase 2), then wire Main
itself (Phase 3), then add the two new view-all screens that reuse everything from Phases 1-2
(Phase 4).

## Critical Implementation Details

**Section weighting formula**: each section's `Modifier.weight()` is
`1f + min(effectiveItemCount, 5)` where `effectiveItemCount` is the section's real item count
(pre-cap). An empty section still gets weight `1f` (room for its empty-state/CTA); a section at or
past the 5-item cap gets weight `6f`. This ties the sizing directly to the same cap already in
play instead of introducing a second, unrelated tuning constant.

**Habit sort computation**: sorting happens on domain data (`Habit` + `HabitCheckIn` list) *before*
mapping to `HabitUiState` — the UI model gains no new fields for this. `RECENTLY_CHECKED_IN` sorts
by `checkIns.filter { it.habitId == habit.id }.maxOfOrNull { it.date }` descending
(`LocalDate.MIN` for habits with zero check-ins ever, so they sort last), tiebreak by `createdAt`
ascending (oldest-created-first among equally-unchecked habits, matching today's implicit order).

**`DsSectionedList` scope boundary**: the component only ever renders a non-empty `items` list (or
its loading skeleton) — it does not know about "empty" as a state. Callers (Main screen, the two
view-all screens) branch around it entirely when their list is empty and render their own
empty-state content instead. This keeps the component's contract simple: title + N item rows +
optional "View all" trailing row, full stop.

## Phase 1: MainViewModel — loading state + habit recency sort

### Overview

Add the missing loading flag and move habit ordering from "whatever `observeHabits()` returns" to
an explicit, sortable mapper step reused later by the Habit view-all screen.

### Changes Required:

#### 1. Habit sort mapper

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/HabitSortUiState.kt` (new)

**Intent**: Define the sort modes both the Main screen (implicitly, always
`RECENTLY_CHECKED_IN`) and the Habit view-all screen (user-selectable via chips) use.

**Contract**: `enum class HabitSortUiState { RECENTLY_CHECKED_IN, ALPHABETICAL, DATE_CREATED }`.

#### 2. Sorting mapper function

**File**: `app/src/main/java/pl/luczka/todaywas/ui/mapper/HabitMapper.kt`

**Intent**: Replace the direct `HabitCheckInBoard.toHabitUiStates(today)` call sites that need
ordering control with a sort-aware variant, without touching the existing today-status mapping
logic.

**Contract**: Add
`fun HabitCheckInBoard.toSortedHabitUiStates(today: LocalDate, sort: HabitSortUiState): List<HabitUiState>`
that sorts `habits` per the rule in "Critical Implementation Details" (for `ALPHABETICAL`/
`DATE_CREATED`, sort by `name`/`createdAt` respectively) then delegates each habit to the existing
`Habit.toUiState(todayCheckIn)`. Keep the old `toHabitUiStates` unchanged (or have it delegate to
the new function with `RECENTLY_CHECKED_IN` as the default) — check existing call sites/tests
before deciding which.

#### 3. MainUiState loading flag

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiState.kt`

**Intent**: Let the screen distinguish "still loading" from "genuinely empty."

**Contract**: Add `val isLoading: Boolean`.

#### 4. MainViewModel wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Seed `isLoading = true`, flip to `false` on the first `combine()` emission, and use
`toSortedHabitUiStates(today, HabitSortUiState.RECENTLY_CHECKED_IN)` instead of the current
`toHabitUiStates(today)` call (`MainViewModel.kt:101`) so Main's habit ordering matches the new
default.

**Contract**: No new public API — internal state-update change only. `isLoading` set unconditionally
`false` inside the existing `.collect { combined -> ... }` block (harmless no-op after the first
emission).

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat :app:compileDebugKotlin` passes
- `./gradlew.bat :app:testDebugUnitTest --tests "*Habit*Mapper*" --tests "*MainViewModel*"` passes
- New/updated unit tests cover: recency sort with mixed checked/unchecked habits, alphabetical
  sort, date-created sort, `isLoading` flips false after first emission

#### Manual Verification:

- N/A for this phase (no UI change yet) — covered by Phase 3

---

## Phase 2: `DsSectionedList` component

### Overview

A new `:core:designsystem` component: a titled section card holding up to N rows (divided,
`DsCard`-wrapped), an optional trailing "View all" row, and a loading-skeleton variant.

### Changes Required:

#### 1. Component

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/lists/DsSectionedList.kt` (new)

**Intent**: A generic, reusable "section with a header, up to N divided rows in one card, optional
overflow action" component — the same shape Journal and Habit sections need today, and either new
view-all screen's own sections could reuse later.

**Contract**:
```kotlin
@Composable
fun <T> DsSectionedList(
    title: String,
    items: List<T>,
    isLoading: Boolean,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    onViewAllClicked: (() -> Unit)? = null,
    viewAllLabel: String? = null,
)
```
Renders `title` as a `DsText` header above a single `DsCard`. Inside the card: if `isLoading`,
render a fixed number (3) of static placeholder rows (muted rounded-rect shapes — no shimmer
animation) separated by `DsHorizontalDivider`; otherwise render `itemContent(item)` per item,
`DsHorizontalDivider` between consecutive rows (not after the last). If `onViewAllClicked` is
non-null, append it as a final row (separated by its own divider) with `viewAllLabel` text and a
trailing chevron icon, tappable. Caller must never pass an empty `items` list while
`isLoading == false` — document this as the component's contract (see "Critical Implementation
Details").

Ships `@PreviewLightDark` previews covering: populated, loading, and with/without the "View all"
row (use a `PreviewParameterProvider`, per project convention for multi-state components).

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat :core:designsystem:compileDebugKotlin` passes

#### Manual Verification:

- Open `DsSectionedListPreview` in Android Studio (or an emulator) and confirm loading, populated,
  and "View all" variants render correctly in both light and dark theme

---

## Phase 3: Main screen integration

### Overview

Rebuild `JournalSection`/`HabitSection` on `DsSectionedList`, add the unified/per-section empty
states, apply the new section-weighting formula, and fix the `NotLogged` label.

### Changes Required:

#### 1. Empty-state strings

**File**: `app/src/main/res/values/strings.xml`

**Intent**: Copy for the unified zero-everything empty state and the two "View all" row labels.

**Contract**: New keys — `main_empty_state_title`, `main_empty_state_description`,
`main_journal_view_all_cta`, `main_habit_view_all_cta`, and
`habit_checkin_not_logged_label` (replacing the current empty-string `NotLogged` case in
`MainScreen.kt:380-381`).

#### 2. MainScreen.kt rewrite

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Replace the raw-Column rendering with `DsSectionedList`, branch to the unified empty
state when both lists are empty (and to the loading skeleton when `uiState.isLoading`), give each
per-section empty state a real CTA button instead of plain text, and apply the weighting formula
from "Critical Implementation Details" to `JournalSection`/`HabitSection`'s
`Modifier.weight(...)` (replacing the current flat `weight(1f)` at `MainScreen.kt:131-132`).

**Contract**: `JournalEntryListItem`/`HabitListItem` become the `itemContent` lambdas passed to
`DsSectionedList` — their internal content stays the same (still `.clickable`), just no longer
individually wrapped in their own `Column`/`Row` root (that's now `DsSectionedList`'s job per row).
The unified empty state's two CTAs dispatch the *existing* `MainIntent.FabActionClicked` intents
(`ADD_JOURNAL_ENTRY`/`CREATE_HABIT`) — no new intents needed. "View all" rows fire two *new*
one-shot navigation events.

#### 3. New navigation events

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiEvent.kt`

**Intent**: Let Main tell the host `NavDisplay` to open a view-all screen.

**Contract**: Add `data object NavigateToJournalList : MainUiEvent` and
`data object NavigateToHabitList : MainUiEvent`.

#### 4. New intents + ViewModel wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainIntent.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Wire the two new "View all" taps through to the new events.

**Contract**: Add `JournalViewAllClicked`/`HabitViewAllClicked` to `MainIntent`, each a one-line
`onIntent` branch sending the corresponding new `MainUiEvent`.

#### 5. MainScreen callback + nav wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Thread the two new navigation events out to `TodayWasApp.kt`'s `entry<MainKey>` block,
matching the existing pattern for every other `MainUiEvent`.

**Contract**: `MainScreen` gains `onJournalListClicked: () -> Unit` /
`onHabitListClicked: () -> Unit` params; `TodayWasApp.kt`'s `MainScreen(...)` call adds
`backStack.add(JournalListKey)` / `backStack.add(HabitListKey)` (new keys defined in Phase 4).

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat :app:compileDebugKotlin` passes
- `./gradlew.bat :app:testDebugUnitTest --tests "*MainViewModel*"` passes (new tests: view-all
  intents emit the right events; unified-empty vs per-section-empty state derivation if any logic
  moved into the ViewModel rather than pure Compose branching)

#### Manual Verification:

- Fresh install (or clear local data): Main shows the unified empty state with both CTAs; tapping
  either opens the right screen
- Add 6+ journal entries: Journal section shows 5 + a "View all" row; tapping it is a no-op until
  Phase 4 lands the destination (acceptable — Phase 4 immediately follows)
- Add 6+ habits with mixed check-in recency: Habit section shows the 5 most-recently-checked-in,
  ordered correctly, no `NotLogged` blank cells
- A section with content next to an empty section: the content section visibly takes more height
  than the empty one, but the empty one still shows its CTA without being cramped
- Toggle light/dark theme, confirm no regressions

**Implementation Note**: Pause here for manual confirmation before Phase 4.

---

## Phase 4: View-all screens

### Overview

Two new screens — `JournalListScreen`/`HabitListScreen` — each a full, unsorted-by-default-except
`DsSectionedList`-less flat list (no cap) with `DsChip` sort controls, reusing the row composables
and sort mapper from Phases 1-3.

### Changes Required:

#### 1. Nav keys

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasKey.kt`

**Intent**: Two new flat destinations, no params needed (each screen owns its own full-list
ViewModel state).

**Contract**: `data object JournalListKey : TodayWasKey` and
`data object HabitListKey : TodayWasKey`.

#### 2. Journal sort model

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/JournalSortUiState.kt` (new)

**Intent**: Sort options for the journal view-all screen.

**Contract**: `enum class JournalSortUiState { NEWEST_FIRST, OLDEST_FIRST }`.

#### 3. JournalListScreen + ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/list/` (new package: `JournalListScreen.kt`,
`JournalListViewModel.kt`, `JournalListIntent.kt`, `JournalListUiState.kt`, `JournalListUiEvent.kt`)

**Intent**: Full journal list, sortable by date direction, tapping an entry opens its existing
detail screen (`JournalEntryDetailKey`), a back action returns to Main.

**Contract**: `JournalListViewModel` observes `ObserveJournalEntriesUseCase()` directly (same use
case `MainViewModel` already uses), holds `selectedSort: JournalSortUiState` (default
`NEWEST_FIRST`, matching the DAO's existing `date DESC` order), re-sorts in-memory on chip
selection (`entries.sortedByDescending/sortedBy { it.date }`) — no new query needed since the DAO
already returns the full list. `JournalListUiState` mirrors the `isLoading` pattern from Phase 1.
No cap, no `DsSectionedList` "View all" row (this *is* the "view all" destination) — a plain
`DsChip` row for sort, then every entry rendered via the same row composable/click behavior as
Main's `JournalEntryListItem` (extract a small shared composable if duplicating verbatim, per the
project's cross-layer-mapper convention of not inlining shared UI twice).

#### 4. HabitListScreen + ViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/list/` (new package: `HabitListScreen.kt`,
`HabitListViewModel.kt`, `HabitListIntent.kt`, `HabitListUiState.kt`, `HabitListUiEvent.kt`)

**Intent**: Full habit list, sortable via the three `HabitSortUiState` modes from Phase 1, tapping
a habit opens its existing detail screen (`HabitDetailKey`), a back action returns to Main.

**Contract**: `HabitListViewModel` observes `ObserveHabitCheckInBoardUseCase()` directly, holds
`selectedSort: HabitSortUiState` (default `RECENTLY_CHECKED_IN`, matching Main's default), calls
the Phase 1 `toSortedHabitUiStates(today, selectedSort)` mapper on each emission plus whenever
`selectedSort` changes. Three `DsChip`s for the sort options, then every habit rendered via the
same row composable/click behavior as Main's `HabitListItem`.

#### 5. TodayWasApp.kt wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Register the two new entries and connect Phase 3's `NavigateToJournalList`/
`NavigateToHabitList` events to them.

**Contract**: `entry<JournalListKey> { JournalListScreen(onBack = { backStack.removeLastOrNull() }, onEntryClicked = { id -> backStack.add(JournalEntryDetailKey(id)) }) }` and the equivalent for
`HabitListKey`/`HabitDetailKey`. Update `entry<MainKey>`'s `MainScreen(...)` call to pass
`onJournalListClicked = { backStack.add(JournalListKey) }` /
`onHabitListClicked = { backStack.add(HabitListKey) }`.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat :app:compileDebugKotlin` passes
- `./gradlew.bat :app:testDebugUnitTest` passes (new `JournalListViewModelTest`,
  `HabitListViewModelTest` covering: default sort order, each sort chip's resulting order, empty
  state, loading flag)

#### Manual Verification:

- From Main with 6+ journal entries, tap "View all" — see every entry, toggle Newest/Oldest chips
  and confirm order flips, tap an entry and confirm it opens the same detail screen Main's rows do,
  back returns to Main
- From Main with 6+ habits, tap "View all" — see every habit, toggle all three sort chips and
  confirm each produces the expected order, tap a habit and confirm it opens habit detail, back
  returns to Main
- Toggle light/dark theme on both new screens, confirm no regressions

---

## Testing Strategy

### Unit Tests:

- `HabitMapperTest` (or extend existing): `toSortedHabitUiStates` for all three sort modes,
  including the zero-check-ins tiebreak case
- `MainViewModelTest`: `isLoading` transitions, habit ordering uses the new sorted mapper, new
  view-all intents emit the right events
- `JournalListViewModelTest` / `HabitListViewModelTest` (new): default order, each sort option,
  empty state

### Manual Testing Steps:

1. Fresh install → confirm unified empty state, both CTAs work
2. Add exactly 5 journal entries → confirm no "View all" row appears (boundary case)
3. Add a 6th → confirm "View all" row appears and the section shows only the 5 most recent
4. Repeat 2-3 for habits, with varied check-in recency to verify sort correctness
5. Full light/dark pass on Main and both new screens

## Performance Considerations

None expected — all lists are still fully loaded into memory (no new pagination), just sliced/
sorted in Kotlin. Given the app's local-first, single-user scope, list sizes are not expected to
be large enough for this to matter; revisit if that assumption changes.

## Migration Notes

No data migration — this is UI/mapper-layer only, no schema or stored-data changes.

## References

- Research: `context/changes/ui-improvements/research.md` (Main / Hub Screen section)
- Screen graph: `context/changes/ui-improvements/screen-graph.md`
- Chip-driven state precedent: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:202-204`
  (`onJournalWindowSelected`)
- Assisted-injection-free new-screen precedent: `HabitDetailKey`/`JournalEntryDetailKey` (need
  params) vs. `CreateHabitKey`/`LogHabitCheckInsKey` (plain `data object`, matching the new
  `JournalListKey`/`HabitListKey` shape)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: MainViewModel — loading state + habit recency sort

#### Automated

- [x] 1.1 ktlintCheck passes
- [x] 1.2 compileDebugKotlin passes
- [x] 1.3 Habit sort mapper + MainViewModel unit tests pass

### Phase 2: DsSectionedList component

#### Automated

- [x] 2.1 ktlintCheck passes
- [x] 2.2 core:designsystem compileDebugKotlin passes

#### Manual

- [ ] 2.3 Loading/populated/"View all" preview variants confirmed in light + dark

### Phase 3: Main screen integration

#### Automated

- [x] 3.1 ktlintCheck passes
- [x] 3.2 compileDebugKotlin passes
- [x] 3.3 MainViewModel unit tests pass

#### Manual

- [ ] 3.4 Unified empty state + CTAs verified on fresh install
- [ ] 3.5 6+ journal entries: capped list + "View all" row verified
- [ ] 3.6 6+ habits: recency ordering + no blank NotLogged cells verified
- [ ] 3.7 Section weighting verified (content section taller than empty section)
- [ ] 3.8 Light/dark pass with no regressions

### Phase 4: View-all screens

#### Automated

- [x] 4.1 ktlintCheck passes
- [x] 4.2 compileDebugKotlin passes
- [x] 4.3 JournalListViewModelTest + HabitListViewModelTest pass

#### Manual

- [ ] 4.4 Journal view-all: full list, sort chips, entry tap, back — all verified
- [ ] 4.5 Habit view-all: full list, sort chips, habit tap, back — all verified
- [ ] 4.6 Light/dark pass on both new screens
