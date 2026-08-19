# Architecture Hardening Implementation Plan

## Overview

Closes five technical-debt items surfaced across `sync-strategy-rework`, `mvp-certification-fixes`,
and `entry-delete` but never independently planned: (1) `HabitRepositoryImpl`/`JournalRepositoryImpl`
mix plain local CRUD with sync orchestration in one class, and their background push has no real
retry; (2) `MainViewModel`/`HabitDetailViewModel` (and four more files) call `domain/util`
calculators/policies directly instead of through a use case; (3) `AuthRepositoryImpl`/
`AiAssistRepositoryImpl`/`HabitMapper` have zero test coverage; (4) the remote
`habit_check_ins.habit_id` FK still has a live `ON DELETE CASCADE`, inconsistent with the
soft-delete/tombstone model; (5) `syncWithRemote()`'s local-apply loop isn't transactional.

`context/changes/architecture-hardening/research.md` verified all five against the current
codebase; `context/changes/architecture-hardening/frame.md` then re-examined the *decisions*
inherited from those prior changes and confirmed two (the WorkManager fix, and not reopening
`arch-cleanup`'s no-new-abstraction call) while reframing one (item 2's scope was too narrow) and
adding a new one (the repository split item 1 should build on). This plan implements all five
against that settled framing.

## Current State Analysis

- `HabitRepositoryImpl.kt`/`JournalRepositoryImpl.kt` each mix Room CRUD, a `Mutex`-guarded
  fire-and-forget background push (`pushHabitInBackground`/`pushCheckInsInBackground`/
  `pushInBackground`, no retry), and full-reconciliation `syncWithRemote()` in one class.
  `TransactionRunner` is used for `deleteHabit`'s cross-table soft-delete but not for
  `syncWithRemote()`'s local-apply loop.
- WorkManager is entirely absent from the project — no gradle dependency, no code.
- 8 call sites across 6 files call `domain/util/EditWindow.kt`/`LocalDataSyncPolicy.kt` or the two
  contribution calculators directly, bypassing `domain/usecase/`.
- `AuthRepositoryImpl`/`AiAssistRepositoryImpl` depend on the concrete `SupabaseClient` (not an
  interface, unlike every other repository) and have zero tests. `HabitMapper` has zero tests and
  calls `LocalDate.now()` directly with no injection point.
- The remote `habit_check_ins.habit_id` FK has a live `ON DELETE CASCADE`
  (`confdeltype = 'c'`), confirmed via direct schema inspection. No local `.sql` migration files
  exist anywhere in the repo — all four existing migrations were applied directly against the
  linked Supabase project.

## Desired End State

- `HabitRepositoryImpl`/`JournalRepositoryImpl` delegate local persistence to new
  `LocalHabitDataSource`/`LocalJournalDataSource` classes and schedule sync via a `SyncScheduler`
  abstraction backed by WorkManager (`NetworkType.CONNECTED`, exponential backoff, `Result.retry()`)
  instead of a bare coroutine launch. `syncWithRemote()`'s local-apply step is transactional.
- `HabitRepository`/`JournalRepository` extend a shared `Syncable` interface exposing
  `syncWithRemote()`/`clearLocal()`, separating that contract from plain CRUD.
- The remote `habit_check_ins_habit_id_fkey` FK no longer cascades.
- All 8 `domain/util`/calculator call sites go through a use case; none of the six touched files
  import `domain/util/EditWindow.kt`, `LocalDataSyncPolicy.kt`, `JournalContributionCalculator.kt`,
  or `HabitContributionCalculator.kt` directly anymore.
- `AuthRepositoryImpl`, `AiAssistRepositoryImpl`, and `HabitMapper` each have test coverage
  following this project's established conventions (hand-rolled fakes / `MockEngine`, no mocking
  library, `` `should [outcome] when [scenario]` `` naming, AAA comments).

Verification: `./gradlew.bat testDebugUnitTest`, `./gradlew.bat ktlintCheck`, and
`./gradlew.bat assembleDebug` all pass; the app builds and runs with sync, habit detail, journal
detail, account, and onboarding flows all manually verified per-phase below.

### Key Discoveries:

- `HabitContributionCalculator.compute(emptyList(), window, now)` and
  `JournalContributionCalculator.compute(emptyList(), window, now)` are provably always
  `ContributionGrid(window, emptyMap())` for an empty input — `HabitDetailViewModel`'s synchronous
  `stateIn` initial-value block never needs to call the calculator at all, closing the
  sync-vs-reactive tension for that call site without special-casing the new use case
  (`HabitDetailViewModel.kt:125`, `HabitContributionCalculator.kt`).
  `HabitDetailViewModel`'s initial `availableWindows(null, now)` call reduces the same way
  `MainViewModel`'s initial state already does it (`MainViewModel.kt:60-65` hardcodes
  `listOf(ContributionWindowUiState.RollingTwelveMonths)` without calling `availableWindows` at
  all) — the same hardcode applies to `HabitDetailViewModel`'s initial value.
- `HabitCheckInDao.upsertAll` (`HabitCheckInDao.kt:45-48`) is already a `@Transaction` Room default
  method wrapping a `forEach`; `JournalEntryDao` has no equivalent — adding one there (mirroring the
  existing precedent) closes `JournalRepositoryImpl`'s apply-loop gap without introducing
  `TransactionRunner` into `JournalRepositoryImpl` at all. `HabitRepositoryImpl`'s gap is
  cross-table (habits + check-ins), so it still needs `TransactionRunner`, same as `deleteHabit`.
  Combined with `applyRemoteSnapshot`, both are folded into Phase 1's extraction — F2 is designed
  in correctly rather than fixed after the fact (`HabitRepositoryImpl.kt:151-190`,
  `JournalRepositoryImpl.kt:83-103`).
