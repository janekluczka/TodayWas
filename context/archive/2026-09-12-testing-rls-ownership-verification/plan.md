# RLS Ownership Verification — Implementation Plan

## Overview

This is rollout Phase 3 of `context/foundation/test-plan.md` — the final phase. It writes the
first Postgres/RLS-level test for this project, proving that a real authenticated request from
one user is actually blocked from reading, writing, or deleting another user's rows on
`journal_entries`, `habits`, and `habit_check_ins` — not just that the policy text looks correct.

## Current State Analysis

- A live `pg_policies` query confirms all 12 policies (3 tables × 4 operations) exist and are
  correctly scoped by `user_id = auth.uid()`, including the DELETE policies added in
  `entry-delete`. No production fix is needed — this phase is test-only.
- No RLS-bypass path exists anywhere: the app's one `SupabaseClient`
  (`app/src/main/java/pl/luczka/todaywas/di/SupabaseModule.kt:21-28`) is built with the anon key,
  and the one Edge Function forwards the caller's own JWT — never a service-role key.
- This project has zero Postgres/RLS-level test tooling: no `.sql` files, no pgTAP, no local
  Supabase CLI test setup, no database-testing CI step. The confirmed project convention is
  "MCP-only" — schema/RLS changes are applied live via the Supabase MCP's `apply_migration`, never
  as local migration files.
- `entry-delete`'s own plan left its signed-in end-to-end verification of the new DELETE policies
  unchecked — this phase closes that specific, named gap.

### Key Discoveries:

- `user_id` on all three tables has a live foreign key to `auth.users` — confirmed by testing it
  directly (an `information_schema` cross-schema join missed this FK; a real `INSERT` attempt with
  a synthetic UUID surfaced it immediately: `violates foreign key constraint
  "journal_entries_user_id_fkey"`). The test must borrow two real, existing `auth.users` IDs as
  fixture owners — synthetic UUIDs are not usable.
