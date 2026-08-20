---
date: 2026-08-18T19:10:09+02:00
researcher: Janek Łuczka
git_commit: 69ab2a182da136c7a5a8aef18b39f8d6c018ef1f
branch: master
repository: janekluczka/TodayWas
topic: "Architecture hardening: sync durability, ViewModel/domain-util coupling, test coverage gaps"
tags: [research, codebase, sync, transaction-runner, work-manager, viewmodel, use-case, test-coverage, habit-mapper, auth-repository, ai-assist-repository, supabase-fk]
status: complete
last_updated: 2026-08-18
last_updated_by: Janek Łuczka
---

# Research: Architecture hardening

**Date**: 2026-08-18T19:10:09+02:00
**Researcher**: Janek Łuczka
**Git Commit**: 69ab2a182da136c7a5a8aef18b39f8d6c018ef1f
**Branch**: master
**Repository**: janekluczka/TodayWas

## Research Question

`context/changes/architecture-hardening/change.md` lists five technical-debt items compiled from
cross-references in three prior changes (`sync-strategy-rework`, `mvp-certification-fixes`,
`entry-delete`) but never independently audited. This research verifies each item against the
current codebase and gathers everything a future `/10x-plan` pass needs:

1. `syncMutex`/`tryLock` background-push pattern has no real retry — verify current implementation
   and the WorkManager-outbox alternative.
2. `MainViewModel`/`HabitDetailViewModel` call `domain/util` calculators directly instead of
   through a use case.
3. Zero test coverage on `AuthRepositoryImpl`/`AiAssistRepositoryImpl`.
4. Untested `HabitMapper` status-derivation logic — is it still accurate?
5. Remote `habit_check_ins.habit_id` FK still has a live `ON DELETE CASCADE`, and
   `syncWithRemote()`'s local-apply loop isn't wrapped in `TransactionRunner`.

## Summary

All five items are confirmed live in the current codebase (commit `69ab2a1`), with exact
file:line references gathered below. Two additional facts change the shape of a future plan:

- **WorkManager is entirely absent from the project** — no gradle dependency, no code anywhere.
  Adopting the outbox pattern from item 1 is a **net-new dependency addition**, not a refactor of
  existing WorkManager usage.
- **The `domain/util`-direct-call violation is broader than the two named ViewModels.** Beyond
  `MainViewModel`/`HabitDetailViewModel` calling the contribution calculators, `EditWindow` and
  `LocalDataSyncPolicy` (also in `domain/util/`) are called directly from `HabitDetailMapper`,
  `LogHabitCheckInsViewModel`, `JournalEntryDetailViewModel`, `AccountViewModel`, and
  `OnboardingViewModel` — none wrapped in a use case. Worth deciding scope (the two named
  ViewModels only, or the full pattern) before planning.
- **`AuthRepositoryImpl`/`AiAssistRepositoryImpl` are hard to unit-test as currently structured**:
  both depend on the concrete `SupabaseClient` class (no wrapping interface, unlike every other
  repo in the app), so testing them requires either a Ktor `MockEngine`-backed `SupabaseClient`
  (the codebase's only precedent for this is `AiAssistErrorMapperTest`, which fakes an HTTP
  response, not a full `SupabaseClient`) or accepting narrower coverage. No mocking library
  (MockK/Mockito) exists in the project — the established convention is 100% hand-rolled fakes.
- **`HabitMapper`'s status-derivation logic is currently accurate**, not drifted — it correctly
  ignores `deletedAt`/`updatedAt` sync-plumbing fields per the documented contract on `Habit`/
  `HabitCheckIn`. It just has zero regression protection.
- Both the FK-cascade and non-transactional-apply-loop items were already investigated once, in
  `sync-strategy-rework`'s implementation review (F1/F2), and explicitly deferred to this change
  with a fix already sketched in code comments — see Historical Context.

## Detailed Findings

### 1. Sync retry: `syncMutex`/`tryLock` pattern

Implemented identically in two repositories, both using a `Mutex` purely to keep an in-flight
`syncWithRemote()` pass from racing an individual background push for the same row — not to
provide retry:

- `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt:48` —
  `private val syncMutex = Mutex()`.