- Every existing use case is Hilt-auto-constructed via `@Inject constructor(...)` — there is no
  `UseCaseModule.kt` and no `@Provides`/`@Binds` for any use case anywhere in `di/`. New use cases
  need no DI module changes as long as their dependencies (repositories, `Clock`) are already bound
  (`ClockModule.kt`, `RepositoryModule.kt` already cover everything Phase 4's use cases need).
- `JournalEntryDetailViewModel`'s `clock: Clock` constructor param is used *only* for the two
  `EditWindow.isEditable` calls being replaced in Phase 4 — it can be removed entirely once those
  move behind `IsEditableUseCase` (`JournalEntryDetailViewModel.kt`, both `init` and `onRefine`).
- `AccountViewModel.proceedAfterAuthSuccess` and `OnboardingViewModel.proceedAfterAuthSuccess` are
  structurally identical (same comment, same `getLocalDataSummary()` → policy-check → branch
  shape) — the new `ShouldReviewLocalDataBeforeSyncUseCase` needs no repository dependency at all
  (it takes the already-fetched `summary` and `hasSyncedLocalData` as parameters), avoiding any
  tension with the "never depend on another use case" rule.
- `install(Auth)`'s default session manager touches platform settings storage
  (`SettingsSessionManager`), which is unsafe in a plain JVM test — supabase-kt ships
  `install(Auth) { minimalConfig() }` specifically for this (swaps in `MemorySessionManager`).
  Production also installs `Postgrest` (`SupabaseModule.kt`) — the test client doesn't need it
  since neither `AuthRepositoryImpl` nor `AiAssistRepositoryImpl` touches it.
- `toHabitUiStates()` has exactly one production call site (`MainViewModel.kt:107`), and
  `Habit.toUiState(todayCheckIn)` itself never calls `LocalDate.now()` — only the board-level
  `toHabitUiStates()` wrapper does, so only that function needs the new `today` parameter.

## What We're NOT Doing

- Not switching signed-in writes from local-first-then-push to remote-first-then-refetch —
  explicitly investigated and rejected in `sync-strategy-rework`'s frame pass; not reopened.
- Not introducing `RemoteAuthDataSource`/`RemoteAiAssistDataSource` — `arch-cleanup`'s decision to
  keep `AuthRepositoryImpl`/`AiAssistRepositoryImpl` depending on the concrete `SupabaseClient`
  directly is confirmed compatible with real test coverage (`MockEngine` + `minimalConfig()`); not
  reopened.
- Not building a checked-in Supabase migration-file infrastructure — the FK fix is applied directly
  against the linked project via MCP, consistent with how the four existing migrations were
  applied; establishing `supabase/migrations/` as a pattern (and backfilling the existing four) is
  a separate infrastructure project, not in scope here.
- Not adding an outbox table or a periodic drain worker — the chosen WorkManager shape enqueues a
  full `syncWithRemote()` resync per write; WorkManager's own persisted job queue supplies
  durability across process death without a bespoke pending-writes table.
- Not touching `HabitMapper`'s status-derivation logic itself — research confirmed it's currently
  accurate; only test coverage (and the `today` parameter needed to write deterministic tests) is
  in scope.
- Not adding `androidx.work:work-testing` — `SyncWorker` itself is covered by manual verification,
  not a JVM unit test; the repository-level scheduling behavior it triggers is unit-tested via a
  `FakeSyncScheduler`.

## Implementation Approach

Phases are ordered by dependency, not by item number: Phase 1 (repository decomposition) is
foundational and touched by everything else in items 1/4/5; Phase 2 (FK fix) is a small, isolated
continuation of the same code Phase 1 just restructured; Phase 3 (WorkManager) builds the new
`SyncScheduler` abstraction Phase 1 already made room for; Phases 4 and 5 (domain/util use cases,
test coverage) are independent of 1-3 and of each other, and come last.

## Phase 1: Repository decomposition (local/remote split + Syncable interface)

### Overview

Extracts plain local CRUD out of `HabitRepositoryImpl`/`JournalRepositoryImpl` into new
`LocalHabitDataSource`/`LocalJournalDataSource` classes (mirroring the existing
`RemoteHabitDataSource`/`RemoteJournalDataSource` pattern), and splits `HabitRepository`/
`JournalRepository` via a shared `Syncable` interface. Builds `syncWithRemote()`'s local-apply step
transactionally from the start, closing finding F2 as part of the extraction rather than as a
follow-up.

### Changes Required:

#### 1. New local data source abstraction

**Files**: `app/src/main/java/pl/luczka/todaywas/data/local/api/LocalHabitDataSource.kt` (new,
interface), `LocalHabitDataSourceImpl.kt` (new), `LocalJournalDataSource.kt` (new, interface),
`LocalJournalDataSourceImpl.kt` (new) — a new `data/local/api/` package, symmetric with
`data/remote/api/`.

**Intent**: Own every DAO-touching operation `HabitRepositoryImpl`/`JournalRepositoryImpl`
currently perform directly, including the `safeDbCall` retry wrapping and ID/timestamp stamping
each write already does. `LocalHabitDataSourceImpl` wraps both `HabitDao` and `HabitCheckInDao`
(kept together, not split, because `deleteHabit`'s cross-table transaction ties them to one atomic
unit — the same reason `HabitRepositoryImpl` treats them as a pair today). `LocalJournalDataSource`
wraps `JournalEntryDao` alone.

**Contract**: `LocalHabitDataSource` exposes: `observeHabits(): Flow<List<HabitEntity>>`,
`observeCheckIns(): Flow<List<HabitCheckInEntity>>`, `insertHabit(entity): Result<Unit>`,
`insertCheckIns(entities): Result<Unit>`, `updateCheckIn(habitId, date, value, updatedAt):
Result<Unit>` (does the existing lookup-by-habitId-and-date internally, matching today's
`updateCheckIn`'s not-found → `Result.failure` behavior), `deleteHabitAndCheckIns(habitId,
deletedAt): Result<Unit>` (the existing `transactionRunner.runInTransaction { softDeleteByHabitId;
softDeleteById }` block, moved here verbatim), `deleteCheckIn(habitId, date, deletedAt):
Result<Unit>` (does the existing lookup-then-soft-delete internally), `getAllHabitsIncludingDeleted():
List<HabitEntity>`, `getAllCheckInsIncludingDeleted(): List<HabitCheckInEntity>`,
`applyRemoteSnapshot(habitsToApply: List<HabitEntity>, checkInsToApply: List<HabitCheckInEntity>)`
(new — wraps both `habitDao.upsert` per-row and `habitCheckInDao.upsertAll` in one
`transactionRunner.runInTransaction { }` block, closing F2 for the habit side by tying both tables'
apply step to the same atomic unit `deleteHabit` already uses), `purgeDeletedBefore(cutoff: Long)`
(both tables), `clearAll(): Result<Unit>` (both tables).

Since there's no more per-row remote push, the local write methods no longer need to return the
tombstoned/created entity — callers only need success/failure to decide whether to trigger a sync
(see Phase 3).

`LocalJournalDataSource` mirrors the same shape for the single `journal_entries` table:
`observeEntries()`, `getEntry(id)`, `insertEntry(entity): Result<Unit>`, `updateEntry(id, text,
updatedAt): Result<Unit>`, `deleteEntry(id, deletedAt): Result<Unit>`, `getAllIncludingDeleted():
List<JournalEntryEntity>`, `applyRemoteSnapshot(toApply: List<JournalEntryEntity>)` (calls a new
`JournalEntryDao.upsertAll(entities)` — see next item — no `TransactionRunner` needed here since
it's a single table, and Room's own `@Transaction` default method already gives atomicity, mirroring
`HabitCheckInDao.upsertAll`'s existing shape exactly), `purgeDeletedBefore(cutoff: Long)`,
`clearAll(): Result<Unit>`.

#### 2. `JournalEntryDao` gets an `upsertAll`

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/dao/JournalEntryDao.kt`

**Intent**: Give the single-table bulk-apply step the same Room-level atomicity
`HabitCheckInDao.upsertAll` already has, rather than introducing `TransactionRunner` into the
journal path for a single-table operation.

**Contract**: Add `@Transaction suspend fun upsertAll(entities: List<JournalEntryEntity>) {
entities.forEach { upsert(it) } }`, matching `HabitCheckInDao.kt:45-48`'s `upsertOne`/`upsertAll`
shape at the structural level (a `@Transaction` default method wrapping a per-row primitive) —
`JournalEntryDao`'s existing single-row primitive is already named `upsert` (no "One" suffix, and
already used by `syncWithRemote()`'s apply loop), so it's reused as-is rather than renamed to
match `HabitCheckInDao`'s naming; no new `@Insert` needed either way).

#### 3. `Syncable` interface + repository interface split

**Files**: `app/src/main/java/pl/luczka/todaywas/domain/repository/Syncable.kt` (new),
`domain/repository/HabitRepository.kt`, `domain/repository/JournalRepository.kt`

**Intent**: Make the CRUD-vs-sync boundary explicit at the type level, per the frame's confirmed
decision. No consumer-visible change — everything that injects `HabitRepository`/`JournalRepository`
today keeps working unchanged, since both interfaces still expose `syncWithRemote()`/`clearLocal()`
via the new supertype.

**Contract**: `Syncable` declares `suspend fun syncWithRemote(): Result<Unit>` and `suspend fun
clearLocal(): Result<Unit>`. `HabitRepository`/`JournalRepository` change from plain interfaces to
`interface HabitRepository : Syncable { /* CRUD members only, syncWithRemote/clearLocal removed */
}` (same for `JournalRepository`).

#### 4. `HabitRepositoryImpl`/`JournalRepositoryImpl` become the composing layer

**Files**: `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt`,
`JournalRepositoryImpl.kt`

**Intent**: Replace direct DAO/`TransactionRunner`/`safeDbCall` usage with delegation to the new
`Local*DataSource`. Every write method keeps its existing `push*InBackground`-style call unchanged
for now — Phase 3 is what replaces those with `SyncScheduler.scheduleSync()`, not this phase.
`SyncScheduler` doesn't exist yet at this point (its gradle dependency, Hilt wiring, and interface
are all introduced in Phase 3), and this phase's own Success Criteria (`assembleDebug` plus manual
verification that writes still reach remote) only hold if the existing push path keeps working
through Phase 1 — so `@ApplicationScope syncScope: CoroutineScope` stays in the constructor
unchanged here too. `syncWithRemote()` keeps its existing `syncMutex.withLock { remoteCall { ... }
}` shape and `mergeForSync` logic, but reads/writes local state through the new
`local.getAllXIncludingDeleted()` / `local.applyRemoteSnapshot(...)` / `local.purgeDeletedBefore(...)`
calls instead of DAOs directly.

