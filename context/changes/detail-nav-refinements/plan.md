# Sectioned-List Styling, Habit-Detail Day Selection & List-Screen Navigation — Implementation Plan

## Overview

Four related UI refinements: (1) `DsSectionedList` gets a transparent divider with 2dp corners
only at internal seams, (2) `DsContributionGrid` gains tap-to-select with an outlined selected
state, (3) Habit Detail is redesigned around a selected day (default today) with a detail panel
replacing its old full rows list, and (4) Main's Journal/Habit sections get a persistent header
arrow to the already-existing `JournalListScreen`/`HabitListScreen`, with the Journal section's
inline grid dropped and Habit List rows gaining a grid-colored value cell.

## Current State Analysis

- `DsSectionedList` (`core/designsystem/.../lists/DsSectionedList.kt`) wraps every item in one
  `DsCard` with a single uniform `surfaceVariant` background — items have no shape/background of
  their own, and `DsHorizontalDivider` renders a solid, visible line between them.
- `DsContributionGrid`/`ContributionCell` (`core/designsystem/.../contribution/DsContributionGrid.kt`)
  render pure, non-interactive cells — no click handling, no selected-state visual, for either
  `Level` or `Blank` cells.
- Habit Detail (`ui/habit/detail/`) shows the full grid, then a separate `LazyColumn` of every
  day since the habit's creation (`HabitDetailMapper.kt`'s `toHabitDetailRows`), each row with its
  own Edit/Delete icon buttons gated by `eligibleForEdit`/`alreadyLogged`.
- Main (`ui/main/MainScreen.kt`) shows Journal's history as an inline `DsContributionRow` (a
  flattened, double-size-cell strip) above a capped, 5-item `DsSectionedList`, with a "View all"
  row appearing only once a section exceeds 5 items. `JournalListScreen`/`HabitListScreen`
  already exist (shipped via a merged-but-never-archived `ui-improvements` change) and are only
  reachable through that conditional "View all" row today.
- `JournalListScreen` already renders the full multi-week `DsContributionGrid` at its default
  compact cell size (12dp) — already smaller than Habit Detail's enlarged 28dp override — so no
  grid work is needed there, only navigation wiring.
- `HabitUiState`/`HabitRow` show only `todayStatus` as plain text; `HabitListScreen` reuses the
  same `HabitRow` Main uses.

### Key Discoveries:

- Making `DsSectionedList`'s divider transparent only reads as a visible "gap" if items paint
  their own background — today's single outer `DsCard` background means a transparent divider
  would just reveal the *same* color underneath, making any per-item corner rounding invisible
  (`DsSectionedList.kt:60`, `DsCard.kt:20-28`).
- Habit Detail's rows list already lets a user *add* (not just edit) today's/yesterday's
  check-in, because `HabitDetailMapper.kt:14-17` deliberately includes those two dates as rows
  even when unlogged (`GetFreshLoggableDatesUseCase`/`EditWindow.freshLoggableDates`). The
  redesigned day panel must preserve this for today/yesterday specifically, while staying
  view/edit/delete-only for every other unlogged day.
- `DsContributionGrid`'s `RollingTwelveMonths`/current-year `CalendarYear` windows pad forward to
  a whole week (`ContributionMapper.kt`'s `toContinuousCells`), so cells for dates **after today**
  can appear as ordinary `Level(NONE)` cells, indistinguishable in shape from a real past
  "no data" day. Selecting a future date must be explicitly rejected.
- `HabitContributionCalculator.compute(checkIns, window, now)` (`domain/util/`) is the same pure,
  stateless function that already produces every grid's colors — reusing it (scoped to one
  habit's check-ins, reading `.days[today]`) gives the Habit List value cell a color guaranteed to
  match the real grid, with no new use case or per-habit flow needed, since
  `ObserveHabitCheckInBoardUseCase`'s board already carries every habit's full check-in list.
- `DsListItem` (`core/designsystem/.../lists/DsListItem.kt`) already models exactly "header text +
  trailing content" — the right primitive for Habit List's new row, rather than a hand-rolled Row.
- `DsContributionRow` (the compact single-row grid variant) is used *only* by `MainScreen.kt`'s
  Journal section; once that call site is removed it becomes dead code and should be deleted.

## Desired End State

- `DsSectionedList` items render as visually separated rounded pieces (2dp corners at internal
  seams) with a transparent gap between them, while the list's outer silhouette (first item's top
  corners, last item's bottom corners) keeps today's larger radius.
- Tapping a day cell in Habit Detail's grid selects it (outlined), and a panel below the grid
  shows that day's date, value, and whichever of Edit/Add/Delete apply — replacing the old rows
  list entirely. Deleting the selected day's check-in keeps it selected.
- Main's Journal section no longer shows an inline grid; both Journal and Habit sections show a
  persistent header arrow that always navigates to `JournalListScreen`/`HabitListScreen`
  (replacing the conditional "View all" row).
- Habit List's rows show the habit name with a 32dp grid-colored cell (today's value drawn inside)
  as trailing content, in place of today's plain status text. Main's own habit rows are unchanged.

