---
change_id: sync-strategy-rework
title: Reconsider local-first-then-push vs remote-first-then-refetch for signed-in writes
status: implementing
created: 2026-08-17
updated: 2026-08-18
archived_at: null
---

## Notes

reconsider this app's local-first-then-background-push write strategy (used by every add/update/delete today) against a more standard remote-API-first-then-refetch-local pattern, at least for signed-in writes. Raised during entry-delete's implementation review triage (2026-08-17): user proposed calling the remote API first and refetching updated data afterward, rather than writing locally first and pushing to remote best-effort in the background. Deliberately deferred out of entry-delete since it's a bigger, precedent-breaking architectural question — user said "if this is a radical change then we'll plan it as new 10x stuff." Related but distinct from the already-scoped-but-never-planned architecture-hardening change (which covers the syncMutex/tryLock retry weak spot, UI-layer business logic entanglement, and untested repositories) — that change's sync-reliability concern (local deletes/edits getting silently resurrected by the next full sync) is part of the same underlying local-vs-remote consistency question and should be considered together when framing this.
