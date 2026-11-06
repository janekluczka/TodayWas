# Sync Strategy Rework — Plan Brief

> Full plan: `context/changes/sync-strategy-rework/plan.md`
> Frame brief: `context/changes/sync-strategy-rework/frame.md`

## What & Why

TodayWas's sync model has no conflict-resolution metadata anywhere (no `updated_at`/version on any
synced table, client or server) and no deletion tombstones, so the periodic full-sync can only
blindly overwrite whichever side's `upsert` ran last — for edits and deletes alike, on every table.
This is a schema/sync-algorithm gap, not the write-ordering problem originally suspected (Google's
own offline-first guidance actually recommends the local-first pattern already in use). While
already restructuring the touched repositories, this plan also relocates three confirmed instances
of business logic sitting in ViewModels down into the domain layer, per official Android
architecture guidance.

## Starting Point

Local entities/DTOs/Postgres tables have only `createdAt`/`created_at`. `syncWithRemote()` pushes
every local row then blindly pulls every remote row, no recency comparison. User-facing delete
hard-deletes locally and best-effort pushes a real remote `DELETE` — a row deleted-but-unpushed
gets re-inserted by the next pull; a row deleted on another device is never pruned locally. Room
has no real `Migration` objects (destructive fallback only, DB at version 5).

## Desired End State

Every synced table has `updated_at`/`deleted_at` on both Room and Postgres. A genuinely older write
never clobbers a newer one; a delete on one device is correctly reflected everywhere instead of
being silently resurrected. Tombstones are purged once aged past a 30-day window. Three confirmed
ViewModel business-logic instances have moved into the domain layer.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Room migration strategy | Keep destructive fallback | Solo/MVP, pre-launch (no Play Store listing); accepted as a tradeoff against the "never lose local data" guardrail | Plan |
| Delete vs. concurrent edit | Delete always wins, enforced in the Postgres trigger *and* the client merge helper | Matches this app's existing framing of delete as deliberate/final (entry-delete precedent); pure timestamp LWW could resurrect a deliberately deleted entry | Plan |
| Tombstone GC | Time-based purge (30 days), opportunistic during `syncWithRemote()` | Simple, fits few-devices-per-user scale; a device-ack handshake would be over-engineered | Plan |
| Merge logic placement | One shared generic `SyncMerge` helper, used by all 3 tables | The gap is structural and identical across tables — one place to get subtle conflict logic right | Plan |
| Local delete representation | Soft-delete in Room (`deletedAt` column), filtered from all UI reads | Symmetric with the remote tombstone model; the tombstone row itself becomes the pending-push signal, fixing today's fire-and-forget delete-push durability gap for free | Plan |
| Timestamp authority | Postgres sets `updated_at`/`deleted_at` server-side via trigger | Removes cross-device clock skew as a source of merge bugs | Plan |
| `updated_at` bump condition | Only when business content or `deleted_at` actually changes | `syncWithRemote()` blindly re-pushes every local row every sync — an unconditional bump would make every row look "just edited," defeating LWW entirely | Plan (discovered during design) |
| Use-case cleanup scope | 3 confirmed instances only (check-in save routing, loggable-dates dedup, post-auth sync policy) | Scoped to flows this plan already touches, not a general audit — that's `architecture-hardening`'s job | Plan |
| Loggable-date range (Log Habit Check-ins) | Keep today+yesterday, dedupe only | Keeps this phase a pure refactor; widening backdated logging is a separate UX decision | Plan |
| `HabitDetailViewModel` save semantics | Preserve partial-success behavior | This phase relocates logic, it doesn't change product behavior | Plan |

## Scope

**In scope:**
- `updated_at`/`deleted_at` columns + triggers on `journal_entries`, `habits`, `habit_check_ins` (Postgres + Room)
- Soft-delete write paths, tombstone-aware `syncWithRemote()`, time-based GC purge
- A shared `SyncMerge` conflict-resolution helper with a full test matrix
- 3 targeted business-logic relocations (habit check-in save, loggable-dates rule, post-auth sync policy)

**Out of scope:**
- Real Room `Migration` objects (destructive fallback stays)
- Write-ordering changes (remote-first vs local-first)
- `syncMutex`/`tryLock` retry durability, WorkManager outbox — deferred to `architecture-hardening`
- CRDTs / field-level merge
- Expanding the "Log Habit Check-ins" date range
- App-wide business-logic audit

## Architecture / Approach

Schema changes first (Postgres via migration + trigger, then Room entities/DAOs — destructive
fallback), then an isolated, pure, unit-testable `SyncMerge` helper implementing tombstone-supremacy
+ last-write-wins, then both repositories' `syncWithRemote()` restructured from blind
push-then-pull into fetch→merge→push-winners→apply-winners→purge-tombstones. User-facing delete
becomes a local soft-delete pushed through the same background-upsert path adds/edits already use
(no separate delete-push method needed). Once the data layer is soft-delete-aware, three confirmed
ViewModel business-logic instances relocate into the domain layer.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Postgres schema | `updated_at`/`deleted_at` + triggers on all 3 tables | Trigger must be content-aware, not bump-on-every-touch |
| 2. Room schema & DAOs | Soft delete, tombstone filtering, GC queries, partial-index fix | `habit_check_ins` unique-index collision with its own tombstone |
| 3. SyncMerge helper | Shared LWW + tombstone-supremacy comparison, full test matrix | Getting tombstone supremacy right client-side, not just server-side |
| 4. Repository rewiring | Restructured `syncWithRemote()`, soft-delete write paths, habit→check-in cascade | Real user-facing behavior change — needs manual two-device verification |
| 5. Repository test coverage | Extended `Fake*`-based tests for soft-delete/merge | Low — mechanical extension of existing pattern |
| 6. Use-case cleanup | 3 relocations from ViewModel to domain layer | Scope creep if not held to the 3 confirmed instances |

**Prerequisites:** None beyond the existing Supabase project access already in use.
**Estimated effort:** ~4-6 sessions across 6 phases — Phases 1-4 are the substantial ones; 5-6 are lighter.

## Open Risks & Assumptions

- Destructive local migration means any currently-installed local-only user loses their data on
  this update — accepted, pre-launch, explicit user decision.
- Time-based tombstone GC has a narrow edge case: a device offline longer than the 30-day window
  could theoretically miss a delete signal on that one device — accepted as rare/low-stakes.
- No automated multi-device test harness exists; the two-device conflict scenario is verified
  manually, not by CI.

## Success Criteria (Summary)

- A delete on one device is never resurrected by an edit from another device, regardless of clock skew or sync timing.
- No data is lost for a device that stays online and syncs normally — only the explicit destructive-migration tradeoff on this one update.
- `SaveHabitCheckInsUseCase`, the loggable-dates rule, and the post-auth sync policy each have exactly one implementation, not two.
