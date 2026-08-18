# Frame Brief: Sync strategy rework — conflict-resolution metadata, not write ordering

> Framing step before /10x-plan. This document captures what is *actually*
> at issue, separated from what was initially assumed.

## Reported Observation

For signed-in users, every write (journal entry, habit, check-in — add/update/delete) writes to
Room first; the local write's success is what's returned to the UI, independent of whether the
background push to Supabase ever succeeds (`JournalRepositoryImpl.kt`/`HabitRepositoryImpl.kt`).
The periodic full sync (`syncWithRemote()`, triggered on every signed-in Main-screen load) merges
local and remote by blind `upsert` in both directions, with nothing to represent a deletion or to
say which side's edit is actually newer. `entry-delete`'s review specifically flagged delete
resurrection, but the investigation below (broadened per user request — "don't fixate on delete")
shows the same root gap affects updates too. Not observed/reproduced in practice — proactive
design concern.

## Initial Framing (preserved)

- **User's stated cause or approach**: local-first-then-background-push isn't the standard
  pattern; switch to remote-API-first-then-refetch-local, at least for signed-in writes — because
  "we shouldn't hope that local data is uploaded there," the API should be the source of truth.
- **User's proposed direction**: rework the write path so signed-in writes call the API first.
- **Pre-dispatch narrowing** (user's answers): (1) leading concern is API-as-source-of-truth, not
  the delete symptom specifically; (2) explicitly separate from `architecture-hardening`'s
  `syncMutex`/`tryLock` retry item — do not fold together; (3) resurrection has not actually
  happened, raised proactively.
- **Mid-frame broadening** (after the first pass came back delete-focused): user asked not to
  fixate on delete, to analyze the sync implementation as a whole, and to check what standards
  exist for this. Also asked whether an official Google library covers this.

## Dimension Map

1. **Per-write local-first pattern itself** — is writing locally before confirming remote
   actually the wrong pattern? ← initial framing
2. **Conflict-resolution metadata** — can the sync merge tell which side's data is actually newer,
   for *any* field, on *any* mutation type?
3. **Deletion representation** — special case of #2: can the merge tell "deleted" apart from
   "never seen this row yet"?
4. **Retry/durability of the background push** (`syncMutex`/`tryLock`, fire-and-forget) —
   explicitly descoped by the user as `architecture-hardening` territory; noted where official
   guidance is directly relevant, not investigated as its own hypothesis.

## Hypothesis Investigation

| Hypothesis | Evidence | Verdict |
| --- | --- | --- |
| **1. Local-first write pattern is architecturally wrong** | Google's official Android architecture guidance (`developer.android.com/topic/architecture/data-layer/offline-first`) explicitly recommends the *opposite* conclusion for critical data: "Lazy Writes" (write local first, queue network sync) is the **recommended** pattern — "it's essential that any tasks the user adds offline are stored locally to avoid the risk of data loss." Google's own guidance also states the local DB "should be the exclusive source of any data that higher layers of the app read." Now in Android (Google's reference app) uses exactly this shape. | NONE (as stated) — the pattern itself matches official guidance for this data category; not the actual problem |
| **2. No conflict-resolution metadata (any mutation type)** | Checked all three synced tables end-to-end: local entities (`JournalEntryEntity.kt`, `HabitEntity.kt`, `HabitCheckInEntity.kt`) have only `createdAt`; remote DTOs (`JournalEntryRemoteDto.kt`, `HabitRemoteDto.kt`, `HabitCheckInRemoteDto.kt`) have only `created_at`; the **live Postgres schema** (queried directly via `mcp__supabase__list_tables`) confirms `journal_entries`/`habits`/`habit_check_ins` have no `updated_at`, version, or etag column at all — server-side, not just a client mapping gap. `syncWithRemote()` (`JournalRepositoryImpl.kt:74-84`, `HabitRepositoryImpl.kt:132-147`) pushes local via `upsert` then pulls remote via `upsert` — with no timestamp to compare, this isn't last-write-wins-by-recency, it's whichever device's sync call happens to run last silently overwriting the other, regardless of which edit is actually newer. Matches the industry-standard prescription directly: "Updated_at + server wins is simple... version numbers/ETags provide safer optimistic concurrency. When multiple devices can edit the same record, versioning becomes necessary" (web research, see References). | STRONG — root gap, confirmed client- and server-side, applies to every mutation type |
| **3. No deletion tombstone** | `RemoteJournalDataSourceImpl.kt:26-27` has a real `.delete()`, called only from the one-shot best-effort `pushDeleteInBackground`, never from the sync/merge path — no `deleted_at` column exists on any table (same schema query as above). A row deleted locally-but-unpushed gets re-inserted by the next pull; a row deleted on another device is never pruned locally and gets re-uploaded by the next push. Matches the standard pattern directly: "Tombstones are used for deletions... Use a tombstone instead of removing the record right away" (web research). Independently corroborated in the first framing pass by a hypothesis-blind agent. | STRONG — same underlying gap as #2 (no state-transition metadata), applied to deletes specifically |
| **4. Retry/durability (`syncMutex`/`tryLock`)** | Out of scope per user's explicit decision (Q2). Noted for whoever plans `architecture-hardening`: Google's official pattern for this exact piece is a **WorkManager-driven outbox** (constrained to `NetworkType.CONNECTED`, `Result.retry()` for automatic exponential backoff) — not a `Mutex.tryLock()` fire-and-forget coroutine launch. Worth using the official terminology/mechanism when that change is eventually planned, rather than patching the current ad hoc approach. | OUT OF SCOPE — by user's explicit direction; flagged for the related change only |

## Narrowing Signals

- User's original framing ("API as source of truth," Q1) correctly identified that something
  about trusting local state is unsafe — but the actual missing piece isn't *where* writes are
  authoritative, it's that there's no data to determine *which* write is newer at all.
- Official Google guidance directly contradicts the specific mechanism proposed (remote-first
  write ordering) for this data category, while directly confirming the underlying worry (sync
  correctness) is legitimate and needs a real fix — just a different one.
- The gap generalizes cleanly across all three tables and both mutation types (edit, delete) —
  it's structural (missing schema/DTO field), not specific to any one repository method.

## Cross-System Convention

**Official Android guidance** (`developer.android.com/topic/architecture/data-layer/offline-first`):
local database is the canonical source of truth for reads; for critical data, "Lazy Writes" (local
first, queued network sync) is recommended over online-only writes; conflict resolution is
timestamp-metadata-based last-write-wins ("discards any data older than its current state...
accepting those newer"); sync orchestration and retry use WorkManager with connectivity
constraints and exponential backoff. Google's **Now in Android** sample app is the reference
implementation of this exact shape.

**Broader industry pattern** (web research, see References): the same three-part prescription
recurs everywhere for this class of app — (a) `updated_at`/version/etag per row for conflict
detection, escalating to CRDTs only when concurrent edits to the *same* field are common and
must merge rather than pick a winner; (b) tombstone rows for deletions, propagated like any other
field change; (c) an outbox/idempotent-retry mechanism so a failed push isn't simply lost. This
app currently has none of the three. Given TodayWas's actual write pattern (a single user editing
their own data from at most a couple of personal devices, never truly concurrent edits to the same
field), plain timestamp-based last-write-wins is the right level of sophistication — CRDTs would
be over-engineering for this scale.

## Reframed (or Confirmed) Problem Statement

> **The actual problem to plan around is**: TodayWas's sync model has no conflict-resolution
> metadata anywhere (no `updated_at`/version on any synced table, client or server) and no
> deletion tombstones, so the periodic full-sync can only blindly overwrite whichever side's
> `upsert` ran last — for edits and deletes alike, on every table. This is a schema/sync-algorithm
> gap, not a write-ordering problem.

The originally proposed fix (call the API before writing locally, at least for signed-in writes)
would only get one device's own writes to remote sooner — it does nothing to let the sync merge
compare recency across devices, and directly contradicts Google's own official guidance
recommending local-first "Lazy Writes" for exactly this category of critical, offline-capable
data. The user's underlying concern (don't just "hope" local data reaches remote correctly) is
valid and confirmed — the fix is conflict-resolution metadata + tombstones, not relocating where
the API call happens in a single write's flow.

