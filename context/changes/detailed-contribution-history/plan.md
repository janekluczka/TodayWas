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
`@Composable fun TodayWasContributionGrid(startDate: LocalDate, endDate: LocalDate, cells:
Map<LocalDate, TodayWasContributionLevel>, modifier: Modifier = Modifier)` renders via
`LazyHorizontalGrid(rows = GridCells.Fixed(7))` — the first `Lazy*Grid` usage in this codebase (see
the weekday-alignment padding requirement in Critical Implementation Details) — each cell a small
(~12dp) rounded-square `Box` colored per level from a fixed, non-`MaterialTheme.colorScheme` palette
(light/dark constant arrays chosen via `isSystemInDarkTheme()`, per Critical Implementation
Details). `startDate`/`endDate` (the caller's selected window bounds) drive the rendered range, not
`cells.keys` — `cells` only contains non-`NONE` days, so deriving bounds from it would collapse a
mostly-empty window down to just its few logged days instead of the full selected range (a bug
caught during Phase 1 implementation; adapted here). No `onClick`/interaction parameter — purely
read-only. Ships `@PreviewLightDark` with a `PreviewParameterProvider` covering: an empty window
(no data at all, full range still renders), a sparse few-day grid, and a full 5-level gradient.

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
endDate = uiState.contributionGrid.endDate, cells = uiState.contributionGrid.cells)` followed by
a `LazyRow` of `TodayWasChip` per `uiState.availableWindows` (`selected = window ==
uiState.selectedWindow`, `onClick = { onIntent(HabitDetailIntent.WindowSelected(window)) }`, label
via `stringResource(R.string.contribution_window_last_12_months_label)` or `year.toString()`). Top
bar: unchanged Edit-icon condition (`uiState.rows.any { it.eligibleForEdit } &&
!uiState.isEditSheetOpen`) dispatching `EditClicked`.

#### 2. Edit bottom sheet

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailScreen.kt` (same file)

**Intent**: Reuse S-04's row-list editing UI verbatim, moved into a sheet.

**Contract**: When `uiState.isEditSheetOpen`, render `ModalBottomSheet(onDismissRequest = {
onIntent(HabitDetailIntent.CancelEditClicked) })` whose content is the existing `LazyColumn` of
`HabitDetailRow` (unchanged row composable/logic from S-04) plus a Cancel/Save action row at the
sheet's bottom (`TodayWasIconButton` + `TodayWasButtonWithLoading`, same as the old top-bar actions,
dispatching `CancelEditClicked`/`SaveClicked`).

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
= uiState.journalContributionGrid.cells)` followed by a `LazyRow` of `TodayWasChip` per
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

- [x] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck`
- [x] 1.3 Debug build compiles: `./gradlew.bat assembleDebug`
- [x] 1.4 `ContributionWindowTest` passes (dateRange both variants, availableWindows no/same/multi-year data)
- [x] 1.5 `HabitContributionCalculatorTest` passes (empty, full-range spread, identical values, out-of-window dates)
- [x] 1.6 `JournalContributionCalculatorTest` passes (presence, absence, window filtering)

#### Manual

- [ ] 1.7 `TodayWasContributionGrid` and `TodayWasChip` previews render correctly in light and dark mode

### Phase 2: Habit — Presentation

#### Automated

- [ ] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 2.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 2.3 `HabitDetailViewModelTest` passes (grid/window state, WindowSelected, existing edit/save/cancel cases under isEditSheetOpen rename)

### Phase 3: Habit — UI

#### Automated

- [ ] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 3.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 3.3 Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 3.4 Grid shows visibly different shades for varying check-in values; empty habit shows empty grid
- [ ] 3.5 Year chip switch changes range without changing existing days' shades
- [ ] 3.6 Edit opens bottom sheet with same editable rows as S-04; Save updates grid
- [ ] 3.7 Swipe-dismiss discards unsaved change
- [ ] 3.8 Regression: add-entry and Log check-ins still work end-to-end

### Phase 4: Journal — Presentation

#### Automated

- [ ] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 4.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 4.3 `MainViewModelTest` passes (journal grid/window state, JournalWindowSelected, existing cases unchanged)

### Phase 5: Journal — UI

#### Automated

- [ ] 5.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 5.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 5.3 Debug build compiles and installs: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 5.4 Main's Journal section shows grid + chips above entries; empty state doesn't crash
- [ ] 5.5 Year chip switch changes range without changing existing days' shades
- [ ] 5.6 Tapping an entry still navigates to detail; Edit opens bottom sheet, Save persists and reflects on Main
- [ ] 5.7 Swipe-dismiss discards unsaved change
- [ ] 5.8 Regression: habit check-in logging/editing and habit creation still work end-to-end
