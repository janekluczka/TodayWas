# Account Creation and Sync Implementation Plan

## Overview

When a user creates an account (or signs into one) via the existing Supabase auth flows
(F-01), review a summary of their existing local journal/habit data and — on confirmation —
upload it to a new RLS-protected Postgres schema. From that point on, remote becomes the
source of truth: local Room stays the fast, offline-first read/write surface, but every write
also pushes to remote in the background, and the app pulls remote → local on every cold start
while signed in (basic reinstall/new-device restore). Signing out clears the synced local
tables.

## Current State Analysis

- `JournalEntryEntity`, `HabitEntity`, `HabitCheckInEntity` (`data/local/*Entity.kt`) all use
  Room `@PrimaryKey(autoGenerate = true) val id: Long`. `HabitCheckInEntity.habitId` is a
  logical (unenforced) FK to `HabitEntity.id`.
- `TodayWasDatabase` (`data/local/TodayWasDatabase.kt:8`) is `version = 3`; `DatabaseModule.kt:22`
  builds it with `.fallbackToDestructiveMigration(dropAllTables = true)` — there is no real
  `Migration` object anywhere in the codebase.
- `JournalRepositoryImpl`/`HabitRepositoryImpl` (`data/repository/`) are local-only today,
  writing through `safeDbCall` (one retry, no remote awareness). Both have existing unit tests
  (`JournalRepositoryImplTest.kt`, `HabitRepositoryImplTest.kt`) built against hand-written
  `FakeDao`s.
- F-01 (`supabase-auth-foundation`, archived) shipped `AuthRepository`/`AuthRepositoryImpl`
  (Supabase `Auth` plugin only — no Postgrest installed), `AuthState` (domain, carries `userId`)
  / `AuthStateUi` (UI-facing, drops `userId`), and two independent auth sub-flows that both
  transition straight to a terminal step on success: `AccountViewModel.applySignUpResult()` /
  `applySignInResult()` (`ui/account/AccountViewModel.kt:129-138,181-194`) go to
  `AccountStep.SUCCESS`; `OnboardingViewModel`'s equivalents
  (`ui/onboarding/OnboardingViewModel.kt:237-251,294-308`) go to `OnboardingStep.ALL_SET`.
  Per that plan's "Deliberate logic duplication" note, the two ViewModels intentionally
  duplicate this state machine rather than sharing one ViewModel across two top-level screens.
- The Supabase project has 0 tables and 0 migrations (confirmed live via the Supabase MCP) —
  clean slate for a new Postgres schema.
- No sync/remote/dirty/uploaded concept exists anywhere in the app today (verified via
  repo-wide search).

### Key Discoveries

- Repositories, not use cases, are the existing seam for wrapping an SDK (`AuthRepositoryImpl`
  wraps `SupabaseClient.auth` directly, no intermediate "data source" layer) — but
  `JournalRepositoryImpl`/`HabitRepositoryImpl` are unit-tested today (unlike
  `AuthRepositoryImpl`, which isn't), so a Postgrest-wrapping dependency injected directly into
  them would remove that testability. This plan introduces `RemoteJournalDataSource`/
  `RemoteHabitDataSource`/`RemoteHabitCheckInDataSource` interfaces specifically so the existing
  `FakeDao`-style testing pattern extends cleanly to fakes of these too.
- Per [[feedback_usecase_depends_on_repository_only]], a use case never depends on another use
  case — but nothing in that rule (or `lessons.md`) forbids a use case depending on *multiple*
  repositories. `SyncLocalDataUseCase`/`ClearSyncedLocalDataUseCase` below each depend on 2-3
  repositories directly, which is a new shape for this codebase's use cases (previously always
  one repository each) but not a rule violation.
- `AccountKey`/`AccountScreen` and onboarding's account sub-flow (`AccountSubStep`) both already
  render a multi-step flow entirely as ViewModel-owned state, not separate `TodayWasKey` nav
  entries (`AccountStep` — `SIGN_IN`/`SIGN_UP`/`SUCCESS` — and `AccountSubStep` — `CHOICE`/
  `SIGN_IN`/`SIGN_UP` — are both plain enums driving `when` in the Screen composable). The new
  data-review step follows this exact precedent (one more enum value + state, not a new
  `TodayWasKey`/ViewModel).
- `UserPreferencesEntity` (`data/local/UserPreferencesEntity.kt`) is a singleton row (`id = 0`)
  already used for onboarding's `focus`/`onboardingCompleted` fields — the natural home for a
  one-time `hasSyncedLocalData` flag, avoiding per-row dirty-tracking columns on the three data
  entities.
