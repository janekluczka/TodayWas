<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Detailed contribution history

- **Plan**: context/changes/detailed-contribution-history/plan.md
- **Scope**: Phase 7 of 7 (full plan review)
- **Date**: 2026-08-03
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 0 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Plan text claims Journal's Edit-action condition is "unchanged" but it changed

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: `app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailScreen.kt` (`JournalEntryDetailActions`); plan.md Phase 5 §2
- **Detail**: Phase 5's contract says the top bar's Edit action (`isEditable && !uiState.isEditing`) "is unchanged." The actual code only checks `uiState.isEditable` — the `!isEditing` gate was dropped, so the Edit icon now stays visible while the sheet is open, matching the "stays visible while sheet is open" decision made for Habit Detail. This is the *intended, consistent* behavior (confirmed by re-reading the conversation), but the plan's own wording undersells that this file changed too — a documentation-accuracy gap, not a code defect.
- **Fix**: Update plan.md's Phase 5 §2 contract text to say the Edit condition was simplified to `isEditable` alone (dropping `!isEditing`), for the same reason as Habit Detail's later fix, rather than "unchanged."
- **Decision**: FIXED

### F2 — Chip-row + label mapping duplicated across two screens

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt` (`ContributionWindowChipRow`, `ContributionWindowUiState.label()`) and `app/src/main/java/pl/luczka/todaywas/ui/habit/HabitDetailScreen.kt` (same names, same bodies)
- **Detail**: The year-chip row composable and its window-label mapping extension are copy-pasted verbatim in both screens instead of living once in a shared file. A future copy change (e.g. relabeling "Last 12 months") requires editing both.
- **Fix**: Extract `ContributionWindowChipRow` and the `label()` extension into a single shared file (e.g. `ui/model/ContributionWindowChipRow.kt`) and have both screens import it.
- **Decision**: SKIPPED

## Notes

- Plan Drift sub-agent verified 14 of 15 planned file/contract items as exact MATCH, including every mid-implementation "superseded" contract revision (Gap→Blank cells, Map-based→precomputed-list cells, LazyHorizontalGrid→LazyRow, the CONTINUOUS/BY_MONTH type split, and the distinctUntilChanged-gated recomputation in both ViewModels). Only F1 above was flagged as drift.
- Safety/Quality/Pattern sub-agent found no CRITICAL or unexpected WARNING issues: the `while (currentDate <= endDate)` loop in `ContributionMapper.toByMonthCells` was specifically checked for termination (guaranteed, since `columnEnd >= currentDate` always and the loop advances past it every iteration); `distinctUntilChanged()` gating was traced end-to-end in both ViewModels and confirmed to actually prevent recomputation on unrelated state changes (not just present but bypassed); all `:core:designsystem` components ship `@PreviewLightDark`; no hardcoded user-facing strings; no logging of journal/habit content; no new DB queries/migrations.
- Automated success criteria re-verified on current HEAD (`bd95fc8`): `testDebugUnitTest`, `ktlintCheck`, `assembleDebug` all pass.
- Overall verdict is APPROVED per the "PASS, or PASS with ≤2 minor warnings" rule — both findings are low-impact and one is documentation-only.
