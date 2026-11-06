---
date: 2026-09-12T21:17:32+02:00
researcher: Claude Sonnet 5
git_commit: 821a649fd690175444c9b672b66aae0dbfed677b
branch: feature/testing-rls-ownership-verification
repository: janekluczka/TodayWas
topic: "Rollout Phase 3 grounding: RLS ownership verification (test-plan.md §2 risk #5)"
tags: [research, supabase, rls, postgres, security]
status: complete
last_updated: 2026-09-12
last_updated_by: Claude Sonnet 5
---

# Research: Rollout Phase 3 grounding — RLS ownership verification

**Date**: 2026-09-12T21:17:32+02:00
**Researcher**: Claude Sonnet 5
**Git Commit**: 821a649fd690175444c9b672b66aae0dbfed677b
**Branch**: feature/testing-rls-ownership-verification
**Repository**: janekluczka/TodayWas

## Research Question

Ground rollout Phase 3 of `context/foundation/test-plan.md` ("RLS ownership verification", risk
#5): verify the live RLS policy state on `journal_entries`/`habits`/`habit_check_ins`, confirm
whether the app has any RLS-bypass path, and establish what test tooling exists — with live
database evidence, not assumption — so `/10x-plan` designs a real test against verified facts.

## Summary

Same pattern as Phases 1-2: the original risk framing doesn't match reality. **All 12 policies (3
tables × 4 operations) exist live and are correctly scoped** by `user_id = auth.uid()`, including
the DELETE policies added in `entry-delete` — a direct `pg_policies` query confirms this today. No
RLS-bypass path exists anywhere in the app or its one Edge Function; every data access goes
through a single anon-keyed, user-session-scoped Supabase client. **The real gap is that this has
never been proven at runtime** — `entry-delete`'s own plan left two manual verification items
unchecked ("signed in, delete + manual sync — entry does not reappear"), and this project has zero
Postgres/RLS-level test tooling of any kind. Phase 3 is genuinely the first policy-level test layer
for this codebase.

## Detailed Findings

### Live RLS policy state (direct `pg_policies` query)

All three tables have exactly four `PERMISSIVE` policies each, one per operation, `roles = {public}`:

| Table | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| `journal_entries` | `USING (user_id = auth.uid())` | `WITH CHECK (user_id = auth.uid())` | `USING` + `WITH CHECK (user_id = auth.uid())` | `USING (user_id = auth.uid())` |
| `habits` | same | same | same | same |
| `habit_check_ins` | same | same | same | same |

Naming follows `<table>_<verb>_own` uniformly (e.g. `journal_entries_delete_own`). The DELETE
policies were added in `entry-delete` (commit `364bae3`) matching the exact naming/shape of the
pre-existing SELECT/INSERT/UPDATE policies from `account-creation-and-sync`
(`context/archive/2026-08-10-account-creation-and-sync/plan.md:242-244`).

**`roles = {public}` is not a gap**: since `auth.uid()` returns `NULL` for an unauthenticated
request, `user_id = NULL` is never true regardless of role, so anonymous access is denied by the
equality itself, not by role restriction. This is a standard, documented Supabase pattern — not
something this rollout needs to fix, but worth naming explicitly since "policy exists" and "role
list looks permissive" could otherwise read as suspicious out of context.

### `entry-delete`'s own plan left verification incomplete

`context/changes/entry-delete/plan.md`'s Progress log shows the DELETE policies were applied and
passed a security-advisor scan (`364bae3`), but two manual verification items were **never
checked**: "Signed in, Phase 1 live: delete + manual sync — entry does not reappear" (journal) and
the equivalent for habits. `plan-brief.md:77-79` names this explicitly as an open risk: "if the
live project has since changed, re-verify before applying." This is the direct ancestor of the
concern this rollout phase exists to close.

### No RLS-bypass path exists anywhere

- `SupabaseModule.provideSupabaseClient()` (`app/src/main/java/pl/luczka/todaywas/di/SupabaseModule.kt:21-28`) constructs the one `@Singleton` client with `BuildConfig.SUPABASE_ANON_KEY` — never a service-role key.
- Every remote data-access class (`AuthRepositoryImpl`, `AiAssistRepositoryImpl`, `RemoteJournalDataSourceImpl`, `RemoteHabitDataSourceImpl`, `RemoteHabitCheckInDataSourceImpl`) takes that same injected client — no second client, no raw HTTP/OkHttp/Retrofit path, no hardcoded `apikey`/`Authorization` header anywhere in the app.
- The one server-side component, `supabase/functions/ai-proxy/index.ts`, also uses the anon key plus the caller's own forwarded JWT (`index.ts:135-139`) — not a service-role key.
- No `SERVICE_ROLE`/`service_role` string appears anywhere in this project's actual app, build, or function source — only in unrelated third-party skill documentation.

