---
change_id: testing-rls-ownership-verification
title: RLS ownership verification
status: new
created: 2026-09-12
updated: 2026-09-12
archived_at: null
---

## Notes

Rollout Phase 3 of `context/foundation/test-plan.md` — the final phase. Risk covered: #5
(RLS policies on `journal_entries`/`habits`/`habit_check_ins` — including the DELETE policies
added for per-item delete — don't actually scope by owning user, allowing cross-user
read/write/delete). Test type: Postgres/RLS-level, not a Kotlin unit test — this is a different
tooling concern from Phases 1-2.

Risk response intent (from `test-plan.md` §2 Risk Response Guidance):
- An authenticated user A can never read, modify, or delete a row owned by user B, across all
  three tables and all four operations.
- Must challenge: "RLS policies exist, therefore they're correct" — existence isn't correctness.
- Context needed: the exact current policy definitions on all three tables, including the
  newly-added DELETE policies (added in `entry-delete`, never independently verified).
- Likely cheapest layer: Postgres/RLS-level test, not a Kotlin unit test.
- Anti-pattern to avoid: testing only "an unauthenticated request is rejected" (that's auth, not
  ownership).
