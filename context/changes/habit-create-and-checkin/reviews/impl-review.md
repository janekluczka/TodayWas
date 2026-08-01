<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Habit Creation and Check-in Implementation Plan

- **Plan**: context/changes/habit-create-and-checkin/plan.md
- **Scope**: Phase 1-4 (full plan)
- **Date**: 2026-08-01
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | PASS |
| Architecture | WARNING |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Domain→UI mapping lives inline in two ViewModels instead of a dedicated mapper file

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Architecture
- **Location**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:117` (`HabitCheckInBoard.toHabitUiStates()`); `app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsViewModel.kt:120,128,137,145` (`toRows`, `toAlreadyLoggedRow`, `toEditableRow`, `habitRange`)
- **Detail**: `lessons.md`'s cross-layer-mapping rule (already followed correctly at the entity↔domain boundary by `HabitEntityMapper.kt`/`HabitCheckInEntityMapper.kt`) states domain→UI mapping "lives in its own dedicated file... not as a private method buried inside the repository/ViewModel class." Both `MainViewModel` and `LogHabitCheckInsViewModel` define private mapping extension functions instead. Neither sibling template (`OnboardingViewModel.kt`, the retrofitted `AddJournalEntryViewModel.kt`) does this — they only call pre-existing `toUiState()` functions from dedicated `ui/model/*Mapper.kt` files. `HabitMapper.kt` already exists and handles the single-habit case (`Habit.toUiState(todayCheckIn)`); the board-level list mapping and the check-in-row mapping were left inline instead of extending that file (or a new one).
- **Fix A ⭐ Recommended**: Extract both inline mapping blocks into dedicated mapper files — add `HabitCheckInBoard.toHabitUiStates()` to `ui/model/HabitMapper.kt` (it already maps single habits there), and add a new `ui/habit/LogHabitCheckInsMapper.kt` for `toRows`/`toAlreadyLoggedRow`/`toEditableRow`/`habitRange`.
  - Strength: Matches the established, repeatedly-applied convention exactly; keeps ViewModels thin and mapping logic independently testable.
  - Tradeoff: Touches two files that are otherwise working correctly; small risk of a copy-paste slip during extraction.
  - Confidence: HIGH — the pattern to follow (`HabitMapper.kt`, `HabitEntityMapper.kt`) already exists in this exact codebase.
  - Blind spot: None significant — this is a pure code-move, no behavior change.
- **Fix B**: Leave as-is, note the exception.
  - Strength: Zero risk, zero effort.
  - Tradeoff: Leaves a real, avoidable inconsistency for the next reviewer or contributor to trip over.
  - Confidence: MED — the functions are private and self-contained, so the inconsistency is low-blast-radius even if left.
  - Blind spot: Whether this becomes precedent for skipping the rule elsewhere.
- **Decision**: FIXED (Fix A) — extracted `HabitCheckInBoard.toHabitUiStates()` into `ui/model/HabitMapper.kt`; extracted `toRows`/`toAlreadyLoggedRow`/`toEditableRow`/`habitRange` into a new `ui/habit/LogHabitCheckInsMapper.kt`. Tests/lint/build re-verified green.

### F2 — Status chip on already-logged habit rows uses hardcoded colors, bypassing TodayWasChip's theme-aware defaults

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: `app/src/main/java/pl/luczka/todaywas/ui/habit/LogHabitCheckInsScreen.kt:41-42` (constants), `:153-156` (call site)
- **Detail**: `TodayWasChip` already defaults to theme-aware `MaterialTheme.colorScheme.secondaryContainer`/`onSecondaryContainer`. This call site overrides both with fixed hex values (`Color(0xFFC8E6C9)` / `Color(0xFFF2E7D32)`) that don't adapt for dark theme — the same light-green chip renders in dark mode, which is likely a contrast/consistency problem the component's own `@PreviewLightDark` preview would catch if applied here. This traces back to your explicit ask for "light green background, darker green text," which the fix needs to honor while still being theme-correct.
- **Fix A ⭐ Recommended**: Define a light/dark-aware "success" color pair (e.g. two `Color` vals switched on `isSystemInDarkTheme()`, or proper light/dark entries added to the app theme) so the green look survives in both themes.
  - Strength: Preserves the explicitly-requested green look while fixing the dark-mode gap.
  - Tradeoff: A few more lines; introduces the app's first explicit "semantic status color" concept, which doesn't exist in the theme yet.
  - Confidence: MED — no existing precedent in this codebase for a semantic success color, so the exact shade for dark mode is a judgment call.
  - Blind spot: Haven't visually verified either color pair against Material3 contrast guidelines.
- **Fix B**: Drop the override, use `TodayWasChip`'s theme defaults.
  - Strength: Zero new code, guaranteed correct theming.
  - Tradeoff: Loses the specific light-green look you asked for two turns ago.
  - Confidence: HIGH — trivial, safe change.
  - Blind spot: None significant.
- **Decision**: FIXED (Fix A) — added `SuccessContainerLight`/`SuccessLabelLight`/`SuccessContainerDark`/`SuccessLabelDark` to `ui/theme/Color.kt`; `LogHabitCheckInsScreen.kt`'s `HabitCheckInRow` now picks the pair via `isSystemInDarkTheme()` instead of hardcoded hex. Tests/lint/build re-verified green.

### F3 — Shared FakeJournalRepository/FakeHabitRepository extraction isn't documented in the plan

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: `context/changes/habit-create-and-checkin/plan.md` (Phase 2 section — no reference); actual files at `app/src/test/java/pl/luczka/todaywas/data/repository/FakeJournalRepository.kt`, `FakeHabitRepository.kt`
- **Detail**: During Phase 2, you asked whether shared test fakes existed; the resulting decision consolidated ~5 near-duplicate private fakes into two shared ones, used across 7 test files. Every other mid-implementation pivot in this session was recorded as an "As built (superseded during Phase X manual verification...)" note in the plan — this one wasn't, even though it's a real, deliberate architectural decision (also already captured in your personal memory as a feedback note, just not in the plan document itself).
- **Fix**: Add a short "As built" note under Phase 2 documenting the shared-fakes extraction, matching the style of the other as-built notes already in the plan.
- **Decision**: FIXED — added an "As built" note under Phase 2's Overview in plan.md documenting the shared-fakes consolidation.

### F4 — `AddJournalEntryScreen.kt` still has a private composable literally named `DayStrip`

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt:120`
- **Detail**: The plan says Phase 4 "removes the private `DayStrip`/`DayCard` composables in favor of the shared `TodayWasDateStrip`." Functionally true — no hand-rolled pager/card logic remains, it's a thin ~15-line adapter that maps slots to dates and delegates to `TodayWasDateStrip` — but the adapter kept the old name, which reads as if the removal didn't happen.
- **Fix**: Rename the adapter function (e.g. `JournalDateStrip`) to make the delegation obvious at a glance. Optional — purely cosmetic.
- **Decision**: FIXED — renamed `DayStrip` → `JournalDateStrip` in `AddJournalEntryScreen.kt`.

### F5 — `TodayWasStepper`'s +/- glyphs are literal strings with no content description

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/TodayWasStepper.kt:27,37`
- **Detail**: `TodayWasText(text = "−")` / `TodayWasText(text = "+")` are hardcoded literals (not preview-only, so not exempt under the project's stringResource convention) with no accessibility label on the surrounding `TodayWasIconButton`. Low real-world impact — single math symbols, only one consumer (`CreateHabitScreen`) — but worth a follow-up if this component gets reused.
- **Fix**: Add `contentDescription` via a wrapping `Modifier.semantics` or move to `stringResource` + `content_description_*` keys if this component gets a second consumer.
- **Decision**: FIXED (differently) — replaced the literal `"−"`/`"+"` `TodayWasText` glyphs with real `Icons.Default.Remove`/`Icons.Default.Add` (via a new `material-icons-extended` dependency on `:core:designsystem`, since `Remove` isn't in `material-icons-core`). Added `decreaseContentDescription`/`increaseContentDescription: String?` params to `TodayWasStepper`, following the existing caller-supplies-the-string pattern (`TodayWasIcon`'s `contentDescription`); `CreateHabitScreen.kt` now passes new `content_description_decrease`/`content_description_increase` string resources. Tests/lint/build re-verified green.
