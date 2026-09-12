# RLS Ownership Verification — Plan Brief

> Full plan: `context/changes/testing-rls-ownership-verification/plan.md`
> Research: `context/changes/testing-rls-ownership-verification/research.md`

## What & Why

The final rollout phase of `context/foundation/test-plan.md`. Writes the first Postgres/RLS-level
test for this project, proving a real authenticated request from one user is actually blocked from
touching another user's rows — not just that the policy SQL text looks correct.

## Starting Point

A live `pg_policies` query confirms all 12 policies (3 tables × 4 operations) are already correctly
scoped, and no RLS-bypass path exists anywhere in the app. This project has zero Postgres-level
test tooling today, and `entry-delete`'s own plan left this exact verification unchecked when the
DELETE policies were added.

## Desired End State

A version-controlled SQL script any future contributor can re-run via the Supabase MCP whenever RLS
policies change, proving cross-user isolation still holds. A clean run is silent; a regression
raises a specific exception naming exactly what broke.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Fixture safety | Everything inside one transaction, always `ROLLBACK` | Zero risk to the live, linked project — there's no separate staging project | Plan |
| Fixture owners | Two real, existing `auth.users` IDs, not synthetic UUIDs | `user_id` has a live FK to `auth.users`, confirmed by testing it directly | Plan (corrected mid-planning) |
| Persistence | New `supabase/tests/rls_ownership.sql`, re-run via MCP `execute_sql` | A "regression lock" that never runs again isn't one — this is a real, version-controlled artifact | Plan |
| CI wiring | Not in this phase | No database-testing CI step exists today; adding one is a separate, bigger concern | Plan |
| Coverage | All 3 tables × 4 operations, one direction (12 checks) | The `user_id = auth.uid()` predicate is symmetric — one direction gives full signal | Plan |
| Assertion style | `RAISE EXCEPTION` on any unexpected success, silent on a clean pass | Unambiguous pass/fail for a script re-run months later | Plan |
| Cascade-specific check | Dropped | The remote FK is `NO ACTION` not `CASCADE` — no real cascade exists to test; the plain per-table DELETE checks already cover both tables independently | Plan (corrected mid-planning) |

## Scope

**In scope:** `supabase/tests/rls_ownership.sql` (12 checks across 3 tables × 4 operations); live
validation of the script; `test-plan.md` §6.4 cookbook + §6.5 rollout notes.

**Out of scope:** CI wiring; throwaway `auth.users` rows; reverse-direction testing; a separate
cascade check; the adjacent `ai_assist_usage`/RPC findings from the security advisor.

## Architecture / Approach

One SQL script, one transaction, one validation run. The technique — session-variable simulation
via `set_config('request.jwt.claims', ...)` + `SET LOCAL ROLE authenticated` — is exactly what
PostgREST does per-request, so this tests the real enforcement path, not an approximation of it.
The exact patterns for all four operation shapes (SELECT/INSERT/UPDATE/DELETE) were prototyped and
validated live against the linked project before being written into the plan.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. RLS ownership regression test | The script, validated live, cookbook updated | Low — technique already prototyped and confirmed working end-to-end |

**Prerequisites:** Supabase MCP access (already available). At least 2 rows in `auth.users` on the
linked project (confirmed: 8 exist today).
**Estimated effort:** ~1 session — one file, already fully prototyped.

## Open Risks & Assumptions

- The script borrows real `auth.users` IDs; if the linked project ever has fewer than 2 users, the
  script fails loudly with a clear setup error rather than silently passing — by design.

## Success Criteria (Summary)

- Running `supabase/tests/rls_ownership.sql` against the linked project produces no output (clean
  pass) today.
- A future regression in any of the 12 covered checks would raise a specific, descriptive exception
  naming exactly what broke.
