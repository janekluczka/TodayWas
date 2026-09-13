---
change_id: journal-edit-screen-refine
title: Replace journal edit bottom sheet with a dedicated edit screen, unify AI assist
status: archived
created: 2026-09-13
updated: 2026-09-13
archived_at: 2026-09-13T19:34:49Z
---

## Notes

Replace the journal edit bottom sheet with a dedicated Edit Journal Entry screen (own nav route,
mirrors Add Journal Entry's layout with a fixed/non-interactive date display and pre-filled text),
and unify AI assist so both Add and Edit screens show "Help me start" when the text field is empty
and "Help me refine" when it has content — including fixing Detail's refine flow to use the
server-enforced remainingToday quota instead of its separate client-side MAX_REGENERATIONS cap,
consistent with how Add's "help me start" already works.
