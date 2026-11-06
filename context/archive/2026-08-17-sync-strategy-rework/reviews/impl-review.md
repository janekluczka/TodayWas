<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Sync Strategy Rework

- **Plan**: context/changes/sync-strategy-rework/plan.md
- **Scope**: Phase 6 of 6 (full plan review)
- **Date**: 2026-08-18
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Remote `habit_check_ins` FK cascade can bypass the tombstone lifecycle during habit GC purge

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Safety & Quality
- **Location**: `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt:176-182` (GC purge ordering); live Postgres FK `habit_check_ins_habit_id_fkey ... ON DELETE CASCADE`
- **Detail**: `deleteHabit` soft-deletes a habit and its check-ins together (same `deletedAt`), and the plan is explicit that the *local* side has no FK and must cascade "by hand" — but the *remote* schema still has a live `ON DELETE CASCADE` FK from `habit_check_ins.habit_id` to `habits.id`, dormant until GC. When a habit's tombstone crosses the 30-day GC window, `syncWithRemote()` hard-deletes the habit row remotely (`remoteHabitDataSource.purgeDeletedBefore`, called *before* the check-in purge on the next line) — and the FK cascade then hard-deletes **every** `habit_check_ins` row referencing that habit, tombstoned or not, bypassing that check-in's own soft-delete/GC lifecycle entirely. Under today's UI this is unreachable (a soft-deleted habit is filtered out of every screen that could write a new check-in against it), but it's a narrow, real multi-device race: device A soft-deletes a habit; device B, not yet synced, still shows it as active and logs a fresh check-in; when the habit's tombstone later ages out, B's genuinely-active check-in is cascade-deleted without ever passing through its own tombstone.
- **Fix A**: Drop the remote `ON DELETE CASCADE` FK (e.g. `ON DELETE NO ACTION`) so remote matches local's "no FK, app cascades explicitly" model, and re-order/guard the GC purge so check-ins for a habit are always purged at or before the habit itself.
  - Strength: Closes the gap architecturally — one canonical soft-delete-then-GC lifecycle for check-ins, local and remote alike, matching what the plan already says about the local side.
  - Tradeoff: Requires a new Postgres migration beyond this plan's scope, and removing the CASCADE without also guaranteeing check-ins are purged first would cause the habit purge itself to fail on the FK constraint — the purge ordering (currently habit-then-check-ins) would need to flip, which is a non-trivial change to reason through correctly.
  - Confidence: MED — the fix direction is right, but the exact ordering/constraint change needs its own careful design pass, not a quick edit.
  - Blind spot: Haven't verified whether any other code path could hit the same FK during normal (non-GC) operation.
- **Fix B ⭐ Recommended**: Document this as a known, currently-unreachable edge case (comment near the GC purge call and/or in the migration) and defer a real fix to the same future change this plan already deferred `syncMutex`/durability hardening to.
  - Strength: Zero schema risk, ships now; matches this plan's own established pattern of deferring adjacent durability/edge-case hardening to `architecture-hardening` rather than scope-creeping this change further.
  - Tradeoff: The narrow multi-device race stays open until that future change lands.
  - Confidence: HIGH — the actual exploitability today is genuinely narrow (requires a specific unsynced-multi-device window the current UI doesn't otherwise facilitate).
  - Blind spot: None significant — this is a documentation/deferral choice, not a technical unknown.
- **Decision**: FIXED via Fix B — comment added at `HabitRepositoryImpl.kt`'s GC purge call site

### F2 — `syncWithRemote()`'s local apply loop isn't transactional

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt:163,171`, `JournalRepositoryImpl.kt:96`
- **Detail**: The per-row `dao.upsert(...)` calls in each table's apply loop aren't wrapped in `transactionRunner.runInTransaction { }` (an abstraction this same class already uses for `deleteHabit`'s 2-write cascade). If an exception fires partway through a sync pass, some rows can end up applied locally while the overall `syncWithRemote()` call still returns `Result.failure`. This is not a regression — the pre-existing `syncWithRemote()` had the identical non-transactional `forEach { dao.upsert(...) }` shape before this plan — and it's self-healing on the next successful sync (upserts are idempotent).
- **Fix A**: Wrap each table's apply-loop in `transactionRunner.runInTransaction { }` for all-or-nothing local application per sync pass.
  - Strength: Consistent with the precedent already set by `deleteHabit` in the same class; removes the partial-state window entirely.
  - Tradeoff: `JournalRepositoryImpl` doesn't currently inject a `TransactionRunner` at all — adding this closes the gap but touches that constructor's signature.
  - Confidence: HIGH — the abstraction already exists and is proven in this exact repository.
  - Blind spot: Haven't measured whether wrapping many upserts in one transaction has a noticeable cost at this app's data scale (likely negligible).
- **Fix B ⭐ Recommended**: Leave as-is — this is the same class of sync-durability concern (alongside `syncMutex`/`tryLock`) that this plan's own "What We're NOT Doing" section already scoped out to the not-yet-planned `architecture-hardening` change, and it's not a regression this plan introduced.
  - Strength: Keeps this plan's diff scoped to what it was actually for; matches the explicit scope boundary already agreed during framing.
  - Tradeoff: The partial-apply window remains live until that future change lands.
  - Confidence: HIGH — directly traceable to an explicit, already-made scope decision, not a new judgment call.
  - Blind spot: None significant.
- **Decision**: SKIPPED — left as-is per Fix B

### F3 — Remote tombstone purge relies on implicit NULL semantics rather than an explicit guard

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: `RemoteJournalDataSourceImpl.kt:26-36`, `RemoteHabitDataSourceImpl.kt:26-36`, `RemoteHabitCheckInDataSourceImpl.kt:26-36`
- **Detail**: `lt("deleted_at", cutoff)` alone is safe — Postgres evaluates `NULL < cutoff` as `NULL`, which `WHERE` excludes, so active rows are never touched (verified correct). The local Room `purgeDeletedBefore` queries add an explicit `deletedAt IS NOT NULL AND ...` guard for the same logic; the remote queries rely on the implicit NULL comparison instead.
- **Fix**: Add an explicit "deleted_at is not null" filter alongside `lt("deleted_at", cutoff)` (or a one-line comment noting the NULL-exclusion is intentional) to match the local DAOs' self-documenting style.
- **Decision**: FIXED — clarifying comment added to all 3 `Remote*DataSourceImpl.kt` files

### F4 — Two minor plan-Contract deviations, both justified

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: Postgres migration `set_sync_metadata_function_search_path` (20260818082328); `app/src/main/java/pl/luczka/todaywas/domain/usecase/SaveHabitCheckInsUseCase.kt:11-14`
- **Detail**: (1) A second migration hardening the 3 trigger functions' `search_path` (a Supabase Advisor security fix) wasn't itemized under Phase 1's "Changes Required," though it's what made Phase 1's own "advisors report no new issues" success criterion pass. (2) `SaveHabitCheckInsUseCase`'s constructor takes a `Clock` beyond what the plan's literal Contract snippet listed (`HabitRepository` only) — needed to reproduce the original `onSaveClicked`'s edit-window check verbatim.
- **Fix**: None needed — both are correct, low-risk, and already necessary for stated plan goals. Noted for documentation completeness only.
- **Decision**: ACKNOWLEDGED — no fix needed
