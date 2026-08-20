<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Architecture Hardening Implementation Plan

- **Plan**: context/changes/architecture-hardening/plan.md
- **Mode**: Deep
- **Date**: 2026-08-19
- **Verdict**: REVISE → SOUND after triage (all 3 findings fixed in plan.md)
- **Findings**: 1 critical, 1 warning, 1 observation — all FIXED

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | FAIL |

## Grounding

21/21 referenced file paths verified to exist. 9/9 riskiest claims from Steps 1-3 confirmed via
sub-agent (HabitCheckInDao.upsertAll shape, empty-input calculator equivalence, background-push
method names + syncMutex, @ApplicationScope blast radius, toHabitUiStates single call site,
JournalEntryDetailViewModel clock usage, AccountViewModel/OnboardingViewModel duplicated
branching, Syncable-interface consumer sweep, absence of an existing data/local/api/ pattern).
plan-brief.md ↔ plan.md consistent. Progress↔Phase mechanical contract clean (exactly one
`## Progress` heading, all 5 phases matched, all Success Criteria bullets have matching
Progress checklist items, no stray checkboxes outside Progress).

## Findings

### F1 — New domain `ContributionData` collides with an existing private class of the same name

- **Severity**: CRITICAL
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 4, items 2-4
- **Detail**: Phase 4 item 2 creates `domain/model/ContributionData.kt` (`grid`,
  `availableWindows`). `HabitDetailViewModel.kt:84` already declares a private
  `data class ContributionData(grid: ContributionGridUiState, availableWindows:
  List<ContributionWindowUiState>)` in the exact file Phase 4 item 4 edits — importing the
  domain type collides with it, won't compile as written. Separately, item 4's
  `.map { it.toUiState(clock.instant()) }` implies a new mapper function that is never listed
  under Changes Required, and its two target UI types (`MainViewModel.JournalContributionData`,
  `HabitDetailViewModel.ContributionData`) are both `private`, so a top-level function in
  `ContributionMapper.kt` (this project's convention for cross-layer mapping) can't construct
  either from outside the ViewModel file.
- **Fix**: Rename the new domain type (e.g. `ContributionSummary`) to avoid the collision, and
  add an explicit Changes Required item: a `ContributionSummary.toUiState(now: Instant)` mapper
  in `ContributionMapper.kt`, with the two private UI-facing types made non-private (or
  constructed by the ViewModel from the mapper's return value) rather than left for the
  implementer to improvise.
- **Decision**: FIXED — domain type renamed to `ContributionSummary` throughout Phase 4; call
  sites now compose the existing `ContributionGrid.toUiState(now)`/`ContributionWindow.toUiState()`
  functions field-by-field inside each ViewModel (no new mapper file, no visibility change needed).

### F2 — Phase 1's constructor contract silently drops `syncScope`, contradicting Phase 3

- **Severity**: WARNING
- **Impact**: MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Blind Spots
- **Location**: Phase 1, item 4
- **Detail**: Phase 1 item 4 offers a choice between keeping `push*InBackground` as a
  "temporary bridge" or wiring Phase 3's `SyncScheduler` directly, calling "either...
  acceptable." But `SyncScheduler` doesn't exist until Phase 3 (its gradle deps, Hilt wiring,
  and interface are introduced there), Phase 1's explicit constructor snippet already omits
  `@ApplicationScope syncScope: CoroutineScope` (needed by the bridge methods) while only
  naming `habitDao`/`habitCheckInDao`/`transactionRunner` as removed params, Phase 3 item 5
  explicitly states `syncScope` is removed *there*, and Phase 1's own Success Criteria
  (assembleDebug + manual "writes reach remote" verification) are only satisfiable if the
  bridge is actually kept through Phase 1.
- **Fix**: Resolve the false choice — Phase 1 keeps `push*InBackground` calls and `syncScope:
  CoroutineScope` in the constructor unchanged; only Phase 3 swaps them for `SyncScheduler`.
  Delete the "or land Phase 3's SyncScheduler call directly here" sentence and add `syncScope:
  CoroutineScope` back into Phase 1's explicit constructor listing.
- **Decision**: FIXED — Phase 1 item 4 now commits to keeping `push*InBackground` and `syncScope`
  unchanged; the false either/or choice and its contradiction with Phase 3 item 5 are removed.

### F3 — Minor: wrong primitive name cited for `HabitCheckInDao`'s upsert pattern

- **Severity**: OBSERVATION
- **Impact**: LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 1, item 2
- **Detail**: Plan says `JournalEntryDao.upsertAll` should "reuse the existing `upsert(entity)`
  as the per-row primitive" mirroring `HabitCheckInDao.kt:45-48`. The actual per-row primitive
  there is named `upsertOne`, not `upsert`.
- **Fix**: s/upsert(entity)/upsertOne(entity)/ in Phase 1 item 2's Contract text.
- **Decision**: FIXED — corrected to `upsertOne(entity)` in Phase 1 item 2.
