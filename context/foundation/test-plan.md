# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan --refresh` when stale (see §8).
>
> Last updated: 2026-09-11

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
| 1 | A soft-deleted habit check-in never reaches the sync push logic, so the deletion either never propagates to another device or the row resurrects after a pull | High | High | interview Q2/Q3/Q4; commit history (`fix(sync)` note on `updatedAt` not bumped on soft-delete, confirmed still present); hot-spot dirs `data/local`, `data/repository` |
| 2 | First sync after account creation (local→remote bulk upload) silently drops or duplicates journal/habit data | High | Medium | interview Q1; PRD US-04 acceptance criteria ("no data lost or duplicated during upload"); archive `2026-08-10-account-creation-and-sync/` impl-review |
| 3 | Ongoing bidirectional sync merge overwrites or reverts a newer local edit with a stale remote snapshot | High | Medium | interview Q1/Q4 ("hit or miss"); archive `2026-08-10-account-creation-and-sync/` impl-review incident; archive `sync-strategy-rework` full conflict-metadata redesign; hot-spot dir `data/repository` (top churn, 30d) |
| 4 | A stale local "already synced" flag survives a sign-out whose remote session-revoke call failed, leaking one account's local data into a different account signed in on the same device | High | Low | archive `2026-08-10-account-creation-and-sync/` impl-review — real past bug, fix has no dedicated regression test |
| 5 | RLS policies on `journal_entries`/`habits`/`habit_check_ins` — including the DELETE policies added for per-item delete — don't actually scope by owning user, allowing cross-user read/write/delete | High | Low–Medium | `entry-delete` plan (new DELETE policies added, never independently verified); project privacy rule (CLAUDE.md); abuse/IDOR lens (multi-tenant data + auth) |
| 6 | Cascading habit delete (local two-step DAO cascade, no transaction) and the remote GC purge ordering fall out of sync, undoing a previously-verified fix | Medium | Low | interview Q3; commit history (remote FK-cascade fix, verified live but unprotected by a regression test); hot-spot dir `ui/habit` (top churn, 30d) |

### Risk Response Guidance

| Risk | What would prove protection | Must challenge | Context `/10x-research` must ground | Likely cheapest layer | Anti-pattern to avoid |
|------|-----------------------------|----------------|--------------------------------------|-----------------------|-----------------------|
| #1 | A soft-deleted check-in is included in the next push batch, and does not resurrect after a subsequent pull | "Row missing from the UI list means it's deleted" — it may just be filtered locally while still looking undeleted to the sync layer | The dirty-detection rule the push logic uses (timestamp-based); whether the same gap affects journal-entry and habit soft-delete, not just check-ins | unit (DAO) + integration (repository push-eligibility) | Testing only that `deletedAt` gets set, not that the row becomes push-eligible |
| #2 | Local row count equals remote row count after the one-time upload, with no drops and no duplicates, including a mid-batch failure case | "A 200 response means every row made it" — batch APIs can partially fail | The upload code path's batching/transactionality; when the "already synced" flag is allowed to flip to true | unit/integration test with a fake remote data source that fails mid-batch | Asserting the upload function was called, not that data actually survived |
| #3 | The merge always keeps the newer edit regardless of which side (local or remote) is fresher | "Last-write-wins by wall-clock timestamp is safe" (clock trust); "the existing sync lock covers every race" | The merge helper's conflict rule (field-level vs row-level); lock coverage across both the pull-merge and push-in-background paths | unit test on the merge helper with constructed local/remote timestamp pairs | Asserting the merge "didn't crash" instead of that it picked the correct side |
| #4 | No previously-signed-in account's local data is visible or uploadable after a sign-out where the remote revoke call failed | "Sign-out returning success means local state is fully cleared" | The sign-out flow's state transitions; every path that can flip auth state without going through the same reset logic | ViewModel-level unit test, extending the existing account ViewModel test suite | Testing only the happy sign-out path, skipping the failed-revoke branch that was the actual historical bug |
| #5 | An authenticated user A can never read, modify, or delete a row owned by user B, across all three tables and all four operations | "RLS policies exist, therefore they're correct" — existence isn't correctness | The exact current policy definitions on all three tables, including the newly-added DELETE policies | Postgres/RLS-level test, not a Kotlin unit test | Testing only "an unauthenticated request is rejected" (that's auth, not ownership) |
| #6 | Habit cascade delete stays consistent locally and remotely even under a mid-cascade failure; the remote GC purge order never regresses | "The two-step local cascade is atomic" — it isn't, by design (no transaction) | Current order of the two local DAO cascade calls; current order of the remote GC purge calls | repository-level test (call order + fake-failure-mid-cascade) | Verifying call order via mocks only, without also asserting the resulting data state |

## 3. Phased Rollout

Each row is a discrete rollout phase that will open its own change folder
via `/10x-new`. Status moves left-to-right through the values below; the
orchestrator updates Status as artifacts appear on disk.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|---|---|---|---|---|---|
| 1 | Sync & deletion critical-path coverage | Prove the account-upload and soft-delete-sync paths don't silently lose data | #1, #2, #3 | unit + integration | change opened | `context/changes/testing-sync-deletion-critical-path/` |
| 2 | Cascade & account-boundary regression locks | Lock in two already-fixed-but-unprotected bugs so they can't silently regress | #4, #6 | unit + integration | not started | — |
| 3 | RLS ownership verification | Verify cross-user isolation on all 3 tables, including the new DELETE policies | #5 | Postgres/RLS-level | not started | — |

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

- TBD — see §3 Phase 1 for the soft-delete-sync and account-upload
  patterns this phase establishes.

### 6.4 Adding an RLS ownership test

- TBD — see §3 Phase 3 for the Postgres-level cross-user isolation
  pattern this phase establishes.

### 6.5 Per-rollout-phase notes

(Filled in as each phase lands.)

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
