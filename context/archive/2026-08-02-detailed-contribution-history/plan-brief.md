# Detailed contribution history — Plan Brief

> Full plan: `context/changes/detailed-contribution-history/plan.md`

## What & Why

Build a GitHub-contribution-style history grid for journals and habits (FR-011), the last piece of
the PRD's "colored contribution cell" vision that was deliberately deferred out of the Main screen
during S-04/S-05 scoping. Journal has no numeric value, so its grid is presence-only; habits are
colored by a 5-level bucket relative to that habit's own all-time value range.

## Starting Point

No color-intensity/grid logic exists anywhere today — Main renders plain text rows, and S-04's
`HabitDetailScreen` was built as an explicit placeholder list, flagged in its own plan as
"a deliberately minimal placeholder for what S-05 will later turn into a full contribution grid."
Journal has no history view at all beyond Main's flat entry list.

## Desired End State

Habit Detail shows a read-only GitHub-style grid with a "Last 12 months / calendar year" chip
picker; Main's Journal section shows an equivalent grid + chips embedded above the existing entries
list. Editing (habit check-ins, journal text) moves from S-04's whole-screen/inline edit modes into
a `ModalBottomSheet` opened by an explicit Edit action — the grid itself is never tappable.

## Key Decisions Made

| Decision                                   | Choice                                                      | Why (1 sentence)                                                                 |
| ------------------------------------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| Habit color algorithm                       | 5-level quantile buckets over all-time values                | Matches PRD's "intensity relative to own historical range"; stable across year switches |
| Journal color signal                        | Binary presence only (fixed shade)                            | Journal has no numeric value — a proxy like word count was rejected as misleading  |
| Grid layout                                 | GitHub-style weeks×days, `LazyHorizontalGrid(rows=Fixed(7))`  | Literal match to the PRD's named reference; first grid in the codebase              |
| Time range                                  | "Last 12 months" default + selectable calendar years (chips)  | Mirrors GitHub's own year picker                                                    |
| Grid interaction                            | Read-only, no tap-to-edit                                     | Keeps grid rendering fully decoupled from write paths                              |
| Habit editing (post S-04 list removal)      | `ModalBottomSheet` opened via Edit action, reuses S-04's row list | Preserves the just-shipped edit capability without a full redesign                |
| Journal editing                             | Same `ModalBottomSheet` pattern, replacing inline text swap   | Consistency with habit's new edit pattern, per this session's decision            |
| Journal entry point                         | Embedded directly on Main (header → grid → chips → entries)  | No bottom-nav/tabs yet; deferred to future navigation work                        |
| Sparse-data bucketing                       | Always compute from whatever data exists, no neutral fallback | Simpler, single code path; explicitly chosen over a minimum-sample-size threshold |

## Scope

**In scope:**
- Domain: `ContributionWindow`, `ContributionLevel`, `ContributionGrid`, quantile calculator
  (habit), presence calculator (journal)
- `:core:designsystem`: new `TodayWasContributionGrid`, extended `TodayWasChip` (selected/onClick)
- Habit Detail: grid + chips replace the S-04 list; edit moves into a bottom sheet
- Main screen: journal grid + chips embedded in the Journal section
- Journal Entry Detail: edit moves into a bottom sheet

**Out of scope:**
- Tap-to-edit on grid cells
- A separate journal history screen / bottom-nav navigation
- A calendar-month grid view or grid-style toggle/preference
- Pagination of grid data
- Any new DB schema, queries, or repository methods

## Architecture / Approach

One shared foundation phase (pure domain calculators + reusable grid/chip UI components), then
Presentation-then-UI phases per vertical. No separate per-vertical domain phase: bucketing is pure
math over data already available via `ObserveHabitCheckInBoardUseCase`/`ObserveJournalEntriesUseCase`,
not a new use case.

## Phases at a Glance

| Phase                     | What it delivers                                                         | Key risk                                                        |
| -------------------------- | --------------------------------------------------------------------------| ------------------------------------------------------------------ |
| 1. Shared foundation       | Domain calculators + `TodayWasContributionGrid` + extended `TodayWasChip` | Grid weekday-alignment padding and fixed (non-dynamic) color palette are easy to get subtly wrong |
| 2. Habit — Presentation    | `HabitDetailViewModel` grid/window state, `isEditSheetOpen` rename        | Recomputing quantiles from the wrong data subset (window instead of all-time) |
| 3. Habit — UI              | Grid + chips as screen body; Edit opens bottom sheet with S-04's row list | Sheet dismiss must route through the same discard path as Cancel |
| 4. Journal — Presentation  | `MainViewModel` gains journal grid/window state                          | Folding a new selection source into the existing 4-way `combine` correctly |
| 5. Journal — UI            | Main embeds grid + chips; Journal Entry Detail edit moves to bottom sheet | Keeping the existing entries list and detail nav untouched |

**Prerequisites:** S-02 (journal) and S-03 (habit) verticals exist; S-04 (edit-within-24h-window)
already shipped and is archived.
**Estimated effort:** ~2-3 sessions across 5 phases.

## Open Risks & Assumptions

- All-time quantile bucketing means a habit logged with identical values every day will render
  every day at the maximum shade (`LEVEL_5`) — this is documented as expected behavior, not a bug,
  given the chosen algorithm.
- `LazyHorizontalGrid`/`ModalBottomSheet` are both first-time usages in this codebase; no existing
  pattern to fall back on if either behaves unexpectedly at implementation time.

## Success Criteria (Summary)

- A habit with varying check-in values shows a grid with visibly different shades; switching years
  changes the range shown without changing any day's shade.
- Main's Journal section shows a presence-based grid above the existing entries list.
- Editing either a habit check-in or a journal entry works via a bottom sheet, and dismissing the
  sheet discards unsaved changes exactly like Cancel does.
