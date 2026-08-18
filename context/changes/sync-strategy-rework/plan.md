# Sync Strategy Rework Implementation Plan

## Overview

TodayWas's sync model has no conflict-resolution metadata anywhere — no `updated_at`/version on
any synced table (client or server) and no deletion tombstones — so the periodic full sync
(`syncWithRemote()`) can only blindly overwrite whichever side's `upsert` ran last, for edits and
deletes alike, on every table (`journal_entries`, `habits`, `habit_check_ins`). This plan adds
timestamp-based last-write-wins conflict metadata and soft-delete tombstones to all three tables
(client + server), rewrites the merge algorithm to use them, and — while already restructuring the
touched repositories' write paths — relocates three confirmed instances of business logic
currently sitting in ViewModels down into the domain layer, per official Android architecture
guidance.

## Current State Analysis

- Local entities (`JournalEntryEntity`, `HabitEntity`, `HabitCheckInEntity`) and remote DTOs
  (`JournalEntryRemoteDto`, `HabitRemoteDto`, `HabitCheckInRemoteDto`) carry only
  `createdAt`/`created_at`. The live Postgres schema (confirmed via `mcp__supabase__list_tables`)
  has no `updated_at`, `deleted_at`, or version column on any of the three tables.
- `syncWithRemote()` in both `JournalRepositoryImpl` and `HabitRepositoryImpl` pushes every local
  row via `upsert`, then pulls every remote row via `upsert` — no recency comparison, no way to
  represent "this row was deleted" versus "never seen this row yet."
- User-facing delete (`deleteEntry`/`deleteHabit`/`deleteCheckIn`) hard-deletes locally and
  best-effort pushes a real remote `DELETE` in the background
  (`pushDeleteInBackground`/`pushHabitDeleteInBackground`/`pushCheckInDeleteInBackground`). A row
  deleted-but-unpushed gets re-inserted by the next pull; a row deleted on another device is never
  pruned locally and gets re-uploaded by the next push.
- Room's `DatabaseModule.kt` uses `.fallbackToDestructiveMigration(dropAllTables = true)` — there
  are no real `Migration` objects in this codebase (DB is at version 5). This plan adds columns via
  the same destructive-fallback mechanism (explicit user decision, see Key Discoveries) rather than
  writing real migrations.
- `EditWindow.isEditable` (24h edit window, FR-006) is keyed on `createdAt` only and lives in
  `domain/util/EditWindow.kt`, enforced by `UpdateJournalEntryUseCase`/`UpdateHabitCheckInUseCase`
  — orthogonal to the new `updatedAt` field; unaffected by this plan.
- Postgres RLS already has full CRUD policies on all three tables
  (`*_select_own`/`*_insert_own`/`*_update_own`/`*_delete_own`, the last added in
  `add_delete_rls_policies`, 2026-08-16) — all keyed on `user_id = auth.uid()`. No new policies are
  needed: soft-deletes are plain `UPDATE`s (already covered by `*_update_own`) and tombstone GC is
  a plain `DELETE` (already covered by `*_delete_own`).
- Existing repository tests (`JournalRepositoryImplTest`, `HabitRepositoryImplTest`) use hand-rolled
  `Fake*` DAOs/data sources, not a mocking framework — new tests follow this pattern.
- Confirmed via a targeted business-logic audit (scoped to the flows this plan already touches):
  `HabitDetailViewModel.onSaveClicked` (lines 202-246) does update-vs-add routing per pending
  check-in plus manual `Result` aggregation; `LogHabitCheckInsViewModel.kt:26-29` and
  `HabitDetailMapper.kt:16-21` each independently hardcode "today + yesterday" as the loggable-date
  rule; `AccountViewModel.proceedAfterAuthSuccess` (lines 214-225) and
  `OnboardingViewModel.proceedAfterAuthSuccess` (lines 299-313) are near-identical copy-pasted
  bodies deciding whether to show the data-sync review step or sync transparently.

## Desired End State

Every synced table has `updated_at`/`deleted_at` on both Room and Postgres. A device's own edits
and deletes always push successfully as before; the periodic `syncWithRemote()` now compares
recency and deletion state instead of blindly overwriting, so a genuinely older write never
clobbers a newer one, and a delete on one device is correctly reflected on every other device
instead of being silently resurrected. Tombstoned rows are purged (hard-deleted, both sides) once
they're older than the GC window. Three confirmed instances of misplaced business logic have moved
from ViewModels into the domain layer, verified against official Android architecture guidance.

Verification: unit tests cover the full conflict-resolution matrix on the shared merge helper;
existing repository tests pass with soft-delete-aware fakes; manual verification confirms a
two-device delete/edit race resolves per the "delete always wins" rule; `ktlintCheck` and
`testDebugUnitTest` pass.

