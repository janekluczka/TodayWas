# Sectioned-List Styling, Habit-Detail Day Selection & List-Screen Navigation — Plan Brief

> Full plan: `context/changes/detail-nav-refinements/plan.md`

## What & Why

Four UI refinements bundled as one change: restyle `DsSectionedList`'s divider/corners, add
tap-to-select to the contribution grid, redesign Habit Detail around a selected day, and give Main
persistent navigation to the already-existing Journal/Habit list screens (with a new grid-colored
value cell on Habit List rows).

## Starting Point

`JournalListScreen`/`HabitListScreen` already exist and work — they shipped via a merged-but-never-
archived change (`ui-improvements`) and are only reachable today through a conditional "View all"
row that appears once a section exceeds 5 items. `DsContributionGrid` cells are purely decorative
(no click, no selection). `DsSectionedList` wraps everything in one uniformly-colored card with a
solid divider. Habit Detail shows a full scrollable history list below its grid.

## Desired End State

Main's Journal/Habit sections each show a persistent header arrow (no more conditional "View all"),
Journal's inline grid is gone. Tapping a day in Habit Detail's grid selects it (outlined) and shows
that day's value/actions below, replacing the old rows list. Habit List rows show a small
grid-colored cell with today's value. Sectioned lists everywhere show a subtle rounded, separated
look instead of one flat card.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Sectioned-list shape | Keep outer card silhouette, 2dp only at internal seams | Preserves today's outer look; requires per-item backgrounds since a transparent divider over one shared background would be invisible |
| Habit Detail rows list | Removed entirely, replaced by the day panel | User's explicit choice — one place to view/act on any day |
| Habit List day scope | Always today, no date selector on that screen | Day-scoped browsing stays exclusive to Habit Detail; List mirrors what Main already shows |
| Value cell color | Reuse the grid's own level→color lookup | Guarantees the badge and the real grid always agree, for free |
| Add via Habit Detail | Preserved for today/yesterday only | Matches an existing capability (`HabitDetailMapper`'s freshLoggableDates rows) that would otherwise regress |
| After-delete selection | Stays on the same day | User's explicit choice — useful when reviewing/cleaning up nearby days |
| Header navigation | Replaces "View all" entirely, cap stays | One clear nav affordance; sections remain short previews |
| Habit List row layout | New `DsListItem`-based row, Main's `HabitRow` untouched | User's explicit correction — this is screen-specific, not a shared-component unification |

## Scope

**In scope:**
- `DsSectionedList` divider/corner restyling
- `DsContributionGrid` tap-to-select + outlined state (new DS capability)
- Habit Detail selected-day redesign (grid selection + detail panel, old rows list removed)
- Main → `JournalListScreen`/`HabitListScreen` persistent-arrow navigation, Journal's inline grid
  removed
- Habit List value cell (new `DsContributionValueBadge`, `HabitUiState.todayLevel`)

**Out of scope:**
- The 24h edit window / delete-anytime semantics themselves (unchanged, just re-scoped to one day)
- The separate "Log Habit Check-Ins" FAB flow (unchanged)
- A date selector on Habit List
- Any change to `JournalListScreen`'s existing grid (already correct)
- Any change to Main's own `HabitRow` (unaffected by the Habit List redesign)

## Architecture / Approach

Design-system primitives first: `DsSectionedList` (Phase 1) and `DsContributionGrid` selection
(Phase 2) are additive, non-breaking changes with defaults that keep every existing caller
unchanged. Habit Detail (Phase 3) consumes Phase 2's new grid capability. Main's navigation
(Phase 4) is independent, wiring persistent arrows to screens that already exist. Habit List's
value cell (Phase 5) closes the loop, reusing Phase 2's now-`internal`-visible color lookup via a
new small `DsContributionValueBadge` component.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. DsSectionedList restyle | Transparent divider, 2dp internal-seam corners | Getting the per-corner shape formula right without breaking the outer silhouette |
| 2. Grid selection | Tap-to-select + outlined state on DsContributionGrid | Purely additive — low risk |
| 3. Habit Detail redesign | Selected-day panel replaces rows list | Reproducing today/yesterday's existing "add" capability correctly; future-date guard |
| 4. Main navigation | Persistent header arrows, Journal grid removed | Cleanly removing now-dead `journalContributionCells`/`DsContributionRow` |
| 5. Habit List value cell | Grid-colored badge with value text | Keeping Main's `HabitRow` untouched while extending the shared mapper |

**Prerequisites:** None beyond this being a solo change on `feature/refinements`.
**Estimated effort:** ~5 focused sessions, one per phase.

## Open Risks & Assumptions

- Phase 1's per-item shape logic fixes a latent inconsistency in today's divider-placement check
  (`onViewAllClicked != null` alone vs. the render condition needing `viewAllLabel` too) as a
  necessary side effect, not separate scope creep.
- Phase 3 assumes `GetFreshLoggableDatesUseCase` continues to return exactly today+yesterday; if
  that ever changes, the day panel's "addable" rule tracks it automatically since it reads the same
  use case rather than hardcoding two dates.
- No instrumented/E2E tests added — relying on manual verification for grid-tap and navigation
  flows, consistent with this codebase's current testing balance.

## Success Criteria (Summary)

- Sectioned lists show a rounded, separated look with no regression to their outer silhouette.
- A user can tap any day in Habit Detail's grid and see/act on that day via the panel below it,
  with the same 24h-edit/always-deletable rules as before.
- Main's Journal/Habit sections always show a working header arrow to their full list screens, with
  no inline grid on Journal.
- Habit List rows show a value cell whose color always matches what Habit Detail's own grid shows.
