# Cascade & Account-Boundary Regression Locks — Implementation Plan

## Overview

This is rollout Phase 2 of `context/foundation/test-plan.md`. Both risks it covers (#4, #6) are
already correctly handled by production code today — research found no live bugs. This plan adds
regression-lock tests only: closing test-double and test-coverage gaps that would let a future
regression in either area pass the entire existing suite undetected.

## Current State Analysis

- **Risk #4**: `SignOutUseCase.kt:21-31` correctly clears local data based on the observed
  `AuthState` transition, not `signOut()`'s `Result` — sound for the real trigger condition (a
  reachable-but-erroring auth server, which the Supabase SDK's `signOut()` clears the session for
  before rethrowing). But `FakeAuthRepository.signOut()` (`FakeAuthRepository.kt:55-61`)
  structurally couples "the call fails" with "the session stays signed in" — it cannot express
  the one case that actually matters, so no test can fail if a future regression re-couples
  clearing to `result.isSuccess`.
- **Risk #6**: `LocalHabitDataSourceImpl.deleteHabitAndCheckIns()` (lines 44-63) is already
  transactional (check-ins soft-deleted before the habit, inside
  `transactionRunner.runInTransaction {}`), and `HabitRepositoryImpl.syncWithRemote()`'s GC purge
  (lines 150-161) still purges check-ins before habits remotely — a live Supabase query confirms
  the underlying FK is still `NO ACTION`, not reverted to `CASCADE`. But nothing tests the call
  order, a mid-cascade failure's rollback, or the remote purge ordering specifically — the one
  existing GC test (`HabitRepositoryImplTest.kt:474-512`) only asserts the habit-side purge count.
- `FakeTransactionRunner` (`data/util/FakeTransactionRunner.kt`) just calls its block directly —
  it has no rollback semantics, so it cannot be used to prove the cascade delete's transaction
  actually protects against a partial delete. Proving that requires the real `RoomTransactionRunner`
  against a real Room DB.

## Desired End State

- A regression that reverts `SignOutUseCase`'s clearing logic back to gating on `result.isSuccess`
  fails a test.
- A regression that breaks the local cascade delete's transactionality, or reorders it, fails a
  test that exercises a real Room DB.
- A regression that reorders or drops the remote GC purge's check-ins-before-habits call fails a
  test.

### Key Discoveries:

- `FakeAuthRepository.kt:55-61` — the exact coupling that needs breaking: `signOutError?.let {
  Result.failure(...) } ?: run { state.value = AuthState.SignedOut; Result.success(Unit) }`.
- `safeDbCall` (`data/util/SafeDbCall.kt:6-20`) retries its block exactly once on failure before
  returning `Result.failure` — a test that injects a failure into the cascade delete's transaction
  block will see it attempted twice (each attempt fully rolling back), not once. The final DB
  state and call-order log must account for two attempts, not one.
- `RoomTransactionRunner` (`data/util/RoomTransactionRunner.kt`) wraps `database.withTransaction`
  — genuine Room rollback semantics, only available against a real `TodayWasDatabase`, matching
  the builder pattern already used in `SyncMergeIntegrationTest.kt`/`HabitCheckInDaoTest.kt`.
- `HabitRepositoryImplTest.kt:474-512`'s existing GC test only constructs a tombstoned habit, no
  check-in — extending it to prove purge order needs a tombstoned check-in referencing the same
  habit, plus a shared call-order log threaded into both `FakeRemoteHabitDataSource` and
  `FakeRemoteHabitCheckInDataSource`.

## What We're NOT Doing

- Not adding a test for the "true offline, no connectivity" sign-out case — research confirmed
  this path never reaches the risky branch at all (session is preserved, nothing to clear), so
  it's already adequately covered by the existing "should not clear local data when signOut
  fails" test.
- Not replacing `FakeAuthRepository`'s error-based API with a more general result/state
  decoupling — the single additive flag covers the one real scenario without touching any
  existing test's setup.
- Not extending `FakeTransactionRunner` to simulate rollback — a real Room DB test proves genuine
  transaction semantics instead of reimplementing them in test code.
- Not touching any production code — both risks are confirmed already correctly handled; this
  plan is test-only.
- Not addressing risk #5 (RLS ownership verification) — a separate, not-yet-started rollout phase.

## Implementation Approach