- `JournalEntryDao`/`HabitDao`/`HabitCheckInDao` use plain `@Insert` (default `OnConflictStrategy
  .ABORT`) for local writes. The pull-side of sync (remote → local) needs a separate
  `OnConflictStrategy.REPLACE`-based upsert so it can overwrite a row that already exists
  locally without touching the existing `insert`/`update` call sites' behavior.
- A full file:line inventory of every `Long` id reference across entities, DAOs, repositories,
  domain models, use cases, nav keys, ViewModels, screens, and tests was compiled for Phase 1 —
  summarized in that phase's Changes Required rather than repeated here in full.

## Desired End State

Signing up or signing into an account shows a one-time review screen ("You have 12 journal
entries, 3 habits with 40 check-ins — add this to your account?"); confirming uploads
everything to Postgres. From then on, new journal entries and habit check-ins push to Postgres
transparently (no visible sync UI, silent best-effort retry on failure), and every cold start
while signed in refreshes local data from Postgres. Signing out clears the three synced local
tables (journal entries, habits, check-ins) but leaves onboarding/focus preferences intact. A
user who skipped the review screen can trigger it again later from the Account screen.

Verification: build succeeds, unit tests pass (including new fakes for the remote data source
layer), and a manual pass confirms upload/pull/sign-out-clear against the real (currently empty)
Supabase project.

## What We're NOT Doing

- Real-time/live sync (e.g. Realtime subscriptions) — pull happens on sign-in and on each cold
  start only, per PRD's non-goal ruling out real-time multi-device sync.
- Conflict resolution UI — the reconcile rule is simply "remote wins per row" on pull; there is
  no per-row "last modified" comparison or user-facing merge conflict prompt.
- Handling the edge case where a *different* account signs in on a device that still has another
  account's local data sitting in Room from before this feature existed (i.e., an upgrade
  scenario predating `hasSyncedLocalData`) — out of scope, same reasoning as the general
  existing-account edge case.
- Deleting remote rows when a local row is deleted — moot for this slice since no `delete`
  capability exists in the app for journal entries, habits, or check-ins today.
- A visible sync-status indicator ("synced"/"pending"/"failed") — failures are silent and
  self-heal via the next successful write or next launch's pull, per product decision.
- Any change to the AI-assist foundation (F-02) — unrelated slice.
- Updating `context/foundation/roadmap.md`'s S-06 outcome text — the roadmap entry already
  describes this at the right level of abstraction; only `prd.md`'s US-04 acceptance criteria
  need a wording update (see Phase 4).

## Implementation Approach

Five phases, ordered so nothing depends on UI that doesn't exist yet: (1) the foundational data
model change (UUID ids) and the empty Postgres schema, (2) making repositories capable of
syncing (push-per-write, bulk `syncWithRemote()`) without any UI wired to it yet, (3) sign-out
clearing (small, independent), (4) the user-facing review screen and its hook into both auth
sub-flows, (5) launch-time pull. Phases 1-2 have no user-visible behavior change and are fully
unit-verifiable; Phase 4 is where the feature actually becomes visible end-to-end.

## Critical Implementation Details

### Data loss is accepted for this schema bump, deliberately

Changing `id: Long` → `id: String` on three entities is itself a breaking Room schema change.
Per product decision, this ships via the existing `fallbackToDestructiveMigration(dropAllTables
= true)` (just bumping `@Database(version = ...)`), not a real `Migration` — meaning any
existing local data on a device is wiped when this update installs. This is an explicit,
accepted risk for a pre-release, internal/sideload-only app (no real user data at stake yet),
not an oversight. Do not add a `Migration` object for this bump.

### Why remote-wins needs no per-row timestamp comparison

The pull side of `syncWithRemote()` always overwrites a local row when the same `id` exists
remotely (`REPLACE` upsert), and inserts remote rows missing locally. It never deletes a local
row just because remote doesn't have it (since local-only rows are exactly the ones waiting to
be pushed). This means "remote wins" requires no `updatedAt`/dirty-flag bookkeeping at all —
the push half of the same call already means any local change either made it to remote (so the
pull half is a no-op for that row) or didn't (so the row stays local-only, untouched by pull,
until the next successful push retries it).

### Fire-and-forget push must not block or fail the caller's Result

`addEntry`/`updateEntry`/`createHabit`/`addCheckIns`/`updateCheckIn` all currently return
`Result<Unit>` describing the *local* write outcome. The new per-write remote push must not
change that contract: it's launched on an injected `@ApplicationScope CoroutineScope` (new —
see Phase 2) *after* the local `Result` is already determined, with its own failures caught and
logged, never surfaced to the caller. A slow or failed network call must never make a local
journal save feel slow or broken — this is the same principle FR-008 already establishes for
core journaling working with zero account.

## Phase 1: UUID id migration + Postgres schema + Postgrest wiring

### Overview

