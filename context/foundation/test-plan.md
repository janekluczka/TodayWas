# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan --refresh` when stale (see §8).
>
> Last updated: 2026-09-12

## 1. Strategy

Tests follow three non-negotiable principles for this project:

1. **Cost × signal.** The cheapest test that gives a real signal for the
   risk wins. Do not promote to an instrumented/e2e test because it "feels
   safer." Do not add a new test layer where the existing JUnit4 unit-test
   base already gives a deterministic signal.
2. **User concerns are first-class evidence.** Risks anchored in "the team
   is worried about X, and the failure would surface somewhere in area Y"
   carry the same weight as PRD lines or hot-spot data.
3. **Risks are scenarios, not code locations.** This plan documents *what
   could fail* and *why we believe it's likely* — drawn from documents,
   interview, and codebase *signal* (churn, structure, test base). It does
   NOT claim to know which line owns the failure. That knowledge is
   produced by `/10x-research` during each rollout phase. If the plan and
   research disagree about where the failure lives, research is the
   ground truth.

Hot-spot scope used for likelihood weighting: `app/src/main/java`,
`core/designsystem/src/main/java`, `supabase/functions/ai-proxy`.

## 2. Risk Map

The top failure scenarios this project must protect against, ordered by
risk = impact × likelihood. Risks are failure scenarios in user / business
terms, not test names. The Source column cites the *evidence that surfaced
this risk* — never a specific file as "where the failure lives" (that is
research's job, see §1 principle #3).

| # | Risk (failure scenario) | Impact | Likelihood | Source (evidence — not anchor) |
|---|---|---|---|---|
| 1 | No test proves a soft-deleted row (journal entry, habit, or check-in) actually reaches the sync push logic and is removed remotely — each link (local soft-delete, merge decision, push) is verified in isolation but never chained end-to-end, so a regression in any one link would go undetected | Medium | Low | research (`testing-sync-deletion-critical-path`) confirmed the deletion-push path works correctly today by design, but found no end-to-end test; interview Q2/Q3/Q4 originally raised this as a suspected live bug, which research disproved |
| 2 | When the first sync after account creation fails, the user is routed to the same success screen as when it succeeds — no error shown, no retry offered — so they believe their data is safely backed up when it silently isn't | High | Medium | interview Q1; PRD US-04 acceptance criteria ("no data lost or duplicated during upload"); research (`testing-sync-deletion-critical-path`) confirmed the completion step runs unconditionally regardless of sync result |
| 3 | The lock meant to serialize concurrent sync attempts does not actually work — each independent trigger point (app launch, account sync, onboarding sync, background worker) gets its own separate, uncoordinated lock instance, so two sync attempts can still run at the same time and race, risking a newer edit being overwritten by a stale snapshot | High | High | interview Q1/Q4 ("hit or miss"); research (`testing-sync-deletion-critical-path`) confirmed the lock is structurally unable to serialize across trigger points — a new, previously-undocumented finding |
| 4 | A regression that re-couples local-data clearing on sign-out to the network result (instead of the actual observed session-state transition) would go completely undetected — no existing test can construct the one real scenario where they diverge (a reachable-but-erroring auth server, not a general network failure), so a stale "already synced" flag could again leak one account's local data into a different account on the same device | High | Low | research (`testing-cascade-account-boundary-locks`) confirmed the current fix is sound for the real trigger condition, but found the test fake structurally cannot construct the decoupled case; archive `2026-08-10-account-creation-and-sync/` impl-review — the original bug this guards against |
| 5 | RLS policies on `journal_entries`/`habits`/`habit_check_ins` are correctly configured (all 12 policies across 3 tables × 4 operations scope by `user_id = auth.uid()`, confirmed live) but have never been proven at runtime against an actual cross-user request — a future policy change or migration could silently break isolation with nothing to catch it | High | Low | research (`testing-rls-ownership-verification`) confirmed live policy correctness via direct `pg_policies` query and confirmed no service-role bypass exists anywhere in the app; `entry-delete` plan's own signed-in E2E verification was left unchecked; abuse/IDOR lens (multi-tenant data + auth) |
| 6 | The correct, transactional local cascade-delete order (check-ins before habit) and the remote GC purge order (check-ins before habits, required by the live `NO ACTION` FK) are unprotected by any regression test — a future refactor could silently break the transaction, reorder the local cascade, or reorder/drop the remote purge call without any test failing | Medium | Low | research (`testing-cascade-account-boundary-locks`) confirmed the fix is fully intact today (transactional cascade, live schema query confirms the FK), but found no test asserts call order, a mid-cascade failure, or the remote purge ordering; hot-spot dir `ui/habit` (top churn, 30d) |

### Risk Response Guidance

| Risk | What would prove protection | Must challenge | Context `/10x-research` must ground | Likely cheapest layer | Anti-pattern to avoid |
|------|-----------------------------|----------------|--------------------------------------|-----------------------|-----------------------|
| #1 | A soft-deleted row of each type (journal entry, habit, check-in), created via the real local DB, is provably included in the next push decision and does not resurrect after a subsequent apply — exercised through the real chain, not hand-built fixtures | "Each link passes its own isolated test, so the chain must work" — composition isn't verified just because the parts are | Confirmed today: push-eligibility is decided by a deletion flag, not a timestamp comparison — the end-to-end test should assert on that observable behavior, not re-derive the mechanism | integration test chaining the real local data source through to the merge decision | A test that only re-asserts the merge function's own unit-test scenarios with different fixture data (no new signal) |
| #2 | A failed sync during the account-creation confirm step surfaces a visible error/retry to the user, and does not advance to the same terminal state as success | "The confirm button's job is done once it calls sync" — the completion step must condition on the result, not just fire once the call returns | The exact completion call and what currently makes it run unconditionally; the "already synced" flag's success-gating (which does work correctly today and should stay covered) | ViewModel-level unit test simulating a failed `syncLocalData()` during confirm | Testing only the happy-path confirm flow (already covered) instead of the failure branch |
| #3 | Two sync attempts triggered from different parts of the app at the same time cannot both proceed unserialized — a second concurrent attempt waits or is skipped, never races | "A lock guarding sync logic guarantees only one sync runs at a time" — true only if every trigger point shares the same lock instance, which must be verified, not assumed | Confirmed today: the lock is scoped per call site rather than shared — the fix (if planned) and its test both need to target the sharing mechanism, not the lock's own logic (which is fine in isolation) | integration/DI-level test proving two trigger points either share a lock instance or are otherwise serialized | Testing the lock's `withLock` behavior in isolation (already effectively proven fine) instead of proving cross-call-site coordination |
| #4 | No previously-signed-in account's local data is visible or uploadable after a sign-out where the local session cleared but the network call still reported failure | "A test that makes signOut() fail also naturally covers this" — it doesn't, if the fake ties call failure to the session staying signed-in, the exact decoupled case never gets exercised | Confirmed today: the real trigger is a reachable-but-erroring auth response (session clears, then the error is rethrown), not a general network failure (which never clears the session at all) — the test double must be able to express that distinction | unit test extending the existing sign-out use-case test suite, with a fake capable of representing "cleared AND failed" as one state | A fake/mock where "call fails" and "session stays signed in" are the same knob — asserting the happy path and the coupled-failure path without ever exercising the decoupled one |
| #5 | An authenticated user A can never read, modify, or delete a row owned by user B, across all three tables and all four operations — proven by an actual request, not by reading policy text | "The policy text says `user_id = auth.uid()`, therefore it's correct" — correct SQL text doesn't prove PostgREST + Supabase actually enforce it at runtime for a real authenticated request | Confirmed today: policy definitions are correct on all three tables including DELETE; no service-role or other bypass path exists anywhere in the app — the test must exercise the real enforcement path (two real users, real requests), not re-derive what's already confirmed in policy text | Postgres/RLS-level test (first of its kind in this project — no existing tooling to extend) | Testing only "an unauthenticated request is rejected" (that's auth, not ownership); re-querying `pg_policies` instead of making an actual cross-user request |
| #6 | Habit cascade delete stays consistent locally and remotely even under a mid-cascade failure; the remote GC purge order never regresses | "This is already correct, so there's nothing to test" — correctness today doesn't survive a future refactor without a test that would fail if the order or transaction changed | Confirmed today: the local cascade is already transactional and ordered check-ins-first; the remote purge is already ordered check-ins-first per the live FK constraint — the test should lock in this specific order and the transaction boundary, not re-verify the fix exists | repository/data-source-level test (call order + fake-failure-mid-cascade) | Testing that both tables end up empty without asserting the order they were touched in, or that a mid-cascade failure leaves no partial state |

## 3. Phased Rollout

Each row is a discrete rollout phase that will open its own change folder
via `/10x-new`. Status moves left-to-right through the values below; the
orchestrator updates Status as artifacts appear on disk.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|---|---|---|---|---|---|
| 1 | Sync & deletion critical-path coverage | Prove the account-upload and soft-delete-sync paths don't silently lose data | #1, #2, #3 | unit + integration | complete | `context/changes/testing-sync-deletion-critical-path/` |
| 2 | Cascade & account-boundary regression locks | Lock in two already-fixed-but-unprotected bugs so they can't silently regress | #4, #6 | unit + integration | complete | `context/changes/testing-cascade-account-boundary-locks/` |
| 3 | RLS ownership verification | Verify cross-user isolation on all 3 tables, including the new DELETE policies | #5 | Postgres/RLS-level | planned | `context/changes/testing-rls-ownership-verification/` |

**Status vocabulary** (fixed — parser literals): `not started` → `change opened` → `researched` → `planned` → `implementing` → `complete`.

## 4. Stack

The classic test base for this project. Recommendations below are grounded
in local manifests/configs plus the MCP/tools actually exposed in the
current session.

| Layer | Tool | Version | Notes |
|---|---|---|---|
| unit + integration | JUnit4 | via AGP/Gradle defaults | 50 existing test files across `data/`, `domain/`, `ui/` — `meaningful` base |
| local DB integration | Room + Robolectric | per `libs.versions.toml` | Used today for DAO/database tests (e.g. `HabitCheckInDaoTest.kt`) |
| instrumented / e2e | AndroidX Test + Espresso | per `libs.versions.toml` | Configured but unused beyond the stock `ExampleInstrumentedTest.kt` |
| Postgres / RLS | none yet — see Phase 3 | n/a | No policy-level test tooling exists today |
| (optional) AI-native | none available this session | n/a | No Context7/Exa/Playwright MCP in this session; not recommended until a real gap justifies it |

**Stack grounding tools (current session):**
- Docs: Supabase docs MCP available — checked for RLS/Postgres testing guidance relevance; checked: 2026-09-11
- Search: generic WebSearch available, no Exa.ai; checked: 2026-09-11
- Runtime/browser: none available — not applicable to a native Android app; checked: 2026-09-11
- Provider/platform: Supabase MCP available (`execute_sql`, `get_advisors`) — relevant to Phase 3's RLS verification; checked: 2026-09-11

## 5. Quality Gates

The full set of gates that must pass before a change reaches production.
"Required for §3 Phase <N>" means the gate is enforced once that rollout
phase lands; before that, the gate is `planned`.

| Gate | Where | Required? | Catches |
|---|---|---|---|
| ktlintCheck | local + CI (`.github/workflows/ci.yml`) | required | style/syntax drift |
| testDebugUnitTest | local + CI | required; expanded scope after §3 Phase 1/2 | logic regressions, including the sync/deletion risks above |
| assembleDebug | CI | required | compile-time breakage |
| RLS/ownership check | manual via Supabase MCP, or scripted | planned — required after §3 Phase 3 | cross-user data access |

## 6. Cookbook Patterns

How to add new tests in this project. Each sub-section is filled in once
the relevant rollout phase ships; before that, the sub-section reads
"TBD — see §3 Phase <N>."

### 6.1 Adding a unit test (ViewModel / use case / domain util)

- **Location**: `app/src/test/java/pl/luczka/todaywas/<mirrors main package>/`.
- **Naming**: `<ClassUnderTest>Test.kt`, test methods backtick-quoted
  `` `should [outcome] when [scenario]` ``, bodies structured as
  Arrange/Act/Assert (see `context/foundation/lessons.md`).
- **Reference test**: `app/src/test/java/pl/luczka/todaywas/domain/util/EditWindowTest.kt`.
- **Run locally**: `./gradlew.bat testDebugUnitTest`.

### 6.2 Adding a local DB integration test (Room DAO)

- **Location**: `app/src/test/java/pl/luczka/todaywas/data/local/dao/`.
- **Approach**: in-memory Room database via Robolectric, not a mocked DAO.
- **Reference test**: `app/src/test/java/pl/luczka/todaywas/data/local/dao/HabitCheckInDaoTest.kt`.
- **Run locally**: `./gradlew.bat testDebugUnitTest`.

### 6.3 Adding a sync/merge test

- **Pure decision-function test** (no DB): construct `SyncMeta` fixtures directly and assert on
  `mergeForSync()`'s `pushIds`/`applyIds`. **Reference test**:
  `app/src/test/java/pl/luczka/todaywas/data/util/SyncMergeTest.kt`.
- **End-to-end chain test** (real DAO → real mapper → real merge decision, for a specific
  entity-level behavior like soft-delete push-eligibility): build a real Robolectric Room DB
  (same builder as 6.2), perform the write, read back, map via the entity's real `toSyncMeta()`,
  then call the real `mergeForSync()` — don't hand-build `SyncMeta` for this style, the point is
  proving the whole chain, not the decision function in isolation. **Reference test**:
  `app/src/test/java/pl/luczka/todaywas/data/util/SyncMergeIntegrationTest.kt`.
- **ViewModel-level sync failure/success handling** (e.g. account-creation upload): use the
  existing `Fake*Repository`'s `syncWithRemoteResult` field to inject success/failure, assert on
  emitted `UiEvent`s and resulting `UiState`. **Reference test**:
  `app/src/test/java/pl/luczka/todaywas/ui/account/AccountViewModelTest.kt`.
- **Run locally**: `./gradlew.bat testDebugUnitTest`.

### 6.4 Adding an RLS ownership test

- TBD — see §3 Phase 3 for the Postgres-level cross-user isolation
  pattern this phase establishes.

### 6.5 Per-rollout-phase notes

- **Phase 1** (`testing-sync-deletion-critical-path`): found `HabitRepositoryImpl`/
  `JournalRepositoryImpl` were unscoped in Hilt, so the `syncMutex` meant to serialize concurrent
  syncs was silently getting a fresh instance per injection site — fixed with `@Singleton` on
  `RepositoryModule`'s bindings. Also found `AccountViewModel`/`OnboardingAccountSetupViewModel`
  proceeded to the success screen even when the account-creation sync failed — fixed to surface
  an error and stay on the review step. If you're adding a new Hilt-bound repository or use case
  that holds coordination state (a `Mutex`, a cache, etc.), check whether it needs `@Singleton` —
  it's easy to add the state and forget the scope.

## 7. What We Deliberately Don't Test

Exclusions agreed during the rollout (Phase 2 interview, Q5). Future
contributors should respect these unless the underlying assumption changes.

- **AI-assist prompt/tone quality** — inherently fuzzy, not a correctness
  bug; not something a deterministic test should score. Re-evaluate if
  the AI-assist proxy takes on a new correctness-sensitive responsibility
  (e.g. moderation). (Source: Phase 2 interview Q5.)
- **Onboarding flow** — already stable, low risk of silent regression
  relative to the sync/deletion risks above. Re-evaluate if onboarding
  starts writing data that sync/delete logic depends on. (Source: Phase 2
  interview Q5.)

## 8. Freshness Ledger

- Strategy (§1–§5) last reviewed: 2026-09-11
- Stack versions last verified: 2026-09-11
- AI-native tool references last verified: 2026-09-11 (none available this session)

Refresh (`/10x-test-plan --refresh`) when:

- a new top-3 risk surfaces from the roadmap or archive,
- a recommended tool's `checked:` date is older than three months,
- the project's tech stack changes (new framework, new test runner),
- §7 negative-space no longer matches what the team believes.
