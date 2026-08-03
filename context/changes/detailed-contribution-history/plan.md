# Detailed contribution history Implementation Plan

## Overview

Build a GitHub-contribution-style history grid for both journal and habits (FR-011), replacing
S-04's placeholder `HabitDetailScreen` list with a real grid, embedding an equivalent grid directly
into Main's Journal section, and moving both verticals' editing interactions from
inline/whole-screen edit modes into a `ModalBottomSheet`.

## Current State Analysis

- No color-intensity, contribution, or heatmap logic exists anywhere in the codebase today. Main
  screen renders journal entries and habits as plain text `LazyColumn` rows
  (`ui/main/MainScreen.kt:141-225`) — the PRD's "colored contribution cell" language was already
  deliberately deferred out of Main and into this separate FR-011 capability (FR-005's Socrates
  note in `context/foundation/prd.md`).
- `HabitDetailScreen`/`HabitDetailViewModel`/`HabitDetailUiState`/`HabitDetailMapper`
  (`ui/habit/`) were built in S-04 as an explicit placeholder: a plain `LazyColumn` of
  `HabitDetailRowUiState` rows (today/yesterday always present + every logged date, full
  unbounded history, no pagination), with a whole-screen `isEditMode` toggle gating a
  `TodayWasSegmentedRow` per row. This is the screen this change rebuilds.
- `JournalEntryDetailScreen`/`JournalEntryDetailViewModel`/`JournalEntryDetailUiState`
  (`ui/journal/`) show a single entry with an inline `isEditing` swap
  (`TodayWasText` ↔ `TodayWasTextField`, `JournalEntryDetailScreen.kt:104-118`) — no history/list
  view exists for journal at all. The only existing "list" of journal entries is Main's flat,
  unfiltered `LazyColumn` (`MainViewModel.kt:54,60`, via `ObserveJournalEntriesUseCase`).
- `ObserveHabitCheckInBoardUseCase` and `ObserveJournalEntriesUseCase` already return **all**
  check-ins/entries, unfiltered by date — both existing verticals already do client-side filtering
  downstream (`HabitDetailViewModel.kt:79-90`), so no new DB queries or repository methods are
  needed; the new contribution-grid math is pure computation over data already flowing through
  these use cases.
- No `LazyHorizontalGrid`/`LazyVerticalGrid` exists anywhere in the codebase (only `LazyColumn` and
  `HorizontalPager`, e.g. `TodayWasDateStrip.kt:51`) — this change introduces the first grid layout.
- No `ModalBottomSheet`/`BottomSheetScaffold` exists anywhere in the codebase — this change
  introduces the first bottom sheet.
- `TodayWasChip` (`core/designsystem/.../TodayWasChip.kt`) is a static display-only wrapper over M3
  `SuggestionChip` with a hardcoded `onClick = {}` — no selected/toggle state. Its one existing
  caller, `LogHabitCheckInsScreen.kt:157-161` (a static "Done" chip), passes only `text` +
  `containerColor`/`labelColor`, so adding new defaulted parameters keeps that call site
  source-compatible.
- `TodayWasTheme` has `dynamicColor = true` by default (`ui/theme/Theme.kt:38`) — Material You
  colors vary per device/wallpaper, so intensity shading cannot be built on `MaterialTheme.colorScheme`
  roles; it needs its own fixed palette (the codebase already has an unused precedent for this:
  `SuccessContainerLight/Dark` in `ui/theme/Color.kt:13-16`, hardcoded and never wired into
  `ColorScheme`).

## Desired End State

- **Habit Detail** (`HabitDetailScreen`, reached by tapping a habit on Main, unchanged nav entry
  point): shows a read-only GitHub-style contribution grid of that habit's check-ins, colored by a
  5-level quantile bucket relative to the habit's own all-time value range. A chip row above the
  grid lets the user pick "Last 12 months" (default) or a specific calendar year. An "Edit" top-bar
  action (shown only when at least one row is currently eligible for edit) opens a
  `ModalBottomSheet` containing the same editable-rows list S-04 built (today/yesterday + in-window
  logged days), with Save/Cancel inside the sheet; dismissing the sheet (swipe/scrim tap) discards
  pending edits, same as Cancel.
- **Main screen's Journal section**: between the section header and the existing entries list, a
  read-only contribution grid + the same chip-row window picker appears, showing which days have a
  journal entry (binary presence, not quantile-bucketed — journal has no numeric value). The
  existing entries list below is untouched.
- **Journal Entry Detail** (`JournalEntryDetailScreen`): the inline text-swap edit interaction is
  replaced by the same `ModalBottomSheet` pattern — tapping "Edit" opens a sheet with the editable
  text field and Save/Cancel; the screen body itself is always read-only display.
- Verify via: open a habit with several check-ins spanning different values → confirm the grid
  shows visibly different shades and that switching the year chip changes the visible date range
  without changing any day's shade; edit a check-in through the new sheet and confirm the grid
  updates after Save; do the same for a journal entry from Main's embedded grid + detail sheet.

### Key Discoveries:

- Quantile bucketing must be computed from a habit's **entire history**, not just the currently
  selected window — otherwise switching between "Last 12 months" and a specific year would
  recolor the same day differently depending on which window happens to be selected, which
  contradicts "intensity relative to the user's own historical range" (the range is all-time, the
  window is only a display filter).
- The list-rendering and edit/save logic already built in `HabitDetailViewModel`/`Screen`
  (S-04) — `pendingValues`, per-row `eligibleForEdit`/`alreadyLogged`, the insert-vs-update Save
  partition — carries over into the new bottom sheet essentially unchanged; only its container
  changes from "always-visible screen body" to "sheet content," and `isEditMode` is renamed
  `isEditSheetOpen` to match.
- `JournalEntryDetailViewModel`'s state/intents (`editedText`, `TextChanged`, `SaveClicked`,
  `CancelEditClicked`) also carry over unchanged into a sheet — only `JournalEntryDetailScreen`'s
  layout changes.

## What We're NOT Doing

