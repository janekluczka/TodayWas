---
change_id: mvp-certification-fixes
title: Close the 3 gaps from the 10xDevs MVP certification check
status: planned
created: 2026-08-16
updated: 2026-08-16
archived_at: null
---

## Notes

closes the 3 gaps from the 10xDevs MVP certification check: (1) per-item delete for journal entries, habits, and habit check-ins (CRUD criterion currently missing Delete), (2) context/foundation/test-plan.md defining the risks the existing test suite already covers (24h edit-window, contribution-level calculation, sync/auth failure handling), (3) root README.md describing the project, its features, module layout, and build commands.

**Split 2026-08-16** into two changes before implementation started: `context/changes/entry-delete/`
(the delete feature) and `context/changes/architecture-hardening/` (a weak-spot audit — sync lock
mechanism, UI-layer business logic entanglement — plus fixes and tests, superseding the plain
retroactive `test-plan.md` idea). The root `README.md` was deferred as a small standalone item, not
part of either. This change's `plan.md`/`plan-brief.md` are kept as the original combined research
record; the two split changes are the active plans.