Switch `JournalEntry`/`Habit`/`HabitCheckIn` ids from Room-autoincremented `Long` to
client-generated UUID `String` end-to-end, and stand up the (currently nonexistent) remote
Postgres schema these ids will be pushed into. No user-visible behavior change — this phase is
purely foundational.

### Changes Required:

#### 1. Room entities

**File**: `data/local/JournalEntryEntity.kt`, `HabitEntity.kt`, `HabitCheckInEntity.kt`

**Intent**: Replace `@PrimaryKey(autoGenerate = true) val id: Long = 0` with `@PrimaryKey val
id: String` (no default, no autoGenerate — callers must supply a UUID). `HabitCheckInEntity
.habitId` becomes `String` too.

**Contract**: `TodayWasDatabase.kt:8` bumps to `version = 4`. No `Migration` object (see
Critical Implementation Details).

#### 2. DAOs

**File**: `data/local/JournalEntryDao.kt`, `HabitCheckInDao.kt`

**Intent**: Retype every `id`/`habitId: Long` parameter to `String`
(`JournalEntryDao.kt:15-16`'s `getById`, `HabitCheckInDao.kt:16-20`'s `getByHabitAndDate`).
`HabitDao.kt` has no id-typed methods — unaffected directly, the type change flows through
`HabitEntity` alone.

**Contract**: Also add, on all three DAOs, the methods Phase 2/3 need: a one-shot `suspend fun
getAll(): List<XEntity>` (mirroring the existing `observeAll()` Flow), an
`@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: XEntity)` (and
`upsertAll` for check-ins), and `@Query("DELETE FROM <table>") suspend fun clearAll()`.

#### 3. Repository interfaces + impls (id-typing only — sync capability is Phase 2)

**File**: `data/repository/JournalRepository.kt`, `JournalRepositoryImpl.kt`,
`HabitRepository.kt`, `HabitRepositoryImpl.kt`, and their entity↔domain mappers
(`JournalEntryEntityMapper.kt`, `HabitEntityMapper.kt`, `HabitCheckInEntityMapper.kt`)

**Intent**: Retype every `id: Long`/`habitId: Long` in these files to `String`
(`JournalRepository.kt:11,20-23`, `JournalRepositoryImpl.kt:18,32-39`,
`HabitRepository.kt:23-26,30-34`, `HabitRepositoryImpl.kt:46-49,51,62-71`). `addEntry`/
`createHabit`/`addCheckIns` now generate `id = UUID.randomUUID().toString()` when constructing
each entity (previously left at the `Long = 0` default for Room to autogenerate).

**Contract**: Mapper `id = id,`/`habitId = habitId,` lines are unaffected in structure — the
type just follows the entity/domain model change.

#### 4. Domain models + use cases

**File**: `domain/model/JournalEntry.kt`, `Habit.kt`, `HabitCheckIn.kt`;
`domain/usecase/GetJournalEntryUseCase.kt`, `UpdateJournalEntryUseCase.kt`,
`UpdateHabitCheckInUseCase.kt`, `LogHabitCheckInsUseCase.kt`

**Intent**: Retype `id`/`habitId: Long` → `String` throughout (`JournalEntry.kt:7`, `Habit.kt:6`,
`HabitCheckIn.kt:7-8`, `GetJournalEntryUseCase.kt:10`, `UpdateJournalEntryUseCase.kt:14-23`,
`UpdateHabitCheckInUseCase.kt:15-25`, `LogHabitCheckInsUseCase.kt:11-14`'s `Map<Long, Int>` →
`Map<String, Int>`).

#### 5. Nav keys + UI (ViewModels, Screens, ui/model)

**File**: `ui/TodayWasKey.kt`, `ui/TodayWasApp.kt`, `ui/journal/JournalEntryDetailViewModel.kt`,
`JournalEntryDetailScreen.kt`, `ui/habit/HabitDetailViewModel.kt`, `HabitDetailScreen.kt`,
`ui/mainshell/MainShellScreen.kt`, `ui/main/MainScreen.kt`, `MainUiEvent.kt`,
`MainViewModel.kt`, `ui/habit/LogHabitCheckInsViewModel.kt`, `LogHabitCheckInsIntent.kt`,
`LogHabitCheckInsUiState.kt`, `LogHabitCheckInsMapper.kt`, `LogHabitCheckInsScreen.kt`,
`ui/model/HabitUiState.kt`, `JournalEntryUiState.kt`, `HabitMapper.kt`, `JournalEntryMapper.kt`

**Intent**: Retype every `id`/`habitId: Long` in this chain to `String`
(`TodayWasKey.kt:19-21,30-32`'s `JournalEntryDetailKey`/`HabitDetailKey`; the assisted-injection
`@Assisted id/habitId: Long` params in `JournalEntryDetailViewModel.kt:26,119-121` and
`HabitDetailViewModel.kt:78,228-230`; every intermediate `(Long) -> Unit` callback/param down
this chain — see the full inventory gathered during planning for the exhaustive line list, it is
purely mechanical type propagation from `TodayWasKey` through Screen → ViewModel → use case →
repository → DAO).