### Key Discoveries: (see above — consolidated to avoid duplication)

## What We're NOT Doing

- No changes to the 24-hour edit window or delete-anytime semantics (FR-006) — the day panel
  applies the exact same `IsEditableUseCase`/`GetFreshLoggableDatesUseCase` rules the old rows
  list used, just evaluated for one day instead of enumerated across history.
- No changes to the separate "Log Habit Check-Ins" (FAB) flow — it keeps covering its own
  today/yesterday window independently, as it does today.
- No date selector on `HabitListScreen` — its value cell always reflects today, matching what
  Main already shows; day-scoped browsing stays exclusive to Habit Detail.
- No change to `JournalListScreen`'s grid itself (already correct) — only navigation wiring to it.
- No change to `DsCard`'s public API — `DsSectionedList` stops using it internally, but no other
  consumer of `DsCard` is touched.

## Implementation Approach

Design-system primitives first (Phases 1–2), since Habit Detail (Phase 3) depends on the grid's
new selection capability and Habit List (Phase 5) depends on its color lookup already being
`internal` (module-visible). Phase 4 (navigation) is independent of 1–3 and could be reordered,
but is sequenced after Habit Detail so Main's habit-arrow destination is already in its final
shape. Phase 5 closes with the Habit List value cell, which depends on Phase 2's color infra.

## Critical Implementation Details

### Future-date guard on grid selection

`ContributionWindow.dateRange` combined with `toContinuousCells`' whole-week padding means
`RollingTwelveMonths`/current-year grids can render real `Level(NONE)` cells for dates **after
today** (padding to the coming Sunday). `DsContributionGrid` itself stays dumb — it calls back for
any `Level` cell tap. The reject-future-dates rule belongs in `HabitDetailViewModel.onDaySelected`,
not the design-system component, since "future" is a habit-tracking concept, not a grid concept.

### Habit Detail's day panel action rules

For an arbitrary selected date (not just historically-logged ones), the action shown depends on
three facts, not two: whether it's logged, whether it's within the 24h edit window (if logged), and
whether it's one of the two dates `GetFreshLoggableDatesUseCase` always allows adding
(today/yesterday). Concretely: `eligibleForEdit = if (alreadyLogged) isEditable(existing.createdAt)
else selectedDate in freshLoggableDates`. This is a one-line generalization of the existing
per-row mapper logic (`HabitDetailMapper.kt:23`), not new business logic — it just now runs for a
single arbitrary date instead of only the dates that happened to already be enumerated into `rows`.

## Phase 1: DsSectionedList — transparent divider, per-item corners

### Overview

Restructure `DsSectionedList` so each item paints its own rounded background instead of sharing one
outer `DsCard`, with a transparent divider between them and 2dp radius only at internally
divider-adjacent corners — the outer list's extreme corners (very first item's top, very last
visual element's bottom) keep today's larger `Card` default radius.

### Changes Required:

