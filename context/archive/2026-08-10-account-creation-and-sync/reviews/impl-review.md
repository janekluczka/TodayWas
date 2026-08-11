<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Account Creation and Sync

- **Plan**: context/changes/account-creation-and-sync/plan.md
- **Scope**: Full plan (Phase 1-5)
- **Date**: 2026-08-11
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 2 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Automated verification (re-run at review time)

- `./gradlew.bat testDebugUnitTest` — PASS (all up-to-date, green; includes the later unrelated `test-refactor` AAA rewrite of the same test files, still green)
- `./gradlew.bat ktlintCheck` — PASS
- `./gradlew.bat assembleDebug` — PASS

## Plan drift summary

24/24 planned Changes-Required items across Phases 1-5 verified MATCH by direct file read (Room entities, DAOs, repositories, domain models, UI/nav chain UUID propagation; Postgrest wiring, `CoroutineScopeModule`, `AuthRepository.currentUserId()`, remote DTOs/mappers with `@SerialName` on every field, remote data sources + `RepositoryModule` bindings, sync capability; `hasSyncedLocalData` flag, `ClearSyncedLocalDataUseCase`, sign-out wiring; `LocalDataSummary`, `DataSyncReviewContent`, `AccountStep`/`AccountSubStep` wiring, PRD update; launch-time pull sync). Live Supabase schema confirmed via MCP: `journal_entries`/`habits`/`habit_check_ins` exist with RLS and `user_id = auth.uid()` policies. The Phase 5 trigger's relocation from `RootViewModel` to `MainViewModel` (explicitly requested by you mid-implementation) landed correctly — `RootViewModel.kt` is untouched by this feature.

## Findings

### F1 — Sign-out failure can leave a stale `hasSyncedLocalData` flag, risking cross-account data leak

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt:265-279
- **Detail**: `onSignOutClicked()` only calls `clearSyncedLocalData()` inside the `result.isSuccess` branch of `signOut()`. Supabase-style auth SDKs commonly clear the local session before/regardless of whether the server-side token-revocation network call succeeds (local-logout-first is the common mobile pattern). If `signOut()` fails purely on the network leg while the local session is already gone, the user ends up locally signed out, but `hasSyncedLocalData` stays `true` and Room's journal/habit/check-in tables stay populated. If a *different* account then signs into the same device, `proceedAfterAuthSuccess()` (AccountViewModel.kt:216-227) sees the stale `hasSyncedLocalData=true`, skips the `DATA_SYNC_REVIEW` confirmation step entirely, and fire-and-forgets `syncLocalData()` — silently pushing the previous account's leftover local data to Postgres tagged with the *new* account's `user_id`, with no user confirmation.
- **Fix A ⭐ Recommended**: Gate the local-data clear on the actual `AuthState` transition to `SignedOut`, observed reactively, instead of `signOut()`'s returned `Result`.
  - Strength: Correctly handles the SDK-clears-locally-even-on-network-failure case — the clear always fires whenever the user is actually locally signed out, which is what `hasSyncedLocalData` is meant to track, and it would also cover any future sign-out entry point.
  - Tradeoff: Introduces a persistent `AuthState` observer at a wider scope than exists today; needs a decision on where it lives so the clear fires exactly once per sign-out transition (not on every re-emission).
  - Confidence: MEDIUM — the exact local-vs-remote ordering in this Supabase Kotlin SDK version wasn't traced against its actual source in this review; if the SDK never clears the session locally on a failed sign-out, this specific failure mode doesn't occur, but the fix is safe either way.
  - Blind spot: Haven't verified `io.github.jan.supabase`'s `Auth.signOut()` implementation directly.
