# Journal Daily Entry — Plan Brief

> Full plan: `context/changes/journal-daily-entry/plan.md`

## What & Why

Implement S-02, the project's north star: a user writes a free-text journal entry for today (or
yesterday, if not yet logged), sees it listed on the main screen, and can open a past entry to
read it back. This is the smallest slice that proves the core product hypothesis — presence-based
tracking without streak guilt — so it's sequenced first after onboarding.

## Starting Point

`MainScreen` is currently a static empty-state screen with no ViewModel (left that way after S-01
deleted the old dialog-era `MainViewModel`). The Room database has one entity (the onboarding
singleton row). `Focus` (journal/habit/both, picked during onboarding) has never been read by any
screen. No text-input or list-item design-system components exist yet.

## Desired End State

A user with journal focus enabled sees a Journal section on the main screen: past entries in a
list, and an "Add entry" action that disappears once both today and yesterday are logged. Adding
an entry is a full screen (text + a Today/Yesterday choice when both are open); tapping a past
entry opens a read-only detail screen. A user who picked habit-only focus sees a placeholder
instead. Everything persists locally, no account required.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| View scope | Journal entries get a read-only detail view (habit values don't, since S-03 doesn't exist) | User explicitly wants a "List → item → detail" structure for journal, not just a bare cell |
| Backfill | Only "today" or "yesterday", never an open date picker, and only while that date is unlogged | Keeps the add-entry flow simple while still covering the realistic "forgot yesterday" case |
| Entries/day | One entry per calendar day | Matches "the daily journal entry" framing; keeps a day's list row unambiguous |
| Entry fields | id, date, text, createdAt | `createdAt` is added now so S-04's 24h edit window doesn't need its own migration later |
| Formatting | Plain multi-line text only | FR-003 says "free text"; rich text is real added scope (storage format, toolbar, rendering) with no PRD ask behind it |
| Main screen shape | A plain list (no contribution grid) | User rejected the grid-first framing — grid/intensity is FR-011's job (S-05), not S-02's |
| Focus gating | Real gating now (placeholder shown for habit-only focus) | Matches the PRD's per-focus intent and is immediately reusable once S-03 adds real habit content |
| Add-entry UI | Full screen via Navigation 3 | Matches this repo's own "give it its own screen once it's screen-scale" lesson from S-01 |
| Bottom navigation | Deferred entirely | Habits/Settings tabs would ship empty right now; not worth the scaffolding cost under the 3-week deadline |
| Detail view | Read-only, no editing | Keeps S-02 and S-04 (edit window) cleanly separated |
| Duplicate add | Not offered as an option (rather than allowing silent overwrite) | Avoids needing any overwrite/error UI — the entry point only ever reflects what's still addable |
| Room migration | Kept destructive (no real `Migration` object) | Explicit, timeline-driven call — flagged as a real risk to revisit before shipping, since this now involves actual user content |
| Testing bar | Matches S-01 exactly (fake-repo tests, Robolectric round-trip, ktlint + build) | Proven pattern, already the house convention |

## Scope

**In scope:**
- Journal entry data layer (entity/DAO/repository) added to the existing database
- `MainViewModel` (recreated with real content) + `Focus`-based gating
- Add-entry screen (Today/Yesterday choice, plain text) and read-only detail screen
- One new design-system component (`TodayWasTextField`)
- Navigation 3 wiring for the two new screens

**Out of scope:**
- Editing entries, habit tracking, bottom navigation, the contribution-intensity grid, rich text,
  arbitrary-past-date backfill, a real Room migration, account sync, AI-assist

## Architecture / Approach

Mirrors S-01's four-phase layering (Data → Domain → Presentation → UI) exactly. The one new
architectural piece: `MainViewModel` combines two observation sources (`Focus` from the existing
onboarding use case, journal entries from a new one) into one state, and derives which dates are
still addable itself — that computation travels through the nav key as a `JournalDateSlot` enum
(`TODAY`/`YESTERDAY`), resolved to an actual calendar date only at save time inside the use case,
so a stale date can never survive navigation across a midnight boundary. The Add-entry and detail
screens are new root-level Navigation 3 destinations on the *same* back stack `TodayWasApp`
already manages — no nested/tabbed navigation needed yet.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Data layer | New entity/DAO/repository on the existing DB | Destructive migration now risks real user data, not just an onboarding flag |
| 2. Domain | Two thin use cases, slot→date resolution rule | Low risk — mirrors existing use-case shape exactly |
| 3. Presentation | Real `MainViewModel` + `AddJournalEntryViewModel` | First use of assisted-injection for a Hilt ViewModel taking nav-key data |
| 4. UI | New text-input component, two new screens, nav wiring | First-ever list/detail UI in this app — no existing component to lean on |

**Prerequisites:** S-01 (done, archived). No blockers.
**Estimated effort:** ~4 sessions across 4 phases, similar scale to S-01.

## Open Risks & Assumptions

- Destructive migration is kept deliberately (see Migration Notes in the full plan) — this is a
  real, accepted risk for future schema changes, not an oversight.
- `LocalDate.now()` is called directly in `MainViewModel`/`AddJournalEntryUseCase` with no injected
  clock abstraction — acceptable for unit testing at this scale (no existing precedent for time
  injection in this codebase), but worth revisiting if date-boundary tests ever become flaky.
- The detail screen's "pass a data snapshot instead of an ID" design is only valid because entries
  are immutable in this slice — flagged explicitly to revisit once S-04 adds editing.

## Success Criteria (Summary)

- A user can write today's (or yesterday's) journal entry with no account, see it immediately on
  the main screen, and it survives an app restart.
- A user can open a past entry and read it back exactly as written.
- A user who picked habit-only focus never sees the journal entry point at all.