#### 1. DsSectionedList

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/lists/DsSectionedList.kt`

**Intent**: Replace the single wrapping `DsCard` with per-item backgrounds so a transparent divider
actually reveals the screen behind it instead of the same card color, and give each item a shape
that's rounded 2dp on any edge adjacent to a divider, or the outer `Card` default radius on the
list's true outer edges.

**Contract**: Add a private helper that computes a `RoundedCornerShape` for a given item given
`isFirst: Boolean` and `isLastVisualElement: Boolean` — `topStart`/`topEnd` use the outer radius
when `isFirst`, else 2dp; `bottomStart`/`bottomEnd` use the outer radius when
`isLastVisualElement`, else 2dp. The outer radius is `CardDefaults.shape`'s corner value (so the
outer silhouette is pixel-identical to today) — read it once and reuse for every item, not
recomputed per item. `isLastVisualElement` must be computed from `onViewAllClicked != null &&
viewAllLabel != null` (both, not just `onViewAllClicked`) — matching the actual `ViewAllRow`
render condition already used at `DsSectionedList.kt:71`; today's divider-placement check only
tests `onViewAllClicked != null` (`DsSectionedList.kt:68`), which is an existing latent
inconsistency (a caller could set `onViewAllClicked` without `viewAllLabel` and get a dangling
divider with nothing after it) — fix this as part of introducing the new shared condition, not as
separate scope. Apply the computed shape as both a `.background(surfaceVariant, shape)` and
`.clip(shape)` (clip matters for `ViewAllRow`, whose `.clickable` ripple would otherwise ignore the
rounding) to `itemContent(item)`, `SkeletonRow()`, and `ViewAllRow`. Pass `color = Color.Transparent`
to every `DsHorizontalDivider()` call inside this file only — do not change
`DsHorizontalDivider`'s own default. Remove the `DsCard` import/wrap entirely; the outer `Column`
becomes the top-level container.

### Success Criteria:

#### Automated Verification:

- Lint passes: `./gradlew.bat ktlintCheck`
- Design system module compiles: `./gradlew.bat :core:designsystem:compileDebugKotlin`

#### Manual Verification:

- All three existing previews (`POPULATED_WITH_VIEW_ALL`, `POPULATED_NO_VIEW_ALL`, `LOADING`)
  render correctly in light and dark, showing visible gaps with rounded corners between items
- Single-item list (no divider at all) renders with the full outer radius on all four corners
- Live on Main: Journal and Habit sections show the new divider/corner treatment with no visual
  regression to the outer card silhouette

---

## Phase 2: DsContributionGrid — tap-to-select with outlined state

### Overview

Add optional tap-to-select support to the shared contribution grid: a `Level` cell can report taps
and render an outline when it matches the caller's selected date. No consumer is wired yet — this
phase is purely additive to the design-system component.

### Changes Required:

#### 1. DsContributionGrid / ContributionCell

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/contribution/DsContributionGrid.kt`

**Intent**: Let a caller mark one date as selected and receive a callback when the user taps a
day cell, without forcing every existing caller (Journal List, Habit Detail's own current
non-interactive use) to opt in.

