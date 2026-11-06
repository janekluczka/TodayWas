# UX Refinements — Plan Brief

> Full plan: `context/changes/ux-refinements/plan.md`

## What & Why

Replace the bottom-navigation tab bar with a single hub-style main screen, remove onboarding's "app
focus" picker (FR-001), and move account access to a top-bar icon that opens a bottom sheet. The
bottom nav switched between two screens that were never actually implemented (placeholder text),
and the focus picker added an upfront choice that just complicates the main screen's rendering
logic for no real benefit.

## Starting Point

`MainShellScreen` hosts a 4-tab bottom nav (Home / Journal-placeholder / Habits-placeholder /
Preferences) via local Compose state. `MainScreen` already renders both a Journal and Habit
section, but only when onboarding's `Focus` value is `BOTH` — `JOURNAL`/`HABIT` show one section
each. `Focus` is a full-stack concept (Room column → repository → use cases → two ViewModels → ~7
test files), and its persistence is also, today, the only thing that marks onboarding complete.
Account/auth (sign-in, sign-up, sign-out, data-sync review) is already fully built as a separate
`AccountScreen`, reached today via Preferences → an account card.

## Desired End State

The main screen is always a hub — both Journal and Habit sections, always both FAB actions — with
a top bar showing an account icon. Tapping it opens a bottom sheet: signed-in shows the account
email and a Sign out button (behind a confirmation dialog); signed-out shows a prompt that
navigates to the existing full `AccountScreen`. No bottom navigation bar exists anywhere in the
app. Onboarding is `Welcome → Account → All set`, with no focus-pick step.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Depth of Focus removal | Full removal incl. Room migration | Pre-launch app with no shipped users — cheaper to remove cleanly now than leave dead schema. |
| Onboarding completion trigger | Moves to `ACCOUNT_INFO → ALL_SET` transition (+ Skip) | Keeps the same "flow ends here" shape with one fewer step. |
| Preferences screen | Deleted entirely | Its only content (the account card) moves to the top-bar sheet. |
| PRD update | In scope for this plan | Keeps `prd.md` truthful rather than diverging from shipped behavior. |
| Account sheet state | Lives in `MainViewModel`, not a new ViewModel or the full `AccountViewModel` | Main screen is one screen now; the sheet is a small overlay on it, matching the project's "flow gets its own ViewModel once it's a screen" rule. |
| Signed-out sheet action | Prompt card → pushes existing full `AccountScreen` | Reuses the already-built multi-step sign-up + data-sync-review flow with zero duplication. |
| Sign-out from sheet | Requires a confirmation dialog | Explicit user request. |
| Top-bar account icon | Single generic icon regardless of state | No avatar/display-name data exists today; sheet content communicates state instead. |
| FAB behavior | Always both actions (today's BOTH-focus behavior) | Simplification, not a new feature — the BOTH case is already built and tested. |
| Auth state in open sheet | Live `StateFlow`, no snapshotting | Falls out of MVI/StateFlow for free; avoids stale "signed in" content after sign-out. |
| Room migration | Bump version, rely on existing `fallbackToDestructiveMigration` | Already configured; no shipped users to preserve data for yet. |
| Test coverage | Rewritten/updated in the same phase as each code change | No regression gap left for later. |

## Scope

**In scope:**
- Removing `Focus` end-to-end (domain, Room, onboarding, main-screen branching) + its tests.
- Collapsing `MainShellScreen`'s bottom nav into `MainScreen` as the direct `MainKey` destination.
- Deleting `PreferencesScreen` and its ViewModel/state files.
- New top-bar account icon + bottom sheet (account card/sign-out, or sign-in/up prompt) on the hub.
- `prd.md` FR-001 amendment.

**Out of scope:**
- Any replacement settings/preferences screen.
- Any form of bottom navigation (tabs, rail).
- Inline sign-in/sign-up forms in the bottom sheet.
- Changes to `AccountScreen`/`AccountViewModel`'s existing sign-up/data-sync-review flow.
- Avatar/initials or per-state top-bar icon visuals.
- An explicit Room `Migration` class for the dropped column.

## Architecture / Approach

`MainViewModel` gains the account-sheet's auth state (via the `ObserveAuthStateUseCase` it already
injects for background sync) and a sign-out flow mirroring `AccountViewModel`'s existing pattern —
no new ViewModel. `MainScreen`'s top bar gets an `actions` icon (via `DsTopBar`'s existing slot)
opening a `DsModalBottomSheet`, with a `DsAlertDialog` gating sign-out — both existing
design-system components, no new ones needed. `Focus` removal cascades top-down: Room column →
repository → use cases → the two ViewModels that read it → tests. `TodayWasApp.kt`'s `MainKey`
entry ends up rendering `MainScreen` directly once `MainShellScreen` is deleted.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Data layer & onboarding domain | Focus gone from Room/repository/use cases | Getting the persist-before-route ordering right for the new completion trigger |
| 2. Onboarding UI | 3-step flow (Welcome/Account/All set), no focus pick | Retry/loading UX correctly relocated to the Account step |
| 3. Main screen becomes the hub | Unconditional both-sections hub + FAB | Test rewrite completeness (many focus-parameterized cases) |
| 4. Account bottom sheet + top bar | New sheet + confirm dialog on the hub | Live-state correctness while the sheet is open |
| 5. Remove bottom nav & Preferences | `MainShellScreen`/`ui/preferences` deleted | Confirming no dangling references before/after deletion |
| 6. Foundation docs | `prd.md` FR-001 amended | Scope creep into unrelated PRD sections |

**Prerequisites:** None — builds directly on the current `master` branch state.
**Estimated effort:** ~4-6 implementation sessions across 6 phases (Phase 1-2 and Phase 3-4 are each
naturally paired; Phase 5 is mostly deletion; Phase 6 is a short doc edit).

## Open Risks & Assumptions

- Assumes no instrumented (`androidTest`) coverage touches `Focus`, `MainShellScreen`, or
  `PreferencesScreen` — confirm with a repo-wide search before Phase 5's deletions.
- Assumes `prd.md`'s focus-related surface is limited to FR-001 and its Socrates note — confirm with
  a grep for "focus" during Phase 6 rather than assuming this list is exhaustive.
- The exact string-resource key names introduced in Phase 2 (retry/error copy for the `ACCOUNT_INFO`
  step) are left to the implementer rather than pre-decided here.

## Success Criteria (Summary)

- The hub always shows both Journal and Habit sections with no focus concept anywhere in the app.
- Account access works fully from the top-bar icon: sheet reflects live sign-in state, sign-out
  requires confirmation, and signed-out users can still reach the full sign-in/up flow.
- No bottom navigation bar exists anywhere in the app, and `prd.md` matches shipped behavior.
