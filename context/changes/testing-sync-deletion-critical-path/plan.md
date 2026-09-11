# Sync & Deletion Critical-Path Coverage — Implementation Plan

## Overview

This is rollout Phase 1 of `context/foundation/test-plan.md`. It fixes and test-locks the three
grounded sync/deletion risks from `research.md`: a sync lock that doesn't actually serialize
across its call sites, a silent sync-failure UX gap on account creation, and a missing
end-to-end regression test for the soft-delete → sync-push chain.

## Current State Analysis

- `HabitRepositoryImpl`/`JournalRepositoryImpl` each hold a private `syncMutex: Mutex` intended
  to serialize concurrent `syncWithRemote()` calls. Neither repository, nor their `@Binds` in
  `RepositoryModule.kt`, carries `@Singleton` — confirmed by grep, zero matches. Four independent
  Hilt injection sites (`MainViewModel`, `AccountViewModel`, `OnboardingAccountSetupViewModel`,
  `SyncWorker`) each therefore receive their own repository instance with their own independent
  `Mutex`, so the lock cannot do its stated job.
- `AccountViewModel.onSyncConfirmClicked()` and `OnboardingAccountSetupViewModel`'s equivalent
  call their post-sync completion step unconditionally, regardless of `syncLocalData()`'s result.
  A failed upload during account creation looks identical to a successful one to the user — no
  error, no retry prompt.
- The soft-delete → merge → push chain (DAO soft-delete → `toSyncMeta()` mapper →
  `mergeForSync()` → repository push) is correct today, but only tested link-by-link
  (`SyncMergeTest.kt` uses hand-built fixtures; DAO tests only check `deletedAt`/read-filtering;
  repository tests use hand-rolled fakes that bypass the real DAO). No test proves the chain as a
  whole.

## Desired End State

- All four sync trigger points share one `Mutex` per repository, so two concurrent
  `syncWithRemote()` calls are always serialized.
- A failed sync during the account-creation confirm step surfaces a visible error and leaves the
  user able to retry, instead of silently proceeding as if it succeeded.
- A real, DAO-backed test proves that soft-deleting any of the three entity types (journal entry,
  habit, check-in) results in that row being selected for push by the real merge logic.

### Key Discoveries:

- `RepositoryModule.kt:35-39` — both repository bindings are plain `@Binds`, no scope.
- `@Singleton` is an established pattern elsewhere in `di/` (`DatabaseModule.kt:24-26`,
  `SupabaseModule.kt:19-21`, `WorkManagerModule.kt:18-20`) — always as `@Provides @Singleton` on a
  module method, never directly on an `@Inject constructor` class. The fix should follow this
  exact convention: `@Singleton` alongside `@Binds` in `RepositoryModule.kt`, not on
  `HabitRepositoryImpl`/`JournalRepositoryImpl` themselves.
- `AccountViewModel.kt:269-278` (`onSyncConfirmClicked`) and the equivalent
  `OnboardingAccountSetupViewModel.kt:260-269` are the only methods that need to change for Risk
  #2 — the other fire-and-forget sync call in `proceedAfterAuthSuccess`'s `else` branch only fires
  when `hasSyncedLocalData` is already `true` or there's no local data at all
  (`ShouldReviewLocalDataBeforeSyncUseCase` / `LocalDataSyncPolicy.kt:7-10`), so there is nothing
  at stake on that path and it's out of scope.
- The existing failure-handling idiom, used identically in three places in each ViewModel
  (`applySignInResult`, `applySignUpResult`, `onSignOutClicked`), is
  `(result.exceptionOrNull() as? AuthException)?.error ?: AuthError.Unknown` →
  `eventChannel.trySend(ShowError(error.toUiState()))`. Reuse it as-is — no new event type, no new
  string resource (`AuthError.Unknown` renders `auth_error_unknown`, already generic enough).
