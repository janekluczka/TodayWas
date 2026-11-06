# Main Screen Improvements — Plan Brief

> Full plan: `context/changes/ui-improvements/plan.md`
> Research: `context/changes/ui-improvements/research.md`

## What & Why

Rework the Main/Hub screen's journal and habit sections: a proper empty state for brand-new users,
lists capped at 5 items with dedicated "view all" screens once there's more, and a new reusable
sectioned-list design-system component backing both. A few adjacent bugs found while researching
this screen (loading-state flash, a blank-looking habit status, a fixed 50/50 layout) ride along.

## Starting Point

`MainScreen.kt` currently renders every journal entry and habit with no cap, as raw
`Column`/`Row` rows with no card/divider/tap affordance. There's no loading state (a returning
user can briefly see a false "no entries yet"), the two sections always split the screen exactly
in half regardless of content, and an unlogged habit renders as a blank space next to its name.

## Desired End State

A brand-new user sees one clear empty state with two CTAs instead of two weak ones. Both sections
show up to 5 items in a bordered, divided card with a "View all" row when there's more, leading to
a full sortable list. Section height reflects how much content is actually there. Loading shows a
skeleton instead of a false-empty flash.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Empty state (both lists empty) | Single unified state, 2 CTAs | One clear first-run moment beats two separate weak ones | Plan |
| New DS component scope | Generic `DsSectionedList`, not Main-only | Matches the explicit ask for a reusable component; other screens can reuse the "section with overflow" pattern later | Plan |
| Habit ordering when capped | Most-recently-checked-in first | Surfaces what's actually active, computable from data already in-flight (no new use case) | Plan |
| `NotLogged` display bug | Fix now (real label, not blank) | Cheap fix while already touching this row for the new component | Plan |
| Loading state | Add `isLoading` + static skeleton rows | Fixes the false-empty-flash bug from research directly | Plan |
| View-all screens | Flat full list + `DsChip` sort (no search/filter) | Matches what was asked without meaningful scope creep | Plan |
| Extra research fixes folded in | Section padding asymmetry + weighted (not fixed 50/50) sizing | Both cheap while already restructuring these sections; habit contribution-grid parity deferred as its own follow-up | Plan |
| Habit row tap behavior | Unchanged (still opens detail, not quick-log) | Deeper interaction redesign, out of scope for this pass | Plan |

## Scope

**In scope:**
- Unified + per-section empty states
- `DsSectionedList` component (title, capped rows, "View all" row, loading skeleton)
- Loading state on Main
- Habit recency sort (Main + view-all default)
- Two new view-all screens with sort chips
- Section weighting fix, padding-asymmetry fix, `NotLogged` label fix

**Out of scope:**
- Search/date-range filtering
- Habit contribution-grid on Main (own future change)
- Habit-row tap-to-quick-log redesign
- Any account-sheet/FAB/contribution-grid changes

## Architecture / Approach

Bottom-up: fix the data layer (loading flag, sortable habit mapper) first since both Main and the
new screens depend on it → build the reusable `DsSectionedList` component next → wire Main onto it
→ add the two new view-all screens, which reuse the same sort mapper and row composables. No new
domain use cases or schema changes — habit recency sort is computed from data `MainViewModel`
already collects (`HabitCheckInBoard` carries every check-in, not just today's).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. ViewModel: loading + habit sort | `isLoading` flag, sort-aware habit mapper reused in Phase 4 | Low — pure mapper/state addition, no UI change yet |
| 2. `DsSectionedList` component | New reusable DS component with loading skeleton | Low — isolated, previewable in isolation |
| 3. Main screen integration | Empty states, capped sections on the new component, weighted sizing | Medium — most user-visible surface area, several branches (loading/unified-empty/per-section-empty/populated) |
| 4. View-all screens | Two new full-list screens with sort chips | Medium — two new nav destinations + ViewModels, but follows established patterns closely |

**Prerequisites:** None beyond current `master`/`feature/ui-improvements` state.
**Estimated effort:** ~4 focused sessions, one per phase.

## Open Risks & Assumptions

- Assumes list sizes stay modest (local-first, single-user app) — no pagination/virtualization
  considered; revisit if that assumption changes.
- The static (non-animated) skeleton placeholder is a deliberate scope cut — a shimmer animation
  would be a nice-to-have, not required for this plan to ship.

## Success Criteria (Summary)

- A brand-new user's first Main-screen view is one clear empty state, not two weak ones.
- No section ever shows more than 5 items without an obvious way to see the rest.
- No user ever sees a false "empty" flash before their real data loads.
