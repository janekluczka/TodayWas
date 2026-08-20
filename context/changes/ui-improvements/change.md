---
change_id: ui-improvements
title: UI improvements across every screen, following the user flow
status: preparing
created: 2026-08-20
updated: 2026-08-20
archived_at: null
---

## Notes

Go through the app's user flow start to finish (onboarding → main/hub screen → journal
add/detail → habit create/log/detail → account/auth) and improve the UI on every screen,
one screen at a time. Scoped as a single change/feature rather than one change per screen.

"Previous step" (`architecture-hardening`) is done, merged to `master`, and archived to
`context/archive/2026-08-18-architecture-hardening/`. This change starts fresh on
`feature/ui-improvements`, branched off `master` at that point.

Note: `context/changes/ux-refinements/` (status `impl_reviewed`, created 2026-08-11 — bottom-nav
removal / hub main screen) already appears fully implemented in the current codebase but was never
archived. Out of scope for this change unless it turns out to overlap; flagged here for later
cleanup.