- No tap-to-edit interaction on grid cells — the grid is a pure read-only visualization in both
  verticals; editing only happens via the explicit "Edit" action opening a bottom sheet (per this
  session's decision).
- No separate journal history screen — the journal grid is embedded directly on Main's Journal
  section (header → grid → chips → entries), not a new nav destination. Per this session's
  decision, a dedicated navigation model (e.g. bottom nav tabs for Journal/Habits) is explicitly
  deferred to future work.
- No calendar-month grid view or a user-facing toggle between grid styles — GitHub-style
  weeks-as-columns is the only layout built now; a future calendar-mode switch (and any user
  preference for it) is out of scope for this change.
- No pagination of grid data — a window is at most one calendar year (365-366 days); this stays
  well within the "no pagination needed at MVP scale" convention already established in prior
  slices.
- No new DB columns, queries, or repository methods — `ObserveHabitCheckInBoardUseCase` and
  `ObserveJournalEntriesUseCase` already return everything needed; this change is pure
  computation + presentation on top of existing reads.
- No change to `LogHabitCheckInsScreen`'s insert-only Today/Yesterday flow.

## Implementation Approach

One shared foundation phase (pure domain calculators for both verticals + a new reusable
`:core:designsystem` grid component and an extended `TodayWasChip`), followed by
Presentation-then-UI phases per vertical. There is no separate per-vertical "domain" phase: the
color-bucketing math is pure computation with no repository dependency (unlike `EditWindow`, which
needed a `Clock` seam but still no repository), so it lives entirely in Phase 1 and both verticals'
ViewModels call it directly using data they already collect via existing use cases.

## Critical Implementation Details

**Grid weekday alignment.** `TodayWasContributionGrid` takes a sparse `cells: Map<LocalDate,
TodayWasContributionLevel>` (real window dates only — no padding entries). Internally it must pad
backward from `cells.keys.min()` to the preceding Monday (`date.minusDays((date.dayOfWeek.value -
1).toLong())`) before generating the full date sequence fed to `LazyHorizontalGrid(rows =
GridCells.Fixed(7))`, otherwise the first column's rows won't align to a consistent weekday across
columns (`LazyHorizontalGrid` lays out items column-major in the order given, so misaligned
starting dates would visibly shift every subsequent column). Dates without a `cells` entry
(including the padding dates) render as `NONE`.

**Fixed intensity palette, not `MaterialTheme.colorScheme`.** `TodayWasTheme` has `dynamicColor =
true`, so `colorScheme.primary`/etc. vary per device/wallpaper (Material You) — using them for
intensity shading would make relative comparisons across days meaningless. `TodayWasContributionGrid`
must use its own fixed `Color` constants (separate light/dark arrays selected via
`isSystemInDarkTheme()`), independent of `MaterialTheme.colorScheme` entirely.

**Bottom sheet dismiss discards, same as Cancel.** For both the Habit Detail edit sheet and the
Journal Entry Detail edit sheet, `ModalBottomSheet`'s `onDismissRequest` (swipe-down, scrim tap)
must dispatch the same `CancelEditClicked` intent as the explicit Cancel button — otherwise a user
could dismiss the sheet without the pending-edit-discard path running, leaving stale
`pendingValues`/`editedText` state around for the next time the sheet opens.

**Journal's contribution level is a fixed constant, not a computed value.** Journal entries have no
numeric field to bucket — `JournalContributionCalculator` maps any date with an entry straight to a
single fixed `ContributionLevel.LEVEL_3`, with no percentile/threshold logic at all. Don't invent a
"word count" or similar proxy metric; that was explicitly decided against.

## Phase 1: Shared foundation (domain calculators + grid/chip components)

### Overview

The pure business-logic layer (window selection, level bucketing, grid data shape) and the
reusable UI primitives (grid rendering, selectable chip) both verticals build on.

### Changes Required:

#### 1. Contribution window model

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/ContributionWindow.kt` (new)

**Intent**: Represent the two ways a user can scope the grid — a rolling trailing window, or a
specific calendar year — and derive which years are actually selectable from a vertical's own data.

**Contract**: `sealed interface ContributionWindow` with `data object RollingTwelveMonths` and
`data class CalendarYear(val year: Int)`. An extension `fun ContributionWindow.dateRange(now:
Instant): ClosedRange<LocalDate>` — `RollingTwelveMonths` is `today.minusMonths(12).plusDays(1)..today`
(today in the system default zone); `CalendarYear` is `Jan 1..Dec 31` of that year. A companion
function `fun availableWindows(earliestDataDate: LocalDate?, now: Instant): List<ContributionWindow>`
returns `[RollingTwelveMonths]` followed by every year from the current year down to
`earliestDataDate?.year ?: currentYear`, descending (e.g. today mid-2026 with data starting 2026
yields `[RollingTwelveMonths, CalendarYear(2026)]`).

#### 2. Contribution level + grid

**Files**: `app/src/main/java/pl/luczka/todaywas/domain/model/ContributionLevel.kt`,
`ContributionGrid.kt` (both new)

**Intent**: A pure, vertical-agnostic representation of "how intense was this day," shared by both
calculators below.

**Contract**: `enum class ContributionLevel { NONE, LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5 }`.
`data class ContributionGrid(val window: ContributionWindow, val days: Map<LocalDate,
ContributionLevel>)` — `days` only contains entries within `window`'s date range; a date absent
from the map is implicitly `NONE`.

#### 3. Habit contribution calculator

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/HabitContributionCalculator.kt` (new)

**Intent**: Bucket each check-in's value into one of 5 levels, relative to that habit's own
all-time value distribution (per Key Discoveries — the range is all-time, the window only filters
which days are shown).

**Contract**: `object HabitContributionCalculator` with `fun compute(checkIns: List<HabitCheckIn>,
window: ContributionWindow, now: Instant): ContributionGrid`. For each date in `window.dateRange(now)`
that has a check-in, its level is:

```kotlin
val sortedValues = checkIns.map { it.value }.sorted()
val percentileRank = sortedValues.count { it <= value }.toDouble() / sortedValues.size
val level = ceil(percentileRank * 5).toInt().coerceIn(1, 5) // 1..5 → LEVEL_1..LEVEL_5
```

A date with no check-in is absent from `days` (`NONE`). If `checkIns` is empty, `days` is empty
(every date renders `NONE`).

#### 4. Journal contribution calculator

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/JournalContributionCalculator.kt` (new)

**Intent**: Presence-only coloring for journal (no numeric value to bucket — see Critical
Implementation Details).

**Contract**: `object JournalContributionCalculator` with `fun compute(entries: List<JournalEntry>,
window: ContributionWindow, now: Instant): ContributionGrid` — every date in `window.dateRange(now)`
with a matching entry maps to `ContributionLevel.LEVEL_3`; dates without an entry are absent
(`NONE`).

#### 5. Contribution grid design-system component

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasContributionGrid.kt` (new)

**Intent**: The visual grid itself — GitHub-style weeks-as-columns, days-as-rows, horizontally
scrollable, pure read-only rendering. `:core:designsystem` cannot depend on `:app`'s `domain.model`,
so this takes its own local enum, not the domain `ContributionLevel`.

**Contract**: `enum class TodayWasContributionLevel { NONE, LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4,
LEVEL_5 }` (mirrors the domain enum 1:1; mapped in `app`'s `ui/model` layer, see Phase 2/4).
`@Immutable data class TodayWasContributionCells(val values: Map<LocalDate,
TodayWasContributionLevel>)` wraps the sparse cell map — a raw `Map` parameter is unstable to the
Compose compiler (interface with mutable subtypes) and would defeat recomposition-skipping for this
~370-cell grid on every unrelated recomposition of the caller; a bug caught during Phase 3 manual
verification ("performance sucks") and fixed here. `@Composable fun TodayWasContributionGrid(startDate:
LocalDate, endDate: LocalDate, cells: TodayWasContributionCells, modifier: Modifier = Modifier)`
renders via `LazyHorizontalGrid(rows = GridCells.Fixed(7), reverseLayout = true)` — the first
`Lazy*Grid` usage in this codebase. The date list is padded to whole weeks at *both* ends (preceding
Monday .. following Sunday, per the weekday-alignment requirement in Critical Implementation
Details) and generated newest-first; combined with `reverseLayout = true` this anchors the most
recent week at the visual end of the row with no blank trailing space, regardless of viewport width
— a bug also caught during Phase 3 manual verification ("start grid display from more recent
entries"); an explicit initial-scroll-index can't achieve this without knowing how many columns fit
on screen, so `reverseLayout` is the correct fix, not a workaround. The date list itself is
`remember`-cached on `(startDate, endDate)`. A small internal `contentPadding` (4dp horizontal) on
the `LazyHorizontalGrid` keeps cells from sitting flush against the component's own bounds — part of
the same Phase 3 fix as the caller-side full-bleed layout change (Phase 3 #1). Each cell is a small
(~12dp) rounded-square `Box` colored per level from a fixed, non-`MaterialTheme.colorScheme` palette
(light/dark constant arrays chosen via `isSystemInDarkTheme()`, per Critical Implementation
Details). `startDate`/`endDate` (the
caller's selected window bounds) drive the rendered range, not `cells.values.keys` — `cells` only
contains non-`NONE` days, so deriving bounds from it would collapse a mostly-empty window down to
just its few logged days instead of the full selected range (a bug caught during Phase 1
implementation; adapted here). No `onClick`/interaction parameter — purely read-only. Ships
`@PreviewLightDark` with a `PreviewParameterProvider` covering: an empty window (no data at all,
full range still renders), a sparse few-day grid, and a full 5-level gradient.

#### 6. Selectable chip support

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasChip.kt`

**Intent**: Support a single-select chip row (the window picker) without breaking the one existing
static-chip caller.

**Contract**: Add `selected: Boolean = false` and change `onClick: () -> Unit = {}` from a hardcoded
no-op to a real threaded parameter; swap the underlying M3 component from `SuggestionChip` to
`FilterChip` (native selected-state support), adding `selectedContainerColor: Color =
MaterialTheme.colorScheme.primary` / `selectedLabelColor: Color = MaterialTheme.colorScheme.onPrimary`
params consumed via `FilterChipDefaults.filterChipColors(...)`. `LogHabitCheckInsScreen.kt:157-161`'s
existing call (no `selected`/`onClick` passed) stays source-compatible via the new defaults. Add a
`selected = true` case to its `@PreviewLightDark` `PreviewParameterProvider`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles: `./gradlew.bat assembleDebug`
- `ContributionWindowTest` passes: `dateRange` for both variants; `availableWindows` for no data,
  same-year data, and multi-year data
- `HabitContributionCalculatorTest` passes: empty check-ins → all `NONE`; single distinct values
  spread across the full 1..5 range; identical values across all check-ins → all map to the same
  level (documented expected behavior, not a bug — see Critical Implementation Details' spirit);
  a date outside `checkIns` but inside the window → `NONE`; window filtering excludes out-of-range
  dates even when they have check-ins
- `JournalContributionCalculatorTest` passes: entries present → `LEVEL_3`; no entry → `NONE`;
  window filtering excludes out-of-range entries

### Manual Verification:

- `TodayWasContributionGrid` and `TodayWasChip` previews render correctly in both light and dark
  mode in Android Studio's preview pane

---

## Phase 2: Habit — Presentation

### Overview

Rewire `HabitDetailViewModel` to compute and expose the contribution grid + window selection,
reshaping the existing batch-edit state into a bottom-sheet-open flag.

### Changes Required:

#### 1. ViewModel state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailViewModel.kt`

**Intent**: Add grid + window-selection state; rename the screen-level edit toggle to reflect it
now gates a sheet, not the whole screen.

**Contract**: `HabitDetailViewModelState` gains `selectedWindow: ContributionWindow` (default
`RollingTwelveMonths`) and `availableWindows: List<ContributionWindow>` (recomputed whenever
`checkIns` updates, via `ContributionWindow.availableWindows(checkIns.minOfOrNull { it.date }, clock.instant())`).
`isEditMode` is renamed `isEditSheetOpen` throughout (state, UI state, and the
`EditClicked`/`CancelEditClicked`/`SaveClicked` handlers already in place — same logic, new name).
`toUiState(now)` additionally computes `contributionGrid =
HabitContributionCalculator.compute(checkIns, selectedWindow, now).toUiState()` (new
`ui/model/ContributionMapper.kt` — see below).

#### 2. Intent + UI state

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailIntent.kt`,
`HabitDetailUiState.kt`

**Intent**: Carry the new grid/window data and window-selection intent.

**Contract**: `HabitDetailIntent` gains `data class WindowSelected(val window:
ContributionWindowUiState)`. `HabitDetailUiState` gains `contributionGrid: ContributionGridUiState`
(see mapper below — carries `startDate`/`endDate`/`cells`, matching
`TodayWasContributionGrid`'s corrected signature), `availableWindows:
List<ContributionWindowUiState>`, `selectedWindow: ContributionWindowUiState`; renames
`isEditMode` to `isEditSheetOpen`.

#### 3. Cross-layer mapper

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/ContributionMapper.kt` (new)

**Intent**: Map domain contribution types to their UI-facing equivalents, per the project's
mapping-lives-in-its-own-file convention.

**Contract**: `data class ContributionGridUiState(val startDate: LocalDate, val endDate: LocalDate,
val cells: Map<LocalDate, TodayWasContributionLevel>)` — carries the window's real bounds
alongside the sparse cell map, since `TodayWasContributionGrid` (Phase 1) needs both to render a
mostly-empty window correctly. `fun ContributionGrid.toUiState(now: Instant): ContributionGridUiState`
maps `days` 1:1 (via `ContributionLevel.toUiState()`) and derives `startDate`/`endDate` from
`window.dateRange(now)`. `fun ContributionLevel.toUiState(): TodayWasContributionLevel` (1:1).
`sealed interface ContributionWindowUiState` mirroring the domain type (`RollingTwelveMonths`,
`data class CalendarYear(val year: Int)`) plus `fun ContributionWindow.toUiState():
ContributionWindowUiState` / a reverse mapper for the intent round-trip. Display labels ("Last 12
months" vs. the year number) are derived in the Composable via `stringResource`, not baked into the
UI state, per this project's strings.xml convention.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `HabitDetailViewModelTest` passes: `contributionGrid`/`availableWindows`/`selectedWindow` reflect
  loaded check-ins; `WindowSelected` updates `selectedWindow` and recomputes `contributionGrid`
  without changing any already-visible day's level; existing edit/save/cancel test cases from S-04
  still pass under the `isEditSheetOpen` rename

---

## Phase 3: Habit — UI

### Overview

Replace `HabitDetailScreen`'s always-visible editable list with the read-only grid + chip row, and
move the existing row-list editing into a `ModalBottomSheet`.

### Changes Required:

#### 1. Screen body

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailScreen.kt`

**Intent**: Grid + chips become the screen's primary (and only default-visible) content.

**Contract**: Body becomes `TodayWasContributionGrid(startDate = uiState.contributionGrid.startDate,
endDate = uiState.contributionGrid.endDate, cells =
TodayWasContributionCells(uiState.contributionGrid.cells), modifier = Modifier.fillMaxWidth())` —
**full-bleed, no horizontal content padding** (unlike the habit name text and chip row around it,
which each carry their own `padding(horizontal = 24.dp)`) — a bug caught during Phase 3 manual
verification ("grid horizontal padding cuts content on sides"): the screen's outer 24dp inset was
visibly shrinking the grid's usable width. Followed by a `LazyRow` of `TodayWasChip` per
`uiState.availableWindows` (`selected = window == uiState.selectedWindow`, `onClick = {
onIntent(HabitDetailIntent.WindowSelected(window)) }`, label via
`stringResource(R.string.contribution_window_last_12_months_label)` or `year.toString()`). Top bar's
Edit icon shows whenever `uiState.rows.any { it.eligibleForEdit }`, **regardless of
`isEditSheetOpen`** — it stays visible while the sheet is open too, per this session's decision
(tapping it again while open is a harmless no-op, since `EditClicked` just re-sets an already-true
flag).

#### 2. Edit bottom sheet

**Files**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasBottomSheet.kt`
(new), `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailScreen.kt`

**Intent**: Reuse S-04's row-list editing UI, moved into a sheet — as a reusable
`:core:designsystem` component rather than an inline `ModalBottomSheet`, per this project's
build-on-M3-slotted-components convention (also needed as-is by Phase 5's journal edit sheet).
Two more adaptations landed here after Phase 3 manual verification: the sheet only lists rows the
user can actually act on, and its actions moved into a fixed top bar instead of a trailing row.

**Contract**: New `TodayWasBottomSheet(onDismissRequest: () -> Unit, onCloseClicked: () -> Unit,
closeContentDescription: String, saveText: String, onSaveClicked: () -> Unit, modifier: Modifier =
Modifier, isSaving: Boolean = false, saveEnabled: Boolean = true, content: @Composable
ColumnScope.() -> Unit)` wraps M3's `ModalBottomSheet`, rendering a fixed top row (`[X
TodayWasIconButton]` start, `[TodayWasButtonWithLoading save button]` end — the exact shape asked
for) above the caller's `content` slot. `HabitDetailScreen` renders it when `uiState.isEditSheetOpen`,
wiring both `onDismissRequest` and `onCloseClicked` to `CancelEditClicked` (so swipe/scrim-dismiss
and the X button discard identically) and `onSaveClicked` to `SaveClicked`. Its `content` is
`uiState.rows.filter { it.eligibleForEdit }` (not the full row list — a bug caught during Phase 3
manual verification: "edit bottom sheet should only display those values that can be edited")
rendered via the existing `LazyColumn` of `HabitDetailRow`, each now always `enabled = true` since
every remaining row is by definition eligible. Row date labels were also changed from
"Today"/"Yesterday" to the formatted date always, with a `(today)` suffix on today's row only
(`habit_detail_today_suffix_format`, `"%1$s (today)"`) — per this session's decision, dropping the
now-unused `habit_detail_today_label`/`habit_detail_yesterday_label` strings.

#### 3. Strings

**File**: `app/src/main/res/values/strings.xml`

**Intent**: New copy for the window chip row.

**Contract**: Add `contribution_window_last_12_months_label` ("Last 12 months").

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Opening a habit with several check-ins of varying values shows a grid with visibly different
  shades; a habit with no check-ins shows an all-empty grid
- Switching the year chip changes the visible date range without changing any already-visible
  day's shade
- Tapping Edit opens a bottom sheet with the same editable rows as before (today/yesterday +
  in-window logged days); Save updates a value and the grid reflects it after the sheet closes
- Swiping the sheet down (or tapping the scrim) discards any typed-but-unsaved change, same as
  tapping Cancel
- Regression: journal's add-entry flow and "Log check-ins" still work end-to-end

**Implementation Note**: Pause here for manual confirmation before starting the journal vertical.

---

## Phase 4: Journal — Presentation

### Overview

Extend `MainViewModel` to compute and expose the journal contribution grid + window selection,
embedded in Main's existing state (per this session's decision — no separate journal screen).

### Changes Required:

#### 1. ViewModel state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Add a window-selection source and fold it into the existing `combine(...)` chain so the
grid recomputes whenever entries or the selected window change.

**Contract**: Add a `private val selectedWindow = MutableStateFlow<ContributionWindow>(RollingTwelveMonths)`.
Extend the existing 4-way `combine(...)` (`MainViewModel.kt:52-64`) to a 5-way combine including
`selectedWindow`, computing `journalContributionGrid =
JournalContributionCalculator.compute(entries, selectedWindow, now).toUiState()` and
`availableWindows = ContributionWindow.availableWindows(entries.minOfOrNull { it.date }, now)`
alongside the existing `journalEntries`/`habits` mapping in `CombinedMainState`.

#### 2. Intent + UI state

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainIntent.kt`, `MainUiState.kt`

**Intent**: Carry the new grid/window data and window-selection intent, following the same shape as
Habit Detail's (Phase 2).

**Contract**: `MainIntent` gains `data class JournalWindowSelected(val window:
ContributionWindowUiState)`. `MainUiState` gains `journalContributionGrid: ContributionGridUiState`
(Phase 2's mapper type), `journalAvailableWindows: List<ContributionWindowUiState>`,
`journalSelectedWindow: ContributionWindowUiState`. `MainViewModel.onIntent` dispatches
`JournalWindowSelected` to `selectedWindow.update { ... }` (mirroring `onHabitClicked`'s shape).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- `MainViewModelTest` passes: `journalContributionGrid`/`journalAvailableWindows`/
  `journalSelectedWindow` reflect loaded entries; `JournalWindowSelected` updates the selected
  window and recomputes the grid without changing any already-visible day's level; existing
  `MainViewModelTest` cases (focus, fab actions, habit/journal click navigation) still pass
  unchanged

---

## Phase 5: Journal — UI

### Overview

Embed the grid + chips into Main's Journal section, and convert `JournalEntryDetailScreen`'s inline
edit into a bottom sheet.

### Changes Required:

#### 1. Main screen — Journal section

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Insert the grid + chip row between the section header and the existing entries list,
per this session's wireframe (header → grid → chips → entries).

**Contract**: `JournalSection` (`MainScreen.kt:141-163`) gains, immediately after the section title
`TodayWasText` and before the empty-state/`LazyColumn` branch: `TodayWasContributionGrid(startDate =
uiState.journalContributionGrid.startDate, endDate = uiState.journalContributionGrid.endDate, cells
= TodayWasContributionCells(uiState.journalContributionGrid.cells))` followed by a `LazyRow` of
`TodayWasChip` per
`uiState.journalAvailableWindows` (same selected/onClick/label shape as Habit Detail's chip row,
dispatching `MainIntent.JournalWindowSelected`). The existing entries list below is untouched.

#### 2. Journal entry detail — bottom sheet edit

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailScreen.kt`

**Intent**: Replace the inline `TodayWasText`/`TodayWasTextField` swap with a `ModalBottomSheet`,
reusing the existing `editedText`/intents unchanged (per Key Discoveries).

**Contract**: Screen body (`JournalEntryDetailScreen.kt:104-118`) always renders
`TodayWasText(text = uiState.entry.text)` (no more `if (uiState.isEditing)` branch in the body).
When `uiState.isEditing`, render `ModalBottomSheet(onDismissRequest = {
onIntent(JournalEntryDetailIntent.CancelEditClicked) })` containing the `TodayWasTextField` bound
to `editedText` plus the existing Cancel/Save action row (moved from the top bar into the sheet,
same as Habit Detail's Phase 3 shape). Top bar's Edit action (`isEditable && !uiState.isEditing`)
is unchanged.

#### 3. Strings

No new strings needed — journal's chip row reuses
`contribution_window_last_12_months_label` (Phase 3); existing `journal_detail_edit_action`/
`_save_cta`/`_cancel_edit_action` strings are reused as-is inside the sheet.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Main's Journal section shows the grid + chip row above the existing entries list; a fresh
  install with no entries shows an all-empty grid without crashing
- Switching the year chip changes the visible date range without changing any already-visible
  day's shade
- Tapping a journal entry still navigates to `JournalEntryDetailScreen`, unchanged
- Tapping Edit there opens a bottom sheet with the entry's text; Save updates it and Main's grid
  reflects the change after returning
- Swiping the sheet down discards the typed change, same as Cancel
- Regression: habit check-in logging/editing (Phase 2-3) and habit creation still work end-to-end

**Implementation Note**: Pause here for manual confirmation before wrapping up the change.

*(Update: a Phase 6 was added after this phase shipped, per user feedback on grid performance and
month-gap styling — see below. Phase 5 no longer wraps up the change; Phase 6 does.)*

---

## Phase 6: Journal/Habit — Contribution grid UI refinements

### Overview

Two issues found after Phase 5 shipped: (1) `TodayWasContributionGrid` still does real work in the
render path (date-sequence generation, `Map` lookups, month-boundary comparisons) every time it
recomposes, despite Phase 3's `@Immutable`-wrapper fix for recomposition-*skipping* — skipping only
helps when the composable doesn't run at all; this phase removes the work from the render path
itself so even a forced re-run is cheap; (2) the grid has no visual gap between months, unlike
GitHub's own graph. Both fixes push all date/week/month logic out of the Composable and into the
`ui.model` mapper layer, computed once per grid recomputation instead of once per frame/interaction
— and since each recomputation is now doing more work (week-chunking, gap insertion), this phase
also decouples *when* that recomputation happens: both `HabitDetailViewModel` and `MainViewModel`
currently recompute their contribution grid on every unrelated state emission (every keystroke/tap
during editing, every unrelated habit-board update), not just when the underlying data or selected
window actually changes.

### Changes Required:

#### 1. Precomputed grid data (mapper)

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/ContributionMapper.kt`

**Intent**: Replace the sparse `Map<LocalDate, TodayWasContributionLevel>` + separate
`startDate`/`endDate` with a single, fully precomputed cell list — all date/week/month math happens
once here, not in the Composable.

**Contract** *(superseded mid-implementation — see note below)*: `ContributionGridUiState` becomes
`data class ContributionGridUiState(val cells: List<TodayWasContributionCellUiState>)` (drops
`startDate`/`endDate`, no longer needed by the component).

**Post-manual-verification revision**: the first implementation padded the whole window to uniform
Monday-Sunday weeks and inserted a full 7-item blank "Gap" column between months. Manual testing
showed this was wrong: a calendar week straddling a month boundary (e.g. a month starting on a
Saturday) rendered as ONE column mixing both months' days, with the gap column landing in the wrong
place relative to the actual boundary. The corrected design (implemented, no separate phase number)
gives each month its own contiguous block of columns instead: `ContributionGrid.toUiState(now:
Instant)` walks `currentDate` forward from the window's `startDate`; each column spans
`weekMonday..weekSunday` (the calendar week `currentDate` falls in), but only cells within
`[currentDate, columnEnd]` get a real `Level` — where `columnEnd = minOf(weekSunday, endDate,
YearMonth.from(currentDate).atEndOfMonth())` — everything outside that range (before `currentDate`
or after `columnEnd`) becomes `TodayWasContributionCellUiState.Blank`. Because `columnEnd` always
stops at a month boundary, a shared calendar week that straddles two months naturally splits into
two side-by-side columns — one per month, each truncated to just its own days (e.g. a month
starting Saturday gets a column with only Saturday+Sunday populated, Monday-Friday `Blank`; the
prior month's tail column gets the inverse). Every cell (`Level` or `Blank`) in the first column of
a new month carries `hasGapBefore = true` so the renderer can add a small leading margin there —
replacing the old dedicated spacer column with a lighter-weight per-cell flag. The whole flat list
is still **reversed** at the end (paired with `reverseLayout = true`), for the same reason as
before — every column is uniformly 7 items regardless of how many are `Blank`, so this rides through
the reversal cleanly.

#### 2. Grid rendering (design system)

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasContributionGrid.kt`

**Intent**: The Composable becomes a pure, mechanical renderer of a precomputed list — no date
arithmetic, no map lookups, no `remember` needed at all.

**Contract** *(superseded mid-implementation — see note above)*: `sealed interface
TodayWasContributionCellUiState { data class Level(val date: LocalDate, val level:
TodayWasContributionLevel, val hasGapBefore: Boolean = false) : TodayWasContributionCellUiState;
data class Blank(val hasGapBefore: Boolean = false) : TodayWasContributionCellUiState }` — `date` on
`Level` is identifying metadata for callers/tests only (the component never reads it); `Blank`
replaced the original `Gap` object once the per-month truncation redesign needed a same-size,
no-background placeholder *within* a column rather than a dedicated spacer column between columns.
`@Composable fun TodayWasContributionGrid(cells: List<TodayWasContributionCellUiState>, modifier:
Modifier = Modifier)` renders via `LazyHorizontalGrid(rows = GridCells.Fixed(7), reverseLayout =
true)` (unchanged from Phase 3); each cell applies `Modifier.padding(start = MONTH_GAP)` when
`hasGapBefore` is true (before the usual cell padding/size), then either a colored `Level` swatch or
an empty `Blank` box. `TodayWasContributionLevel` and the fixed light/dark palettes are unchanged.
Ships `@PreviewLightDark` covering: a month-boundary scenario matching the mid-week-Saturday-start
shape, and a small edge-case grid (single full column, no gaps).

#### 3. Habit — decoupled recomputation

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailViewModel.kt`

**Intent**: Stop recomputing the contribution grid on every unrelated state change (every
`ValueChanged`/`isSaving` toggle during an edit-sheet session) — only recompute when `checkIns` or
`selectedWindow` actually change.

**Contract**: Derive `contributionGrid` as its own flow: `viewModelState.map { it.checkIns to
it.selectedWindow }.distinctUntilChanged().map { (checkIns, window) ->
HabitContributionCalculator.compute(checkIns, window, clock.instant()).toUiState(clock.instant()) }`,
then combine this with `viewModelState` (via `combine`, not inline inside a single `toUiState` call)
to produce the final `HabitDetailUiState` — same output shape as before, cheaper trigger frequency.

#### 4. Journal — decoupled recomputation

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Same decoupling for `journalContributionGrid`, which today sits inside the existing
5-way `combine(...)` and recomputes on every onboarding/habit-board emission even when journal
entries/window haven't changed.

**Contract**: Split the journal-entries+selected-window pair out with its own
`distinctUntilChanged()` gate before computing `JournalContributionCalculator.compute(...)`, folding
the result back into the existing combine chain rather than recomputing inline on every emission.

#### 5. Grid layout as a choice, not a fixed behavior (added after manual verification)

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/ContributionMapper.kt`

**Intent**: Per this session's decision, don't hard-code the by-month layout as the only option —
keep both layouts available behind a type so a future user-facing preference can switch between
them, defaulting to the plain continuous ("GitHub-like") shape for now. Neither screen currently
overrides the default, so this change has no visible effect yet — it's purely making the by-month
work from item 1 selectable rather than deleting or hard-wiring it.

**Contract**: New `enum class ContributionGridType { CONTINUOUS, BY_MONTH }`.
`ContributionGrid.toUiState(now: Instant, type: ContributionGridType =
ContributionGridType.CONTINUOUS)` dispatches to one of two private extension functions:
`toContinuousCells` (plain Monday-Sunday padding across the whole window, every cell a `Level`,
`NONE` for no-data days — the original Phase 3 shape) or `toByMonthCells` (the per-month
truncated-column algorithm from item 1 above, unchanged). Both `HabitDetailViewModel` and
`MainViewModel` call `toUiState(now)` without a `type` argument, so they get `CONTINUOUS` — the
by-month layout is fully built and unit-tested but not reachable from any screen yet.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`
- `ContributionMapperTest` passes (rewritten after the post-manual-verification redesign above):
  `toUiState(now)` defaults to `CONTINUOUS`; `CONTINUOUS` never produces a `Blank` cell and covers
  the whole window padded to full weeks; for `BY_MONTH` — no cell in the window's very first column
  has `hasGapBefore`; every column has exactly 7 cells; a known real month-boundary case (August 1,
  2026, a Saturday) produces the exact expected split — the prior month's tail column has Mon-Fri
  `Level` + Sat-Sun `Blank`, the new month's head column has the inverse plus `hasGapBefore` on all
  7 cells; a real check-in's date maps to the correct level in both modes.
- `HabitDetailViewModelTest`/`MainViewModelTest` still pass, plus a new case per ViewModel
  confirming `contributionGrid`/`journalContributionGrid` is the *same instance* (`===`, not just
  equal) after an unrelated state change (`ValueChanged` during editing, for Habit; `FabToggled`,
  for Journal/Main) — proving the `distinctUntilChanged` gate actually skipped recomputation, not
  just that the recomputed value happened to match

#### Manual Verification:

- With the default `CONTINUOUS` type active in both screens, the grid reads as plain continuous
  weeks (no month gaps/truncation visible) — matching the "GitHub-like" default this session
  settled on
- Editing values in the Habit Detail bottom sheet feels noticeably smoother than before (no visible
  jank while tapping/typing)
- Regression: grid still anchors to the most recent week with no blank trailing space; switching
  the year chip still works; editing/saving in both verticals still works end-to-end

**Implementation Note**: Pause here for manual confirmation before wrapping up the change.

*(Update: a Phase 7 was added after this phase shipped, per user feedback that a vertical timeline
suits Habit Detail's single-item drill-down better than the horizontal grid. Phase 6 no longer
wraps up the change; Phase 7 does.)*

---

## Phase 7: Habit Detail — vertical contribution timeline

### Overview

The horizontal `TodayWasContributionGrid` reads best for compact, multi-item overviews (Main's
Journal section, potentially future multi-habit views) but is a cramped fit for a single habit's
own drill-down screen. This phase adds a vertical-scrolling alternative — one row per week,
scrolling down through history — and swaps it in on Habit Detail only; Main's Journal grid stays
horizontal.

### Changes Required:

#### 1. Vertical timeline component

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasContributionTimeline.kt` (new)

**Intent**: Reuse the exact same precomputed `List<TodayWasContributionCellUiState>` the horizontal
grid already consumes — no new mapper/domain work needed — just group and orient it differently.

**Contract**: `@Composable fun TodayWasContributionTimeline(cells: List<TodayWasContributionCellUiState>,
modifier: Modifier = Modifier)` renders a `LazyColumn` where `items(cells.chunked(7))` — each chunk
is one week (the same grouping the horizontal grid relies on, since `cells` is already
newest-first/whole-weeks per the mapper's existing contract) rendered as a `Row` of that chunk's 7
cells **reversed** (so within a row it reads Monday→Sunday left-to-right, ascending — the opposite
of the horizontal grid, where within-column order is cosmetically irrelevant since it's never
directly scanned; here each row IS directly scanned, so a sensible left-to-right order matters).
Because `cells` is already newest-first, item 0 (top of the list) is the most recent week —
`LazyColumn` needs no `reverseLayout` trick here, unlike the horizontal grid. The per-cell
color/size/shape rendering (including `hasGapBefore` leading spacing, forward-compatible with a
future `BY_MONTH` selection) is extracted from `TodayWasContributionGrid.kt` into a shared,
internal (module-visible, not public) `ContributionCell` composable — now taking an optional
`cellSize: Dp = CELL_SIZE` param — so both components stay visually consistent without duplicating
the palette lookup, while each can size its cells independently. Each row is
`Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth())` — centered
within the full available width — using a `TIMELINE_CELL_SIZE = 20.dp` (bigger than the horizontal
grid's compact 12dp, since this view only ever shows one week per row and has room to spare) — both
adjustments added post-manual-verification, per this session's feedback. Ships `@PreviewLightDark`
with a `PreviewParameterProvider` covering a multi-week history and a single-week edge case.

#### 2. Wire into Habit Detail only

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailScreen.kt`

**Intent**: Swap the horizontal grid for the new vertical timeline on this screen only — Main's
Journal section (`MainScreen.kt`) is untouched and keeps the horizontal grid.

**Contract**: Replace the `TodayWasContributionGrid(cells = uiState.contributionGrid.cells, modifier
= Modifier.fillMaxWidth())` call with `TodayWasContributionTimeline(cells =
uiState.contributionGrid.cells, modifier = Modifier.weight(1f).fillMaxWidth())` — `weight(1f)`
lets it consume the remaining vertical space in the screen's `Column` and scroll internally. The
year-chip row moves **above** the timeline (order: name → chips → timeline, not the original
name → timeline → chips) — post-manual-verification feedback — both so the chip row acts as a
control bar above the scrollable content, and so the timeline's centered rows measure against the
same full-width context as the chip row above them (a chip row still inset at 24dp, sitting above
a full-bleed centered timeline, wouldn't visually align).

#### 3. Week-as-single-item rendering (added post-manual-verification, both files)

**Files**: `core/designsystem/.../TodayWasContributionGrid.kt`, `TodayWasContributionTimeline.kt`

**Intent**: A user question during manual verification ("would it be more optimal to use Row in
vertical grid and Column in horizontal to display a week?") surfaced a real improvement: a week is
always exactly 7 cells, so `LazyHorizontalGrid(rows = GridCells.Fixed(7))` doing per-cell grid-slot
math was unnecessary overhead — treating each *week* as one lazy item (a plain `Column`/`Row` of 7
cells inside a `LazyRow`/`LazyColumn` item) is simpler and cheaper, and fewer lazy-tracked items
(~52 weeks vs. ~364 cells for a year). It also exposed a latent bug: `hasGapBefore` was applied as
horizontal leading padding *inside* `ContributionCell` for both components — correct for the grid's
columns, but wrong for the timeline's rows (needs *vertical* spacing instead). Inactive today since
neither screen selects `BY_MONTH`, but wrong regardless.

**Contract**: `TodayWasContributionGrid` now uses a plain `LazyRow(reverseLayout = true)` (not
`LazyHorizontalGrid`/`GridCells`), `items(cells.chunked(7))`, each week rendered as a `Column` of
its 7 `ContributionCell`s, with `Modifier.padding(start = if (week.weekHasGapBefore()) MONTH_GAP
else 0.dp)` on the `Column` itself. `TodayWasContributionTimeline`'s `Row` per week gains the
mirrored `Modifier.padding(top = if (week.weekHasGapBefore()) MONTH_GAP else 0.dp)`. New internal
extension `List<TodayWasContributionCellUiState>.weekHasGapBefore(): Boolean` (checks `first()`,
since the mapper already sets the same value across all 7 cells in an affected week) replaces the
per-cell check `ContributionCell` used to do — `ContributionCell` itself is now pure rendering (no
gap logic at all).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Habit Detail shows a vertically-scrolling list of weeks (most recent at the top), each row
  reading Monday→Sunday left-to-right, centered within the width, with cells visibly bigger than
  the horizontal grid's, and the year-chip row sitting above the timeline
- Main's Journal section is unchanged (still the horizontal grid)
- Regression: year chip switch, edit bottom sheet, and habit check-in editing/saving still work
  end-to-end on Habit Detail

**Implementation Note**: Pause here for manual confirmation before wrapping up the change.

---

## Testing Strategy

### Unit Tests:

- `ContributionWindow.dateRange`/`availableWindows` (Phase 1) — the shared date-range and
  year-list logic both verticals depend on.
- `HabitContributionCalculator`/`JournalContributionCalculator` (Phase 1) — the actual bucketing
  math, including the all-time-range-vs-window-filter distinction called out in Key Discoveries.
- ViewModel-level: grid/window state derivation and window-switch behavior for both
  `HabitDetailViewModel` (Phase 2) and `MainViewModel` (Phase 4); existing edit/save/cancel
  coverage from S-04 continues to pass under the `isEditSheetOpen` rename.

### Integration Tests:

- None — out of scope per this repo's established "unit tests only" testing-scope convention.

### Manual Testing Steps:

1. Create a habit, log check-ins across several days with varying values → open its Habit Detail
   screen → confirm the grid shows visibly different shades for different values.
2. Switch the year chip → confirm the date range shown changes but no previously-visible day's
   shade changes.
3. Tap Edit → confirm the bottom sheet shows the same editable rows as S-04's screen → change a
   value → Save → confirm the grid updates and the sheet closes.
4. Swipe the sheet down without saving → confirm the change was discarded.
5. On Main, confirm the Journal section shows a grid + chip row above the entries list, correctly
   reflecting which days have an entry.
6. Tap a journal entry → tap Edit → confirm a bottom sheet opens with the entry's text → edit and
   Save → confirm it persists and Main's grid is unaffected (journal is presence-only).
7. Regression: add-entry (journal), "Log check-ins" (habit), and habit creation still work
   end-to-end.

## Performance Considerations

Negligible at MVP scale — a window is at most 366 days, computed once per data/window-selection
change over already-in-memory lists (no new DB queries), matching the existing
no-pagination-needed convention.

## References

- Roadmap: `context/foundation/roadmap.md` (`S-05: Detailed contribution history`)
- PRD: `context/foundation/prd.md` (FR-011, Business Logic section)
- Prior slices: `context/archive/2026-08-01-edit-within-24h-window/plan.md` (S-04 — built the
  placeholder `HabitDetailScreen`/row-list edit logic this change rebuilds on top of, and
  explicitly flagged the grid as future work), `context/archive/2026-07-31-habit-create-and-checkin/plan.md`
  (S-03 — added `TodayWasChip`/`TodayWasDateStrip`, the last time new `:core:designsystem`
  components were introduced)
- Project conventions: `CLAUDE.md`, `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: Shared foundation (domain calculators + grid/chip components)

#### Automated

- [x] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 2a2f33c
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck` — 2a2f33c
- [x] 1.3 Debug build compiles: `./gradlew.bat assembleDebug` — 2a2f33c
- [x] 1.4 `ContributionWindowTest` passes (dateRange both variants, availableWindows no/same/multi-year data) — 2a2f33c
- [x] 1.5 `HabitContributionCalculatorTest` passes (empty, full-range spread, identical values, out-of-window dates) — 2a2f33c
- [x] 1.6 `JournalContributionCalculatorTest` passes (presence, absence, window filtering) — 2a2f33c

#### Manual

- [x] 1.7 `TodayWasContributionGrid` and `TodayWasChip` previews render correctly in light and dark mode — 2a2f33c

### Phase 2: Habit — Presentation

#### Automated

- [x] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 17bc991
- [x] 2.2 Lint passes: `./gradlew.bat ktlintCheck` — 17bc991
- [x] 2.3 `HabitDetailViewModelTest` passes (grid/window state, WindowSelected, existing edit/save/cancel cases under isEditSheetOpen rename) — 17bc991

### Phase 3: Habit — UI

#### Automated

- [x] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 3a98335
- [x] 3.2 Lint passes: `./gradlew.bat ktlintCheck` — 3a98335
- [x] 3.3 Debug build compiles and installs: `./gradlew.bat assembleDebug` — 3a98335

#### Manual

- [x] 3.4 Grid shows visibly different shades for varying check-in values; empty habit shows empty grid — 3a98335
- [x] 3.5 Year chip switch changes range without changing existing days' shades — 3a98335
- [x] 3.6 Edit opens bottom sheet with same editable rows as S-04; Save updates grid — 3a98335
- [x] 3.7 Swipe-dismiss discards unsaved change — 3a98335
- [x] 3.8 Regression: add-entry and Log check-ins still work end-to-end — 3a98335

### Phase 4: Journal — Presentation

#### Automated

- [x] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 14d6823
- [x] 4.2 Lint passes: `./gradlew.bat ktlintCheck` — 14d6823
- [x] 4.3 `MainViewModelTest` passes (journal grid/window state, JournalWindowSelected, existing cases unchanged) — 14d6823

### Phase 5: Journal — UI

#### Automated

- [x] 5.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 3c99500
- [x] 5.2 Lint passes: `./gradlew.bat ktlintCheck` — 3c99500
- [x] 5.3 Debug build compiles and installs: `./gradlew.bat assembleDebug` — 3c99500

#### Manual

- [x] 5.4 Main's Journal section shows grid + chips above entries; empty state doesn't crash — 3c99500
- [x] 5.5 Year chip switch changes range without changing existing days' shades — 3c99500
- [x] 5.6 Tapping an entry still navigates to detail; Edit opens bottom sheet, Save persists and reflects on Main — 3c99500
- [x] 5.7 Swipe-dismiss discards unsaved change — 3c99500
- [x] 5.8 Regression: habit check-in logging/editing and habit creation still work end-to-end — 3c99500

### Phase 6: Journal/Habit — Contribution grid UI refinements

#### Automated

- [x] 6.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 5a3a045
- [x] 6.2 Lint passes: `./gradlew.bat ktlintCheck` — 5a3a045
- [x] 6.3 Debug build compiles and installs: `./gradlew.bat assembleDebug` — 5a3a045
- [x] 6.4 `ContributionMapperTest` passes (CONTINUOUS default + coverage, BY_MONTH truncated-column boundary case) — 5a3a045
- [x] 6.5 `HabitDetailViewModelTest`/`MainViewModelTest` confirm contributionGrid isn't recomputed on unrelated state changes — 5a3a045

#### Manual

- [x] 6.6 Grid uses the default CONTINUOUS layout in both screens (plain continuous weeks, no month gaps visible) — 5a3a045
- [x] 6.7 Editing values in the Habit Detail bottom sheet feels noticeably smoother — 5a3a045
- [x] 6.8 Regression: grid still anchors to the most recent week with no blank trailing space; year chip switch and editing/saving in both verticals still work end-to-end — 5a3a045

### Phase 7: Habit Detail — vertical contribution timeline

#### Automated

- [x] 7.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [x] 7.2 Lint passes: `./gradlew.bat ktlintCheck`
- [x] 7.3 Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual

- [x] 7.4 Habit Detail shows a vertically-scrolling list of weeks (most recent at top), each row reading Monday-Sunday left-to-right, centered, bigger cells, chips above
- [x] 7.5 Main's Journal section is unchanged (still the horizontal grid)
- [x] 7.6 Regression: year chip switch, edit bottom sheet, and habit check-in editing/saving still work end-to-end on Habit Detail