- `JournalEntryDao.upsertAll()` (`JournalEntryDao.kt:34-37`) is `@Transaction`-annotated,
  identically to `HabitCheckInDao.upsertAll()` — and journal's `applyRemoteSnapshot` only ever
  writes to one table, so the "non-transactional apply" concern research flagged as an open
  question does not apply to journal entries; no fix or test needed there.
- No Hilt test infrastructure exists anywhere in this project (no `hilt-android-testing`
  dependency, no custom test runner, no `HiltAndroidRule` usage). Verifying the `@Singleton` fix
  via a real instrumented Hilt graph would mean introducing all of that from scratch — out of
  proportion for one regression assertion, so verification uses reflection instead.

## What We're NOT Doing

- Not introducing Hilt instrumented test infrastructure (`hilt-android-testing`, a custom test
  runner, `HiltAndroidRule`) — a reflection-based annotation check gives the same regression
  protection at a fraction of the cost, given no such infra exists in this project today.
- Not touching the fire-and-forget background-sync branch in `proceedAfterAuthSuccess`'s `else`
  branch — it only runs when there is no local data at risk (already synced, or nothing to sync).
- Not fixing `OnboardingAccountSetupViewModel.finish()`'s separate silent-failure gap (no
  `Finished`/`ShowError` event if `completeOnboarding()` itself fails) — a different bug in a
  different method, outside this phase's sync-focused scope.
- Not adding `SyncMergeTest.kt` coverage for the tie-break (`updatedAt` equal) or
  both-sides-tombstoned cases — real gaps, but not raised by the interview or by any confirmed
  live bug; candidates for a future rollout phase, not this one.
- Not scoping `SyncLocalDataUseCase` (or anything else) as `@Singleton` — the race lives in the
  two repositories' `Mutex` fields; the use case has no state of its own that needs sharing.
- Not addressing risks #4 (cross-account leak), #5 (RLS ownership), or #6 (cascade-delete
  regression lock) from `test-plan.md` §2 — separate, not-yet-started rollout phases.

## Implementation Approach