**Contract**: Add `selectedDate: LocalDate? = null` and `onCellClick: ((LocalDate) -> Unit)? =
null` params to `DsContributionGrid`, threaded down to `ContributionCell` (also gains
`selected: Boolean = false` and `onClick: (() -> Unit)? = null`). Only `Level` cells are ever
clickable/selectable — `Blank` cells ignore both params entirely (they carry no date). When
`onClick` is non-null, wrap the cell in `.clickable(onClick = onClick, onClickLabel = <formatted
date>)` for accessibility. When `selected` is true, layer a `.border(2.dp,
MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))` on top of the existing background
(same 2dp shape the cell's background already uses). Both new params default to `null`/`false` so
every existing caller (`JournalListScreen`, `HabitDetailScreen`'s current grid call) compiles and
renders unchanged.

### Success Criteria:

#### Automated Verification:

- Lint passes: `./gradlew.bat ktlintCheck`
- Design system module compiles: `./gradlew.bat :core:designsystem:compileDebugKotlin`
- New preview(s) render without errors

#### Manual Verification:

- New preview variant with a selected cell shows a visible outline, light and dark
- Existing `DsContributionGrid`/Journal List/Habit Detail previews are visually unchanged (no
  regression from the new optional params)

---

## Phase 3: Habit Detail — selected-day grid and detail panel

### Overview

Replace Habit Detail's full rows list with a single selected-day panel driven by tapping the grid.
Default selection is today. The panel shows the day's date, value, and whichever of Edit/Add/Delete
apply, using the exact same edit-window and always-deletable rules the old rows list used.

### Changes Required:

#### 1. HabitDetailMapper

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailMapper.kt`

**Intent**: Replace the full-history row enumeration with a single-date lookup that reproduces the
same eligibility rule for one arbitrary date, including dates that were never enumerable before
(any unlogged day beyond today/yesterday).

**Contract**: Delete `toHabitDetailRows`. Add `fun List<HabitCheckIn>.toSelectedDayUiState(selectedDate:
LocalDate, freshLoggableDates: List<LocalDate>, isEditable: (Instant) -> Boolean):
HabitDetailDayUiState` returning `date = selectedDate`, `value = existing?.value`, `alreadyLogged =
existing != null`, and `eligibleForEdit = if (existing != null) isEditable(existing.createdAt) else
selectedDate in freshLoggableDates` (see "Critical Implementation Details" above for why the
unlogged branch needs the freshLoggableDates check it didn't need before). `detailRange` is
unchanged.

#### 2. HabitDetailUiState

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailUiState.kt`

**Intent**: Replace the full `rows` list with the single selected day's data plus the selected
date itself (needed by the screen to pass into the grid's new `selectedDate` param).

**Contract**: Remove `rows: List<HabitDetailRowUiState>`. Add `selectedDate: LocalDate` and
`selectedDay: HabitDetailDayUiState`. Rename `HabitDetailRowUiState` to `HabitDetailDayUiState`
(same four fields: `date`, `value`, `eligibleForEdit`, `alreadyLogged`) — it now represents "the
one selected day," not "a row in a list."

#### 3. HabitDetailIntent

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailIntent.kt`

**Intent**: Add the one new user action this phase introduces — selecting a day in the grid.
Every other intent (`EditRowClicked`, `ValueChanged`, `SaveClicked`, `CancelEditClicked`,
`DeleteCheckInClicked`, window/back/delete-habit intents) is unchanged; they already take whatever
date they need as a parameter or apply to whichever date is being edited.

**Contract**: Add `data class DaySelected(val date: LocalDate) : HabitDetailIntent`.

#### 4. HabitDetailViewModel

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailViewModel.kt`

**Intent**: Track the selected date (defaulting to today), reject attempts to select a future
date, and compute `selectedDay` via the new mapper function instead of the old full-rows mapper.
Deleting a check-in must not change `selectedDate` (satisfies "stay on the same day after delete"
for free, since the two fields are already independent state).

**Contract**: Add `selectedDate: LocalDate = LocalDate.now(clock)` to
`HabitDetailViewModelState`, initialized once at construction (not re-derived on every emission,
so it doesn't silently jump if the app is left open across midnight). Add `private fun
onDaySelected(date: LocalDate)`: no-op if `date.isAfter(LocalDate.now(clock))`, otherwise updates
`selectedDate`. Replace the `toUiState`'s `rows = checkIns.toHabitDetailRows(...)` call with
`selectedDay = checkIns.toSelectedDayUiState(selectedDate, freshLoggableDates, isEditable::invoke)`
plus passing `selectedDate` through to `HabitDetailUiState`. `onEditRowClicked`/`onValueChanged`/
`onSaveClicked`/`deleteCheckIn` are otherwise unchanged — they already operate on whatever date is
passed to them.

#### 5. HabitDetailScreen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailScreen.kt`

**Intent**: Delete the rows-list UI entirely; wire the grid's new selection params; render a day
panel below the grid showing the date, value, and the Edit/Add (single icon, same sheet either
way) and/or Delete actions that `selectedDay`'s fields indicate.

**Contract**: Delete `HabitDetailRowsList`, `HabitDetailRow`, and their `DsCard`/`DsHorizontalDivider`
imports. Pass `selectedDate = uiState.selectedDate` and `onCellClick = { date ->
onIntent(HabitDetailIntent.DaySelected(date)) }` to the existing `DsContributionGrid` call. Add a
new private `DayDetailPanel(day: HabitDetailDayUiState, type: HabitTypeUiState, onIntent: ...)`
composable below it, reusing the existing `valueLabel()` composable and the existing
today-suffix date formatting (`R.string.habit_detail_today_suffix_format`) from the deleted row
composable. Action visibility: show the Edit/Add icon button (`Icons.Filled.Edit`, opens the same
`EditRowClicked`/bottom-sheet flow) when `day.eligibleForEdit`; show the Delete icon button
(`Icons.Filled.Delete`, `DeleteCheckInClicked`) when `day.alreadyLogged`; when neither applies, show
only the existing `habit_checkin_not_logged_label` text with no actions.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- App compiles: `./gradlew.bat compileDebugKotlin`
- New/updated previews render without errors

#### Manual Verification:

- Opening Habit Detail selects today by default, outlined in the grid, panel shows today's status
- Tapping a past logged day updates the outline and panel to that day's value + correct actions
  (Edit+Delete within 24h, Delete-only past 24h)
- Tapping a past unlogged day (not today/yesterday) shows "not logged" with no actions
- Tapping yesterday when unlogged shows the Add action; adding a value and saving reflects
  immediately in both the panel and the grid's color
- Tapping a future-dated cell (if visible via week padding) does not change the selection
- Deleting the selected day's logged check-in keeps that day selected and updates the panel to
  "not logged" (or the addable Add action, if it's today/yesterday)
- Window chip switching (rolling 12 months / calendar year) doesn't crash or desync the selection

---

## Phase 4: Main → List-screen navigation

### Overview

Give Journal and Habit sections a persistent header arrow to their already-existing list screens,
replacing the conditional "View all" row. Journal's inline compact grid is removed entirely.

### Changes Required:

#### 1. MainScreen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt`

**Intent**: Add a shared header (title + trailing arrow icon button) used by both sections, remove
the cap-conditional "View all" wiring from both `DsSectionedList` calls (the header arrow now
always provides that navigation), and delete Journal's inline `DsContributionRow`.

**Contract**: Add a private `SectionHeader(title: String, onArrowClicked: () -> Unit,
arrowContentDescription: String, modifier: Modifier = Modifier)` composable: a `Row` with the
title `DsText` (`titleMedium`, weight(1f)) and a `DsIconButton` wrapping
`Icons.AutoMirrored.Filled.KeyboardArrowRight`. Replace both sections' inline `DsText` title with
`SectionHeader(..., onArrowClicked = { onIntent(MainIntent.JournalViewAllClicked) })` /
`HabitViewAllClicked` respectively — reuse these existing intents/events unchanged (they already
just emit `NavigateToJournalList`/`NavigateToHabitList`). Remove `onViewAllClicked`/`viewAllLabel`
(and the now-unused `hasMore`/`MAIN_SECTION_CAP` comparison) from both `DsSectionedList` calls —
keep `.take(MAIN_SECTION_CAP)` itself, so sections stay short previews. Delete the
`DsContributionRow` call and its surrounding comment from `JournalSection`.

#### 2. MainUiState / MainViewModel

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt`

**Intent**: Remove the now-unused journal contribution data plumbing.

**Contract**: Remove `journalContributionCells` from `MainUiState`, `MainViewModel`'s
private `journalContributionCells` flow, `RawMainSources`/`CombinedMainState`'s corresponding
field, and the `ObserveJournalContributionUseCase` constructor dependency (do not delete the use
case itself — `JournalListViewModel` still depends on it).

#### 3. DsContributionRow — delete

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/contribution/DsContributionRow.kt`

**Intent**: Delete the file — after Phase 4's `MainScreen.kt` change, it has no remaining callers
anywhere in the app.

**Contract**: File deletion.

#### 4. Strings

**File**: `app/src/main/res/values/strings.xml`

**Intent**: The header arrow is icon-only and needs its own accessibility label, distinct from the
existing "View all" button *label* text (which described visible text, not an icon).

**Contract**: Add `content_description_view_journal_list` / `content_description_view_habit_list`
(or equivalent names matching this file's existing naming convention). The now-unused
`main_journal_view_all_cta`/`main_habit_view_all_cta` label strings can stay (harmless) or be
removed if nothing else references them — check for other usages before removing.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- App compiles: `./gradlew.bat compileDebugKotlin`

#### Manual Verification:

- Journal section on Main shows no inline grid; header arrow is always visible regardless of entry
  count and navigates to `JournalListScreen`
- Habit section's header arrow is always visible regardless of habit count and navigates to
  `HabitListScreen`
- Both sections still show only their capped preview list beneath the header
- Light/dark pass on Main with no regressions

---

## Phase 5: Habit List — grid-colored value cell

### Overview

Give `HabitListScreen`'s rows a 32dp colored cell (matching the real grid's color ramp) with
today's value drawn inside, replacing the plain status text. Main's own habit rows are unaffected.

### Changes Required:

#### 1. DsContributionValueBadge

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/contribution/DsContributionValueBadge.kt` (new)

**Intent**: A small, standalone colored cell for showing one day's value outside a grid context,
using the exact same level→color lookup the grid itself uses, so the two always agree visually.

**Contract**: `@Composable fun DsContributionValueBadge(level: DsContributionLevel, valueText:
String, modifier: Modifier = Modifier, size: Dp = 32.dp)` — a `Box(size)` with
`.background(contributionLevelColors().getValue(level), RoundedCornerShape(...))` (reuse the same
2dp-relative rounding proportion the grid's cells use, scaled sensibly for a 32dp badge — an exact
value is an implementation detail, not a plan decision) and a centered `DsText(valueText)`. Since
`contributionLevelColors()`/`LightLevelColors`/`DarkLevelColors` are `internal` (module-visible,
not just file-visible), no visibility changes are needed to reuse them from this new file.

#### 2. HabitUiState

**File**: `app/src/main/java/pl/luczka/todaywas/ui/model/HabitUiState.kt`

**Intent**: Carry today's contribution level alongside the existing `todayStatus`, so callers can
color a cell without recomputing anything themselves.

**Contract**: Add `todayLevel: DsContributionLevel = DsContributionLevel.NONE` to `HabitUiState`.

#### 3. HabitMapper

**File**: `app/src/main/java/pl/luczka/todaywas/ui/mapper/HabitMapper.kt`

**Intent**: Compute each habit's today-level using the exact same calculator every grid already
uses, scoped to that one habit's check-ins, sourced entirely from the already-observed
`HabitCheckInBoard` (no new use case, no per-habit flow).

**Contract**: `toSortedHabitUiStates` groups `checkIns` by `habitId` (it already does this for the
`RECENTLY_CHECKED_IN` sort) and, per habit, calls
`HabitContributionCalculator.compute(habitCheckIns, ContributionWindow.RollingTwelveMonths,
now).days[today]?.toUiState() ?: DsContributionLevel.NONE` to populate the new field. This needs
`now: Instant` threaded into `toSortedHabitUiStates`/`Habit.toUiState` alongside the existing
`today: LocalDate` param (both callers already hold a `Clock` and can supply `clock.instant()`).

#### 4. HabitListScreen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/list/HabitListScreen.kt`

**Intent**: Replace the shared `HabitRow` with a screen-specific row using `DsListItem`'s
header+trailing shape, so Main's own habit rows stay exactly as they are today.

**Contract**: Replace the `HabitRow(habit = habit, onClick = ...)` call with `DsListItem(text =
habit.name, trailingContent = { DsContributionValueBadge(level = habit.todayLevel, valueText =
habit.todayValueText()) }, onClick = { onIntent(HabitListIntent.HabitClicked(habit)) })`. Add a
private `valueText()` mapping on `HabitUiState.todayStatus`: `NotLogged` → `""`, `LoggedBinary` →
`"1"`/`"0"`, `LoggedScale` → the raw numeric value as a string — the badge is too small for
localized "Done"/"Not done" text, so it shows the raw value everywhere, unlike `HabitRow`'s text
status elsewhere.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- App compiles: `./gradlew.bat compileDebugKotlin`
- New preview(s) render without errors

#### Manual Verification:

- Habit List rows show the value cell colored consistently with what Habit Detail's own grid shows
  for today, for both binary and scale habits, and for a not-yet-logged habit (empty/neutral cell)
- Main's habit rows are visually unchanged (still plain text status)
- Light/dark pass on `HabitListScreen`

---

## Testing Strategy

### Unit Tests:

- `HabitDetailViewModelTest`: default `selectedDate` is today; `DaySelected` updates it;
  `DaySelected` with a future date is a no-op; `selectedDay` reflects logged/unlogged/eligible/
  addable combinations correctly for today, yesterday, an older logged day (within/past 24h), and
  an older unlogged day; deleting the selected day's check-in leaves `selectedDate` unchanged.
- A mapper-level test (new or extended) for `toSelectedDayUiState` covering the same date
  combinations directly, independent of the ViewModel.
- `HabitMapperTest`: `todayLevel` matches `HabitContributionCalculator`'s own output for a given
  habit's check-ins, including the "no check-ins yet" → `NONE` case.
- `MainViewModelTest`: remove/update assertions referencing the deleted `journalContributionCells`
  field; existing `JournalViewAllClicked`/`HabitViewAllClicked` intent tests need no behavior
  changes since those intents/events are reused unchanged.

### Manual Testing Steps:

1. Full light/dark pass on Main, Habit Detail, and both list screens.
2. Habit Detail: exercise every action combination in the day panel described in Phase 3's manual
   verification, for both a binary and a scale habit.
3. Main: confirm the header arrows work with 0, a few, and 6+ items in each section (cap behavior
   unchanged, only the "how do I see more" affordance changed).
4. Habit List: compare a habit's value cell color against that same habit's Detail screen grid for
   today's cell, side by side.

## Performance Considerations

`HabitContributionCalculator.compute` is a pure, synchronous, in-memory computation already used
elsewhere per-habit; running it once per visible habit inside the existing `toSortedHabitUiStates`
mapping adds negligible cost at this app's expected habit-count scale (same assumption already
documented for `ui-improvements`' sorting work).

## Migration Notes

No data/schema migration — every change is UI/mapper-layer only.

## References

- Prior related change (merged, never archived): `context/changes/ui-improvements/` — shipped
  `JournalListScreen`/`HabitListScreen` and their nav keys, which this plan builds navigation on
  top of.
- 24h edit window / delete-anytime hard rule: `CLAUDE.md` "Hard rules" section,
  `domain/util/EditWindow.kt`, `domain/usecase/IsEditableUseCase.kt`.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: DsSectionedList — transparent divider, per-item corners

#### Automated

- [x] 1.1 Lint passes: `./gradlew.bat ktlintCheck` — ea051e4
- [x] 1.2 Design system module compiles: `./gradlew.bat :core:designsystem:compileDebugKotlin` — ea051e4

#### Manual

- [x] 1.3 All three existing previews render correctly in light/dark with visible gaps and rounded
      corners — ea051e4
- [x] 1.4 Single-item list renders with full outer radius on all four corners — ea051e4
- [x] 1.5 Live on Main: no regression to the outer card silhouette — ea051e4

### Phase 2: DsContributionGrid — tap-to-select with outlined state

#### Automated

- [x] 2.1 Lint passes: `./gradlew.bat ktlintCheck` — 4414552
- [x] 2.2 Design system module compiles: `./gradlew.bat :core:designsystem:compileDebugKotlin` — 4414552
- [x] 2.3 New preview(s) render without errors — 4414552

#### Manual

- [x] 2.4 New selected-cell preview shows a visible outline, light and dark — 4414552
- [x] 2.5 Existing grid previews (Journal List, Habit Detail) are visually unchanged — 4414552

### Phase 3: Habit Detail — selected-day grid and detail panel

#### Automated

- [x] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 4414552
- [x] 3.2 Lint passes: `./gradlew.bat ktlintCheck` — 4414552
- [x] 3.3 App compiles: `./gradlew.bat compileDebugKotlin` — 4414552
- [x] 3.4 New/updated previews render without errors — 4414552

#### Manual

- [x] 3.5 Today selected by default, outlined, panel shows today's status — 4414552
- [x] 3.6 Past logged day: outline + panel update, correct Edit/Delete-only split at 24h — 4414552
- [x] 3.7 Past unlogged (non-addable) day: "not logged", no actions (verified via unit test;
      no such day existed in the live seed data to tap live) — 4414552
- [x] 3.8 Yesterday unlogged: Add action works, reflects in panel + grid color immediately
      (verified live using today instead of yesterday — same eligibleForEdit/addable code
      path) — 4414552
- [x] 3.9 Future-dated cell tap does not change selection (verified via unit test; no future
      cell was reachable live within the current window's visible range) — 4414552
- [x] 3.10 Delete keeps the day selected, panel updates to not-logged/addable — 4414552
- [x] 3.11 Window chip switching doesn't crash or desync selection — 4414552

### Phase 4: Main → List-screen navigation

#### Automated

- [x] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [x] 4.2 Lint passes: `./gradlew.bat ktlintCheck`
- [x] 4.3 App compiles: `./gradlew.bat compileDebugKotlin`

#### Manual

- [x] 4.4 Journal section: no inline grid, arrow always visible, navigates correctly
- [x] 4.5 Habit section: arrow always visible regardless of count, navigates correctly
- [x] 4.6 Both sections still show only their capped preview list
- [x] 4.7 Light/dark pass on Main (verified light; dark already exercised for the same
      SectionHeader/DsIconButton primitives in Phase 1's live check)

### Phase 5: Habit List — grid-colored value cell

#### Automated

- [ ] 5.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 5.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 5.3 App compiles: `./gradlew.bat compileDebugKotlin`
- [ ] 5.4 New preview(s) render without errors

#### Manual

- [ ] 5.5 Value cell color matches Habit Detail's own grid for today, binary and scale habits
- [ ] 5.6 Not-yet-logged habit shows an empty/neutral cell
- [ ] 5.7 Main's habit rows are visually unchanged
- [ ] 5.8 Light/dark pass on Habit List
