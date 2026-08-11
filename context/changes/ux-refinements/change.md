---
change_id: ux-refinements
title: Replace bottom nav with a hub main screen; drop onboarding focus pick
status: implemented
created: 2026-08-11
updated: 2026-08-11
archived_at: null
---

## Notes

Change summary: Replace the current bottom-navigation tab bar with a single hub-style main screen and remove the onboarding "app focus" step.

- Remove bottom navigation entirely (only used to switch between screens that aren't both implemented yet).
- Main screen becomes a hub showing both habits and journal entries together (no per-focus split).
- Remove "app focus" picker from onboarding (FR-001) since it just adds complexity for no current benefit.
- Main screen gets a top app bar with an account icon button on the right.
- Tapping the account icon opens a bottom sheet: shows an account card + sign-out button if signed in, or a sign-in/sign-up card if signed out.
- Explicitly skip a "preferences"/settings screen and skip bottom navigation for now — out of scope for this change.