- `HabitRepositoryImpl.kt:202-213` — `pushHabitInBackground(entity)`:
  ```kotlin
  private fun pushHabitInBackground(entity: HabitEntity) {
      val userId = authRepository.currentUserId() ?: return
      syncScope.launch {
          if (syncMutex.tryLock()) {
              try {
                  remoteHabitDataSource.upsert(listOf(entity.toDomain().toRemoteDto(userId)))
              } finally {
                  syncMutex.unlock()
              }
          }
      }
  }
  ```
- `HabitRepositoryImpl.kt:215-226` — `pushCheckInsInBackground(entities)`, identical shape.
- Call sites: `createHabit` (line 73), `addCheckIns` (96), `updateCheckIn` (109), `deleteHabit`
  (130, 132), `deleteCheckIn` (147).
- `HabitRepositoryImpl.kt:197-201` (comment directly above `pushHabitInBackground`) explicitly
  documents the fire-and-forget nature: failures are silently swallowed since the local write
  already succeeded, and the push is *skipped entirely* (not queued) when `tryLock()` fails
  because a full sync is in flight — the only safety net is that the next successful write or
  next full `syncWithRemote()` pass happens to also cover that row.
- `app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt:39` — same
  `Mutex()`, same rationale comment (36-38).
- `JournalRepositoryImpl.kt:112-123` — `pushInBackground(entity)`, same shape. Call sites:
  `addEntry` (58), `updateEntry` (69), `deleteEntry` (79).
- `syncWithRemote()` itself always runs (`withLock`, not `tryLock`) in both repos — only the
  opportunistic per-row background pushes are ever dropped.
- **No retry mechanism exists anywhere in the codebase.**

