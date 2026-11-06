# Cascade & Account-Boundary Regression Locks — Plan Brief

> Full plan: `context/changes/testing-cascade-account-boundary-locks/plan.md`
> Research: `context/changes/testing-cascade-account-boundary-locks/research.md`

## What & Why

Rollout Phase 2 of `context/foundation/test-plan.md`. Both risks (#4: cross-account data leak on
sign-out; #6: cascade-delete/GC-purge ordering) turned out to already be correctly handled in
production — this plan closes the gap between "correct today" and "protected against silently
regressing."

## Starting Point

`SignOutUseCase` already clears local data based on the observed auth-state transition, not the
network result — sound for the real trigger condition. `LocalHabitDataSourceImpl`'s cascade delete
is already transactional and correctly ordered; the remote GC purge is already ordered
check-ins-before-habits, matching a live-verified `NO ACTION` FK. None of this is protected by a
test that would fail if it regressed — in two of three cases because the existing test doubles are
structurally incapable of expressing the failure mode.

## Desired End State

A regression in either area — re-coupling sign-out clearing to the network result, breaking or
reordering the cascade delete's transaction, or reordering the remote GC purge — fails a test
immediately instead of shipping silently.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Risk #4 fix scope | Test-only, no production change | Research confirmed the production logic is already correct for the real trigger condition | Research |
| FakeAuthRepository extension | One additive boolean flag | Minimal, backward-compatible — every existing test's setup stays unchanged | Plan |
| Mid-cascade-failure test strategy | Real Room DB, not the fake TransactionRunner | The fake has no rollback semantics; only a real DB proves genuine transactional protection | Plan |
| Call-order proof (local) | Thin recording DAO wrappers delegating to real DAOs | Combines real transactional semantics with an explicit order assertion in one test | Plan |
| Call-order proof (remote) | Shared call-order log passed into both existing fakes | Minimal, explicit, directly proves cross-fake ordering | Plan |
| Offline sign-out case | Explicitly out of scope | Research confirmed this path never reaches the risky branch — already covered by an existing test | Research |
| Priority if time is tight | #4, then local cascade, then remote purge | Matches confirmed severity and blast radius | Plan |

## Scope

**In scope:** a `FakeAuthRepository` flag + `SignOutUseCase` test (Phase 1); a real-DB integration
test proving local cascade order and rollback (Phase 2); a shared-log-based remote purge order test
(Phase 3).

**Out of scope:** the "true offline sign-out" test case; a general `FakeAuthRepository`
result/state refactor; extending `FakeTransactionRunner` to simulate rollback; any production code
change; risk #5 (RLS ownership — a separate rollout phase).

## Architecture / Approach

Three independent, test-only phases. No production code changes anywhere in this plan — every
phase either extends a test double additively (Phases 1, 3) or adds a new real-DB integration test
following the exact pattern established in rollout Phase 1 (Phase 2).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Fix FakeAuthRepository + sign-out test | Regression lock for the account-boundary leak | None — additive, backward-compatible fake change |
| 2. Real-DB cascade order + rollback test | Regression lock for the local cascade delete | Low — new recording-wrapper pattern, first of its kind for DAOs |
| 3. Remote purge order test | Regression lock for the remote GC purge ordering | None — additive fake change, mirrors Phase 1's cascade fix pattern |

**Prerequisites:** None beyond repo access — no external services, no schema changes.
**Estimated effort:** ~1 session — each phase is a small, self-contained test addition.

## Open Risks & Assumptions

- The mid-cascade-failure test's expected call-order log accounts for `safeDbCall`'s one automatic
  retry (two attempts, not one) — if that retry behavior ever changes, this test's exact assertion
  would need updating alongside it.

## Success Criteria (Summary)

- A regression reverting `SignOutUseCase`'s clearing logic to gate on `result.isSuccess` fails a test.
- A regression breaking or reordering the local cascade delete's transaction fails a test against a real DB.
- A regression reordering or dropping the remote GC purge's check-ins-before-habits call fails a test.
