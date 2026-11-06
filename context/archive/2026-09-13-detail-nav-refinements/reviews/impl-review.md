<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Sectioned-List Styling, Habit-Detail Day Selection & List-Screen Navigation

- **Plan**: context/changes/detail-nav-refinements/plan.md
- **Scope**: Phase 5 of 5 (full plan)
- **Date**: 2026-09-13
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — strings.xml reused existing CTA strings instead of adding new content-description strings

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt:382,435
- **Detail**: Phase 4's contract said to add `content_description_view_journal_list`/
  `content_description_view_habit_list` (or equivalent new names) for the header arrow's a11y
  label. No new strings were added — the arrow reuses the existing `main_journal_view_all_cta`
  ("View all entries")/`main_habit_view_all_cta` ("View all habits") strings directly as
  `arrowContentDescription`. Functionally fine (the copy reads naturally as a content
  description, and the plan itself flagged these strings as "can stay... or be removed"), but the
  contract's literal instruction to add distinct strings wasn't followed.
- **Fix**: Leave as-is — reusing the existing strings is a reasonable simplification the plan
  already anticipated as acceptable; no functional or a11y issue.
- **Decision**: ACCEPTED — no code change; reuse is intentional and acceptable

### F2 — Planned standalone mapper test for toSelectedDayUiState was never written

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailMapper.kt
- **Detail**: The plan's Testing Strategy explicitly asked for "a mapper-level test (new or
  extended) for `toSelectedDayUiState` covering the same date combinations directly, independent
  of the ViewModel." No such test exists — coverage of the eligibility rule is only indirect via
  `HabitDetailViewModelTest`. Not a functional gap (the ViewModel tests do exercise every date
  combination end-to-end), but a documented plan commitment left unfulfilled.
- **Fix**: Add a small `HabitDetailMapperTest.kt` covering `toSelectedDayUiState` directly for the
  same combinations already covered indirectly (today unlogged, in-window logged, past-window
  logged, older unlogged non-addable).
- **Decision**: FIXED — added `app/src/test/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailMapperTest.kt`
  with 5 tests covering today-unlogged, in-window-logged, past-window-logged, older-unlogged, and
  yesterday-unlogged.

### F3 — Stale comment references deleted journalContributionCells

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/list/JournalListUiState.kt:14-15
- **Detail**: A comment references `MainViewModel.journalContributionCells`, which Phase 4 deleted
  entirely. This file wasn't part of the plan's touched-file list, so the deletion's side effect
  on this comment was missed.
- **Fix**: Update or remove the stale `(see MainViewModel.journalContributionCells)` reference.
- **Decision**: FIXED — removed the stale reference in `JournalListUiState.kt`.

### F4 — Per-habit contribution recompute on every board emission (performance watch-item)

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/mapper/HabitMapper.kt:37-46
- **Detail**: `toSortedHabitUiStates` now runs `HabitContributionCalculator.compute()` (a full
  sort + percentile-rank pass over each habit's check-ins) for every habit, on every emission of
  the combined check-in-board flow, just to extract a single day's level. Not O(n²), and fine at
  this app's expected scale (a handful of habits, hundreds of check-ins), but it's doing
  full-window work to answer a single-day question with no memoization between recompositions.
- **Fix**: No action needed now; revisit with a cheaper "today's level only" helper if habit/
  check-in volume ever makes this measurable.
- **Decision**: FIXED (differently) — added `HabitContributionCalculator.levelForDate(checkIns,
  date)`, a direct O(n log n) lookup for one date that skips the O(365) whole-window date-range
  walk `compute()` always does, while reusing the exact same private `levelFor()` ranking so
  results stay identical to the real grid. `HabitMapper.toSortedHabitUiStates` now calls this
  instead of `compute()`, which also let the added `now: Instant` parameter be dropped entirely
  (no longer needed) — simplifying both call sites (`MainViewModel`, `HabitListViewModel`) back to
  just `today: LocalDate`.

## What checked out cleanly

- **Plan Adherence**: every file across all 5 phases matches its planned Intent/Contract almost
  verbatim — exact param names, shape formulas, eligibility rules, and state semantics. The
  subtle "24h edit window generalized from enumerated rows to an arbitrary selected date" logic
  (Phase 3's trickiest piece) was independently verified correct against the pre-change code by
  the safety reviewer, not just assumed.
- **Scope Discipline**: full git diff matches the plan's file list almost exactly. The one
  unlisted file (`HabitListViewModel.kt`, a one-line `clock.instant()` addition) is a directly
  implied consequence of the `HabitMapper` signature change the plan itself specified — not scope
  creep. All four "What We're NOT Doing" boundaries (24h edit window, Log-Check-Ins FAB flow,
  HabitListScreen date selector, JournalListScreen grid, DsCard public API) were respected.
- **Safety & Quality**: no CRITICAL/WARNING findings. Future-date rejection has no off-by-one
  (today itself stays selectable). Delete-check-in correctly leaves `selectedDate` untouched with
  no race against `onDaySelected`/`onEditRowClicked`. `HabitMapper`'s per-habit check-in scoping
  correctly excludes other habits' data from the contribution calculator.
- **Architecture**: MVI conventions (sealed Intent, single `onIntent`, buffered-Channel UiEvent,
  dedicated mapper files, no domain models in UiState) followed identically to existing screens.
  `DsContributionValueBadge` matches its sibling `DsContributionGrid`'s structure and preview
  conventions exactly.
- **Success Criteria**: `ktlintCheck`, `testDebugUnitTest`, and `assembleDebug` all pass across the
  full plan. Every phase was verified live on an ADB emulator (light and dark) — sectioned-list
  gaps/rounding, grid tap-to-select with outline, the full Habit Detail day-panel action matrix
  (add/edit/delete/window-switch), persistent header-arrow navigation, and the Habit List value
  badge's color/value round-tripped against Habit Detail's own grid.