**Contract**: No behavior change — `String` UUIDs flow through exactly where `Long` ids did.

### Success Criteria:

#### Automated Verification:

- Project builds: `./gradlew.bat assembleDebug`
- Unit tests pass: `./gradlew.bat testDebugUnitTest` (existing tests updated for the id type
  change — `JournalEntryDaoTest.kt` line 115's `inserted.id + 1` "missing id" idiom needs a
  different missing-id value under UUIDs, e.g. `UUID.randomUUID().toString()`; the
  `date.toEpochDay()`-as-id trick in `JournalContributionCalculatorTest.kt:14` and
  `HabitContributionCalculatorTest.kt:17` needs a real/fake UUID string instead)
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- Fresh install (post-schema-bump wipe is expected and fine at this stage): add a journal entry,
  create a habit, log a check-in, edit each within the 24h window — all still work with UUID ids
  under the hood.
- Supabase project has `journal_entries`, `habits`, `habit_check_ins` tables created via the
  Supabase MCP `apply_migration` tool, each with RLS enabled and a `user_id = auth.uid()`
  policy for select/insert/update; `postgrest-kt` compiles into the app (no runtime use yet).

---

## Phase 2: Remote data sources + repository sync capability

### Overview

Give `JournalRepository`/`HabitRepository` the ability to push local writes to Postgres
(fire-and-forget, silent failure) and to run a full `syncWithRemote()` (bulk push-all-local,
then pull-remote-into-local). No UI calls any of this yet.

### Changes Required:

#### 1. Postgrest dependency + client plugin

**File**: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `di/SupabaseModule.kt`

**Intent**: Add `postgrest-kt` (same `supabaseBom = "3.7.0"` catalog entry as `auth-kt`);
`install(Postgrest)` alongside the existing `install(Auth)` in `SupabaseModule.kt:19-24`.

#### 2. Application-scoped CoroutineScope

**File**: `di/CoroutineScopeModule.kt` (new)

**Intent**: A Hilt-provided, app-lifetime `CoroutineScope` for fire-and-forget background
pushes, since repository write methods are `suspend` and return before a slow/offline push
would complete.

**Contract**:
```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
```

#### 3. `AuthRepository` gains a synchronous current-user accessor

**File**: `data/repository/AuthRepository.kt`, `AuthRepositoryImpl.kt`

**Intent**: Repositories need the signed-in user's id synchronously (not via the `Flow`) to
decide whether/how to push. Add `fun currentUserId(): String?`, implemented as
`supabase.auth.currentUserOrNull()?.id` (reads the SDK's cached session, no network call).

#### 4. Remote DTOs + mappers

**File**: `data/repository/JournalEntryRemoteDto.kt`, `HabitRemoteDto.kt`,
`HabitCheckInRemoteDto.kt` (new), `JournalEntryRemoteMapper.kt`, `HabitRemoteMapper.kt`,
`HabitCheckInRemoteMapper.kt` (new)

**Intent**: `kotlinx.serialization`-annotated shapes matching the Postgres columns (snake_case
via `@SerialName`), and `toRemoteDto(userId)`/`toEntity()` mapping functions per
[[feedback_ui_state_no_domain_models]]-style layering discipline (cross-layer mapping in its own
file, mirroring the existing `*EntityMapper.kt` convention).

**Contract**: e.g. `JournalEntryRemoteDto(val id: String, @SerialName("user_id") val userId:
String, val date: String, val text: String, @SerialName("created_at") val createdAt: String)`
(`createdAt` as `Instant.toString()`, already ISO-8601). Analogous shapes for
`HabitRemoteDto`/`HabitCheckInRemoteDto`.

#### 5. Remote data source interfaces + impls

**File**: `data/repository/RemoteJournalDataSource.kt`, `RemoteHabitDataSource.kt`,
`RemoteHabitCheckInDataSource.kt` (new, `*Impl.kt` siblings), `RepositoryModule.kt`

**Intent**: One interface per table (mirrors the DAO-per-table local shape), each with `upsert`
(batch) and `fetchAll(userId)`. Postgrest-backed impls wrap `supabase.postgrest.from("<table>")`.
Bind all three via `@Binds` in `RepositoryModule.kt`, alongside the existing four bindings.

**Contract**:
```kotlin
interface RemoteJournalDataSource {
    suspend fun upsert(entries: List<JournalEntryRemoteDto>): Result<Unit>
    suspend fun fetchAll(userId: String): Result<List<JournalEntryRemoteDto>>
}
```
Impl wraps each Postgrest call in `runCatching`; `upsert` uses Postgrest's `upsert(...)` (keyed
by the `id` primary key, safe to call repeatedly); `fetchAll` filters `eq("user_id", userId)`.