- **Fix B**: Call `clearSyncedLocalData()` unconditionally in `onSignOutClicked()`, regardless of `signOut()`'s `Result`.
  - Strength: One-line change, keeps the clear tied to the explicit user action rather than a new reactive observer.
  - Tradeoff: Doesn't help if a session is cleared through some other future path (e.g. expiry) that doesn't go through this button; also clears local data even on sign-out failures unrelated to session state.
  - Confidence: HIGH — trivial to verify.
  - Blind spot: Doesn't address non-button sign-out paths.
- **Decision**: FIXED via Fix A — `AccountViewModel.init` now tracks the previous `AuthState` and calls `clearSyncedLocalData()` on a `SignedIn`→`SignedOut` transition, decoupled from `signOut()`'s `Result`. `onSignOutClicked()`'s direct call removed. `FakeAuthRepository.signOut()` updated to emit `AuthState.SignedOut` on success (mirroring the real SDK's session-status flow) so the reactive path is testable; `AccountViewModelTest` unchanged in assertions, still green.

### F2 — Launch-time `syncWithRemote()` pull can race a concurrent local edit and silently revert it

- **Severity**: ⚠️ WARNING
- **Impact**: 🔬 HIGH — architectural stakes; think carefully before deciding
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:135-139 (via HabitRepositoryImpl.kt:89-102 / JournalRepositoryImpl.kt's `syncWithRemote()`)
- **Detail**: `MainViewModel`'s launch-time sync calls `syncWithRemote()` unconditionally whenever Main loads while signed in. `syncWithRemote()` snapshots local rows, pushes them, then fetches remote and `REPLACE`-upserts back into Room, with no lock or per-row timestamp guard against the independent per-write fire-and-forget pushes (`pushCheckInsInBackground` etc.). If a user edits a check-in at the same moment Main's sync is mid-flight, and the edit's own background push hasn't reached remote by the time the sync's `fetchAll()` runs, the sync's stale snapshot can be written back over the fresh local edit — the edit silently reverts with no error surfaced. The plan's "remote wins needs no per-row timestamp" reasoning (plan.md's Critical Implementation Details) assumes a write either fully lands before or fully lands after a sync pass; it doesn't cover a write landing *during* one, which is exactly what launch-time sync introduces.
- **Fix A ⭐ Recommended**: Add a lightweight per-repository `Mutex`, held for the duration of `syncWithRemote()`, with push-in-background using `tryLock`/skip-if-held so a bulk sync and an individual write-push never interleave.
  - Strength: Closes the exact race with a small, local, no-schema-change fix; a skipped push during sync isn't lost — the edit is still saved locally and will push on the next successful write or sync pass.
  - Tradeoff: Adds a bit of concurrency machinery to two repositories; needs verification it doesn't deadlock with the existing `safeDbCall`/`remoteCall` wrapping.
  - Confidence: MEDIUM — design is straightforward but unimplemented/untested in this review.
  - Blind spot: Haven't checked whether Room's own transaction guarantees already bound the worst outcome to "revert to stale value" rather than partial/corrupted writes (which they do — this is specifically about staleness, not corruption).
- **Fix B**: Accept as a known, narrow limitation of the plan's explicitly-chosen "remote wins, no per-row timestamp" design; document it rather than adding locking.
  - Strength: Zero code risk, keeps sync as simple as the plan intended; the window requires editing at the exact moment Main cold-starts a sync, which is rare for a single-device daily-journaling app.
  - Tradeoff: A real (if rare) bug remains live indefinitely — an edit made right after opening the app could silently vanish.
  - Confidence: MEDIUM — likelihood is a product judgment call, not something derivable from the code alone.
  - Blind spot: Unclear whether this scenario is covered by your planned manual regression pass (`manual-verification-pending.md`).
- **Decision**: FIXED via Fix A — added a private `syncMutex: Mutex` to both `JournalRepositoryImpl`/`HabitRepositoryImpl`. `syncWithRemote()` now runs its entire push+pull body inside `syncMutex.withLock { ... }`; `pushInBackground`/`pushHabitInBackground`/`pushCheckInsInBackground` use `syncMutex.tryLock()` and skip the push entirely if a sync is already in flight (the edit stays saved locally and is picked up by the next successful push or sync pass). Discussed with you first: confirmed the race is real (sync's *own* push of a stale snapshot can lose a race against a concurrent edit's push, then sync's pull writes the stale value back to Room) though narrow (once per `MainViewModel` init, ~1-2s network round trip, only affects edits to existing rows). Verified via `testDebugUnitTest`/`ktlintCheck`, both green.

### F3 — Redundant outer `runCatching` in push-in-background silently swallows `CancellationException`

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt:111-123 (and the equivalent in JournalRepositoryImpl.kt)
- **Detail**: `pushHabitInBackground`/`pushCheckInsInBackground` wrap `remoteXDataSource.upsert(...)` in `runCatching {}`, but `upsert()` already routes through `RemoteCall.kt`'s `remoteCall {}` helper, which catches all non-`CancellationException` exceptions internally and returns `Result.failure` — it never throws except `CancellationException`. The outer `runCatching` is redundant and also silently swallows `CancellationException`, a general coroutines anti-pattern (cancellation should always propagate). Currently low-risk since the `@ApplicationScope` scope's `SupervisorJob` is never cancelled anywhere in the codebase today, but it's a latent trap if that changes.
- **Fix**: Drop the outer `runCatching` (the call already returns `Result<Unit>` safely), or explicitly `catch (e: CancellationException) { throw e }` before any general catch.
- **Decision**: FIXED + ACCEPTED-AS-RULE: "A Result-returning wrapper should not be re-wrapped in another runCatching by its callers" (appended to `context/foundation/lessons.md`). Dropped the outer `runCatching` in `pushInBackground`/`pushHabitInBackground`/`pushCheckInsInBackground`; verified via `testDebugUnitTest`/`ktlintCheck`, both green.

### F4 — Unplanned shared `RemoteCall.kt` helper (benign scope addition)

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: app/src/main/java/pl/luczka/todaywas/data/repository/RemoteCall.kt
- **Detail**: The plan's Phase 2 item 5 said each remote data source impl should "wrap each Postgrest call in `runCatching`" individually. The actual implementation factors this into one shared `remoteCall(block)` helper reused by all three `Remote*DataSourceImpl` classes and by both repositories' `syncWithRemote()`. This is a reasonable DRY refactor, not called out in the plan text.
- **Fix**: No code change needed — note it in the plan as an addendum so the plan stays an accurate record of what was built.
- **Decision**: FIXED — added an "## Addenda" section to `plan.md` documenting `RemoteCall.kt` as an intentional DRY refactor of Phase 2 item 5's per-data-source `runCatching` contract.

### F5 — `AccountViewModel`'s `SyncConfirmClicked` routes through `postSyncAction`, not literally `step=SUCCESS` as the plan's contract text states

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt:49,216-263
- **Detail**: The plan's Phase 4 contract says `SyncConfirmClicked` sets `isSyncing = true`, syncs, then `step = SUCCESS`. The actual implementation introduces a `PostSyncAction` enum (`NAVIGATE_BACK` / `SHOW_SUCCESS` / `RETURN_HOME`) captured at entry, so a sign-in-triggered review correctly resolves to `NavigatedBack` (matching pre-existing sign-in behavior, which never showed a `SUCCESS` step even before this feature) while sign-up resolves to `SUCCESS`. This is a deliberate fix for an inconsistency in the plan's own contract text (sign-in and sign-up have different terminal transitions), not an accidental deviation — it was reasoned through and intentionally implemented this way.
- **Fix**: No action needed — the plan's Phase 4 contract text could be updated to describe `PostSyncAction` for future readers, but this is optional documentation cleanup, not a code fix.
- **Decision**: FIXED — added an "Implementation note (added during impl-review)" under Phase 4 item 3's Contract explaining the `PostSyncAction` pattern and why sign-in/sign-up don't share one terminal transition.