**WorkManager is not present in the project at all**: no `androidx.work`/`WorkManager`/
`CoroutineWorker` match anywhere under `app/`, `gradle/`, or any `.kts`/`.toml` file; no
`androidx-work-*` entry in `gradle/libs.versions.toml`; no `implementation(libs.androidx.work...)`
in `app/build.gradle.kts`. Adopting the Google-recommended outbox pattern (`NetworkType.CONNECTED`
constraint + `Result.retry()` exponential backoff, per Google's *Now in Android* reference app) is
a new dependency, not a rewire of existing infrastructure.

### 2. ViewModel → `domain/util` direct calls

**Named violations** (from `change.md`):

- `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:29` imports
  `JournalContributionCalculator`; called directly at line 56-65 (initial `_uiState`) and line 92
  (inside the `journalContributionData` flow's `.map`).
- `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailViewModel.kt:29` imports
  `HabitContributionCalculator`; called directly at line 110 (`contributionData` flow) and line 125
  (synchronous `stateIn` initial value with an empty check-in list).
- Neither calculator (`domain/util/JournalContributionCalculator.kt`,
  `domain/util/HabitContributionCalculator.kt`) is wrapped by any use case today.
- Both ViewModels also call `availableWindows(...)` from `domain.model.ContributionWindow`
  directly at the same call sites (`MainViewModel.kt:93`, `HabitDetailViewModel.kt:111,126`) —
  technically `domain/model`, not `domain/util`, but the same structural issue (business/
  derivation logic invoked straight from a ViewModel), and would need to be folded into the same
  fix since it's derived from the same `entries`/`checkIns` + `now` inputs.

**Broader instances of the same pattern, not named in `change.md`** (worth a scope decision):

- `domain/util/EditWindow.kt` (`isEditable`, `freshLoggableDates`) is called *correctly* from
  inside use cases (`UpdateJournalEntryUseCase.kt:19`, `UpdateHabitCheckInUseCase.kt:21`,
  `SaveHabitCheckInsUseCase.kt:32`) but *also* called directly from UI-layer code that bypasses a
  use case entirely: `ui/habit/detail/HabitDetailMapper.kt:18,24`,
  `ui/habit/logcheckin/LogHabitCheckInsViewModel.kt:30`,
  `ui/journal/detail/JournalEntryDetailViewModel.kt:73,210`.
- `domain/util/LocalDataSyncPolicy.kt` (`shouldReviewBeforeSync`) is called directly from
  `ui/account/AccountViewModel.kt:217` and `ui/onboarding/OnboardingViewModel.kt:302` — no use
  case wraps it either.

**Established use-case pattern** to follow (three existing examples, all wrapping `EditWindow`):
`UpdateJournalEntryUseCase.kt`, `UpdateHabitCheckInUseCase.kt`, `SaveHabitCheckInsUseCase.kt` — all
share the shape `class <Verb><Noun>UseCase @Inject constructor(private val repository: <Feature>Repository, private val clock: Clock) { suspend operator fun invoke(...): Result<Unit> }`,
one class per action, flat under `domain/usecase/`, depending on the repository directly (never on
another use case — `SaveHabitCheckInsUseCase.kt:16-22` states this rule explicitly in its own
comment). Observer-style use cases return `Flow<T>` instead (e.g.
`ObserveHabitCheckInBoardUseCase.kt:13`).

A new use case wrapping `JournalContributionCalculator`/`HabitContributionCalculator` would need
to depend on the relevant repository directly (sourcing `entries`/`checkIns` itself, mirroring
`ObserveJournalEntriesUseCase`/`ObserveHabitCheckInBoardUseCase`) plus `Clock`, and would need to
support both `HabitDetailViewModel`'s reactive flow path and its synchronous empty-initial-value
path.

### 3. Test coverage: `AuthRepositoryImpl` / `AiAssistRepositoryImpl` / `HabitMapper`

**Confirmed zero test files** for `AuthRepositoryImpl` and `AiAssistRepositoryImpl` (no
`AuthRepositoryImplTest.kt`/`AiAssistRepositoryImplTest.kt` anywhere under `app/src/test/`), and
**zero test file** for `HabitMapper` (no `HabitMapperTest.kt`).

**`app/src/main/java/pl/luczka/todaywas/data/repository/AuthRepositoryImpl.kt`**
- Sole dependency: `private val supabase: SupabaseClient` (line 18-20) — the **concrete** Supabase
  SDK class, not wrapped behind an interface (unlike `HabitRepositoryImpl`/`JournalRepositoryImpl`,
  which depend on DAO/data-source interfaces).
- Public API: `observeAuthState()`, `currentUserId()`, `signUpWithEmail`, `signInWithEmail`,
  `signInWithGoogleIdToken`, `signOut` (lines 22-53).
- All suspend methods funnel through a shared `authCall` helper (55-62): rethrows
  `CancellationException`, otherwise wraps any `Exception` as
  `Result.failure(AuthException(e.toAuthError()))`.

**`app/src/main/java/pl/luczka/todaywas/data/repository/AiAssistRepositoryImpl.kt`**
- Sole dependency: `private val supabase: SupabaseClient` (line 33-35), used via
  `supabase.functions`.
- `generateJournalStarterPrompt`/`refineJournalEntry` (37-45) both delegate to
  `invokeAiProxy(request)` (47-61), which calls `supabase.functions.invoke(function = "ai-proxy")`
  with an explicit 30s `requestTimeoutMillis`/`socketTimeoutMillis` override — a documented fix for
  the ai-proxy edge function's cold-start + free-tier latency exceeding supabase-kt's ~10s default.
  Same `CancellationException`-rethrow / `Result.failure(AiAssistException(...))` shape as
  `AuthRepositoryImpl`.

**Testability**: both classes' error-mapping functions (`toAuthState`, `toAuthError`,
`toAiAssistError`) are *already* separately unit-tested (`AuthStateMapperTest.kt`,
`AuthErrorMapperTest.kt`, `AiAssistErrorMapperTest.kt`), which narrows what the repo tests
themselves need to re-verify (mainly: success path, generic-exception → failure path, cancellation
propagation, and for AI-assist specifically, the raised-timeout behavior). `AiAssistErrorMapperTest`
already demonstrates this codebase's only precedent for faking an HTTP response
(`HttpClient(MockEngine { respond(...) })`), which is the most idiomatic path toward testing
`invokeAiProxy`'s branching — likely by constructing a `SupabaseClient` with
`httpEngine = MockEngine {...}` via `createSupabaseClient(...)`. No mocking library (MockK/
Mockito) exists in the project (confirmed via `app/build.gradle.kts` test deps: junit,
robolectric, kotlinx-coroutines-test, androidx-test-core, ktor-client-mock only) — the established
convention is 100% hand-rolled `Fake*` classes implementing real interfaces (see
`FakeAuthRepository.kt`, `FakeAiAssistRepository.kt`, and the richest exemplar,
`HabitRepositoryImplTest.kt`'s private `FakeHabitDao`/`FakeRemoteHabitDataSource`/
`FakeTransactionRunner` classes with a small `repository(...)` factory helper).

**`app/src/main/java/pl/luczka/todaywas/ui/mapper/HabitMapper.kt:20-29`** — status-derivation
logic:
```kotlin
fun Habit.toUiState(todayCheckIn: HabitCheckIn?): HabitUiState = HabitUiState(
    id = id,
    name = name,
    type = type.toUiState(),
    todayStatus = when {
        todayCheckIn == null -> HabitCheckInStatusUiState.NotLogged
        type == HabitType.BINARY -> HabitCheckInStatusUiState.LoggedBinary(done = todayCheckIn.value == 1)
        else -> HabitCheckInStatusUiState.LoggedScale(value = todayCheckIn.value)
    },
)
```
Fed by `toHabitUiStates()` (12-18), which finds today's check-in via
`checkIns.find { it.habitId == habit.id && it.date == today }` using `LocalDate.now()` — notable
for testability since, unlike `ContributionMapperTest` (which injects a fixed `Instant`),
`HabitMapper` exposes no `now` parameter to inject.

**Accuracy check**: the mapper is **currently accurate, not drifted**. `Habit.kt`/`HabitCheckIn.kt`
both carry an explicit comment that `updatedAt`/`deletedAt` are "data-layer sync plumbing only ...
never surfaced through any ui/mapper/ or UiState" (added during `sync-strategy-rework`), and
`HabitMapper.kt` correctly never references those fields — soft-deleted rows are already filtered
upstream in `HabitRepositoryImpl.observeHabits()`/`observeCheckIns()` before reaching the mapper.
`git log` on `HabitMapper.kt` shows only one commit, a pure file-move
(`1ebbf11 refactor(arch-cleanup): split ui/model into models and ui/mapper (p6)`) — no logic
change since. The gap is purely regression protection: any future change to `Habit`/`HabitCheckIn`/
`HabitType` would ship silently without a failing test.

**Test-writing conventions** (already established, to follow for all new tests): backtick-quoted
`` `should [outcome] when [scenario]` `` names, explicit `// Arrange` / `// Act` / `// Assert`
comments, plain JUnit4 `org.junit.Assert.*` (no Truth/AssertK), no mocking library. Best exemplars:
`HabitRepositoryImplTest.kt` (repository-layer, fakes + `runTest`/`backgroundScope`),
`AuthErrorMapperTest.kt`/`AiAssistErrorMapperTest.kt` (pure-mapper style, `MockEngine` for HTTP
faking).

### 4/5. Remote FK cascade and non-transactional `syncWithRemote()` apply loop

**`TransactionRunner`** — the established "wrap the framework class" pattern (per
`context/foundation/lessons.md`'s no-concrete-framework-class-injection rule):
- Interface: `app/src/main/java/pl/luczka/todaywas/data/util/TransactionRunner.kt:1-6` —
  `interface TransactionRunner { suspend fun <T> runInTransaction(block: suspend () -> T): T }`.
- Impl: `data/util/RoomTransactionRunner.kt:7-12` — delegates to `TodayWasDatabase.withTransaction`.
- DI: `di/DatabaseModule.kt:40-41`.
- Used today only in `HabitRepositoryImpl.deleteHabit` (lines 120-125):
  ```kotlin
  transactionRunner.runInTransaction {
      habitCheckInDao.softDeleteByHabitId(id, deletedAt)
      habitDao.softDeleteById(id, deletedAt)
  }
  ```
  with a comment (117-119) explaining why: a habit must never be left with only some of its
  check-ins deleted if the second delete fails.

**Not used** in `syncWithRemote()`'s local-apply loops:
- `HabitRepositoryImpl.kt:151-190` — `habitsToApply.forEach { habitDao.upsert(it.toEntity()) }`
  (line 163) and `habitCheckInDao.upsertAll(checkInsToApply.map { it.toEntity() })` (line 173) are
  each internally atomic (the DAO's own `@Transaction` on `upsertAll`), but the two loops together
  are not tied into one all-or-nothing unit the way `deleteHabit`'s two calls are.
- `JournalRepositoryImpl.kt` doesn't inject a `TransactionRunner` at all (constructor at 29-34)
  — its own apply loop, `toApply.forEach { dao.upsert(it.toEntity()) }` (line 96), is unwrapped
  and there's currently nothing to wrap it with.

**Remote schema** — no local `.sql` migration files exist in the repo (`supabase/` only contains
`functions/ai-proxy/index.ts`); the schema lives purely on the linked remote Supabase project.
Direct inspection of the live project (`pg_constraint` for `habit_check_ins_habit_id_fkey`)
confirms `confdeltype = 'c'` and
`FOREIGN KEY (habit_id) REFERENCES habits(id) ON DELETE CASCADE` is still live. Local Room
entities have no `@ForeignKey` at all (`HabitEntity.kt`, `HabitCheckInEntity.kt`,
`JournalEntryEntity.kt`) — the cascade only exists remotely.

**Documented deferral already in code** — `HabitRepositoryImpl.kt:174-182` (comment directly above
the GC purge calls):
> Known limitation: the remote `habit_check_ins.habit_id` FK still has a live `ON DELETE CASCADE`,
> so purging a habit here can hard-delete check-in rows that never went through their own
> tombstone/GC lifecycle... Currently unreachable through the app's own UI... a real fix means
> dropping the CASCADE and redesigning purge ordering, deferred to architecture-hardening.

The purge call ordering matters for a future fix: `HabitRepositoryImpl.kt:186` purges the remote
habit (firing the cascade) **before** line 187 purges remote check-ins under their own tombstone
criteria — for a check-in that hasn't yet earned its own tombstone (the multi-device race), it's
hard-deleted with no `deletedAt` ever set.

**Schema-relationship scope check**: this is specific to `habits`/`habit_check_ins`. Live schema
dump confirms `journal_entries` has only a `user_id` FK (no child table references it), and
`habits` is the target of exactly one inbound FK — `habit_check_ins_habit_id_fkey`. No second,
hidden instance of the dormant-cascade pattern exists elsewhere in the schema.

Both facts (FK cascade, non-transactional apply loop) match findings F1/F2 in
`sync-strategy-rework`'s implementation review, both explicitly deferred here — see Historical
Context below for the exact review language and decision rationale.

## Code References

- `data/repository/HabitRepositoryImpl.kt:48,73,96,109,130,132,147,151-190,197-226` — syncMutex,
  push-in-background, syncWithRemote, GC purge + deferral comment
- `data/repository/JournalRepositoryImpl.kt:29-34,39,58,69,79,83-103,112-123` — syncMutex,
  push-in-background, syncWithRemote (no TransactionRunner injected)
- `data/util/TransactionRunner.kt:1-6`, `data/util/RoomTransactionRunner.kt:7-12`,
  `di/DatabaseModule.kt:40-41` — the wrap-the-framework-class pattern
- `data/util/SyncMerge.kt:18,25-52` — `TOMBSTONE_GC_WINDOW`, `mergeForSync()` tombstone supremacy
- `ui/main/MainViewModel.kt:29,56-65,92-93` — direct `JournalContributionCalculator`/
  `availableWindows` calls
- `ui/habit/detail/HabitDetailViewModel.kt:29,110-111,125-126` — direct
  `HabitContributionCalculator`/`availableWindows` calls
- `domain/util/HabitContributionCalculator.kt:13,30`,
  `domain/util/JournalContributionCalculator.kt:12`,
  `domain/util/EditWindow.kt:8-22`, `domain/util/LocalDataSyncPolicy.kt:5-11` — the four
  `domain/util` objects
- `domain/usecase/UpdateJournalEntryUseCase.kt`, `UpdateHabitCheckInUseCase.kt`,
  `SaveHabitCheckInsUseCase.kt:16-22` — established use-case pattern (incl. explicit
  never-depend-on-another-use-case rule)
- `ui/habit/detail/HabitDetailMapper.kt:18,24`, `ui/habit/logcheckin/LogHabitCheckInsViewModel.kt:30`,
  `ui/journal/detail/JournalEntryDetailViewModel.kt:73,210`, `ui/account/AccountViewModel.kt:217`,
  `ui/onboarding/OnboardingViewModel.kt:302` — additional (unnamed in change.md) direct
  `domain/util` call sites
- `data/repository/AuthRepositoryImpl.kt:18-62`, `data/repository/AiAssistRepositoryImpl.kt:21-61`
  — untested repositories, concrete `SupabaseClient` dependency
- `ui/mapper/HabitMapper.kt:12-29` — untested status-derivation logic
- `domain/model/Habit.kt:5-6`, `domain/model/HabitCheckIn.kt:6-7` — sync-plumbing-fields contract
  comment that `HabitMapper` correctly honors
- `data/mapper/AuthErrorMapperTest.kt`, `data/mapper/AiAssistErrorMapperTest.kt:20-23`,
  `data/repository/HabitRepositoryImplTest.kt:32-47` — test-pattern exemplars
  (`MockEngine` HTTP faking, fake-repository-builder pattern)

## Architecture Insights

- The project's "wrap the framework class" convention (lessons.md) is applied consistently for
  Room (`TransactionRunner`) but has a real gap for Supabase: `AuthRepositoryImpl`/
  `AiAssistRepositoryImpl` inject the concrete `SupabaseClient` directly, unlike every other
  repository which depends on interfaces (DAOs, `Remote*DataSource`). This was raised (and
  explicitly accepted, not fixed) during the separate `arch-cleanup` change — see Historical
  Context — so it's a known, intentional asymmetry, not an oversight to silently "fix" as part of
  closing the test-coverage gap. A plan should test around this asymmetry (Ktor `MockEngine`) or
  explicitly re-open whether to introduce `RemoteAuthDataSource`/`RemoteAiAssistDataSource`.
- The `domain/util` direct-call violation is a project-wide pattern, not isolated to the two named
  ViewModels — six additional call sites across four files show the same shape. This changes the
  cost/benefit of fixing "MainViewModel/HabitDetailViewModel only" vs. the whole pattern; worth an
  explicit scope decision in planning rather than assuming `change.md`'s two named ViewModels are
  the full extent.
- All three synced tables share one `syncWithRemote()` shape (push → apply → GC-purge), but only
  `habits`/`habit_check_ins` have a parent/child FK relationship — the CASCADE fix is schema-local
  to those two tables, not a general problem across all sync tables.
- No local Room `@ForeignKey` exists on any entity — local soft-delete cascade (`deleteHabit`)
  is handled entirely in application code via `TransactionRunner`, which is why the remote-only FK
  is the sole source of the CASCADE inconsistency; there's no equivalent local-side risk to fix.

## Historical Context (from prior changes)

- `context/changes/sync-strategy-rework/frame.md:40-42,51` — the `syncMutex`/`tryLock` retry gap
  was explicitly out of scope for that change per the user's own pre-dispatch decision, not
  evaluated as a hypothesis; flagged only to note the correct official terminology
  (WorkManager-driven outbox, `NetworkType.CONNECTED`, `Result.retry()`) for whoever plans this
  change.
- `context/changes/sync-strategy-rework/frame.md:66-72` — Google's official
  offline-first guidance (`developer.android.com/topic/architecture/data-layer/offline-first`):
  local DB as source of truth, "Lazy Writes" for critical data, WorkManager +
  connectivity constraints + exponential backoff for sync orchestration; *Now in Android* is cited
  as the reference implementation.
- `context/changes/sync-strategy-rework/frame.md:92-98` and `plan.md` "What We're NOT Doing" —
  switching signed-in writes from local-first-then-push to remote-first-then-refetch was
  **investigated and explicitly rejected**, citing the same Lazy-Writes guidance; do not re-open
  as a live design question.
- `context/changes/sync-strategy-rework/reviews/impl-review.md` — **Finding F1** (⚠️ WARNING,
  🔬 HIGH impact): the exact FK-cascade issue, decision "FIXED via Fix B" (documented in code,
  real fix deferred to this change). **Finding F2** (⚠️ WARNING, 🔎 MEDIUM impact): the exact
  non-transactional-apply-loop issue, decision "SKIPPED — left as-is," explicitly grouped with the
  `syncMutex` durability item as the same class of concern, both deferred here.
- `context/changes/sync-strategy-rework/plan.md` Phase 6 — `SaveHabitCheckInsUseCase` design
  (already implemented, commit `e3be852`): `class SaveHabitCheckInsUseCase @Inject constructor(private val repository: HabitRepository) { suspend operator fun invoke(habitId, existing, pending): Result<Unit> }`,
  explicitly built to depend on `HabitRepository` directly rather than on
  `LogHabitCheckInsUseCase`/`UpdateHabitCheckInUseCase` — the concrete precedent for the
  never-depend-on-another-use-case rule this future change should also follow.
- `context/changes/mvp-certification-fixes/change.md` — this change was split 2026-08-16 into
  `entry-delete` (the delete feature) and `architecture-hardening` (this one, "a weak-spot audit —
  sync lock mechanism, UI-layer business logic entanglement — plus fixes and tests, superseding
  the plain retroactive test-plan.md idea"). Neither `mvp-certification-fixes` nor `entry-delete`
  discusses `AuthRepositoryImpl`, `AiAssistRepositoryImpl`, `HabitMapper`, or the domain/util
  ViewModel-coupling issue anywhere — those items have no historical "why deferred" narrative
  beyond being out of scope for the delete/CRUD/test-plan/README work those changes actually did.
- `context/changes/arch-cleanup/` (not one of the three named source changes, found during
  research) — confirms `AuthRepositoryImpl`/`AiAssistRepositoryImpl` calling `SupabaseClient`
  directly (vs. `Remote*DataSource` for Habit/Journal) was a **known, explicitly accepted**
  asymmetry: "This asymmetry is out of scope; both impls move as-is, unchanged internally... No
  new abstractions... no `RemoteAuthDataSource`/`RemoteAiAssistDataSource` is introduced." Confirms
  current file locations (`data/repository/AuthRepositoryImpl.kt`, `ui/mapper/HabitMapper.kt`)
  post-reorganization.

## Related Research

None — this is the first research artifact for this change (`research.md` was not previously
generated for `sync-strategy-rework`, `mvp-certification-fixes`, or `entry-delete` either; their
docs are `frame.md`/`plan-brief.md`/`plan.md`/`reviews/impl-review.md` only).

## Open Questions

1. **Scope of item 2**: should the fix cover only `MainViewModel`/`HabitDetailViewModel`'s
   contribution-calculator calls (as literally named in `change.md`), or the full pattern
   (`EditWindow`/`LocalDataSyncPolicy` direct calls across `HabitDetailMapper`,
   `LogHabitCheckInsViewModel`, `JournalEntryDetailViewModel`, `AccountViewModel`,
   `OnboardingViewModel` too)?
2. **Testability approach for `AuthRepositoryImpl`/`AiAssistRepositoryImpl`**: accept a
   `MockEngine`-backed `SupabaseClient` (more realistic coverage, more setup complexity) or scope
   tests narrowly to the `authCall`/`invokeAiProxy` catch/rethrow branching only? Does not
   requiring `RemoteAuthDataSource`/`RemoteAiAssistDataSource` abstraction (per `arch-cleanup`'s
   explicit rejection) constrain the answer?
3. **FK-cascade fix mechanics**: dropping `ON DELETE CASCADE` on `habit_check_ins.habit_id`
   requires a remote Supabase migration (no local `.sql` migration files exist in-repo today —
   this would be the first). Does this change also want to establish checked-in migration files as
   infrastructure, or apply the fix directly via Supabase MCP/dashboard as the existing four
   migrations were?
4. **WorkManager outbox scope**: does item 1's fix replace both `HabitRepositoryImpl` and
   `JournalRepositoryImpl`'s push mechanisms in one pass, or land incrementally?