- The full technique was prototyped live against the linked project (wrapped in `BEGIN;` /
  `ROLLBACK;`, confirmed nothing persists) and validated end-to-end for all four operation shapes:
  - **SELECT**: cross-user rows are simply invisible — `IF EXISTS (...)` after the attempt.
  - **INSERT** (attempting to write a row claiming another user's `user_id`): raises a real
    Postgres exception, `SQLSTATE 42501` / `insufficient_privilege` — confirmed by direct test.
    Must be caught in a nested `BEGIN ... EXCEPTION WHEN insufficient_privilege` block.
  - **UPDATE** and **DELETE**: silently affect 0 rows (the `USING` clause filters visibility
    before `WITH CHECK` is ever considered) — confirmed by direct test via `GET DIAGNOSTICS ...
    ROW_COUNT`.
- Simulating a user's session is exactly what PostgREST itself does per-request:
  `PERFORM set_config('request.jwt.claims', json_build_object('sub', <uuid>, 'role',
  'authenticated')::text, true); SET LOCAL ROLE authenticated;` — confirmed `auth.uid()` resolves
  correctly afterward.
- A cascade-specific check (user A deletes user B's habit, verify check-ins untouched) was
  considered but dropped: the remote FK (`habit_check_ins.habit_id → habits.id`) is `ON DELETE NO
  ACTION`, not `CASCADE` (confirmed in the prior rollout phase), so there is no real database-level
  cascade for this SQL-only script to exercise. The plain per-table DELETE checks on both tables
  already independently prove both are protected.

## Desired End State

A single, version-controlled SQL script exists that any future contributor can re-run against the
linked Supabase project (via the Supabase MCP's `execute_sql`) whenever RLS policies change, to
prove cross-user access is still blocked on all three tables and all four operations. A clean run
produces no output; any regression raises a specific, descriptive exception naming exactly which
table/operation/user-boundary broke.

## What We're NOT Doing

- Not wiring this test into CI — `.github/workflows/ci.yml` has zero database-testing steps today;
  adding one means new Supabase secrets and a new job, a meaningfully bigger, separate concern from
  writing the test itself.
- Not creating throwaway rows directly in `auth.users` — borrowing two existing real users' IDs
  inside a transaction that always rolls back is simpler, more robust (avoids GoTrue's own
  NOT NULL/trigger constraints on that table), and equally safe.
- Not testing the reverse direction (user B blocked from user A) — the RLS predicate
  (`user_id = auth.uid()`) is a symmetric equality check with no asymmetry to expose; testing one
  direction across all tables/operations gives full signal.
- Not adding a separate cascade-specific check — see Key Discoveries above; it would be redundant
  with the plain per-table DELETE checks.
- Not addressing the adjacent `ai_assist_usage`/`increment_ai_assist_usage` findings surfaced by
  the security advisor during research — a different enforcement shape (RPC-gated, not row-level
  policy), out of scope for this risk, flagged in research.md as a possible future `--refresh` item.

## Implementation Approach

One phase, one deliverable: a self-contained SQL script covering all 3 tables × 4 operations (12
checks total), validated live before being considered done, plus the cookbook/rollout-notes update
that closes out this test-plan rollout (this is the final phase).

## Critical Implementation Details

**Fixture safety**: the entire script must run inside one transaction that always ends in
`ROLLBACK` — including the `SELECT id FROM auth.users` calls, which happen before any role
switch and therefore run with full table visibility (no RLS applies to the role executing the
script, since it owns the tables). Every fixture insert must happen *before* `SET LOCAL ROLE
authenticated`, since after that switch the session is itself subject to the same RLS policies
being tested and could no longer freely insert rows for an arbitrary `user_id`.

## Phase 1: RLS ownership regression test

### Overview

Write, validate, and document the first Postgres/RLS-level test for this project.

### Changes Required:

#### 1. The RLS ownership test script

**File**: `supabase/tests/rls_ownership.sql` (new)

**Intent**: Prove, via a real simulated authenticated request (not a policy-text read), that user
A cannot SELECT, INSERT-as-B, UPDATE, or DELETE any row owned by user B, on all three tables.

**Contract**: One `BEGIN; DO $$ ... $$; ROLLBACK;` block. Setup: select two existing `auth.users`
IDs (`user_a`, `user_b`); if fewer than 2 exist, raise a clear setup-failure exception rather than
silently no-op. Insert one fixture row per table owned by `user_b` (a journal entry, a habit, and
a check-in on that habit). Switch session to `user_a` via `set_config('request.jwt.claims', ...)` +
`SET LOCAL ROLE authenticated`, and sanity-check `auth.uid() = user_a` before proceeding (fail
loudly on a setup problem, not a false-positive RLS pass). Then, for each of the three tables, in
this order — SELECT, INSERT (claiming `user_b`'s ownership, expecting `insufficient_privilege`),
UPDATE, DELETE — assert the cross-user attempt is blocked using the exact validated patterns from
Key Discoveries above (`IF EXISTS` for SELECT, nested `BEGIN/EXCEPTION WHEN insufficient_privilege`
for INSERT, `GET DIAGNOSTICS ... ROW_COUNT` for UPDATE/DELETE). Each failure path names the exact
table and operation in its `RAISE EXCEPTION` message. End with `RAISE NOTICE` confirming all 12
checks passed, then `ROLLBACK`. Include a file-header comment explaining what the script does, how
to run it (paste into the Supabase MCP `execute_sql` tool, or the SQL editor, against the linked
project), and that a clean run produces no output.

#### 2. Validate the script live

**Intent**: Prove the finished script actually passes against the current live project state
before considering this phase done — this is the one piece of "automated verification" available
for SQL that isn't runnable through Gradle.

**Contract**: Run the complete script via the Supabase MCP's `execute_sql` tool. Confirm it
completes with no error (a clean `ROLLBACK`, no `RAISE EXCEPTION` surfaced). If any check fails,
that's a genuine, newly-discovered RLS problem — stop and treat it as a real finding, not a script
bug, until proven otherwise.

#### 3. Update the test-plan cookbook and close out the rollout

**File**: `context/foundation/test-plan.md`

**Intent**: This is the final rollout phase — its own contract with `/10x-test-plan` is to fill in
§6.4's placeholder and leave the guide in a state where every §3 phase reads `complete`.

**Contract**: Replace §6.4 ("Adding an RLS ownership test") placeholder text with the pattern this
phase establishes: session-variable simulation, `ROLLBACK`-wrapped, real `auth.users` IDs as
fixture owners, referencing `supabase/tests/rls_ownership.sql` as the canonical example and how to
re-run it. Add a §6.5 per-rollout-phase note summarizing what this phase found (policies were
already correct; the real gap was the missing runtime proof, plus the `auth.users` FK discovery).
Do not flip §3's Phase 3 status row here — that happens through the normal `/10x-implement`
Progress/commit flow and a final `/10x-test-plan` pass, matching how Phases 1-2 closed out.

### Success Criteria:

#### Automated Verification:

- The script runs clean via the Supabase MCP `execute_sql` tool: no exception raised, transaction
  rolls back successfully.
- Lint passes: `./gradlew.bat ktlintCheck` (no Kotlin changed, included for consistency with the
  rest of this rollout — expected to be a no-op).

#### Manual Verification:

- None required — the live-script run against the linked project *is* the verification; there is
  no separate manual step beyond what Automated Verification already covers.

---

## Testing Strategy

### Unit Tests:

- None — this phase's entire testing surface is the SQL script itself.

### Integration Tests:

- `supabase/tests/rls_ownership.sql`, run via Supabase MCP `execute_sql`, is the test.

### Manual Testing Steps:

None beyond running the script (covered under Automated Verification, since it's the only way to
verify SQL in this project and produces an unambiguous pass/fail).

## Performance Considerations

None — a single short-lived transaction, always rolled back, run once during implementation.

## Migration Notes

None — no schema change. Consistent with this project's "MCP-only" convention, this script is a
test artifact, not a migration; it is never applied via `apply_migration`.

## References

- Research: `context/changes/testing-rls-ownership-verification/research.md`
- Rollout strategy: `context/foundation/test-plan.md` §2 (risk #5), §3 (Phase 3)
- Historical DELETE policy shape: `context/changes/entry-delete/plan.md:129-161`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: RLS ownership regression test

#### Automated

- [x] 1.1 The script runs clean via the Supabase MCP `execute_sql` tool — ee5d627
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck` — ee5d627