**Contract**: `HabitRepositoryImpl @Inject constructor(private val local: LocalHabitDataSource,
private val remoteHabitDataSource: RemoteHabitDataSource, private val
remoteHabitCheckInDataSource: RemoteHabitCheckInDataSource, private val authRepository:
AuthRepository, @ApplicationScope private val syncScope: CoroutineScope)` — `habitDao`,
`habitCheckInDao`, and `transactionRunner` are no longer direct constructor params; `syncScope`
stays (Phase 3 removes it once `push*InBackground` is replaced with `SyncScheduler`).
`JournalRepositoryImpl` mirrors the same shape with `LocalJournalDataSource`. The `syncMutex`
comment updates to reflect its narrowed purpose: guarding only against two concurrent
`syncWithRemote()` calls racing each other, since there's no more per-row background push to race
against.

#### 5. DI wiring

**File**: `app/src/main/java/pl/luczka/todaywas/di/RepositoryModule.kt`

**Intent**: Bind the two new interfaces to their impls, following the existing `@Binds` pattern.

**Contract**: Add `@Binds abstract fun bindLocalHabitDataSource(impl: LocalHabitDataSourceImpl):
LocalHabitDataSource` and the equivalent for `LocalJournalDataSource`.

#### 6. Test updates

**Files**: `app/src/test/java/pl/luczka/todaywas/data/repository/HabitRepositoryImplTest.kt`,
`JournalRepositoryImplTest.kt`, plus new `LocalHabitDataSourceImplTest.kt`,
`LocalJournalDataSourceImplTest.kt`