**Consequence**: RLS is the *only* enforcement boundary for cross-user data isolation in this
app. There is no secondary authorization check anywhere to fall back on.

### Zero Postgres/RLS test tooling exists — confirmed, not assumed

- No `.sql` files anywhere in the repo.
- No pgTAP references anywhere.
- No `supabase/config.toml`, no `.supabase/` local dev directory.
- `.github/workflows/ci.yml`'s only two jobs are `android` (ktlint/unit-test/build) and
  `deploy-edge-function` — no database-testing step of any kind.
- `supabase/` in this repo contains exactly one file: `functions/ai-proxy/index.ts`. No
  `migrations/` directory.
- This project's confirmed convention (quoted verbatim in `context/changes/mvp-certification-fixes/plan-brief.md:49` and `context/changes/entry-delete/plan.md:35-39`) is **"MCP-only practice"** — every schema/RLS change is applied live via the Supabase MCP's `apply_migration` tool, never captured as a version-controlled local migration file.

This matches `test-plan.md`'s own stack table (§4) and §6.4 cookbook placeholder, both of which
already anticipated this phase would be the first to establish a Postgres-level test pattern —
consistent with how Phase 1 found no Hilt-instrumented-test infra and used a lighter-weight
alternative instead of standing up new infra from scratch.

### Adjacent findings (out of scope for this risk, noted for awareness)

Live security-advisor scan surfaced three findings unrelated to the three target tables:

1. `ai_assist_usage` has RLS enabled with **zero policies** — this is deny-all by default, and
   appears intentional: the `ai-proxy` function's own comment states "RLS on `ai_assist_usage`
   denies direct table access entirely — this RPC is the only way in," confirming the design
   choice rather than an oversight.
2. `increment_ai_assist_usage` is a `SECURITY DEFINER` RPC callable by both `anon` and
   `authenticated` roles — plausibly intentional (it's the sole sanctioned access path per finding
   1), but not independently verified as correct in this research pass.
3. Leaked password protection is disabled — a general Auth-hardening item, unrelated to RLS
   ownership.

None of these are in scope for risk #5 (which names `journal_entries`/`habits`/`habit_check_ins`
specifically) — noted as an Open Question below rather than expanding this phase's scope.

## Code References

- `app/src/main/java/pl/luczka/todaywas/di/SupabaseModule.kt:21-28` — the one client, anon-keyed
- `supabase/functions/ai-proxy/index.ts:135-143` — Edge Function client + RLS-reliance comment
- `context/changes/entry-delete/plan.md:129-161` — DELETE policy SQL as originally applied
- `context/changes/entry-delete/plan.md:493-503,529,545` — applied + advisor-clean, but manual E2E verification left unchecked
- `context/archive/2026-08-10-account-creation-and-sync/plan.md:242-244` — original SELECT/INSERT/UPDATE policy convention
- `.github/workflows/ci.yml` — confirmed no database-testing step exists

## Architecture Insights

- This project's RLS-policy history is itself a clean example of the "MCP-only" convention working
  as intended: every policy across three separate changes (`account-creation-and-sync`,
  `entry-delete`, and implicitly `sync-strategy-rework`'s trigger functions) was applied the same
  way, with the same naming convention, verified via the same `pg_policies` query pattern each
  time — but "verified via query" has only ever meant "the policy text exists," never "a real
  cross-user request was actually blocked by it."
- The single-client, no-bypass architecture is a genuine strength for this rollout phase: there is
  exactly one enforcement surface to test, not several inconsistent ones.

## Historical Context (from prior changes)

- `context/changes/entry-delete/plan.md` — added the DELETE policies this phase must verify; its
  own plan-brief flagged the exact assumption ("policies follow the pattern... re-verify before
  applying") that was never closed out.
- `context/archive/2026-08-10-account-creation-and-sync/plan.md` — established the original
  SELECT/INSERT/UPDATE policy convention this phase's DELETE policies (and this test) follow.
- `context/changes/testing-sync-deletion-critical-path/research.md` and
  `context/changes/testing-cascade-account-boundary-locks/research.md` — the prior two rollout
  phases' research; both found their originally-described risk didn't match current reality
  (one disproven, one sharpened). This phase follows the identical pattern a third time.

## Related Research

- `context/changes/testing-sync-deletion-critical-path/research.md`
- `context/changes/testing-cascade-account-boundary-locks/research.md`

## Open Questions

- Should the `ai_assist_usage`/`increment_ai_assist_usage` RPC surface (adjacent findings above)
  become a future risk in `test-plan.md`, given it's a genuinely different enforcement shape
  (RPC-gated, not row-level policy)? Not blocking for this phase — flagged for a future
  `--refresh` if the team wants to expand scope.
- No test-tooling decision has been made yet (e.g., `execute_sql`-driven test scripts run via the
  Supabase MCP vs. a different mechanism) — this is a `/10x-plan` decision, not a research one,
  since it's genuinely novel infrastructure for this project.