#### 6. Repository sync capability

**File**: `data/repository/JournalRepository.kt`, `JournalRepositoryImpl.kt`,
`HabitRepository.kt`, `HabitRepositoryImpl.kt`

**Intent**: `JournalRepositoryImpl`/`HabitRepositoryImpl` gain the new remote data source(s) +
`AuthRepository` + `@ApplicationScope CoroutineScope` as constructor deps. Every existing write
method (`addEntry`, `updateEntry`, `createHabit`, `addCheckIns`, `updateCheckIn`) launches a
best-effort background push after its local `Result` succeeds — silently swallowing push
failures (see Critical Implementation Details). Both repositories gain `suspend fun
syncWithRemote(): Result<Unit>` and `suspend fun clearLocal(): Result<Unit>` on their
interfaces.

**Contract**:
```kotlin
override suspend fun syncWithRemote(): Result<Unit> {
    val userId = authRepository.currentUserId() ?: return Result.success(Unit)
    return runCatching {
        val local = dao.getAll()
        remoteDataSource.upsert(local.map { it.toDomain().toRemoteDto(userId) }).getOrThrow()
        val remote = remoteDataSource.fetchAll(userId).getOrThrow()
        remote.forEach { dao.upsert(it.toEntity()) }
    }
}
```
`clearLocal()` wraps `dao.clearAll()` (and, for `HabitRepositoryImpl`, both `habitDao.clearAll()`
and `habitCheckInDao.clearAll()`) in the existing `safeDbCall` pattern.

#### 7. Test fakes

**File**: `app/src/test/java/pl/luczka/todaywas/data/repository/FakeRemoteJournalDataSource.kt`,
`FakeRemoteHabitDataSource.kt`, `FakeRemoteHabitCheckInDataSource.kt` (new); update
`JournalRepositoryImplTest.kt`, `HabitRepositoryImplTest.kt`'s constructor calls; extend
`FakeAuthRepository.kt` with a settable `currentUserId`.

**Intent**: Hand-written fakes mirroring the existing `FakeDao` style, backed by an in-memory
`MutableList`/`MutableMap`, with a settable failure flag for testing swallowed push failures.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (new: push-after-write is attempted but
  never fails the returned `Result` even when the fake remote data source is set to fail;
  `syncWithRemote()` pushes all local rows then pulls remote rows not present locally;
  `clearLocal()` empties the DAO)
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- N/A — no UI in this phase; covered by Phase 4/5's manual steps.

---

## Phase 3: Sign-out clears synced local data

### Overview

Signing out wipes the three synced local tables and resets the one-time sync-confirmed flag,
while leaving onboarding/focus preferences untouched.

### Changes Required:

#### 1. `hasSyncedLocalData` flag

**File**: `data/local/UserPreferencesEntity.kt`, `data/repository/OnboardingRepository.kt`,
`OnboardingRepositoryImpl.kt`, `domain/model/OnboardingState.kt`

**Intent**: Add `val hasSyncedLocalData: Boolean = false` to the entity. Extend
`OnboardingState` with the same field (sourced in `observeState()`'s existing mapping). Add
`suspend fun markLocalDataSynced(): Result<Unit>` and `suspend fun resetSyncFlag(): Result<Unit>`
to `OnboardingRepository`/`Impl` — both do a read-modify-write of the singleton row via the
existing `dao.upsert(entity)`, preserving `focus`/`onboardingCompleted`.

#### 2. `ClearSyncedLocalDataUseCase`

**File**: `domain/usecase/ClearSyncedLocalDataUseCase.kt` (new)

**Intent**: Depends on `JournalRepository`, `HabitRepository`, `OnboardingRepository` directly
(three repositories, no other use case — see Key Discoveries). `invoke()` calls
`journalRepository.clearLocal()`, `habitRepository.clearLocal()`, `onboardingRepository
.resetSyncFlag()`, combining into one `Result<Unit>`.

#### 3. Wire into sign-out

**File**: `ui/account/AccountViewModel.kt`

**Intent**: `onSignOutClicked()` (`AccountViewModel.kt:196-209`), after `signOut()` succeeds,
also calls the new use case. Its own failure doesn't block the sign-out UI transition (the
remote sign-out already succeeded, which is the part the user cares about) — log and move on.

**Contract**: `AccountViewModel` gains a `private val clearSyncedLocalData:
ClearSyncedLocalDataUseCase` constructor param.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (new `ClearSyncedLocalDataUseCaseTest`;
  `AccountViewModelTest` extended to verify sign-out triggers the clear)
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- Sign out from the Account screen with local journal entries/habits/check-ins present: main
  screen shows empty state afterward; onboarding is not re-shown (focus/completed preference
  intact).