**Intent**: Relocate the existing retry-behavior tests (`` `should succeed after one retry when
createHabit's first write fails` ``, etc. — currently in `HabitRepositoryImplTest.kt` against
`FakeHabitDao`/`FakeHabitCheckInDao`) down to the new `LocalHabitDataSourceImplTest`, since that's
where the retry logic now lives. `HabitRepositoryImplTest`/`JournalRepositoryImplTest` get new
private `FakeLocalHabitDataSource`/`FakeLocalJournalDataSource` classes (implementing the new
interfaces directly, following this project's hand-rolled-fake convention) and keep only the tests
that exercise sync/merge/GC behavior (`` `should push all local habits and check-ins then pull
remote-only rows when syncWithRemote is called` ``, tombstone-supremacy, GC-purge tests) plus new
tests verifying `applyRemoteSnapshot` is called with the right merge decision. `FakeHabitDao`/
`FakeHabitCheckInDao`/`FakeTransactionRunner` classes move (or get duplicated in simplified form)
into the new `Local*DataSourceImplTest` files.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Creating/editing/deleting a habit, check-in, and journal entry while signed in still reaches
  remote (verify via Supabase dashboard or `mcp__supabase__execute_sql` row check)
- Deleting a habit still soft-deletes its check-ins in the same local transaction (kill the app
  mid-operation is not practically testable manually, but confirm the delete completes atomically
  under normal use)
- `syncWithRemote()` (triggered via the existing account/onboarding sync-confirm flow) still
  correctly merges local and remote state with no data loss

---

## Phase 2: Remote FK-cascade fix

### Overview

Drops the dormant `ON DELETE CASCADE` on `habit_check_ins.habit_id`, closing finding F1. Small and
isolated — no local code changes beyond removing the now-stale documentation comment.

### Changes Required:

#### 1. Remote schema migration

**Target**: linked Supabase project, applied via `mcp__supabase__apply_migration` (matching how the
four existing migrations were applied — no local `.sql` file is added, per this plan's "What We're
NOT Doing").

**Intent**: Remove the cascade so purging a habit's tombstone during GC no longer hard-deletes
check-in rows that haven't independently earned their own tombstone/GC eligibility.

**Contract**: `ALTER TABLE habit_check_ins DROP CONSTRAINT habit_check_ins_habit_id_fkey;` followed
by `ALTER TABLE habit_check_ins ADD CONSTRAINT habit_check_ins_habit_id_fkey FOREIGN KEY (habit_id)
REFERENCES habits(id) ON DELETE NO ACTION;` — re-adds the FK for referential integrity (habit_id
must still reference a real habit row) without the cascade. Apply via a single
`mcp__supabase__apply_migration` call with a descriptive name (e.g.
`drop_habit_check_ins_cascade`), then confirm with `mcp__supabase__list_migrations` and a
`pg_constraint` query (`confdeltype` should read `'a'` for NO ACTION, not `'c'`).

#### 2. Remove the stale documentation comment

**File**: wherever Phase 1 relocated the GC purge call (`LocalHabitDataSourceImpl.kt`'s
`purgeDeletedBefore`, or `HabitRepositoryImpl.kt`'s `syncWithRemote()` if the purge trigger stays
there)

**Intent**: The "Known limitation... deferred to architecture-hardening" comment
(`HabitRepositoryImpl.kt:174-182` pre-Phase-1) documented a bug that no longer exists — remove it
rather than leaving stale documentation.

**Contract**: Delete the comment block; no replacement needed since the fix is now just "the FK
doesn't cascade," not a workaround requiring explanation in application code.

### Success Criteria:

#### Automated Verification:

- Unit tests still pass: `./gradlew.bat testDebugUnitTest` (no local behavior change expected)

#### Manual Verification:

- `mcp__supabase__execute_sql` confirms `habit_check_ins_habit_id_fkey`'s `confdeltype` is no
  longer `'c'`
- Deleting a habit whose tombstone has aged past the 30-day GC window (simulate via direct SQL
  update of `deleted_at` if needed) purges only that habit's own row, and any check-ins with their
  own independent tombstone lifecycle are unaffected by the habit's purge

---

## Phase 3: WorkManager-driven sync outbox

### Overview

Replaces the `Mutex.tryLock()` fire-and-forget background push with a WorkManager-enqueued full
`syncWithRemote()` resync, gaining WorkManager's connectivity-gated retry and durability across
process death.

### Critical Implementation Details

**Dependency versions**: `androidx.work:work-runtime:2.11.2` (not `work-runtime-ktx` — as of this
version, `CoroutineWorker` and other Kotlin APIs live in the main `work-runtime` artifact;
`work-runtime-ktx` is now an empty compatibility shim) and `androidx.hilt:hilt-work:1.2.0`, both
confirmed current-stable via direct lookup during planning (verify again before implementing if
this phase lands significantly later — versions move).

**Two separate KSP processors needed**: `com.google.dagger:hilt-compiler` (already present, handles
`@HiltAndroidApp`/`@HiltViewModel`) and `androidx.hilt:hilt-compiler` (new, handles `@HiltWorker`'s
generated assisted factory) are different artifacts from different groups — both need a `ksp(...)`
line in `app/build.gradle.kts`.

**Manifest**: providing a custom `Configuration.Provider` requires disabling WorkManager's default
auto-initialization, or `WorkManager.getInstance(context)` throws at first use. Add to
`AndroidManifest.xml`:
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

### Changes Required:

#### 1. Dependencies

**Files**: `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Intent**: Add WorkManager + Hilt-Work.

**Contract**: New version entries `androidxWork = "2.11.2"`, `androidxHiltWork = "1.2.0"`; new
libraries `androidx-work-runtime = { group = "androidx.work", name = "work-runtime", version.ref =
"androidxWork" }`, `androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref
= "androidxHiltWork" }`, `androidx-hilt-compiler = { group = "androidx.hilt", name =
"hilt-compiler", version.ref = "androidxHiltWork" }`. In `app/build.gradle.kts`:
`implementation(libs.androidx.work.runtime)`, `implementation(libs.androidx.hilt.work)`,
`ksp(libs.androidx.hilt.compiler)` alongside the existing `ksp(libs.hilt.compiler)`.

#### 2. Hilt + WorkManager wiring

**Files**: `app/src/main/java/pl/luczka/todaywas/TodayWasApplication.kt`,
`app/src/main/java/pl/luczka/todaywas/di/WorkManagerModule.kt` (new),
`app/src/main/AndroidManifest.xml`

**Intent**: Let `@HiltWorker`-annotated Workers receive constructor injection.

**Contract**: `TodayWasApplication` implements `Configuration.Provider`, injects
`HiltWorkerFactory`, overrides the `workManagerConfiguration` property to build a `Configuration`
with `.setWorkerFactory(hiltWorkerFactory)`. `WorkManagerModule` provides a `@Singleton
WorkManager` via `WorkManager.getInstance(context)`. Manifest change per Critical Implementation
Details above.

#### 3. `SyncScheduler` abstraction

**Files**: `app/src/main/java/pl/luczka/todaywas/data/util/SyncScheduler.kt` (new, interface),
`data/util/WorkManagerSyncScheduler.kt` (new)

**Intent**: Wrap `WorkManager` behind a project-owned interface, per this project's established
no-concrete-framework-class-injection convention (same reasoning as `TransactionRunner` wrapping
`RoomDatabase`).

**Contract**: `interface SyncScheduler { fun scheduleSync() }`. `WorkManagerSyncScheduler @Inject
constructor(private val workManager: WorkManager) : SyncScheduler` enqueues a `OneTimeWorkRequest`
for `SyncWorker` via `workManager.enqueueUniqueWork("sync-local-data",
ExistingWorkPolicy.KEEP, request)` — `KEEP` rather than `REPLACE`: since `SyncWorker` re-reads DB
state at execution time rather than snapshotting at enqueue time, a redundant enqueue while one is
already pending/running adds nothing, and `KEEP` avoids cancelling a request that's mid-flight on a
slow network call. Constraints: `NetworkType.CONNECTED`. Backoff:
`BackoffPolicy.EXPONENTIAL` with WorkManager's minimum backoff.

#### 4. `SyncWorker`

**File**: `app/src/main/java/pl/luczka/todaywas/data/worker/SyncWorker.kt` (new)

**Intent**: The enqueued unit of work — delegates entirely to the existing `SyncLocalDataUseCase`
(already calls `journalRepository.syncWithRemote()` then `habitRepository.syncWithRemote()`), so no
new sync-orchestration logic is written here.

**Contract**: `@HiltWorker class SyncWorker @AssistedInject constructor(@Assisted context: Context,
@Assisted params: WorkerParameters, private val syncLocalData: SyncLocalDataUseCase) :
CoroutineWorker(context, params)`. `override suspend fun doWork(): Result = if
(syncLocalData().isSuccess) Result.success() else Result.retry()`.

#### 5. Repositories schedule instead of pushing

**Files**: `data/repository/HabitRepositoryImpl.kt`, `JournalRepositoryImpl.kt`

**Intent**: Every write method that currently calls `pushHabitInBackground`/
`pushCheckInsInBackground`/`pushInBackground` calls `syncScheduler.scheduleSync()` instead, gated
the same way (only when `authRepository.currentUserId() != null`). Remove `pushHabitInBackground`,
`pushCheckInsInBackground`, `pushInBackground`, and the `@ApplicationScope syncScope:
CoroutineScope` constructor param (no longer used once these methods are gone — confirm no other
consumer of `@ApplicationScope CoroutineScope` exists before removing `CoroutineScopeModule`
itself; leave the module in place if anything else still depends on it).

**Contract**: `HabitRepositoryImpl`/`JournalRepositoryImpl` gain a `private val syncScheduler:
SyncScheduler` constructor param (completing Phase 1's placeholder). Each write method's existing
`if (result.isSuccess) push*InBackground(...)` line becomes `if (result.isSuccess &&
authRepository.currentUserId() != null) syncScheduler.scheduleSync()`.

#### 6. Test updates

**Files**: `HabitRepositoryImplTest.kt`, `JournalRepositoryImplTest.kt`

**Intent**: Replace the old push-verification tests (`` `should push only the tombstoned habit
when signed in...` ``, asserting on `remoteHabits.upsertCallCount`) with tests asserting on a new
`FakeSyncScheduler`'s `scheduleSyncCallCount` — verifying the repository schedules a sync exactly
when expected (on successful write while signed in; not at all while signed out; not on a failed
write).

**Contract**: `private class FakeSyncScheduler : SyncScheduler { var scheduleSyncCallCount = 0
private set; override fun scheduleSync() { scheduleSyncCallCount++ } }`, wired into the
`repository(...)` test builder alongside the existing fakes.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- App builds and launches without a WorkManager initialization crash (confirms the manifest +
  `Configuration.Provider` wiring is correct)
- Creating a habit/journal entry/check-in while signed in and online triggers a sync shortly after
  (confirm via Logcat — WorkManager logs worker execution — or a Supabase row check)
- Creating an entry while signed in and offline (airplane mode), then reconnecting, results in the
  entry reaching remote without needing to reopen the app (the actual behavior WorkManager adds
  over the old fire-and-forget push)
- Force-stopping the app immediately after a signed-in write, then relaunching with network
  available, still results in that write reaching remote (durability across process death)

---

## Phase 4: `domain/util` direct calls → use cases

### Overview

Wraps all 8 call sites across 6 files. Three small use cases with minimal/no repository dependency
(`EditWindow.isEditable`, `EditWindow.freshLoggableDates`, `LocalDataSyncPolicy.shouldReviewBeforeSync`),
two `Flow`-returning use cases wrapping the contribution calculators for `MainViewModel`/
`HabitDetailViewModel`.

### Changes Required:

#### 1. Three small use cases

**Files**: `domain/usecase/IsEditableUseCase.kt` (new), `domain/usecase/GetFreshLoggableDatesUseCase.kt`
(new), `domain/usecase/ShouldReviewLocalDataBeforeSyncUseCase.kt` (new)

**Intent**: Thin wrappers giving each `domain/util` function a use-case entry point, matching the
project's stated convention. Neither `EditWindow` wrapper needs a repository — only `Clock`, which
every existing `EditWindow`-wrapping use case (`UpdateJournalEntryUseCase`,
`UpdateHabitCheckInUseCase`) already injects the same way. `ShouldReviewLocalDataBeforeSyncUseCase`
needs no dependency at all — it takes the already-fetched summary as a parameter, so it never needs
to depend on `GetLocalDataSummaryUseCase` (which stays a separate use case, called first by the
ViewModel as it is today).

**Contract**: `IsEditableUseCase @Inject constructor(private val clock: Clock) { operator fun
invoke(createdAt: Instant): Boolean = EditWindow.isEditable(createdAt, clock.instant()) }`.
`GetFreshLoggableDatesUseCase @Inject constructor(private val clock: Clock) { operator fun
invoke(): List<LocalDate> = EditWindow.freshLoggableDates(clock.instant()) }`.
`ShouldReviewLocalDataBeforeSyncUseCase @Inject constructor() { operator fun
invoke(hasSyncedLocalData: Boolean, summary: LocalDataSummary): Boolean =
LocalDataSyncPolicy.shouldReviewBeforeSync(hasSyncedLocalData, summary) }`.

#### 2. Shared `ContributionSummary` domain type

**File**: `domain/model/ContributionSummary.kt` (new)

**Intent**: A domain-level bundle for grid + available windows, so the two new observe-use-cases
return domain models (not UI types) per this project's mapping-layer convention — the ViewModels
map to UI-facing types afterward, same as they already do for every other observed domain model.
Named `ContributionSummary`, not `ContributionData` — `HabitDetailViewModel.kt:84` already
declares a private UI-facing class named `ContributionData`; reusing that name for the new domain
type would collide when both are in scope in the same file (Phase 4 item 4 edits that file).

**Contract**: `data class ContributionSummary(val grid: ContributionGrid, val availableWindows:
List<ContributionWindow>)`.

#### 3. Two Flow-returning use cases

**Files**: `domain/usecase/ObserveJournalContributionUseCase.kt` (new),
`domain/usecase/ObserveHabitContributionUseCase.kt` (new)

**Intent**: Take over `MainViewModel`'s `journalContributionData` flow and `HabitDetailViewModel`'s
`contributionData` flow respectively — the `combine(...).distinctUntilChanged().map { ... }` chain
each ViewModel currently builds inline moves into the use case, sourcing entries/check-ins from the
repository directly (mirroring `ObserveJournalEntriesUseCase`/`ObserveHabitCheckInBoardUseCase`,
never depending on either of them per the never-depend-on-another-use-case rule).
`ObserveHabitContributionUseCase` takes check-ins as a `Flow<List<HabitCheckIn>>` parameter rather
than observing `HabitRepository` itself, since `HabitDetailViewModel` already filters the full
check-in board down to one habit's check-ins before this point — passing that filtered flow in
avoids the use case re-deriving a per-habit filter the ViewModel already computed.

**Contract**: `ObserveJournalContributionUseCase @Inject constructor(private val repository:
JournalRepository, private val clock: Clock) { operator fun invoke(window: Flow<ContributionWindow>):
Flow<ContributionSummary> }` — internally the same `combine(repository.observeEntries(), window) { ...
}.distinctUntilChanged().map { entries, w -> ContributionSummary(grid =
JournalContributionCalculator.compute(entries, w, clock.instant()), availableWindows =
availableWindows(entries.minOfOrNull { it.date }, clock.instant())) }` shape `MainViewModel`
already builds today, just relocated. `ObserveHabitContributionUseCase @Inject constructor(private
val clock: Clock) { operator fun invoke(checkIns: Flow<List<HabitCheckIn>>, window:
Flow<ContributionWindow>): Flow<ContributionSummary> }` — same shape, no repository dependency needed
since check-ins arrive as a parameter.

#### 4. ViewModel/mapper call-site updates

**Files**: `ui/main/MainViewModel.kt`, `ui/habit/detail/HabitDetailViewModel.kt`,
`ui/habit/detail/HabitDetailMapper.kt`, `ui/habit/logcheckin/LogHabitCheckInsViewModel.kt`,
`ui/journal/detail/JournalEntryDetailViewModel.kt`, `ui/account/AccountViewModel.kt`,
`ui/onboarding/OnboardingViewModel.kt`

**Intent**: Replace each direct `domain/util`/calculator call with the corresponding use case,
injected via constructor. Mapping the new use cases' `ContributionSummary` domain result to each
ViewModel's existing private UI-facing type needs no new mapper function or file: both fields map
via functions `ContributionMapper.kt` already exposes (`ContributionGrid.toUiState(now)`,
`ContributionWindow.toUiState()`) — each ViewModel composes them field-by-field when constructing
its own already-private target class, so no visibility change is needed either.

**Contract**:
- `MainViewModel`: inject `ObserveJournalContributionUseCase`, replace the `journalContributionData`
  flow's body with `observeJournalContribution(selectedJournalWindow).map { summary ->
  JournalContributionData(grid = summary.grid.toUiState(clock.instant()), availableWindows =
  summary.availableWindows.map { it.toUiState() }) }` (field-by-field, reusing the existing
  `ContributionMapper.kt` functions — see this item's Intent). The `_uiState` initial value's
  `JournalContributionCalculator.compute(...)` call is replaced with a direct `ContributionGrid(window
  = ContributionWindow.RollingTwelveMonths, days = emptyMap())` construction (see Key Discoveries —
  provably equivalent for empty input, no use case needed for this specific seed value).
- `HabitDetailViewModel`: inject `ObserveHabitContributionUseCase`, replace `contributionData`'s
  body with `observeHabitContribution(viewModelState.map { it.checkIns }.distinctUntilChanged(),
  viewModelState.map { it.selectedWindow }.distinctUntilChanged())` (or equivalent), mapping the
  result the same field-by-field way as `MainViewModel` above, into this file's own private
  `ContributionData` UI class (unrelated to and no longer name-colliding with the new domain
  `ContributionSummary`, since only the domain type's name changed). `stateIn`'s synchronous
  `initialValue` block drops the calculator/`availableWindows` calls entirely, replaced by a direct
  empty `ContributionGridUiState` construction and `listOf(ContributionWindowUiState.RollingTwelveMonths)`
  — matching `MainViewModel`'s existing initial-state precedent (see Key Discoveries).
- `HabitDetailMapper.toHabitDetailRows`: signature changes from `(pendingValues, now: Instant)` to
  `(pendingValues, freshLoggableDates: List<LocalDate>, isEditable: (Instant) -> Boolean)` — the
  mapper stays a pure function with no DI; `HabitDetailViewModel` (its only caller, inside
  `HabitDetailViewModelState.toUiState`) injects `IsEditableUseCase`/`GetFreshLoggableDatesUseCase`
  and passes `getFreshLoggableDates()` / `isEditable::invoke` in.
- `LogHabitCheckInsViewModel`: inject `GetFreshLoggableDatesUseCase`, replace
  `EditWindow.freshLoggableDates(Instant.now())` with `getFreshLoggableDates()` — also fixes the
  pre-existing `Instant.now()`-instead-of-injected-`Clock` inconsistency flagged during framing,
  since the use case owns its own `Clock`.
- `JournalEntryDetailViewModel`: inject `IsEditableUseCase`, replace both
  `EditWindow.isEditable(entry.createdAt, clock.instant())` call sites with
  `isEditable(entry.createdAt)`. Remove the now-unused `clock: Clock` constructor param (see Key
  Discoveries).
- `AccountViewModel`/`OnboardingViewModel`: inject `ShouldReviewLocalDataBeforeSyncUseCase`,
  replace `LocalDataSyncPolicy.shouldReviewBeforeSync(_uiState.value.hasSyncedLocalData, summary)`
  with `shouldReviewLocalDataBeforeSync(_uiState.value.hasSyncedLocalData, summary)` in both
  `proceedAfterAuthSuccess` implementations.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Build succeeds: `./gradlew.bat assembleDebug`
- No remaining direct imports: grep confirms zero matches for
  `import pl.luczka.todaywas.domain.util.EditWindow`,
  `import pl.luczka.todaywas.domain.util.LocalDataSyncPolicy`,
  `import pl.luczka.todaywas.domain.util.JournalContributionCalculator`, or
  `import pl.luczka.todaywas.domain.util.HabitContributionCalculator` outside `domain/usecase/`

#### Manual Verification:

- Main screen's journal contribution grid and window selector still render correctly, including
  the initial (pre-first-emission) empty state
- Habit detail screen's contribution grid, window selector, and per-row edit eligibility
  (today/yesterday always visible, 24h edit window enforced) all still behave identically
- Logging a habit check-in still offers exactly today and yesterday as selectable dates
- Journal entry detail's edit button and "help me refine" still respect the 24h window, including
  the mid-refine-request expiry race
- Signing in with existing local data still shows (or correctly skips) the data-review step in
  both onboarding and account flows

---

## Phase 5: Test coverage — `AuthRepositoryImpl`, `AiAssistRepositoryImpl`, `HabitMapper`

### Overview

Closes the last debt item. Auth/AiAssist tests use a real `SupabaseClient` built with a `MockEngine`
`httpEngine` and `install(Auth) { minimalConfig() }`, verified viable during framing. `HabitMapper`
gets an explicit `today` parameter so its test doesn't depend on the real system clock.

### Changes Required:

#### 1. `HabitMapper` gets an explicit `today` parameter

**File**: `ui/mapper/HabitMapper.kt`, plus its one call site in `ui/main/MainViewModel.kt`

**Intent**: Make `toHabitUiStates()` deterministic for testing, mirroring how
`ContributionMapperTest` injects a fixed `Instant` rather than relying on `Instant.now()`.

**Contract**: `fun HabitCheckInBoard.toHabitUiStates(today: LocalDate): List<HabitUiState>` —
drops its internal `LocalDate.now()` call in favor of the new parameter. `MainViewModel.kt:107`'s
call site becomes `board.toHabitUiStates(today = LocalDate.now(clock))` (the ViewModel already has
`clock: Clock` injected).

#### 2. `HabitMapperTest`

**File**: `app/src/test/java/pl/luczka/todaywas/ui/mapper/HabitMapperTest.kt` (new)

**Intent**: Cover the three-way status derivation (`NotLogged`/`LoggedBinary`/`LoggedScale`) and the
today-lookup behavior, following this project's established test conventions.

**Contract**: `` `should return NotLogged when no check-in exists for today` ``, `` `should return
LoggedBinary with done true when a BINARY habit's today check-in has value 1` ``, `` `should return
LoggedScale with the check-in's value when habit type is SCALE` ``, `` `should ignore a check-in
that is not dated today` `` — backtick names, AAA comments, a fixed `private val today =
LocalDate.of(2026, 6, 15)` field mirroring `ContributionMapperTest`'s `private val now =
Instant.parse(...)` pattern.

#### 3. `AuthRepositoryImplTest`

**File**: `app/src/test/java/pl/luczka/todaywas/data/repository/AuthRepositoryImplTest.kt` (new)

**Intent**: Cover `authCall`'s success/failure/cancellation branching for each public method, using
a real `SupabaseClient` so the test exercises the actual `supabase.auth.*` call shape, not a
hand-rolled fake of the SDK.

**Contract**: A private `supabaseClient(engine: MockEngine)` test helper builds `createSupabaseClient(supabaseUrl
= "https://example.supabase.co", supabaseKey = "test-key") { install(Auth) { minimalConfig() };
install(Functions) }` with `httpEngine = engine` (`Postgrest` omitted — `AuthRepositoryImpl` never
touches it). Tests: `` `should return success when signInWithEmail succeeds` ``, `` `should return
AuthException with the mapped error when signInWithEmail's response is an error status` ``, ``
`should return null from currentUserId when signed out` ``, and equivalent coverage for
`signUpWithEmail`/`signOut`. Cancellation-propagation (the `CancellationException` rethrow in
`authCall`) is verified via a `runTest` coroutine cancelled mid-call, confirming the exception
propagates rather than being converted to `Result.failure`.

#### 4. `AiAssistRepositoryImplTest`

**File**: `app/src/test/java/pl/luczka/todaywas/data/repository/AiAssistRepositoryImplTest.kt`
(new)

**Intent**: Cover `invokeAiProxy`'s success/failure branching for both
`generateJournalStarterPrompt` and `refineJournalEntry`, and confirm the 30s timeout override is
actually configured (regression protection for the documented cold-start fix).

**Contract**: Same `MockEngine`-backed `SupabaseClient` pattern as `AuthRepositoryImplTest` (`install(Functions)`
only — `AiAssistRepositoryImpl` never touches `Auth`). Tests: `` `should return the generated text
when generateJournalStarterPrompt succeeds` ``, `` `should return AiAssistException with the mapped
error when the proxy call fails` ``, `` `should return the refined text when refineJournalEntry
succeeds` ``, cancellation-propagation test mirroring `AuthRepositoryImplTest`'s.

### Success Criteria:

#### Automated Verification:

- New tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Main screen still correctly shows each habit's today-status after the `today` parameter change
  (spot-check a binary habit, a scale habit, and a not-yet-logged habit)

---

## Testing Strategy

### Unit Tests:

- Phase 1: retry/durability behavior relocates to `LocalHabitDataSourceImplTest`/
  `LocalJournalDataSourceImplTest`; repository-level tests focus on sync/merge/GC and (from Phase
  3) sync-scheduling behavior.
- Phase 4: no new tests required beyond compilation — the use cases are thin wrappers around
  already-tested pure functions (`EditWindow`, `LocalDataSyncPolicy`, the calculators); their
  behavior is exercised indirectly through existing ViewModel-level tests, if any exist for the
  touched files (verify during implementation; add minimal ViewModel-level coverage only if a
  touched ViewModel already has a test file that would otherwise silently stop covering this path).
- Phase 5: new `AuthRepositoryImplTest`, `AiAssistRepositoryImplTest`, `HabitMapperTest`.

### Integration Tests:

- None planned — this project has no integration-test tier separate from JVM unit tests (fakes)
  and manual verification.

### Manual Testing Steps:

1. Fresh install, complete onboarding without an account, create a habit, log a check-in, add a
   journal entry — confirm everything works with zero account (FR-008 regression check).
2. Sign in, confirm the data-review step appears once and data syncs correctly.
3. With the app foregrounded and signed in, create several entries/check-ins in quick succession,
   then background the app — confirm all reach remote (WorkManager durability check).
4. Repeat step 3 in airplane mode, then reconnect — confirm eventual delivery without reopening
   the app.
5. Force-stop the app immediately after a signed-in write, relaunch with network available —
   confirm the write still reaches remote.
6. Delete a habit, confirm its check-ins soft-delete together; separately delete a single check-in,
   confirm only that row is affected.

## Performance Considerations

Switching from a targeted single-row push to a full `syncWithRemote()` resync per write is
heavier per-write (a full local/remote diff instead of one upsert call), accepted as the tradeoff
for WorkManager's durability guarantees per the framing decision. `WorkManager.enqueueUniqueWork`
with `ExistingWorkPolicy.KEEP` bounds this: rapid successive writes (e.g. logging several habit
check-ins in one sitting) collapse into a single pending sync rather than one job per write.

## Migration Notes

Phase 2's schema change (dropping `ON DELETE CASCADE`) is backward-compatible — no data migration
needed, purely a constraint definition change. No local Room schema/migration changes are needed
anywhere in this plan (`fallbackToDestructiveMigration` already covers local dev; no entity shape
changes occur).

## References

- Related research: `context/changes/architecture-hardening/research.md`
- Related framing: `context/changes/architecture-hardening/frame.md`
- Existing patterns followed: `data/remote/api/RemoteHabitDataSource.kt` (Local*DataSource
  mirror), `data/util/TransactionRunner.kt` (SyncScheduler wrap-the-framework-class precedent),
  `domain/usecase/SaveHabitCheckInsUseCase.kt` (use-case shape + never-depend-on-another-use-case
  rule), `ui/mapper/ContributionMapperTest.kt` (fixed-time-injection test pattern)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: Repository decomposition (local/remote split + Syncable interface)

#### Automated

- [x] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — cb140bd
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck` — cb140bd
- [x] 1.3 Build succeeds: `./gradlew.bat assembleDebug` — cb140bd

#### Manual

- [x] 1.4 Creating/editing/deleting a habit, check-in, and journal entry while signed in still
      reaches remote — cb140bd
- [x] 1.5 Deleting a habit still soft-deletes its check-ins atomically — cb140bd
- [x] 1.6 `syncWithRemote()` still correctly merges local and remote state with no data loss — cb140bd

### Phase 2: Remote FK-cascade fix

#### Automated

- [x] 2.1 Unit tests still pass: `./gradlew.bat testDebugUnitTest`

#### Manual

- [x] 2.2 `habit_check_ins_habit_id_fkey`'s `confdeltype` is no longer `'c'`
- [x] 2.3 Purging an aged-out habit tombstone no longer hard-deletes check-ins with their own
      independent tombstone lifecycle

### Phase 3: WorkManager-driven sync outbox

#### Automated

- [ ] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 3.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 3.3 Build succeeds: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 3.4 App builds and launches without a WorkManager initialization crash
- [ ] 3.5 A signed-in write while online triggers a sync shortly after
- [ ] 3.6 A signed-in write made offline reaches remote after reconnecting, without reopening the
      app
- [ ] 3.7 A signed-in write followed immediately by a force-stop still reaches remote after
      relaunch

### Phase 4: `domain/util` direct calls → use cases

#### Automated

- [ ] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 4.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 4.3 Build succeeds: `./gradlew.bat assembleDebug`
- [ ] 4.4 No remaining direct `domain/util` calculator/policy imports outside `domain/usecase/`

#### Manual

- [ ] 4.5 Main screen's journal contribution grid and window selector render correctly, including
      initial empty state
- [ ] 4.6 Habit detail screen's contribution grid, window selector, and edit eligibility behave
      identically to before
- [ ] 4.7 Logging a habit check-in offers exactly today and yesterday
- [ ] 4.8 Journal entry detail's edit and "help me refine" respect the 24h window, including the
      mid-refine expiry race
- [ ] 4.9 Sign-in shows (or correctly skips) the data-review step in both onboarding and account
      flows

### Phase 5: Test coverage — `AuthRepositoryImpl`, `AiAssistRepositoryImpl`, `HabitMapper`

#### Automated

- [ ] 5.1 New tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 5.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 5.3 Build succeeds: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 5.4 Main screen still correctly shows each habit's today-status after the `today` parameter
      change
