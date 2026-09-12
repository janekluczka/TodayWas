---
date: 2026-09-11T22:35:46+02:00
researcher: Claude Sonnet 5
git_commit: 4d3bc62b7ac929d6771cd9527d4dbafe2dc1c1d6
branch: feature/mvp-certification-test-plan
repository: janekluczka/TodayWas
topic: "Rollout Phase 1 grounding: sync & deletion critical-path risks (test-plan.md §2 risks #1, #2, #3)"
tags: [research, codebase, sync, deletion, hilt-di, account-creation]
status: complete
last_updated: 2026-09-11
last_updated_by: Claude Sonnet 5
---

# Research: Rollout Phase 1 grounding — sync & deletion critical-path risks

**Date**: 2026-09-11T22:35:46+02:00
**Researcher**: Claude Sonnet 5
**Git Commit**: 4d3bc62b7ac929d6771cd9527d4dbafe2dc1c1d6
**Branch**: feature/mvp-certification-test-plan
**Repository**: janekluczka/TodayWas

## Research Question

Ground rollout Phase 1 of `context/foundation/test-plan.md` ("Sync & deletion critical-path coverage", risks #1–#3): verify the soft-delete dirty-detection path, the account-creation bulk-upload path, and the ongoing sync-merge/mutex safety — with real code evidence, not assumption — so `/10x-plan` can design tests against verified failure modes rather than hypothesized ones.

## Summary

Two of the three original risks needed correction after grounding:

1. **Original Risk #1 ("soft-delete doesn't sync") is NOT a live bug.** The historical commit note it was based on (`f0bb263`) misdiagnosed the mechanism even at the time it was written. `SyncMerge.kt`'s `mergeForSync()` decides push/apply priority using a boolean `isDeleted` flag (derived directly from `deletedAt != null`), never by comparing `updatedAt`. So the fact that `softDeleteById`/`softDeleteByHabitId` never bump `updatedAt` is real but inconsequential — deletions push correctly for all three entity types. The real gap is narrower: no test exercises the full DAO→mapper→merge→push chain end-to-end, so a regression in any one link (e.g. the `isDeleted` derivation in a mapper) would go undetected.

2. **Original Risk #3 ("ongoing merge overwrites concurrent edits") is confirmed, and worse than assumed.** The `syncMutex: Mutex` in `HabitRepositoryImpl`/`JournalRepositoryImpl` cannot serialize concurrent `syncWithRemote()` calls, because neither repository (nor `SyncLocalDataUseCase`, nor their Hilt bindings) is `@Singleton`-scoped — every injection site (`MainViewModel`, `AccountViewModel`, `OnboardingAccountSetupViewModel`, `SyncWorker`) gets its own repository instance with its own independent `Mutex`. The mutex only serializes two calls made through the literal same instance, which in practice is close to never. This is a new, previously-undocumented, code-confirmed bug.

3. **Original Risk #2 ("account-upload drops or duplicates data") — duplication is structurally a non-issue** (client-generated UUID PKs + `upsert(onConflict=id)` make retries idempotent by construction). **Silent failure is the real, concrete risk**: `AccountViewModel.onSyncConfirmClicked()` and the equivalent `OnboardingAccountSetupViewModel` method call `finishPostSyncAction()` **unconditionally**, regardless of whether `syncLocalData()` succeeded. On failure, the user is routed to the same terminal screen as success, with no error shown — the only difference is `hasSyncedLocalData` stays `false`, silently re-offering the review next launch. Zero test coverage exists for this path today.

## Detailed Findings

### Sync/deletion pipeline structure

- `JournalRepositoryImpl.kt:72-92` / `HabitRepositoryImpl.kt:121-164` — `syncWithRemote()`: fetch local (`getAllIncludingDeleted()`) + remote (`fetchAll()`) → `mergeForSync()` → bulk `upsert()` push → `applyRemoteSnapshot()` → `purgeDeletedBefore()` GC, all inside `syncMutex.withLock { remoteCall { ... } }`.
- `data/util/SyncMerge.kt:25-52` (`mergeForSync`) — the single source of truth for push/apply decisions across all three entities.
- Writes no longer push individually in the background (that pattern was removed in `architecture-hardening`, commit `83aad02`). Every mutating repository method now calls `scheduleSyncIfSignedIn()` → `SyncScheduler.scheduleSync()` (`data/util/WorkManagerSyncScheduler.kt:23-35`), which enqueues a `SyncWorker` (`data/worker/SyncWorker.kt:12-24`) under unique work name `"sync-local-data"` with `ExistingWorkPolicy.KEEP`, `NetworkType.CONNECTED`, and exponential backoff.
- Four independent call sites reach `syncWithRemote()` today: `MainViewModel.kt:125` (launch-time silent resync for signed-in users), `AccountViewModel.kt:264,273`, `OnboardingAccountSetupViewModel.kt:255,264`, and `SyncWorker.kt:18`.

### Risk #1 — soft-delete push-eligibility (reframed)

- All three soft-delete queries omit `updatedAt`: `HabitCheckInDao.kt:55-59,61-67`, `JournalEntryDao.kt:42-46`, `HabitDao.kt:32-36` — confirmed identical gap across all three entities, not check-in-specific.
- `mergeForSync()` (`data/util/SyncMerge.kt:36-49`) branches on `isDeleted` *before* any `updatedAt` comparison:
  ```kotlin
  localMeta != null && remoteMeta == null -> pushIds += id   // unconditional, no timestamp check
  remoteMeta.isDeleted -> applyIds += id
  localMeta.isDeleted -> pushIds += id
  localMeta.updatedAt > remoteMeta.updatedAt -> pushIds += id
  else -> applyIds += id
  ```
- `isDeleted` is derived from `deletedAt != null` in each entity's `toSyncMeta()` mapper (`HabitCheckInEntityMapper.kt:25-29`, `HabitEntityMapper.kt:27-31`, `JournalEntryEntityMapper.kt:24-28`) — never from `updatedAt`.
- **Verdict: confirmed absent as a live bug.** `SyncMerge.kt` was introduced (`f0c79a1`) the day before the commit note (`f0bb263`) that raised the concern; the note's premise doesn't match the code as it existed at that time or since.
- Real gap: `SyncMergeTest.kt` tests `mergeForSync()` in isolation with hand-built `SyncMeta` values; DAO tests (`HabitCheckInDaoTest.kt:238-281`, `JournalEntryDaoTest.kt:187-226`) only assert `deletedAt` gets set and active reads filter it out. **No test exercises the real DAO → mapper → merge → push chain end-to-end** for a soft-deleted row.

### Risk #3 — merge conflict rule and mutex safety (confirmed + new finding)

- Conflict rule is row-level, `updatedAt`-based, with tombstone supremacy in both directions (`SyncMerge.kt:25-52`): a numerically-newer non-deleted edit never resurrects a tombstone on either side. A tie (`localMeta.updatedAt == remoteMeta.updatedAt`) falls through to `else -> applyIds` — remote silently wins ties, untested.
- **New finding**: `syncMutex` (`HabitRepositoryImpl.kt:40`, `JournalRepositoryImpl.kt:34`) is a per-instance field, and neither repository impl, their `@Binds` in `RepositoryModule.kt:35-39`, nor `SyncLocalDataUseCase.kt:7` carries `@Singleton` — confirmed by grep, zero matches. Under Hilt, this means each of the four call sites (`MainViewModel`, `AccountViewModel`, `OnboardingAccountSetupViewModel`, `SyncWorker`) receives its **own** repository instance and therefore its **own** `Mutex`. The mutex's own comment claims it "guards against two concurrent `syncWithRemote()` calls racing each other" — that guarantee does not hold across call sites, only within accidentally-shared instances.
- Untested paths: tie-break (remote-wins-on-tie), both-sides-tombstoned simultaneously, the "local wins" tombstone direction at the repository/integration level (only proven at the pure-function `SyncMergeTest` level), and any actual concurrent-invocation/mutex-contention scenario (no test constructs repositories through Hilt).

### Risk #2 — account-creation bulk upload (confirmed + sharpened)

- `GetLocalDataSummaryUseCase.kt:9-19`, `MarkLocalDataSyncedUseCase.kt:6-11`, `SyncLocalDataUseCase.kt:7-18` read/act as expected; `SyncLocalDataUseCase` runs both repositories' `syncWithRemote()` unconditionally (habit sync still runs even if journal sync already failed) and surfaces whichever failed first.
- **New finding**: `AccountViewModel.kt:256-278` — `onSyncConfirmClicked()` calls `finishPostSyncAction(postSyncAction)` **unconditionally**, regardless of `result.isSuccess` from `syncLocalData()`. `markLocalDataSynced()` is correctly gated on success (so the flag itself isn't falsely set), but the user is routed to the same terminal UI state whether the sync worked or failed — no `ShowError`, no retry prompt. Same pattern in `OnboardingAccountSetupViewModel.kt:264-265`.
- Push is a single bulk Postgrest `upsert(onConflict=id)` call per table (`RemoteJournalDataSourceImpl.kt:19-21` and equivalents) — atomic per table at the statement level, but the three tables sync independently with no cross-table transaction. Local apply is transactional for habits (`LocalHabitDataSourceImpl.kt:85-93`, wrapped in `transactionRunner.runInTransaction`) but **not** for journal entries (`LocalJournalDataSourceImpl.kt:46-48` calls `dao.upsertAll()` directly) — a known, deliberately-deferred gap (`sync-strategy-rework` impl-review F2, "SKIPPED").
- Duplicate-prevention is solid and structural: client-generated UUID PKs never change across retries, so re-running a failed sync re-upserts the same ids — no duplicate remote rows possible by construction, not something a test needs to prove.
- Zero test coverage for: a failed `syncLocalData()` during `SyncConfirmClicked` (what UI state results, whether an error is surfaced); a multi-table partial failure (journal succeeds, habit fails); the non-transactional journal-entry apply path failing mid-batch.

## Code References

- `app/src/main/java/pl/luczka/todaywas/data/util/SyncMerge.kt:25-52` — the merge decision function, ground truth for both Risk #1 and #3
- `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt:40,121-164` — mutex field + `syncWithRemote()`
- `app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt:34,72-92` — mutex field + `syncWithRemote()`
- `app/src/main/java/pl/luczka/todaywas/di/RepositoryModule.kt:35-39` — unscoped `@Binds` for both repositories (no `@Singleton`)
- `app/src/main/java/pl/luczka/todaywas/domain/usecase/SyncLocalDataUseCase.kt:7-18` — unscoped, combinator over both repos
- `app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt:256-278` — `proceedAfterAuthSuccess`/`onSyncConfirmClicked`, the unconditional `finishPostSyncAction` bug
- `app/src/main/java/pl/luczka/todaywas/ui/onboarding/accountsetup/OnboardingAccountSetupViewModel.kt:255,264-265` — same pattern
- `app/src/main/java/pl/luczka/todaywas/data/local/dao/HabitCheckInDao.kt:55-67`, `JournalEntryDao.kt:42-46`, `HabitDao.kt:32-36` — soft-delete queries, none bump `updatedAt`
- `app/src/main/java/pl/luczka/todaywas/data/local/api/LocalHabitDataSourceImpl.kt:85-93` (transactional apply) vs `LocalJournalDataSourceImpl.kt:46-48` (non-transactional apply)
- `app/src/main/java/pl/luczka/todaywas/data/worker/SyncWorker.kt:12-24` — WorkManager entry point
- `app/src/test/java/pl/luczka/todaywas/data/util/SyncMergeTest.kt` — pure-function merge coverage (tie-break and both-tombstoned cases absent)
- `app/src/test/java/pl/luczka/todaywas/ui/account/AccountViewModelTest.kt` — no failed-sync-during-confirm test

## Architecture Insights

- The sync architecture evolved through three changes: `account-creation-and-sync` (naive push-then-pull, no conflict metadata) → `sync-strategy-rework` (added `updatedAt`/`deletedAt` tombstones + `mergeForSync`, deliberately deferred transactional-apply and mutex/outbox durability work) → `architecture-hardening` (replaced per-write background push with a WorkManager outbox, split CRUD from sync via a `Syncable` interface, fixed the dormant FK-cascade). The mutex now guards a single code path (`syncWithRemote()`) rather than the original two-path race it was designed for — but the DI scoping was never revisited after that consolidation, leaving it structurally unable to do its one remaining job.
- This project's own convention against "concrete framework class injection" (wrap `RoomDatabase` etc. behind a small interface) was followed for `TransactionRunner`/`SyncScheduler`, but Hilt scoping (`@Singleton`) appears to have been overlooked as a separate, equally load-bearing concern for stateful sync coordination.
- Tombstone supremacy (`SyncMerge.kt`) is a deliberate, well-tested design choice: a delete always wins over a conflicting edit regardless of timestamp, in both directions. This is the correct policy for this product's "delete is always allowed, no undo" stance (per `CLAUDE.md`).

## Historical Context (from prior changes)

- `context/archive/2026-08-10-account-creation-and-sync/reviews/impl-review.md` — F1 (stale `hasSyncedLocalData` on failed sign-out revoke) and F2 (launch-pull racing a concurrent edit) were fixed via reactive `AuthState` observation and the original `syncMutex`, respectively. Both were superseded by the current architecture; F2's underlying race class (a sync racing a write) is what the newly-found DI-scoping bug reopens in a different shape.
- `context/changes/sync-strategy-rework/reviews/impl-review.md` — F1 (dormant `ON DELETE CASCADE` on `habit_check_ins.habit_id`) was **fixed and verified live** in `architecture-hardening` Phase 2 (commit `259524e`) — do not treat as open. F2 (non-transactional local apply loop) was explicitly **SKIPPED**, deferred indefinitely — still open today, confirmed by this research (journal-entry apply path).
- `context/archive/2026-08-18-architecture-hardening/plan.md` — replaced `Mutex.tryLock()`/fire-and-forget background push with the current WorkManager outbox (`SyncScheduler`). This consolidation is what made the `syncMutex`'s original two-path justification stale, without anyone revisiting whether the mutex still achieved its stated purpose under the new call-site topology.

## Related Research

None prior — this is the first research document for the `testing-sync-deletion-critical-path` rollout phase.

## Open Questions

- Should the `@Singleton` fix (or an equivalent — e.g. a dedicated `SyncCoordinator` singleton wrapping both repositories) be in scope for this test-rollout phase's plan, or is it a separate `architecture-hardening`-style fix that the test phase should merely *prove* is broken (regression test documenting current behavior) without fixing? This is a planning decision, not a research one — `/10x-plan` should decide based on cost×signal (a fix + test is more valuable than a test alone, but changes scope from "test rollout" to "bug fix").
- Does the non-transactional journal-entry apply path (`LocalJournalDataSourceImpl.kt:46-48`) share the same risk profile as the already-tested habit path, or does `JournalEntryDao.upsertAll()`'s own `@Transaction` annotation make table-level atomicity sufficient even without the outer `transactionRunner` wrap? Worth a quick confirmation during planning before writing a redundant test.
