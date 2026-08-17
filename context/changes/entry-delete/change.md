---
change_id: entry-delete
title: Per-item delete for journal entries, habits, and habit check-ins
status: impl_reviewed
created: 2026-08-16
updated: 2026-08-17
archived_at: null
---

## Notes

Split off from `mvp-certification-fixes` (2026-08-16) — that change originally bundled delete
functionality with a retroactive `test-plan.md` and a root `README.md`. This change carries only
the delete feature (RLS policies, a small design-system color-override addition, and per-item
delete for journal entries/habits/check-ins). The test-plan.md work was folded into a broader
`architecture-hardening` change that also audits and fixes weak spots (sync lock mechanism,
UI-layer business logic) found in this codebase, with matching tests. The root `README.md` is
deferred as a small standalone item, not part of either.
