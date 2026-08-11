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

## Phase 4: Local-data review screen + sign-up/sign-in hook-in

- [ ] 4.4 Sign-up with local data: review screen shows correct counts, confirm uploads to
      Supabase with correct `user_id`
      Automated coverage instead: `AccountViewModelTest`/`OnboardingViewModelTest` assert the
      `DATA_SYNC_REVIEW` step is entered with the right `dataSyncSummary` counts, and that
      `SyncConfirmClicked` calls `syncWithRemote()` on both repos + `markLocalDataSynced()`.
      What automated tests can't cover: the actual on-screen rendering of
      `DataSyncReviewContent`, and confirming real rows land in Supabase's table editor with the
      correct `user_id` (this needs a real sign-up against the live project).
- [ ] 4.5 Sign out then sign back into same account with new local-only data: review reappears
      Automated coverage instead: Phase 3's sign-out tests confirm `resetSyncFlag()` is called;
      this phase's tests confirm `DATA_SYNC_REVIEW` re-appears when `hasSyncedLocalData` is
      false and data is non-empty. Not verified together end-to-end on a device.
- [ ] 4.6 Skip once, then manually re-trigger via Account's "Sync local data" action
      Automated coverage instead: `` `should show DATA_SYNC_REVIEW when SyncLocalDataClicked is
      dispatched with unsynced local data` ``. The actual "Sync local data" button's visibility
      toggling on `SignedInContent` (shown only when `!hasSyncedLocalData`) isn't visually
      confirmed.
- [ ] 4.7 Sign-up with no local data: goes straight to success, review never appears
      Automated coverage instead: `` `should go straight to SUCCESS when sign-up succeeds with
      no local data` `` / the onboarding equivalent.

## Phase 5 (not yet implemented)

Will add its `#### Manual` items here once implemented — expect the launch-time pull restore to
need real device verification (force-stop/relaunch, fresh install + sign into an existing
account with cloud data), since both involve state changes that only show up after a real cold
start, not something a unit test can simulate.