### Key Discoveries:

- **Blind full-table re-push would defeat LWW if the trigger bumped `updated_at` unconditionally.**
  `syncWithRemote()` pushes every local row on every sync call, regardless of whether it changed.
  If the Postgres trigger set `updated_at = now()` on every touched row unconditionally, every row
  would look "just edited" on every sync, making recency comparison meaningless. The trigger must
  only bump `updated_at` when the row's actual business columns (or `deleted_at`) differ from the
  prior value — see Critical Implementation Details.
- **"Delete always wins" (per user decision) must be enforced in two places, not one**: server-side
  in the Postgres trigger (so no `UPDATE`, however it originates, can clear an already-set
  `deleted_at`), and client-side in the merge helper (so a local device's own stale, unsynced edit
  doesn't "win" against a remote tombstone just because its local `updatedAt` happens to be
  numerically newer than the tombstone's server timestamp). Pure `updatedAt` comparison alone is
  insufficient — see Critical Implementation Details.
- **The existing `Domain.toRemoteDto()` mapping pattern forces `updatedAt`/`deletedAt` onto domain
  models too**, even though the UI never needs them. The push path today is
  `entity.toDomain().toRemoteDto(userId)` — if the new fields aren't threaded through the domain
  model, they're silently dropped on every push. Adding them to `JournalEntry`/`Habit`/
  `HabitCheckIn` (never surfaced through any `ui/mapper/`) is less disruptive than re-shaping the
  established Entity→Domain→Dto pipeline.
- **The `habit_check_ins` unique `(habitId, date)` index collides with its own tombstone.**
  Soft-deleting a check-in leaves a row occupying that slot, so re-logging the same habit+date
  later would violate the unique constraint. SQLite supports partial unique indexes
  (`CREATE UNIQUE INDEX ... WHERE deletedAt IS NULL`) but Room's `@Index` annotation doesn't — the
  fix is a `RoomDatabase.Callback.onCreate()` raw-SQL index, compatible with the destructive
  fallback already in use (no versioned `Migration` needed).
- **A soft-deleted habit's check-ins aren't cascade-deleted locally or remotely anymore.** The
  existing remote `ON DELETE CASCADE` FK only fires on a real `DELETE`; a soft-delete is an
  `UPDATE`, which doesn't cascade. `deleteHabit` must explicitly soft-delete the habit's check-ins
  itself, both locally and via the same push path — mirroring the comment already in
  `pushHabitDeleteInBackground` about the local side lacking an FK.
- **`pushDeleteInBackground` becomes redundant.** Since a user-facing delete is now just a normal
  local write (soft-delete) followed by a normal push (upsert of the now-tombstoned entity), the
  existing `pushInBackground(entity)` path handles it — no separate delete-push method is needed.
  This is a net code reduction in Phase 4, not an addition.

## What We're NOT Doing

- Not writing real Room `Migration` objects — the destructive-fallback wipe-on-upgrade stays,
  by explicit user decision, accepting the tradeoff against the PRD's "local data never lost"
  guardrail for this pre-launch (no Play Store listing yet) app.
- Not changing per-operation write ordering (remote-first vs local-first) — Google's official
  offline-first guidance recommends local-first "Lazy Writes" for this data category, and the
  frame brief already confirmed this isn't the actual gap.
- Not touching `syncMutex`/`tryLock` retry/durability semantics, or building a WorkManager-based
  outbox — explicitly out of scope, deferred to the (not-yet-planned) `architecture-hardening`
  change.
- Not building CRDTs or field-level merge — row-level last-write-wins is the right level of
  sophistication for this app's single-user, few-devices-per-user scale.
- Not a device-ack handshake for tombstone GC — time-based purge only.
- Not auditing business logic placement app-wide. The Phase 6 cleanup is scoped strictly to the
  three confirmed instances that touch flows this plan already modifies (habit check-in save
  routing, loggable-date rule, post-auth sync policy) — not a general refactor pass.
- Not expanding "Log Habit Check-ins"'s selectable-date range beyond today+yesterday — explicit
  user decision to keep this phase a pure move-not-change refactor; widening backdated logging is a
  separate UX-scoping decision for another change.
- Not making `HabitDetailViewModel`'s save operation atomic — preserves the existing
  partial-success behavior (some pending check-ins can save while others fail) since this phase is
  a relocation, not a behavior change.

## Implementation Approach

Schema first (Postgres, then Room/DAOs), then the shared conflict-resolution algorithm as an
isolated, unit-testable helper, then rewire the two repositories to use it, then extend repository
tests, then — building on the now-soft-delete-aware DAOs — do the scoped use-case cleanup. Each
phase is independently compilable and testable; the repository rewiring phase is the one with real
user-facing behavior change (soft-delete instead of hard-delete, tombstone-aware sync) and gets the
most manual verification.

## Critical Implementation Details

**Postgres trigger: content-aware `updated_at`, monotonic `deleted_at`.** Each of the three tables
gets a `BEFORE INSERT OR UPDATE` trigger that (a) sets `updated_at := now()` only when a business
column or `deleted_at` actually changed relative to `OLD` — otherwise `updated_at := OLD.updated_at`
— and (b) enforces tombstone monotonicity: `IF OLD.deleted_at IS NOT NULL THEN NEW.deleted_at :=
OLD.deleted_at` (an already-tombstoned row can never be un-tombstoned by any later `UPDATE`,
regardless of what the client sends), `ELSIF NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
THEN NEW.deleted_at := now()` (the server clock is authoritative for when a delete actually lands,
not whatever timestamp the client's local clock attached). Representative shape (journal_entries;
habits and habit_check_ins follow identically with their own business columns):

```sql
CREATE OR REPLACE FUNCTION set_journal_entries_sync_metadata() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.updated_at := now();
    RETURN NEW;
  END IF;
  IF OLD.deleted_at IS NOT NULL THEN
    NEW.deleted_at := OLD.deleted_at;
  ELSIF NEW.deleted_at IS NOT NULL THEN
    NEW.deleted_at := now();
  END IF;
  IF NEW.date IS DISTINCT FROM OLD.date
     OR NEW.text IS DISTINCT FROM OLD.text
     OR NEW.deleted_at IS DISTINCT FROM OLD.deleted_at THEN
    NEW.updated_at := now();
  ELSE
    NEW.updated_at := OLD.updated_at;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Client-side merge must mirror tombstone supremacy, not just compare timestamps.** A device with a
stale, unsynced local edit can have a local `updatedAt` that's numerically newer than a remote
tombstone's server-stamped `updated_at` (the remote delete happened chronologically after the local
edit, but the local device hasn't round-tripped yet to find out). If the merge helper only compared
`updatedAt`, that stale local edit would incorrectly resurrect the row locally. The rule, applied
per id present on either side: only-local → local wins; only-remote → remote wins; both present and
remote is deleted → remote wins outright (tombstone supremacy, regardless of local `updatedAt`);
both present and local is deleted (remote isn't yet) → local wins (push the fresh tombstone); both
present and neither is deleted → whichever has the newer `updatedAt` wins.

**Local reads exclude tombstones; sync needs the untombstoned view too.** All UI-facing DAO reads
(`observeAll`, `getAll`, `getById`, `getByHabitAndDate`) filter `WHERE deletedAt IS NULL`. `syncWithRemote()`
needs the *full* local row set (including tombstones) to run the merge comparison — this requires a
separate DAO method (e.g. `getAllIncludingDeleted()`) distinct from the UI-facing `getAll()`.

## Phase 1: Postgres schema — conflict metadata and tombstones

### Overview

Add `updated_at`/`deleted_at` to all three tables, with triggers enforcing content-aware recency
and tombstone monotonicity as described above. Update remote DTOs to carry the new fields.

### Changes Required:

#### 1. New migration: sync conflict metadata

**File**: new Supabase migration, e.g. `add_sync_conflict_metadata` (via `mcp__supabase__apply_migration`)

**Intent**: Add `updated_at timestamptz not null default now()` and `deleted_at timestamptz null`
to `journal_entries`, `habits`, `habit_check_ins`; backfill existing rows' `updated_at` from their
`created_at`; add the three `BEFORE INSERT OR UPDATE` triggers described in Critical Implementation
Details, one per table, each diffing that table's own business columns
(`journal_entries`: `date`, `text`; `habits`: `name`, `description`, `type`, `scale_min`,
`scale_max`; `habit_check_ins`: `value`) plus `deleted_at`.

**Contract**: Three `ALTER TABLE ... ADD COLUMN` pairs, three trigger functions, three `CREATE
TRIGGER ... BEFORE INSERT OR UPDATE ... EXECUTE FUNCTION ...` statements. No RLS policy changes
needed (existing `*_update_own`/`*_delete_own` policies already cover the new write shapes).

#### 2. Remote DTOs

**File**: `app/src/main/java/pl/luczka/todaywas/data/remote/dto/JournalEntryRemoteDto.kt`,
`HabitRemoteDto.kt`, `HabitCheckInRemoteDto.kt`

**Intent**: Carry the two new columns through deserialization.

**Contract**: Add `@SerialName("updated_at") val updatedAt: String` and
`@SerialName("deleted_at") val deletedAt: String?` to each DTO, matching the existing
`createdAt`/`created_at` string-ISO8601 pattern.

### Success Criteria:

#### Automated Verification:

- Migration applies cleanly: `mcp__supabase__apply_migration` succeeds, `mcp__supabase__list_migrations` shows it
- `mcp__supabase__get_advisors` (security/performance) reports no new issues
- Project builds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- `mcp__supabase__execute_sql` confirms `updated_at`/`deleted_at` exist on all three tables with the trigger attached
- A manual `UPDATE` on a test row with unchanged business columns leaves `updated_at` unchanged
- A manual `UPDATE` setting `deleted_at` on an already-tombstoned row leaves the original `deleted_at` untouched

---

## Phase 2: Room schema and DAO updates

### Overview

Mirror the two new fields locally, soft-delete instead of hard-delete on the DAOs' user-facing
delete methods, filter tombstones out of all UI-facing reads, add GC purge queries, and fix the
`habit_check_ins` partial-uniqueness gap.

### Changes Required:

#### 1. Local entities

**File**: `JournalEntryEntity.kt`, `HabitEntity.kt`, `HabitCheckInEntity.kt`

**Intent**: Add the two new columns.

**Contract**: `val updatedAt: Long`, `val deletedAt: Long? = null` on each entity.

#### 2. Database version bump and partial index

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/database/TodayWasDatabase.kt`,
`app/src/main/java/pl/luczka/todaywas/di/DatabaseModule.kt`

**Intent**: Bump the schema version (destructive fallback handles the rest); remove the
non-partial `@Index(["habitId","date"], unique=true)` from `HabitCheckInEntity` (Room can't express
"unique among active rows only") and replace it with a raw partial index created via a
`RoomDatabase.Callback`.

**Contract**: `version = 6` on `@Database`. The callback is extracted into a standalone
`fun todayWasDatabaseCallbacks(): RoomDatabase.Callback` in a new
`data/local/database/TodayWasDatabaseCallbacks.kt`, rather than an inline anonymous object in
`DatabaseModule.kt` — so the Phase 2 test below (which builds its own `Room.databaseBuilder(...)`
directly, same pattern as the existing `TodayWasDatabaseTest`/`HabitCheckInDaoTest`, bypassing Hilt)
can attach the exact same callback instead of duplicating the raw SQL and risking prod/test drift.
`DatabaseModule.kt`'s builder chain calls `.addCallback(todayWasDatabaseCallbacks())`. The callback's
`onCreate(db: SupportSQLiteDatabase)` executes `db.execSQL("CREATE UNIQUE INDEX
index_habit_check_ins_habitId_date ON habit_check_ins(habitId, date) WHERE deletedAt IS NULL")` —
fires once per DB (re)creation, compatible with the existing destructive-fallback flow.

#### 3. DAOs — soft delete, tombstone filtering, GC

**File**: `JournalEntryDao.kt`, `HabitDao.kt`, `HabitCheckInDao.kt`

**Intent**: All UI-facing reads exclude tombstones; user-facing delete becomes an `UPDATE` setting
`deletedAt`; a new sync-only read returns every row including tombstones; a new purge query
hard-deletes aged-out tombstones.

**Contract**: `observeAll`/`getAll`/`getById`/`getByHabitAndDate` gain `WHERE deletedAt IS NULL`.
New `suspend fun getAllIncludingDeleted(): List<Entity>` (no filter) per DAO, for sync use only.
`deleteById` is replaced by `suspend fun softDeleteById(id: String, deletedAt: Long)` (`UPDATE ...
SET deletedAt = :deletedAt WHERE id = :id`); `HabitCheckInDao.deleteByHabitId` similarly becomes
`softDeleteByHabitId(habitId: String, deletedAt: Long)`. New `suspend fun
purgeDeletedBefore(cutoff: Long)` per DAO (`DELETE FROM x WHERE deletedAt IS NOT NULL AND deletedAt
< :cutoff`) — a true hard delete, used only by the GC step in Phase 4. `clearAll()` is unchanged
(stays a true hard wipe — sign-out is unrelated to sync tombstones).

#### 4. Entity and remote mappers

**File**: `JournalEntryEntityMapper.kt`, `HabitEntityMapper.kt`, `HabitCheckInEntityMapper.kt`,
`JournalEntryRemoteMapper.kt`, `HabitRemoteMapper.kt`, `HabitCheckInRemoteMapper.kt`, and the
three domain models (`JournalEntry.kt`, `Habit.kt`, `HabitCheckIn.kt`)

**Intent**: Thread `updatedAt`/`deletedAt` through the existing Entity↔Domain↔Dto mapping pipeline
so pushes carry the new fields (see Key Discoveries — domain models gain these two fields purely as
data-layer plumbing; they are never referenced by any `ui/mapper/` or exposed through a `UiState`).

**Contract**: `updatedAt: Instant` and `deletedAt: Instant?` added to `JournalEntry`, `Habit`,
`HabitCheckIn`; corresponding `Instant`↔`Long` (entity) and `Instant`↔`String` (DTO) conversions
added to each existing `toDomain()`/`toEntity()`/`toRemoteDto()` function, following the exact
pattern already used for `createdAt`.

#### 5. Partial index regression test

**File**: `app/src/test/java/pl/luczka/todaywas/data/local/dao/HabitCheckInDaoTest.kt`

**Intent**: Pin the partial-index behavior with an automated test rather than relying on manual
click-through, per user request — a future change could otherwise silently regress this. Two
cases are needed together, not one: a test that only checks "revival works" would still pass even
if the partial index were missing entirely (since removing the old blanket unique index without
successfully adding the partial one would also let revival "succeed," just for the wrong reason —
because nothing is enforcing uniqueness at all). A companion positive-control case confirms the
constraint still holds among active rows.

**Contract**: Same `Room.databaseBuilder(...)` + `RobolectricTestRunner` pattern as the existing
tests in this file, with `.addCallback(todayWasDatabaseCallbacks())` added to the builder chain so
the partial index actually gets created. Two new `@Test` cases:
`` `should reject a second active check-in for the same habit and date` `` (insert one active
row, attempt a second active insert for the same `(habitId, date)`, assert it throws/fails) and
`` `should allow a fresh active check-in after the previous one for the same habit and date was soft-deleted` ``
(insert one row, soft-delete it via `softDeleteById`, insert a fresh active row for the same
`(habitId, date)`, assert it succeeds and the active read returns exactly the new row).

### Success Criteria:

#### Automated Verification:

- Project builds: `./gradlew.bat assembleDebug`
- Lint passes: `./gradlew.bat ktlintCheck`
- Existing unit tests still compile and pass: `./gradlew.bat testDebugUnitTest`
- New `HabitCheckInDaoTest` partial-index cases pass: `./gradlew.bat testDebugUnitTest --tests "*HabitCheckInDaoTest*"`

#### Manual Verification:

- Fresh install (destructive fallback triggers, expected): app launches without crash, DB recreated at version 6
- Logging a check-in for a habit+date, deleting it, then logging it again for the same habit+date succeeds in the real app (sanity check beyond the Robolectric test)

---

## Phase 3: Shared SyncMerge helper

### Overview

A generic, pure, unit-testable conflict-resolution function used identically by all three sync call
sites, implementing the tombstone-supremacy + LWW rule from Critical Implementation Details.

### Changes Required:

#### 1. SyncMerge helper

**File**: `app/src/main/java/pl/luczka/todaywas/data/util/SyncMerge.kt` (new)

**Intent**: Given a local and a remote row set (reduced to just id/updatedAt/isDeleted for the
comparison), decide which local rows need pushing and which remote rows need applying locally.

**Contract**:

```kotlin
data class SyncMeta(val id: String, val updatedAt: Instant, val isDeleted: Boolean)
data class SyncMergeDecision(val pushIds: Set<String>, val applyIds: Set<String>)

fun mergeForSync(local: List<SyncMeta>, remote: List<SyncMeta>): SyncMergeDecision

val TOMBSTONE_GC_WINDOW: Duration = Duration.ofDays(30)
```

Per-id resolution exactly as specified in Critical Implementation Details (only-local → push;
only-remote → apply; both + remote deleted → apply; both + only local deleted → push; both +
neither deleted → newer `updatedAt` wins). Callers convert their entity/DTO lists to `SyncMeta` via
small `toSyncMeta()` extension functions colocated with each existing entity/remote mapper, then
use the returned id sets to filter their original (fully-populated) lists for the actual push/apply
step.

#### 2. Unit tests

**File**: `app/src/test/java/pl/luczka/todaywas/data/util/SyncMergeTest.kt` (new)

**Intent**: Cover the full conflict matrix at the cheapest possible layer, per the approved test
scope.

**Contract**: One `@Test` per scenario, backtick-named per this codebase's convention
(`` `should push local-only row when remote has never seen it` ``, etc.): local-only new row;
remote-only new row; both edited, remote newer wins; both edited, local newer wins; local deleted +
remote has a newer non-deleted edit (tombstone still wins); remote deleted + local has a newer
non-deleted edit (tombstone still wins); local deleted, remote never saw it (push the tombstone).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (all `SyncMergeTest` scenarios green)
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- None — this phase is a pure, fully unit-testable function with no I/O.

---

## Phase 4: Repository rewiring

### Overview

Restructure `syncWithRemote()` in both repositories to fetch→merge→push-winners→apply-winners
(replacing today's blind push-then-pull), switch user-facing deletes to soft-delete + normal
upsert-push, replace the remote hard-`delete()` with a bulk tombstone-purge call, and handle the
habit→check-in cascade and check-in-slot revival explicitly.

### Changes Required:

#### 1. Remote data source interfaces

**File**: `RemoteJournalDataSource.kt`/`Impl.kt`, `RemoteHabitDataSource.kt`/`Impl.kt`,
`RemoteHabitCheckInDataSource.kt`/`Impl.kt`

**Intent**: User-facing delete no longer needs a dedicated remote call (it's now a normal `upsert`
of a tombstoned row); the interface's per-id `delete(id)` is replaced by a bulk, cutoff-based purge
used only by GC.

**Contract**: Remove `delete(id: String)`. Add `suspend fun purgeDeletedBefore(userId: String,
cutoff: String): Result<Unit>`, implemented as `supabase.postgrest.from(TABLE).delete { filter {
eq("user_id", userId); lt("deleted_at", cutoff) } }`.

#### 2. `JournalRepositoryImpl` / `HabitRepositoryImpl`

**File**: `data/repository/JournalRepositoryImpl.kt`, `data/repository/HabitRepositoryImpl.kt`

**Intent**: `deleteEntry`/`deleteHabit`/`deleteCheckIn` soft-delete locally (via the new
`softDeleteById`/`softDeleteByHabitId` DAO methods) and push through the existing
`pushInBackground`/`pushHabitInBackground`/`pushCheckInsInBackground` methods (the dedicated
`pushDeleteInBackground` family is deleted — see Key Discoveries). `deleteHabit` additionally
soft-deletes and pushes the habit's own check-ins (reading them via `getAllIncludingDeleted()`
filtered by `habitId`, or a habit-scoped equivalent, before soft-deleting). `syncWithRemote()` is
restructured to: fetch local (`getAllIncludingDeleted()`) and remote in parallel or sequence, call
`mergeForSync` (via each table's `toSyncMeta()`), push only the rows in `pushIds`, apply only the
rows in `applyIds` locally via `dao.upsert`, then purge tombstones older than `TOMBSTONE_GC_WINDOW`
both locally (`purgeDeletedBefore`) and remotely (`purgeDeletedBefore`). `addCheckIns` needs no
special revival handling: the Phase 2 partial index (`WHERE deletedAt IS NULL`) only enforces
uniqueness among active rows, so inserting a fresh row (`deletedAt = null`) for a `(habitId, date)`
that only has a tombstoned occupant never collides — the stale tombstone is left in place for GC to
sweep up normally, alongside the fresh row it no longer blocks.

**Contract**: `syncWithRemote()`'s new shape, in order: fetch local+remote → `mergeForSync` →
push `pushIds` → apply `applyIds` → purge both sides. No behavior change to `pushInBackground`
itself — it already just upserts whatever entity it's given, soft-deleted or not.

### Success Criteria:

#### Automated Verification:

- Project builds: `./gradlew.bat assembleDebug`
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- Deleting a journal entry removes it from the main screen immediately, and it does not reappear after a manual sync
- Deleting a habit removes both the habit and all its check-ins from the main screen
- Simulated two-device scenario (two emulators/accounts or manual DB edits): device A deletes an entry, device B has a stale unsynced edit to the same entry — after both sync, the entry stays deleted everywhere
- Re-logging a habit check-in for a previously-deleted habit+date succeeds

**Implementation Note**: Pause here for manual confirmation of the two-device scenario before proceeding to Phase 5.

---

## Phase 5: Repository test coverage

### Overview

Extend the existing `Fake*`-based repository tests to cover soft-delete and merge-driven sync
behavior, as thin integration coverage complementing Phase 3's pure-function matrix.

### Changes Required:

#### 1. Repository tests

**File**: `JournalRepositoryImplTest.kt`, `HabitRepositoryImplTest.kt`

**Intent**: Verify the repositories correctly wire `SyncMerge`'s decisions through to their DAOs
and remote data sources, and that delete now soft-deletes rather than hard-deletes.

**Contract**: Extend the existing `Fake*` DAO/data-source classes with `deletedAt`/`updatedAt`
support (soft-delete methods, `getAllIncludingDeleted`, `purgeDeletedBefore`). New test cases:
`` `should soft-delete locally and push the tombstone when deleteEntry succeeds` ``,
`` `should not resurrect a remotely-deleted row during syncWithRemote` ``,
`` `should purge tombstones older than the GC window during syncWithRemote` ``.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- None — covered by automated tests.

---

## Phase 6: Use-case layer cleanup

### Overview

Relocate the three confirmed instances of business logic from ViewModels into the domain layer,
per official Android architecture guidance ("a domain layer... avoids code duplication... avoids
large classes by allowing you to split responsibilities" —
[developer.android.com/topic/architecture/domain-layer](https://developer.android.com/topic/architecture/domain-layer)).
Scoped strictly to the three confirmed instances; not a general audit.

### Changes Required:

#### 1. `SaveHabitCheckInsUseCase`

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/SaveHabitCheckInsUseCase.kt` (new)

**Intent**: Move `HabitDetailViewModel.onSaveClicked`'s per-date update-vs-add routing and `Result`
aggregation into a use case, depending directly on `HabitRepository` (never on
`LogHabitCheckInsUseCase`/`UpdateHabitCheckInUseCase` — this codebase's established rule is that
one use case never depends on another). Preserves the existing partial-success behavior exactly
(per user decision): each pending value is written independently; the aggregate result reports
failure (with `EditWindowExpiredException` surfaced if any failure was that) while successful
writes are kept, unchanged from today.

**Contract**: `class SaveHabitCheckInsUseCase @Inject constructor(private val repository:
HabitRepository) { suspend operator fun invoke(habitId: String, existing: List<HabitCheckIn>,
pending: Map<LocalDate, Int>): Result<Unit> }` — same routing/aggregation logic currently inline in
`onSaveClicked` (lines 210-244), relocated verbatim. `HabitDetailViewModel` is updated to call this
one use case instead of looping over `updateHabitCheckIn`/`logHabitCheckIns` itself.

#### 2. Loggable-dates rule

**File**: `app/src/main/java/pl/luczka/todaywas/domain/util/EditWindow.kt`

**Intent**: Deduplicate the "today + yesterday" rule currently hardcoded independently in
`LogHabitCheckInsViewModel.kt:26-29` and `HabitDetailMapper.kt:16-21`, per the user's explicit
decision to deduplicate without changing the allowed range.

**Contract**: `fun freshLoggableDates(now: Instant): List<LocalDate>` added to `EditWindow`,
returning `[today, yesterday]` relative to `now` (device default zone, matching the existing
`LocalDate.ofInstant(now, ZoneId.systemDefault())` pattern already used in `HabitDetailMapper`).
`LogHabitCheckInsViewModel` calls this directly for `selectableDates`; `HabitDetailMapper.
toHabitDetailRows` unions it with `existingByDate.keys` instead of hardcoding `today`/`yesterday`
itself.

#### 3. Shared post-auth sync/review policy

**File**: `app/src/main/java/pl/luczka/todaywas/domain/util/LocalDataSyncPolicy.kt` (new)

**Intent**: Deduplicate `AccountViewModel.proceedAfterAuthSuccess` and
`OnboardingViewModel.proceedAfterAuthSuccess` (near-identical bodies deciding whether to show the
data-sync review step or sync transparently). Only the *decision* is truly duplicated — the
surrounding state transitions (`postSyncAction`/`pendingAllSetReason`, which `UiState` gets
updated, `finishPostSyncAction`/`reachAllSet`) are inherently per-screen and stay in each
ViewModel. A pure policy object (not a use case) is the right shape here, same reasoning as
`EditWindow.freshLoggableDates`: it depends on no repository, just plain inputs already available
to both ViewModels via their existing `GetLocalDataSummaryUseCase`/`SyncLocalDataUseCase` calls
(which are unaffected by this change) — this avoids re-deriving those use cases' repository calls
inline, which would conflict with the established "use case depends on repository only, never on
another use case" rule while also duplicating their logic.

**Contract**: `fun shouldReviewBeforeSync(hasSyncedLocalData: Boolean, summary: LocalDataSummary):
Boolean = !hasSyncedLocalData && !summary.isEmpty`. Both ViewModels' `proceedAfterAuthSuccess` keep
their own `getLocalDataSummary()`/`syncLocalData()` calls exactly as today, but branch on this
function's result instead of the inline `if (!_uiState.value.hasSyncedLocalData && !summary.isEmpty)`
condition duplicated in both files today.

### Success Criteria:

#### Automated Verification:

- Project builds: `./gradlew.bat assembleDebug`
- Lint passes: `./gradlew.bat ktlintCheck`
- Unit tests pass: `./gradlew.bat testDebugUnitTest`

#### Manual Verification:

- Habit detail edit sheet: saving a mix of new and existing check-in values still behaves identically to before (partial success on a simulated edit-window failure)
- "Log Habit Check-ins" still offers exactly today + yesterday
- Both the sign-up-during-onboarding and sign-up-from-account-screen flows still show the data-sync review step on first sign-in with existing local data, and sync transparently on subsequent sign-ins

---

## Testing Strategy

### Unit Tests:

- `habit_check_ins` partial-index behavior (Phase 2) — Robolectric-backed, real SQLite: active-row uniqueness still enforced, revival after soft-delete succeeds
- Full conflict-resolution matrix on `SyncMerge` (Phase 3) — the cheapest, most exhaustive coverage layer
- Repository-level tests confirming correct wiring of merge decisions to DAOs/remote calls (Phase 5)
- `EditWindow.freshLoggableDates` — today/yesterday boundary cases (Phase 6)
- `SaveHabitCheckInsUseCase` — partial-success aggregation, existing-vs-new routing (Phase 6)

### Integration Tests:

- Two-device delete/edit race, exercised manually against the real Supabase project (no automated
  multi-device harness exists in this codebase)

### Manual Testing Steps:

1. Fresh install after the schema bump — confirm no crash, DB recreates at version 6
2. Create a habit, log a check-in, delete it, re-log the same habit+date — confirm success
3. Delete a journal entry, confirm it's gone from the main screen and stays gone after a manual sync trigger
4. Delete a habit, confirm both it and its check-ins disappear
5. Two-device (or two-account) simulated conflict: delete on one side, stale edit on the other, confirm delete wins after both sync
6. Sign up during onboarding with existing local data — confirm the data-sync review step still appears; sign in again later — confirm no review step, silent sync

## Performance Considerations

`syncWithRemote()` now does one extra local query (`getAllIncludingDeleted()` instead of the
already-existing `getAll()`) and up to two extra remote calls (the GC purge) per sync — negligible
given this app's per-user data volumes (personal journaling/habit data, not bulk records). The GC
purge is opportunistic and best-effort; a failed purge simply retries on the next successful sync.

## Migration Notes

Destructive local migration (explicit user decision) — installed users lose local-only data on
this update. No remote data migration needed beyond the new nullable/defaulted columns; existing
rows backfill `updated_at` from `created_at` and `deleted_at` stays `null` (nothing was ever
deleted before this change existed).

## References

- Frame brief: `context/changes/sync-strategy-rework/frame.md`
- `app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt`
- `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt`
- `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailViewModel.kt:202-246`
- `app/src/main/java/pl/luczka/todaywas/ui/habit/logcheckin/LogHabitCheckInsViewModel.kt:26-29`
- `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailMapper.kt:16-21`
- `app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt:214-225`
- `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingViewModel.kt:299-313`
- Official guidance: [Build an offline-first app](https://developer.android.com/topic/architecture/data-layer/offline-first), [Domain layer](https://developer.android.com/topic/architecture/domain-layer)
- Lesson: `context/foundation/lessons.md` — "A Result-returning wrapper should not be re-wrapped", use-case-depends-on-repository-only convention

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Postgres schema — conflict metadata and tombstones

#### Automated

- [x] 1.1 Migration applies cleanly — 90cc189
- [x] 1.2 Security/performance advisors report no new issues — 90cc189
- [x] 1.3 Project builds — 90cc189

#### Manual

- [x] 1.4 Columns and triggers confirmed on all three tables — 90cc189
- [x] 1.5 Unchanged-content update leaves updated_at unchanged — 90cc189
- [x] 1.6 Already-tombstoned row's deleted_at can't be cleared by a later update — 90cc189

### Phase 2: Room schema and DAO updates

#### Automated

- [x] 2.1 Project builds
- [x] 2.2 Lint passes
- [x] 2.3 Existing unit tests compile and pass
- [x] 2.4 New HabitCheckInDaoTest partial-index cases pass

#### Manual

- [x] 2.5 Fresh install works after schema bump
- [x] 2.6 Re-logging a check-in after deleting it for the same habit+date succeeds in the real app

### Phase 3: Shared SyncMerge helper

#### Automated

- [ ] 3.1 SyncMergeTest scenarios pass
- [ ] 3.2 Lint passes

### Phase 4: Repository rewiring

#### Automated

- [ ] 4.1 Project builds
- [ ] 4.2 Lint passes

#### Manual

- [ ] 4.3 Deleted journal entry stays deleted after manual sync
- [ ] 4.4 Deleted habit removes its check-ins too
- [ ] 4.5 Two-device delete/edit race resolves with delete winning
- [ ] 4.6 Re-logging a previously-deleted habit+date succeeds

### Phase 5: Repository test coverage

#### Automated

- [ ] 5.1 Unit tests pass
- [ ] 5.2 Lint passes

### Phase 6: Use-case layer cleanup

#### Automated

- [ ] 6.1 Project builds
- [ ] 6.2 Lint passes
- [ ] 6.3 Unit tests pass

#### Manual

- [ ] 6.4 Habit detail save behavior unchanged (partial-success preserved)
- [ ] 6.5 Log Habit Check-ins still offers today + yesterday only
- [ ] 6.6 Data-sync review step still appears/skips correctly in both onboarding and account flows
