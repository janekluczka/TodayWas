---
change_id: testing-cascade-account-boundary-locks
title: Cascade & account-boundary regression locks
status: implementing
created: 2026-09-12
updated: 2026-09-12
archived_at: null
---

## Notes

Rollout Phase 2 of `context/foundation/test-plan.md` ("Cascade & account-boundary regression
locks"). Risks covered: #4 (a stale local "already synced" flag surviving a sign-out whose
remote session-revoke call failed, leaking one account's local data into a different account
signed in on the same device) and #6 (cascading habit delete — local two-step DAO cascade, no
transaction — and the remote GC purge ordering falling out of sync, undoing a previously-verified
fix). Test types planned: unit + integration.

Both risks lock in already-fixed-but-unprotected bugs from prior changes (`account-creation-and-sync`
impl-review for #4, the `259524e` remote FK-cascade fix for #6) rather than open defects — the
goal is regression protection, not a new fix.

Risk response intent (from `test-plan.md` §2 Risk Response Guidance):
- #4: prove no previously-signed-in account's local data is visible or uploadable after a
  sign-out where the remote revoke call failed; challenge "sign-out returning success means local
  state is fully cleared."
- #6: prove habit cascade delete stays consistent locally and remotely even under a mid-cascade
  failure, and the remote GC purge order never regresses; challenge "the two-step local cascade is
  atomic" (it isn't, by design).