Three independent, priority-ordered phases (#3 → #2 → #1, per your call): fix the DI scoping bug
first since it's the most severe and touches the least code: one file, two annotations, one new
reflection test. Then fix the silent-failure UX, which touches two ViewModels but reuses an
existing, proven error-handling pattern verbatim. Finally, close the Risk #1 test gap with a new
integration test that composes already-correct production code (no fix needed there) — pure
addition, zero behavior change.

## Critical Implementation Details

**Hilt scoping**: `@Singleton` on a `@Binds` method requires its enclosing `@Module` to be
`@InstallIn(SingletonComponent::class)` — `RepositoryModule.kt` already is, so adding the
annotation to the two binding methods is the entire fix; no other module or component wiring
changes.

## Phase 1: Fix the sync lock's DI scoping

### Overview

Make `HabitRepositoryImpl` and `JournalRepositoryImpl` singleton-scoped in Hilt so every
injection site shares one instance — and therefore one `Mutex` — closing the confirmed race.

### Changes Required:

#### 1. Repository bindings gain `@Singleton`

**File**: `app/src/main/java/pl/luczka/todaywas/di/RepositoryModule.kt`

**Intent**: Ensure `bindJournalRepository` and `bindHabitRepository` resolve to the same instance
everywhere in the app, so the existing `syncMutex` actually serializes concurrent
`syncWithRemote()` calls across `MainViewModel`, `AccountViewModel`,
`OnboardingAccountSetupViewModel`, and `SyncWorker`.

**Contract**: Add `@Singleton` alongside the existing `@Binds` annotation on both
`bindJournalRepository` (line 35-36) and `bindHabitRepository` (line 38-39), matching the
`@Provides @Singleton` convention already used elsewhere in `di/`. Add the
`javax.inject.Singleton` import if not already present in this file.

#### 2. Regression test locking in the scope annotation

**File**: `app/src/test/java/pl/luczka/todaywas/di/RepositoryModuleScopeTest.kt` (new)

**Intent**: Prevent a future refactor from silently dropping the scope annotation and
reintroducing the cross-call-site race.

**Contract**: A plain JUnit4 test using `java.lang.reflect` to fetch
`RepositoryModule::class.java`'s declared `bindJournalRepository`/`bindHabitRepository` methods
and assert `isAnnotationPresent(Singleton::class.java)` for both.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build succeeds (confirms Hilt's annotation processor accepts the new scope with no graph
  validation error): `./gradlew.bat assembleDebug`

#### Manual Verification:

- On a device/emulator, sign in and trigger two near-simultaneous sync paths (e.g. launch the app
  while a background sync from a recent write is still pending) — confirm no crash, ANR, or
  duplicate-sync log noise from lock contention.

---

## Phase 2: Fix silent sync failure on account creation

### Overview

Stop `AccountViewModel`/`OnboardingAccountSetupViewModel` from treating a failed
`syncLocalData()` call the same as a successful one during the account-creation confirm step.

### Changes Required:

#### 1. `AccountViewModel.onSyncConfirmClicked()` conditions on the sync result

**File**: `app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt`

**Intent**: On success, behave exactly as today (mark synced, clear the summary, finish). On
failure, surface an error and leave the `DATA_SYNC_REVIEW` step in place (summary retained) so the
user can tap Confirm again — no new screen, no new event type.

**Contract**: Reuse the existing `(result.exceptionOrNull() as? AuthException)?.error ?:
AuthError.Unknown` → `eventChannel.trySend(AccountUiEvent.ShowError(error.toUiState()))` idiom
already used in `applySignInResult`/`applySignUpResult`/`onSignOutClicked` (lines 173-182,
243-252, 308-321). Only `markLocalDataSynced()`, the `dataSyncSummary = null` update, and
`finishPostSyncAction(postSyncAction)` move inside the success branch; `isSyncing` resets to
`false` unconditionally either way.

#### 2. `OnboardingAccountSetupViewModel.onSyncConfirmClicked()` — mirror the same change

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/accountsetup/OnboardingAccountSetupViewModel.kt`

**Intent**: Same fix, same reasoning, applied to the onboarding flow's equivalent method
(currently lines 260-269), using `OnboardingAccountSetupUiEvent.ShowError` and its own existing
`AuthException` cast idiom (lines 168/170, 236/238).

**Contract**: Same shape as change 1 — `finish(pendingAllSetReason)` moves inside the success
branch.

#### 3. `AccountViewModelTest.kt` — failure-path test

**File**: `app/src/test/java/pl/luczka/todaywas/ui/account/AccountViewModelTest.kt`

**Intent**: Prove the fix — a failed confirm-sync shows an error, doesn't mark data synced, and
doesn't advance past the review step.

**Contract**: New test(s) alongside the existing `SyncConfirmClicked` tests (around lines
484-534): set the fake sync path to fail, dispatch `SyncConfirmClicked`, assert
`markLocalDataSyncedCallCount == 0`, `dataSyncSummary` still non-null, `step` still
`DATA_SYNC_REVIEW`, and a `ShowError` event was emitted.

#### 4. `OnboardingAccountSetupViewModelTest.kt` — mirror test

**File**: `app/src/test/java/pl/luczka/todaywas/ui/onboarding/accountsetup/OnboardingAccountSetupViewModelTest.kt`

**Intent**: Same proof for the onboarding flow's equivalent method.

**Contract**: Same shape as change 3.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- On a device, reach the data-sync-review step with local data present, force a failure (e.g.
  enable airplane mode right after tapping Confirm), and confirm an error message appears while
  the review screen with a working Confirm button remains visible.

---

## Phase 3: End-to-end soft-delete → sync-push regression test

### Overview

Close the one real gap left by Risk #1's research: no test proves the full chain from a real
DAO soft-delete through to the merge logic's push decision, for any of the three entity types.

### Changes Required:

#### 1. New integration test chaining DAO → mapper → merge

**File**: `app/src/test/java/pl/luczka/todaywas/data/util/SyncMergeIntegrationTest.kt` (new,
sibling of the existing `SyncMergeTest.kt`)

**Intent**: Prove — through real production code, not hand-built fixtures — that soft-deleting a
row of each entity type results in that row being selected for push by `mergeForSync()`.

**Contract**: Robolectric-backed, real in-memory Room DB (same pattern as
`HabitCheckInDaoTest.kt`/`JournalEntryDaoTest.kt`/`HabitDaoTest.kt`). One test per entity type
(journal entry, habit, check-in): insert a row via the real DAO, soft-delete it via the real DAO,
read it back via `getAllIncludingDeleted()`, map it through the real `toSyncMeta()` mapper, call
the real `mergeForSync()` with an empty remote-side map, and assert the row's id lands in
`pushIds`.

#### 2. Update the test-plan cookbook

**File**: `context/foundation/test-plan.md`

**Intent**: Per this rollout phase's own contract with `/10x-test-plan`, its final sub-phase
records the pattern it shipped so future contributors don't have to rediscover it.

**Contract**: Replace §6.3 ("Adding a sync/merge test") placeholder text with a short description
of the pattern established by change 1 above (real DAO + real mapper + real merge function,
Robolectric in-memory DB, one test per entity type), referencing
`SyncMergeIntegrationTest.kt` as the canonical example. Add a short §6.6 per-rollout-phase note
summarizing what this phase found (the DI-scoping bug, the silent-failure UX bug) for future
readers.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- None required — pure test addition over already-correct production code, no user-visible
  behavior change.

---

## Testing Strategy

### Unit Tests:

- Reflection-based scope assertion (Phase 1)
- ViewModel-level failure-path tests for both account-creation flows (Phase 2)
- Real-DAO-backed integration tests for soft-delete → merge push-eligibility, all 3 entity types
  (Phase 3)

### Integration Tests:

- Phase 3's `SyncMergeIntegrationTest.kt` is the integration layer for this plan — no separate
  integration-test phase needed beyond it.

### Manual Testing Steps:

1. Sign in, trigger two overlapping sync attempts, confirm no crash/race artifacts (Phase 1).
2. Force a sync failure during account-creation confirm, confirm error + retry-capable review
   screen (Phase 2).
3. No manual step needed for Phase 3.

## Performance Considerations

Singleton-scoping the two repositories has no meaningful performance cost — if anything, it
avoids constructing a fresh repository (and its dependencies) at every injection site, which is
the standard, recommended scope for a repository in Android architecture generally.

## Migration Notes

None — no schema or data migration involved in this plan.

## References

- Research: `context/changes/testing-sync-deletion-critical-path/research.md`
- Rollout strategy: `context/foundation/test-plan.md` §2 (risks #1-#3), §3 (Phase 1)
- Existing patterns followed: `AccountViewModel.kt:173-182` (error-handling idiom),
  `HabitCheckInDaoTest.kt`/`JournalEntryDaoTest.kt` (Robolectric DAO test pattern),
  `SyncMergeTest.kt` (pure-function merge test pattern)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Fix the sync lock's DI scoping

#### Automated

- [ ] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 1.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 1.3 Debug build succeeds: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 1.4 Two near-simultaneous sync paths on a device don't crash/ANR/race

### Phase 2: Fix silent sync failure on account creation

#### Automated

- [ ] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 2.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 2.3 Debug build succeeds: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 2.4 Forced sync failure during confirm shows an error and stays retry-capable on a device

### Phase 3: End-to-end soft-delete → sync-push regression test

#### Automated

- [ ] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 3.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 3.3 Debug build succeeds: `./gradlew.bat assembleDebug`