## Confidence

**HIGH** — confirmed at every layer (client entities, DTOs, and the live Postgres schema queried
directly), cross-checked against both Google's official Android architecture guidance and general
industry practice, and the gap is structural/schema-level rather than a single-file bug — hard to
be wrong about.

## What Changes for /10x-plan

Scope the plan around adding conflict-resolution metadata and deletion tombstones to all three
synced tables (`journal_entries`, `habits`, `habit_check_ins`, client + server), and changing
`syncWithRemote()`'s merge to use them (timestamp-based last-write-wins; tombstone rows excluded
from normal reads but respected during merge, then eventually hard-deleted once both sides have
seen them — a standard tombstone-GC step worth designing explicitly). Per-operation write ordering
is not the fix and should not be part of this plan. The retry/durability piece (`syncMutex`/
`tryLock` → WorkManager-style outbox) stays out of this change's scope per the user's explicit
decision (Q2) — flag the WorkManager-pattern note above for whoever eventually plans
`architecture-hardening` (no `context/changes/architecture-hardening/` folder exists yet, per
prior session memory — check before assuming it does).

## References

- Source files:
  - `app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt:42-120`
  - `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt:51-211`
  - `app/src/main/java/pl/luczka/todaywas/data/remote/dto/JournalEntryRemoteDto.kt`,
    `HabitRemoteDto.kt`, `HabitCheckInRemoteDto.kt`
  - `app/src/main/java/pl/luczka/todaywas/data/local/entity/JournalEntryEntity.kt`,
    `HabitEntity.kt`, `HabitCheckInEntity.kt`
  - Live Postgres schema: `public.journal_entries`/`habits`/`habit_check_ins` (queried via
    `mcp__supabase__list_tables`, verbose) — no `updated_at`/`deleted_at`/version column on any
  - `app/src/main/java/pl/luczka/todaywas/data/remote/api/RemoteJournalDataSourceImpl.kt:26-27`
- Related prior discussion:
  - `context/changes/entry-delete/plan.md:11-24`, `plan-brief.md:31`, `reviews/impl-review.md:64-71`
  - `context/foundation/roadmap.md:258-259` (PRD Non-Goal: no real-time multi-device conflict resolution)
- External research:
  - [Build an offline-first app — Android Developers (official)](https://developer.android.com/topic/architecture/data-layer/offline-first)
  - [Offline-First | Outbox, Idempotency & Conflict Resolution](https://www.educba.com/offline-first/)
  - [Offline-First Mobile App Architecture: Syncing, Caching, and Conflict Resolution](https://dev.to/odunayo_dada/offline-first-mobile-app-architecture-syncing-caching-and-conflict-resolution-518n)
  - [A Design Guide for Building Offline First Apps — Hasura](https://hasura.io/blog/design-guide-to-offline-first-apps)