---

## Phase 4: Local-data review screen + sign-up/sign-in hook-in

### Overview

Build the shared review-screen content (counts of local journal entries, habits, and check-ins,
with confirm/skip actions), wire it into both `AccountViewModel`'s and `OnboardingViewModel`'s
existing post-auth-success transitions as one more step in their state machines, and add a
manual re-trigger for users who skip. Update `prd.md`'s US-04 acceptance criteria to match.

### Changes Required:

#### 1. `LocalDataSummary` + use cases

**File**: `domain/model/LocalDataSummary.kt` (new), `domain/usecase/GetLocalDataSummaryUseCase.kt`,
`SyncLocalDataUseCase.kt`, `MarkLocalDataSyncedUseCase.kt` (new)

**Intent**: `LocalDataSummary(val journalEntryCount: Int, val habitCount: Int, val
checkInCount: Int)` with an `isEmpty` derived property. `GetLocalDataSummaryUseCase` depends on
`JournalRepository` + `HabitRepository` (counts via their existing `observeEntries()`/
`observeHabits()`/`observeCheckIns()` flows' first emission). `SyncLocalDataUseCase` depends on
`JournalRepository` + `HabitRepository`, calling both `syncWithRemote()`s and combining results.
`MarkLocalDataSyncedUseCase` wraps `OnboardingRepository.markLocalDataSynced()`.

#### 2. Shared review-screen state + content

**File**: `ui/model/LocalDataSummaryUi.kt` + mapper (new), `ui/datasync/DataSyncReviewContent.kt`
(new)

**Intent**: A stateless composable (same pattern as `ui/auth/SignInFormContent.kt`/
`SignUpFormContent.kt`) rendering the counts, a syncing/loading state, and confirm ("Add my
data") / skip ("Not now") actions. Consumed by both `AccountScreen` and `OnboardingScreen`
below — no ViewModel of its own, per the existing precedent that this kind of embedded sub-flow
stays inside its host screen's ViewModel while small.

#### 3. `AccountStep`/`AccountUiState`/`AccountViewModel` wiring

**File**: `ui/account/AccountStep.kt`, `AccountUiState.kt`, `AccountIntent.kt`,
`AccountViewModel.kt`, `AccountScreen.kt`

**Intent**: `AccountStep` gains `DATA_SYNC_REVIEW` (between `SIGN_IN`/`SIGN_UP` and `SUCCESS`).
`AccountUiState` gains `dataSyncSummary: LocalDataSummaryUi?`, `isSyncing: Boolean`,
`hasSyncedLocalData: Boolean` (mirrored from `ObserveOnboardingStateUseCase`, injected
alongside the existing auth use cases). New intents: `SyncConfirmClicked`, `SyncSkipClicked`,
`SyncLocalDataClicked` (manual re-trigger, shown on the signed-in success/home state of Account
when `!hasSyncedLocalData`).

**Contract**: `applySignInResult()`/`applySignUpResult()` (`AccountViewModel.kt:129-138,
181-194`) now branch: if `!hasSyncedLocalData` and `getLocalDataSummary()` is non-empty, set
`step = DATA_SYNC_REVIEW` with the summary; otherwise fire-and-forget `syncLocalData()` and go
straight to `SUCCESS` as today. `SyncConfirmClicked` sets `isSyncing = true`, calls
`syncLocalData()` then `markLocalDataSynced()` on success, then `step = SUCCESS`.
`SyncSkipClicked` goes straight to `SUCCESS` without marking synced. `SyncLocalDataClicked`
(available once signed in, flag false) re-enters `DATA_SYNC_REVIEW` directly.

**Implementation note (added during impl-review)**: sign-in and sign-up don't actually share one
terminal transition — sign-in resolves to `NavigatedBack`, sign-up to `SUCCESS` (pre-existing
behavior, unrelated to this plan). The literal "go straight to SUCCESS as today" text above glosses
over that difference. The actual implementation introduces a private `PostSyncAction` enum
(`NAVIGATE_BACK` / `SHOW_SUCCESS` / `RETURN_HOME`) captured by `proceedAfterAuthSuccess()` at entry
and resolved by `finishPostSyncAction()` once the review step (or its skip) completes, so each entry
point reaches its own correct terminal state instead of both collapsing onto `SUCCESS`.

#### 4. `AccountSubStep`/`OnboardingUiState`/`OnboardingViewModel` wiring

**File**: `ui/onboarding/AccountSubStep.kt`, `OnboardingUiState.kt`, `OnboardingIntent.kt`,
`OnboardingViewModel.kt`, `OnboardingScreen.kt`

**Intent**: Same shape as AccountStep's change, applied to onboarding's account sub-flow.
`AccountSubStep` gains `DATA_SYNC_REVIEW`. `applySignInResult()`/`applySignUpResult()`
(`OnboardingViewModel.kt:237-251,294-308`) get the identical branch, transitioning to
`accountSubStep = DATA_SYNC_REVIEW` instead of `step = ALL_SET` when there's unsynced local
data, otherwise unchanged. `SyncConfirmClicked`/`SyncSkipClicked` intents added, both eventually
reaching `step = ALL_SET` (confirm sets `allSetReason` the same as the underlying sign-in/sign-up
already did).

#### 5. PRD update

**File**: `context/foundation/prd.md`

**Intent**: US-04's acceptance criteria currently says "The user takes no additional action
beyond creating the account." Update to reflect the review-screen confirm/skip step (still "no
manual export/import step" — the deviation is the one extra tap to confirm, not a full manual
flow).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (new `GetLocalDataSummaryUseCaseTest`,
  `SyncLocalDataUseCaseTest`, `MarkLocalDataSyncedUseCaseTest`; `AccountViewModelTest`/
  `OnboardingViewModelTest` extended for the new step transitions — first-time-with-data goes to
  review, already-synced/empty-data skips straight to success, confirm/skip both terminate
  correctly)
- Lint passes: `./gradlew.bat ktlintCheck`
- `DataSyncReviewContent` ships `@PreviewLightDark` previews (empty state shouldn't render at
  all — covered by ViewModel logic, not this composable — so previews cover: counts shown,
  syncing, and post-confirm states), wrapped in `DsTheme`

#### Manual Verification:

- Fresh local data (a few journal entries, a habit with check-ins) → sign up: review screen
  shows correct counts → confirm → data appears in Supabase's table editor with the right
  `user_id`.
- Sign out, sign back into the same account on the same device with new local-only data added
  in between: review screen appears again (flag was reset by Phase 3's sign-out clear) with
  correct counts.
- Skip the review screen once, then use Account's manual "Sync local data" action to trigger it
  again.
- No local data (fresh account, nothing added yet): sign-up goes straight to `SUCCESS`/`ALL_SET`,
  review screen never appears.

---

## Phase 5: Launch-time pull sync

### Overview

While signed in, refresh local data from Postgres on every cold start — basic reinstall/new-device
restore, without building realtime sync.

### Changes Required:

#### 1. Startup sync trigger

**File**: `ui/RootViewModel.kt` (or equivalent root-level ViewModel already observing
`AuthState` at startup — confirm exact current file at implementation time), `SyncLocalDataUseCase.kt`
(reused, no change)

**Intent**: On `ViewModel init` (app cold start), if `AuthState` is `SignedIn`, fire-and-forget
call `syncLocalDataUseCase()` once — same use case Phase 4's review-screen confirm already uses,
now also invoked automatically. No loading UI, no blocking (per the Failure UX / silent-retry
decision).

**Contract**: A single `viewModelScope.launch { if (observeAuthState().first() is
AuthState.SignedIn) syncLocalData() }`-shaped call at startup, guarded so it only fires once per
process (not on every `AuthState` re-emission).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (startup sync fires when already
  signed in at launch, does not fire when signed out)
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- Sign in, add data via a second client (e.g. directly in Supabase's table editor) that
  wouldn't otherwise exist locally, force-stop and relaunch the app: the new remote row appears
  locally after cold start.
- Fresh install + sign into an existing account with cloud data already present (no local data
  on this device at all): cold start pulls all existing cloud data down, main screen reflects it
  without the user doing anything beyond signing in — the basic "restore on reinstall" case.

---

## Testing Strategy

### Unit Tests:

- Phase 1: existing DAO/repository/use case/ViewModel tests updated for the `String` id type;
  the two non-mechanical spots (missing-id idiom, epoch-day-as-id trick) get a real fake-UUID
  substitute.
- Phase 2: `JournalRepositoryImplTest`/`HabitRepositoryImplTest` extended with fake remote data
  sources — push-after-write never fails the caller's `Result` even when the fake is set to
  fail; `syncWithRemote()` push-then-pull behavior; `clearLocal()`.
- Phase 3: `ClearSyncedLocalDataUseCaseTest` (new); `AccountViewModelTest` sign-out path.
- Phase 4: `GetLocalDataSummaryUseCaseTest`, `SyncLocalDataUseCaseTest`,
  `MarkLocalDataSyncedUseCaseTest` (new); `AccountViewModelTest`/`OnboardingViewModelTest`
  extended for the review-step branch (with-data-unsynced → review; already-synced/empty →
  skip; confirm; skip).
- Phase 5: startup sync fires only when already signed in.

### Integration Tests:

- None planned — consistent with the rest of this codebase's convention (no instrumented tests
  for auth-adjacent work either); Postgrest calls are exercised through the fakeable
  `RemoteXDataSource` interfaces in unit tests instead.

### Manual Testing Steps:

See each phase's Manual Verification. End-to-end: sign up with local data present → review →
confirm → verify in Supabase dashboard → sign out (data clears locally) → sign back in with new
local-only data added in between (review appears again) → force-stop/relaunch (launch-time pull
picks up anything added via the dashboard directly).

## Performance Considerations

Fire-and-forget pushes run on `Dispatchers.IO` via the new `@ApplicationScope` scope, off the
caller's coroutine, so they never add latency to a journal save or habit check-in. The bulk
`syncWithRemote()` call (review-screen confirm, and every cold-start pull) does one upsert +
one fetch per table (3 tables × 2 calls = 6 network calls) — acceptable for this app's expected
data volume (PRD's `shape-notes.md`: "small" data volume, "large" user count but "low" QPS).

## Migration Notes

The Phase 1 schema bump destructively wipes local data (accepted risk, see Critical
Implementation Details) — there is no data-preserving migration path for existing installs of
this pre-release app.

## References

- F-01 precedent for shared-across-two-ViewModels sub-flows:
  `context/archive/2026-08-03-supabase-auth-foundation/plan.md`'s "Deliberate logic duplication
  between OnboardingViewModel and PreferencesViewModel" section.
- `context/foundation/lessons.md` — "Use case depends on repository only",
  "Cross-layer mapping lives in its own dedicated file", "A flow gets its own ViewModel once
  it's a screen, not an overlay".
- UUID migration file:line inventory — compiled during planning via an Explore agent pass over
  `app/src/main/java` and `app/src/test/java`; the exhaustive list is mechanical type
  propagation and is not repeated in full here (see Phase 1's Changes Required for the
  non-mechanical exceptions).

## Addenda

- **`data/repository/RemoteCall.kt`** (Phase 2, discovered during impl-review): Phase 2 item 5's
  intent said each `Remote*DataSourceImpl` should wrap its Postgrest calls "in `runCatching`"
  individually. The actual implementation factors this into one shared `remoteCall(block):
  Result<T>` helper, reused by all three `Remote*DataSourceImpl` classes and by both
  repositories' `syncWithRemote()`. A benign DRY refactor of the same contract, not called out
  in the original plan text.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: UUID id migration + Postgres schema + Postgrest wiring

#### Automated

- [x] 1.1 Project builds: `./gradlew.bat assembleDebug` — bcfada3
- [x] 1.2 Unit tests pass: `./gradlew.bat testDebugUnitTest` — bcfada3
- [x] 1.3 Lint passes: `./gradlew.bat ktlintCheck` — bcfada3

#### Manual

- [x] 1.4 Fresh install: add/edit journal entry, habit, check-in — all work with UUID ids
- [x] 1.5 Supabase tables (`journal_entries`, `habits`, `habit_check_ins`) exist with RLS
      policies via MCP `apply_migration`; `postgrest-kt` compiles in

### Phase 2: Remote data sources + repository sync capability

#### Automated

- [x] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 3eb2805
- [x] 2.2 Lint passes: `./gradlew.bat ktlintCheck` — 3eb2805

### Phase 3: Sign-out clears synced local data

#### Automated

- [x] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — e476b0d
- [x] 3.2 Lint passes: `./gradlew.bat ktlintCheck` — e476b0d

#### Manual

- [ ] 3.3 Sign out clears journal/habit/check-in data; onboarding not re-shown

### Phase 4: Local-data review screen + sign-up/sign-in hook-in

#### Automated

- [x] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 1c65ddb
- [x] 4.2 Lint passes: `./gradlew.bat ktlintCheck` — 1c65ddb
- [x] 4.3 `DataSyncReviewContent` `@PreviewLightDark` previews (counts, syncing, post-confirm) — 1c65ddb

#### Manual

- [ ] 4.4 Sign-up with local data: review screen shows correct counts, confirm uploads to
      Supabase with correct `user_id`
- [ ] 4.5 Sign out then sign back into same account with new local-only data: review reappears
- [ ] 4.6 Skip once, then manually re-trigger via Account's "Sync local data" action
- [ ] 4.7 Sign-up with no local data: goes straight to success, review never appears

### Phase 5: Launch-time pull sync

#### Automated

- [x] 5.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — a9d0990
- [x] 5.2 Lint passes: `./gradlew.bat ktlintCheck` — a9d0990

#### Manual

- [ ] 5.3 Data added via Supabase dashboard directly appears locally after force-stop/relaunch
- [ ] 5.4 Fresh install + sign into existing account with cloud data: cold start restores it
