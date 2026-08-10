# Manual verification pending

Items from `plan.md`'s `#### Manual` Progress subsections that have **not** been hands-on
click-tested on a device/emulator — only covered by automated unit tests (or, for Phase 1,
asserted on trust). Touch injection via `adb` is blocked on the available emulator (production
build, no root, shell lacks the input-injection permission — confirmed via `input tap`,
`input touchscreen swipe`, `input -d 0 tap`, the monkey tool's touch socket, and direct
`sendevent` which failed with `Permission denied` on `/dev/input/eventN`). Key-event injection
(Tab/Enter) works, but blind keyboard-only navigation through multi-field forms (sign-up, add
entry) wasn't reliable enough to trust without visual focus feedback.

Check these off by hand when you get a chance to click through on a real device or a dev-build
emulator with working touch injection.

## Phase 1: UUID id migration + Postgres schema + Postgrest wiring

- [ ] 1.4 Fresh install: add/edit journal entry, habit, check-in — all work with UUID ids
      *(marked done in plan.md on trust after automated tests passed — not actually clicked
      through)*
- [ ] 1.5 (partial) Supabase tables verified via MCP `list_tables`/`get_advisors` — real, not
      trust-based. The `postgrest-kt` "compiles in" half was confirmed by Phase 2's build
      succeeding, also not a hands-on click test (N/A here, nothing to click).

## Phase 3: Sign-out clears synced local data

- [ ] 3.3 Sign out clears journal/habit/check-in data; onboarding not re-shown
      Automated coverage instead: `AccountViewModelTest`'s
      `` `should clear synced local data when SignOutClicked succeeds` `` and
      `` `should not clear local data when SignOutClicked fails` `` directly assert
      `journalRepository.clearLocalCallCount`, `habitRepository.clearLocalCallCount`, and
      `onboardingRepository.resetSyncFlagCallCount`. What automated tests can't cover: the
      actual on-screen empty state after sign-out, and that onboarding truly doesn't re-appear
      (that's `RootViewModel` routing off `OnboardingState.completed`, unaffected by this
      change, but worth a real look).

## Phases 4-5 (not yet implemented)

Will add their `#### Manual` items here as those phases land — expect the review-screen counts
UI (Phase 4) and the launch-time pull restore (Phase 5) to need real device verification, since
both involve visual state (counts shown, data appearing after a cold start) that's inherently
hard to assert from a unit test alone.
