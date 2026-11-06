# Sync & Deletion Critical-Path Coverage — Plan Brief

> Full plan: `context/changes/testing-sync-deletion-critical-path/plan.md`
> Research: `context/changes/testing-sync-deletion-critical-path/research.md`

## What & Why

This is rollout Phase 1 of `context/foundation/test-plan.md` — fixing and test-locking the three
sync/deletion risks grounded by research. Two turned out to be real, previously-unknown-in-this-
shape production bugs; the third turned out not to be a bug at all, just a missing regression
test.

## Starting Point

The app's sync layer (`HabitRepositoryImpl`/`JournalRepositoryImpl`) has a `Mutex` meant to
prevent two concurrent syncs from racing, an account-creation flow that uploads local data on
sign-up, and a soft-delete/tombstone merge algorithm (`SyncMerge.kt`) that decides what gets
pushed to remote. Research found the mutex doesn't actually work, a sync failure during account
creation is invisible to the user, and the deletion-sync path is correct but untested end-to-end.

## Desired End State

Two sync paths can never race each other regardless of which part of the app triggered them. A
user whose account-creation sync fails sees an error and can retry, instead of believing their
data is safely backed up when it silently isn't. A real, DAO-backed test proves soft-deleting any
of the three entity types results in that deletion reaching the sync push logic.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Risk #3 (broken sync lock) | Fix + test | Confirmed live, high-impact data-loss race; the fix is a two-annotation change | Plan |
| Risk #2 (silent sync failure) | Fix + test | Directly closes the user's own top-stated worry (losing data on sync) | Plan |
| Risk #1 (soft-delete sync) | Test only | Research disproved it as a live bug — nothing to fix, just close the test gap | Research |
| Risk #3 verification method | Reflection-based annotation check, not a real Hilt instrumented test | Project has zero existing Hilt test infra; standing it up for one assertion is disproportionate | Plan |
| Risk #2 fix scope | Only the explicit "confirm sync" path, not the fire-and-forget background path | The background path only fires when there's no data at stake (already synced, or empty) | Plan |
| Risk #2 failure UX | Reuse existing `ShowError` event + generic error string, stay on the review step | Matches the app's existing error-handling idiom exactly, zero new UI | Plan |
| Priority if time is tight | #3, then #2, then #1 | Matches confirmed severity — #3 and #2 are live bugs, #1 is a test-only gap | Plan |

## Scope

**In scope:** `@Singleton`-scoping the two sync repositories; a reflection test locking that in;
fixing `AccountViewModel`/`OnboardingAccountSetupViewModel`'s silent sync-failure path plus tests;
a new end-to-end integration test for soft-delete → merge → push across all 3 entity types;
updating `test-plan.md` §6's cookbook.

**Out of scope:** Hilt instrumented test infrastructure; the fire-and-forget background-sync
branch; `OnboardingAccountSetupViewModel.finish()`'s separate completion-failure gap;
`SyncMergeTest.kt` tie-break/both-tombstoned edge cases; risks #4-#6 from `test-plan.md` (separate
future rollout phases).

## Architecture / Approach

Three independent phases, each touching a small, well-isolated surface: one DI module file plus
one new test (Phase 1); two ViewModel methods plus two tests, reusing an existing error-handling
pattern verbatim (Phase 2); one new integration test composing already-correct production code
(Phase 3). No new architecture, no data model changes.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Fix the sync lock's DI scoping | `@Singleton` on both repository bindings + a reflection test | None major — two-line fix, well-understood Hilt mechanism |
| 2. Fix silent sync failure on account creation | Conditional error handling in both account-setup ViewModels + tests | Low — reuses an existing, already-tested error pattern |
| 3. End-to-end soft-delete → sync-push test | New integration test, all 3 entity types, no behavior change | None — pure test addition over verified-correct code |

**Prerequisites:** None beyond repo access — no external services, no schema changes.
**Estimated effort:** ~1 session — Phase 1 is a few minutes of code plus one test; Phase 2 is two
small, symmetric ViewModel changes plus tests; Phase 3 is one new test file.

## Open Risks & Assumptions

- Phase 1's manual verification (two overlapping syncs not crashing) is a sanity check, not proof
  of the fix — the real guarantee comes from Hilt's scope semantics, which are well-established
  and not something this plan re-verifies at the framework level.
- If a future change adds a new Hilt injection site for `JournalRepository`/`HabitRepository`,
  the `@Singleton` scope automatically covers it — no action needed, but worth knowing why.

## Success Criteria (Summary)

- Two concurrent sync attempts, triggered from any two of the four call sites, can never both
  proceed unserialized.
- A user is never shown a success state when their account-creation sync actually failed.
- Every entity type's soft-delete-to-push path has a real, DAO-backed regression test.
