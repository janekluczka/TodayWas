---
change_id: architecture-hardening
title: Architecture hardening
status: impl_reviewed
created: 2026-08-18
updated: 2026-08-20
archived_at: null
---

## Notes

Scoped across three prior changes but never planned until now. Current list, as of 2026-08-18
(see `[[project_architecture_hardening_pending]]` memory for the full history):

- `syncMutex`/`tryLock` background-push pattern has no real retry. Official Google pattern for
  this is a WorkManager-driven outbox (`NetworkType.CONNECTED` constraint, `Result.retry()` for
  exponential backoff), not a `Mutex.tryLock()` fire-and-forget coroutine launch (per
  `sync-strategy-rework`'s frame research, `context/changes/sync-strategy-rework/frame.md`).
- `MainViewModel`/`HabitDetailViewModel` call `domain/util` calculators directly instead of
  through a use case.
- Zero test coverage on `AuthRepositoryImpl`/`AiAssistRepositoryImpl`.
- Untested `HabitMapper` status-derivation logic (unverified whether still accurate as of this
  writing — re-check when scoping).
- From `sync-strategy-rework`'s implementation review (2026-08-18,
  `context/changes/sync-strategy-rework/reviews/impl-review.md`, findings F1/F2): the remote
  `habit_check_ins.habit_id` FK still has a live `ON DELETE CASCADE`, dormant until a habit's
  tombstone crosses the 30-day GC window — at that point it can hard-delete check-in rows that
  never went through their own tombstone/GC lifecycle (narrow multi-device race, not reachable
  through today's UI, but the FK design itself is inconsistent with the soft-delete model). And
  `syncWithRemote()`'s local apply loop isn't wrapped in the existing `TransactionRunner` the way
  `deleteHabit` already is, so a mid-sync exception can leave partial local state applied
  (self-healing on next sync, not a regression, but the same class of durability concern as the
  `syncMutex` item above).

Already resolved, do not re-scope: `HabitDetailViewModel.onSaveClicked`'s inline update-vs-create
branching (fixed by `SaveHabitCheckInsUseCase` in `sync-strategy-rework` Phase 6). Already
explicitly rejected, do not re-open: switching signed-in writes from local-first-then-push to
remote-first-then-refetch (`sync-strategy-rework`'s `/10x-frame` pass concluded local-first is
correct per Google's official offline-first guidance; the real gap was missing conflict-resolution
metadata, which that change fixed).

This list was compiled from cross-references in other changes' docs, not a dedicated audit — treat
it as a starting point for research, not ground truth.

## Plan Review

`/10x-plan-review` ran 2026-08-19 (deep mode): 1 critical, 1 warning, 1 observation, all fixed
during triage. See `context/changes/architecture-hardening/reviews/plan-review.md`. Plan is now
SOUND — ready for `/10x-implement`.