Three independent phases, priority-ordered (#4 → local cascade → remote purge, per your call):
fix the test double that structurally can't express the account-boundary risk first, since it's
the most severe; then lock in the local cascade's transactional order against a real DB; then
lock in the remote purge order via a shared call-order log across the two existing remote fakes.

## Phase 1: Fix `FakeAuthRepository` and lock in the sign-out clearing gate

### Overview

Give `FakeAuthRepository` a way to express "the session cleared, but the call still reports
failure" — the one real decoupled scenario — then add a test proving `SignOutUseCase` clears
local data in exactly that case.

### Changes Required:

#### 1. `FakeAuthRepository` gains a decoupling flag

**File**: `app/src/test/java/pl/luczka/todaywas/domain/repository/FakeAuthRepository.kt`

**Intent**: Let a test construct "the call fails AND the session is now signed out" as one state,
which is currently impossible.

**Contract**: Add `var signOutClearsSessionAnyway: Boolean = false`. In `signOut()`, when
`signOutError != null` and `signOutClearsSessionAnyway` is true, set `state.value =
AuthState.SignedOut` before returning `Result.failure(...)`. Default `false` preserves every
existing test's behavior unchanged.

#### 2. New `SignOutUseCaseTest` case for the decoupled scenario

**File**: `app/src/test/java/pl/luczka/todaywas/domain/usecase/SignOutUseCaseTest.kt`

**Intent**: Prove the use case's actual guarantee — clearing is driven by the observed state
transition, not the `Result` — using the one scenario where they diverge.

**Contract**: New test alongside the existing three: `FakeAuthRepository` with
`initialState = AuthState.SignedIn(...)`, `signOutError = AuthError.NetworkUnavailable`, and
`signOutClearsSessionAnyway = true`. Assert `result.isFailure` AND all three clear/reset call
counts are `1` (not `0`, unlike the existing "signOut fails" test, which represents the coupled
case where the session stays signed in).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- None required — pure test addition, no behavior change.

---

## Phase 2: Real-DB test for the local cascade delete's order and rollback

### Overview

Prove, against a real Room database, that `deleteHabitAndCheckIns()` soft-deletes check-ins
before the habit, and that a failure partway through the transaction leaves no partial delete.

### Changes Required:

#### 1. New integration test with call-order-recording DAO wrappers

**File**: `app/src/test/java/pl/luczka/todaywas/data/local/api/LocalHabitDataSourceIntegrationTest.kt`
(new, sibling of `LocalHabitDataSourceImplTest.kt`, naming mirrors `SyncMergeIntegrationTest.kt`)

**Intent**: Prove the real, currently-correct order and transactionality — not a fake's
approximation of it.

**Contract**: Robolectric-backed, real in-memory Room DB (same builder pattern as
`SyncMergeIntegrationTest.kt`). Two small private test-only classes, `RecordingHabitDao` and
`RecordingHabitCheckInDao`, each wrapping the real DAO obtained from the real DB and delegating
every call to it, appending a tag (`"habit"` / `"check-in"`) to a shared `MutableList<String>`
before delegating. Construct `LocalHabitDataSourceImpl` with these two wrappers and the real
`RoomTransactionRunner(db)`.

- **Order + rollback test**: insert a habit and one of its check-ins via the real DAOs, call
  `deleteHabitAndCheckIns()`, and assert: the call-order log's `"check-in"` entries always precede
  their paired `"habit"` entries; the result is `Result.success`; both rows are soft-deleted
  (`deletedAt` set) when read directly from the real DB.
- **Mid-cascade failure test**: same setup, but `RecordingHabitDao.softDeleteById` throws after
  recording its call (simulating a failure on the *second* step of the transaction). Because
  `safeDbCall` retries once, expect the call-order log to show two check-in/habit pairs (one per
  attempt). Assert the result is `Result.failure`, and — read directly from the real DB — both the
  habit and the check-in are still present with `deletedAt == null` (the transaction rolled back
  completely on both attempts, no partial delete).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- None required — pure test addition over already-correct production code, no behavior change.

---

## Phase 3: Remote GC purge order regression test

### Overview

Prove `HabitRepositoryImpl.syncWithRemote()`'s GC purge always calls the check-in remote purge
before the habit remote purge, using a shared call-order log across the two existing fakes.

### Changes Required:

#### 1. `FakeRemoteHabitDataSource` and `FakeRemoteHabitCheckInDataSource` gain an optional shared log

**File**: `app/src/test/java/pl/luczka/todaywas/data/remote/api/FakeRemoteHabitDataSource.kt`,
`app/src/test/java/pl/luczka/todaywas/data/remote/api/FakeRemoteHabitCheckInDataSource.kt`

**Intent**: Let a test observe the relative order of purge calls across two independently-faked
data sources.

**Contract**: Add an optional constructor param `callOrderLog: MutableList<String>? = null` to
both. In each `purgeDeletedBefore`, append `"habit"` / `"check-in"` to the log (if non-null)
before doing anything else. Default `null` preserves every existing test's behavior unchanged.

#### 2. New `HabitRepositoryImplTest` case for purge order

**File**: `app/src/test/java/pl/luczka/todaywas/data/repository/HabitRepositoryImplTest.kt`

**Intent**: Prove the ordering, not just that both sides eventually get purged (the existing test
at lines 474-512 only asserts the habit-side call count).

**Contract**: New test alongside the existing GC test: construct both a tombstoned, aged-out habit
*and* a tombstoned, aged-out check-in referencing it (locally and remotely), pass a shared
`callOrderLog` into both `FakeRemoteHabitDataSource` and `FakeRemoteHabitCheckInDataSource`, call
`syncWithRemote()`, and assert the log reads `["check-in", "habit"]` (check-in purge strictly
before habit purge).

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- None required — pure test addition over already-correct production code, no behavior change.

---

## Testing Strategy

### Unit Tests:

- `SignOutUseCase` decoupled-scenario test (Phase 1)
- Remote GC purge order test (Phase 3)

### Integration Tests:

- Real-DB local cascade order + rollback test (Phase 2) is the integration layer for this plan.

### Manual Testing Steps:

None — all three phases are pure test additions over already-correct, unchanged production code.

## Performance Considerations

None — no production code changes.

## Migration Notes

None — no schema or data migration involved.

## References

- Research: `context/changes/testing-cascade-account-boundary-locks/research.md`
- Rollout strategy: `context/foundation/test-plan.md` §2 (risks #4, #6), §3 (Phase 2)
- Prior phase's precedent for real-DB integration tests: `context/changes/testing-sync-deletion-critical-path/plan.md` Phase 3, `SyncMergeIntegrationTest.kt`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Fix FakeAuthRepository and lock in the sign-out clearing gate

#### Automated

- [ ] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 1.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 1.3 Debug build succeeds: `./gradlew.bat assembleDebug`

### Phase 2: Real-DB test for the local cascade delete's order and rollback

#### Automated

- [ ] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 2.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 2.3 Debug build succeeds: `./gradlew.bat assembleDebug`

### Phase 3: Remote GC purge order regression test

#### Automated

- [ ] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 3.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 3.3 Debug build succeeds: `./gradlew.bat assembleDebug`
