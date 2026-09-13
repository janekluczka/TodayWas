---
change_id: detail-nav-refinements
title: Sectioned-list styling, habit-detail selected-day redesign, and list-screen navigation
status: impl_reviewed
created: 2026-09-13
updated: 2026-09-13
archived_at: null
---

## Notes

Sectioned-list divider/corner styling, habit-detail selected-day redesign, and header-arrow
navigation to journal/habit list screens with a day-scoped value indicator on habit rows.

Discovered while starting this change: `context/changes/ui-improvements/` (status `planned`,
never updated) already shipped and merged to master (PR #37) — `JournalListScreen`/`HabitListScreen`
plus their nav keys already exist and are reachable today via a conditional "View all" row (only
shown once a section exceeds 5-6 items). Its Manual Progress checkboxes were also never checked off
despite the automated ones passing. Not this change's job to fix, but flagged here since this
change's navigation work builds directly on top of it (making those screens reachable via a
persistent header arrow instead of the conditional row).
