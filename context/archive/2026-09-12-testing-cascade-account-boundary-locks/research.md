---
date: 2026-09-12T10:44:34+02:00
researcher: Claude Sonnet 5
git_commit: 310b746dd457f2e0138ef19d0b0fbf0ed640247d
branch: feature/testing-cascade-account-boundary-locks
repository: janekluczka/TodayWas
topic: "Rollout Phase 2 grounding: cascade & account-boundary regression locks (test-plan.md §2 risks #4, #6)"
tags: [research, codebase, sign-out, cascade-delete, gc-purge, supabase-auth-sdk]
status: complete
last_updated: 2026-09-12
last_updated_by: Claude Sonnet 5
---

# Research: Rollout Phase 2 grounding — cascade & account-boundary regression locks

**Date**: 2026-09-12T10:44:34+02:00
**Researcher**: Claude Sonnet 5
**Git Commit**: 310b746dd457f2e0138ef19d0b0fbf0ed640247d
**Branch**: feature/testing-cascade-account-boundary-locks
**Repository**: janekluczka/TodayWas

## Research Question

Ground rollout Phase 2 of `context/foundation/test-plan.md` ("Cascade & account-boundary regression locks", risks #4 and #6): verify the current sign-out/cross-account data-clearing mechanism and the current cascade-delete/GC-purge ordering — with real code and live-schema evidence, not the historical bug reports these risks were originally sourced from — so `/10x-plan` designs tests against what actually exists today.

## Summary

Both risks needed correction after grounding — one premise was outright wrong, the other's trigger condition was narrower than described:

1. **Risk #6's "no transaction" premise is false.** `LocalHabitDataSourceImpl.deleteHabitAndCheckIns()` is already wrapped in `transactionRunner.runInTransaction {}` (check-ins soft-deleted before the habit), with an explicit comment explaining why. A live Supabase query confirms `habit_check_ins_habit_id_fkey.confdeltype = 'a'` (`NO ACTION`, not reverted to `CASCADE`), and `HabitRepositoryImpl.syncWithRemote()`'s GC purge still runs check-ins-before-habits, matching the `259524e` fix exactly. **The fix is fully intact** — the real gap is narrower: nothing regression-tests the transactional cascade order, a mid-cascade failure, or the remote purge ordering specifically.

2. **Risk #4's "network failure / airplane mode" framing doesn't match how the Supabase Auth SDK actually behaves.** Extracting and reading the `auth-kt` 3.7.0 SDK sources shows true offline sign-out (no connectivity at all) throws before the SDK clears anything — the local session is **preserved**, so there's nothing to leak. The real decoupling only happens when the auth server is reachable but returns a non-ignored error (e.g. a 5xx) — the SDK clears the local session, then rethrows. `SignOutUseCase.kt` already handles this correctly: it gates local-data clearing on the observed `AuthState` transition, not on `signOut()`'s `Result`. **The fix is sound for the real trigger condition.** The actual gap: `FakeAuthRepository` structurally couples "session transitions to SignedOut" with "the call succeeds" — it cannot construct the decoupled case at all, so no existing test can fail if a future regression re-couples clearing to `result.isSuccess`.

## Detailed Findings

### Risk #4 — sign-out data-clearing mechanism

- The historical commit (`8a94620`)'s fix lived in `AccountViewModel` as a reactive `observeAuthState()` collector. That collector **no longer exists** — `AccountViewModel`'s `init` block (`AccountViewModel.kt:66-71`) now only maps `AuthState` into UI state.
- The clearing logic was relocated into `SignOutUseCase.kt:21-31`: it reads `observeAuthState().first()` before and after calling `authRepository.signOut()`, and clears `journalRepository`/`habitRepository`/resets `onboardingRepository`'s sync flag **only if** the state genuinely transitioned `SignedIn → SignedOut` — regardless of the returned `Result`. Same guarantee as the original fix, different layer.
- `AuthRepositoryImpl.signOut()` (`AuthRepositoryImpl.kt:55`) is a thin wrapper: `authCall { supabase.auth.signOut() }`. All session-clearing behavior lives inside the Supabase SDK itself.
- Extracted `auth-kt-android-3.7.0-sources.jar`, read `AuthImpl.signOut()` directly: local session clearing (`clearSession()`) happens in three sub-cases — (a) no session was even present, clears trivially; (b) server returns one of `SIGN_OUT_IGNORE_CODES` (401/403/404), clears and returns success; (c) server returns any other error, clears **then rethrows** (this is the real decoupled case: local cleared, `Result.failure` returned). A genuine network/connectivity exception (not an HTTP response at all) is thrown *before* any of this — `clearIfValidScope()` never runs, session is preserved.
- **Test-coverage gap, confirmed structural**: `FakeAuthRepository.signOut()` (`app/src/test/java/pl/luczka/todaywas/domain/repository/FakeAuthRepository.kt:55-61`) only sets `state.value = AuthState.SignedOut` in the branch where `signOutError == null` — it is impossible to construct "state transitions to SignedOut" simultaneously with "Result.isFailure" using this fake. Every existing "signOut fails" test (`SignOutUseCaseTest.kt` lines 45-73, `AccountViewModelTest.kt` line 353) is therefore indistinguishable, under the fake, from "session stays SignedIn" — a regression back to gating clearing on `result.isSuccess` would pass the entire current suite undetected.

### Risk #6 — cascade delete and GC purge ordering

- `LocalHabitDataSourceImpl.deleteHabitAndCheckIns()` (`LocalHabitDataSourceImpl.kt:44-63`): check-ins soft-deleted first, then the habit, inside `transactionRunner.runInTransaction {}` — already transactional, with a comment explaining why ("a habit is never left with only some of its check-ins deleted... if the second delete fails").
- `LocalHabitDataSourceImpl.purgeDeletedBefore()` (lines 95-102) mirrors the same check-ins-before-habit order locally, with a comment noting this isn't locally required (no `@ForeignKey` on the Room entities) but kept consistent with the remote ordering constraint.
- `HabitRepositoryImpl.syncWithRemote()`'s GC section (lines 150-161): `remoteHabitCheckInDataSource.purgeDeletedBefore(...)` runs before `remoteHabitDataSource.purgeDeletedBefore(...)`, same comment as introduced in `259524e`. Diffed against `259524e`'s own change — unchanged except cosmetic line-wrapping.
- **Live schema check** (via Supabase MCP, direct query against `pg_constraint`): `habit_check_ins_habit_id_fkey.confdeltype = 'a'` (`NO ACTION`). Migration history since the `259524e`-corresponding `drop_habit_check_ins_cascade` migration shows no later migration touching this constraint.
- **Test-coverage gap, confirmed**: no test asserts DAO call order for the local cascade (the existing test only checks both tables end up empty); no test simulates a mid-cascade failure (the `FakeHabitDao`/`FakeHabitCheckInDao` failure-injection pattern isn't wired for the soft-delete methods); `HabitRepositoryImplTest.kt`'s only GC test (lines 474-512) asserts on the habit-side purge call count only — it never constructs a scenario with a check-in still referencing the habit and never asserts anything about the check-in-side purge call or its ordering relative to the habit-side one.

## Code References

- `app/src/main/java/pl/luczka/todaywas/domain/usecase/SignOutUseCase.kt:21-31` — the actual (procedural, not reactive) clearing gate
- `app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt:314-327` — `onSignOutClicked()`, no clearing logic left here
- `app/src/main/java/pl/luczka/todaywas/data/repository/AuthRepositoryImpl.kt:55-65` — `signOut()`/`authCall()`
- `app/src/test/java/pl/luczka/todaywas/domain/repository/FakeAuthRepository.kt:55-61` — the fake that structurally can't construct the decoupled scenario
- `app/src/main/java/pl/luczka/todaywas/data/local/api/LocalHabitDataSourceImpl.kt:44-63,95-102` — transactional cascade delete + purge ordering
- `app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt:150-161` — remote GC purge ordering
- `app/src/test/java/pl/luczka/todaywas/data/repository/HabitRepositoryImplTest.kt:474-512` — the one GC test, habit-side only
- `app/src/test/java/pl/luczka/todaywas/data/local/api/LocalHabitDataSourceImplTest.kt:376-421` — the one cascade test, no order/failure assertions

## Architecture Insights

- The sign-out fix migrated from a ViewModel-level reactive collector (as originally shipped in `8a94620`) to a use-case-level procedural before/after state check, with an identical semantic guarantee. This is a reasonable refactor (keeps `AccountViewModel` from needing sign-out-specific data-clearing knowledge) but means anyone reading only the historical commit message would misdescribe where the fix lives today — confirms this project's own principle that `/10x-research` output, not historical commit notes, is ground truth for current code.
- The FK/cascade-ordering fix (`259524e`) is duplicated in three places (local cascade delete, local purge, remote purge) with matching comments at each — a deliberate, well-documented "ordering constraint enforced in application code because the local Room schema can't express the FK" pattern. This triples the surface a future refactor could silently break one instance of without the others catching it, which is exactly why a single order-asserting test at each layer is the right response, not one shared test.

## Historical Context (from prior changes)

- `context/archive/2026-08-10-account-creation-and-sync/reviews/impl-review.md` (F1) — the original bug report and fix, which research confirms was later *relocated*, not reverted, into `SignOutUseCase`.
- `context/changes/sync-strategy-rework/reviews/impl-review.md` (F1) — the FK-cascade finding that `259524e` fixed; research confirms the fix holds today via live schema query.
- `context/changes/testing-sync-deletion-critical-path/research.md` — the prior rollout phase's research; this document follows the same "verify, don't assume" pattern for risks #4 and #6 that phase's research applied to risks #1-#3, and found the same category of drift (a historical description no longer matching current code) twice in this phase's own two risks.

## Related Research

- `context/changes/testing-sync-deletion-critical-path/research.md` — Phase 1's grounding research (risks #1-#3).

## Open Questions

- None blocking. One nuance for `/10x-plan` to weigh: since true network-down sign-out doesn't trigger the decoupled case at all (session preserved, `wasSignedIn && isNowSignedOut` is false), a test simulating "signOut fails" must specifically simulate a reachable-but-erroring server response (session clears, then throws) rather than a generic network exception — `FakeAuthRepository` needs a way to express that distinction, which it currently cannot.
