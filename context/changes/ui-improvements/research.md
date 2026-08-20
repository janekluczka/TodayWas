---
date: 2026-08-20T00:00:00-00:00
researcher: Claude Code
git_commit: 51c3a8b8822cdf0943cd50b2a0240d30f36301f7
branch: feature/ui-improvements
repository: TodayWas
topic: "UI improvements across every screen, following the user flow"
tags: [research, codebase, ui, onboarding, main, journal, habit, account, auth, design-system]
status: complete
last_updated: 2026-08-20
last_updated_by: Claude Code
---

# Research: UI improvements across every screen, following the user flow

**Date**: 2026-08-20
**Researcher**: Claude Code
**Git Commit**: 51c3a8b8822cdf0943cd50b2a0240d30f36301f7
**Branch**: feature/ui-improvements
**Repository**: TodayWas

## Research Question

Walk the app's full user flow (onboarding → main/hub screen → journal add/detail → habit
create/log/detail → account/auth) and identify concrete UI issues per screen, across four lenses:
design-system consistency, missing UI states, UX/flow friction, and visual polish. This research
is the input to a screen-by-screen collaborative pass ("pair programming") where the user adds
their own improvement ideas on top before planning.

## Summary

The app's `Ds*` component adoption is strong almost everywhere — no screen was found rolling raw
`Button`/`TextField`/`Text` where a `Ds*` wrapper exists, spacing consistently uses `DsSpacing`
tokens, and all user-facing text goes through `stringResource`. The real gaps cluster into four
recurring, cross-cutting patterns rather than isolated one-off bugs:

1. **No typography hierarchy anywhere in the app.** `DsTypography` only defines `bodyLarge`; every
   screen renders titles, section headers, and body text at the same size/weight because no screen
   ever passes `style =` to `DsText`. This is the single biggest, most consistent gap — most visible
   on Onboarding-Welcome, Journal Entry Detail, and Main's section headers vs. list rows.
2. **The 24h edit-window rule (FR-006) is enforced but never explained.** On Journal Entry Detail,
   Habit Detail, and (reactively, via snackbar) elsewhere, a locked entry just silently loses its
   edit affordance or renders in a generic "disabled" visual style — there's no lock icon, label, or
   proactive copy telling the user *why*, only a snackbar if they happen to race the window closing.
3. **Loading/error states exist in ViewModels but aren't wired into Compose.** `isLoading` on
   `JournalEntryDetailUiState` and `HabitDetailUiState`, and `isDeletingHabit`/`isDeletingCheckIn` on
   `HabitDetailUiState`, are all correctly populated by their ViewModels but never read by their
   screens — first-open renders a flash of empty content, and deletes show no in-flight feedback.
   Separately, sync failures (onboarding + Account) are silently swallowed with no error shown at
   all, unlike every other async action in the app.
4. **AI-assist "silent gates" and near-duplicate dialogs.** Both "help me start" and "help me
   refine" hide their entry chip outright when a limit is hit (regeneration cap, refine text-length
   cap, signed-out state) with no explanation, and the two dialogs are near-identical copy-pasted
   implementations — any fix (error-text color, remaining-attempts counter, back-navigation between
   steps) needs to be applied twice today.

Each screen's individual findings are below, organized to match the user flow order in
`context/changes/ui-improvements/change.md`.

## Detailed Findings

### Onboarding — Welcome step

`OnboardingScreen.kt:171-177` (`WelcomeStepBody`)

- **Design-system consistency**: Title and description both render via plain `DsText` with no
  `style =` — visually identical size/weight, no hierarchy (`OnboardingScreen.kt:171-177`).
- **Visual polish**: The `Column` has no `verticalArrangement`, so title/description sit with 0dp
  gap — inconsistent with `AccountChoiceBody` (`OnboardingScreen.kt:221`) and `AllSetStepBody`
  (`OnboardingScreen.kt:267`), which both use `Arrangement.spacedBy(DsSpacing.space400)`.
- **UX/flow friction**: `DsTopBar(title = "")` (`OnboardingScreen.kt:95`) has no visible back
  affordance; the only way back is the system gesture, which triggers `ExitApp` (closes the app, no
  confirmation) at `OnboardingViewModel.kt:166`. `AccountScreen.kt:84-93` shows an explicit back
  arrow — inconsistent back-affordance treatment between the two flows.

### Onboarding — Account setup (CHOICE / SIGN_IN / SIGN_UP / DATA_SYNC_REVIEW)

`OnboardingScreen.kt:179-262`, `OnboardingViewModel.kt:109-143, 158-185`

- **UX/flow friction (most significant finding across the whole flow)**: The bottom-bar
  "Next"/"Continue" button is visible and enabled on *every* `AccountSubStep`
  (`OnboardingBottomBar`, `OnboardingScreen.kt:129-152`), but always just completes onboarding using
  whatever `authState` currently is (`onCompleteAccountStep`, `OnboardingViewModel.kt:109-143`) —
  it does not submit the visible sign-in/sign-up form. While a user is filling in the sign-up form,
  three overlapping CTAs are visible at once: bottom "Skip", bottom "Continue" (silently abandons
  the form), and the form's own "Create account" button. On `DATA_SYNC_REVIEW`, the same bottom
  "Continue" bypasses the review entirely, a third undocumented exit alongside the two buttons
  already inside `DataSyncReviewContent`.
- **UX/flow friction**: `onAccountInfoStepBack` (`OnboardingViewModel.kt:170-185`) no-ops on
  `DATA_SYNC_REVIEW` — pressing back while reviewing the sync summary is a dead end.
- **UX/flow friction**: On `CHOICE`, "Continue without account" (`OnboardingScreen.kt:223-226`) is
  functionally redundant with the bottom-bar "Continue" — two controls doing the same thing.
- **Visual polish**: `AccountChoiceBody` renders both options as equal-weight `DsTextButton`s
  (`OnboardingScreen.kt:223-230`) — no visual hierarchy on the most consequential decision point in
  onboarding (no primary `DsButton` for "create account").
- **Missing UI states**: `saveError` text (`OnboardingScreen.kt:203-205`) uses plain `DsText`, no
  error color — doesn't read as an error next to normal copy.
- **Strength worth preserving**: `SignInFormContent`/`SignUpFormContent` loading is wired correctly
  (`loading = uiState.step == ACCOUNT_INFO && uiState.isSaving`, `OnboardingScreen.kt:149`; per-field
  `enabled = !state.isSubmitting`).

### Onboarding — All Set step

`OnboardingScreen.kt:264-286`

- **UX/flow friction**: Auto-advances via `delay(5_000L)` (`OnboardingScreen.kt:87-92, 127`) with no
  visible countdown and no way to pause — a slow reader may get navigated away mid-read. A manual
  "Get started" CTA exists as an escape hatch, but the timeout itself isn't discoverable/cancelable.
- **Visual polish**: Text-only, no icon/illustration for what should be a celebratory "done" moment
  — reads as unfinished/placeholder.

### DataSyncReviewContent (shared by Onboarding + Account)

`DataSyncReviewContent.kt`

- **Design-system consistency**: Clean, no issues — good `@PreviewLightDark` coverage of both
  `isSyncing` states.
- **Missing UI states**: If `syncLocalData()` fails, both `OnboardingViewModel.onSyncConfirmClicked`
  (`OnboardingViewModel.kt:354-363`) and `AccountViewModel.onSyncConfirmClicked`
  (`AccountViewModel.kt:254-263`) check `result.isSuccess` only to decide whether to call
  `markLocalDataSynced()` — a failure is never surfaced to the user, unlike every other async action
  in the app (sign-in/up/out all show a snackbar on failure). A user could believe their data synced
  when it silently didn't.
- **UX/flow friction**: Back semantics differ depending on host — dead end in Onboarding (see
  above), but exits the Account screen entirely when hosted there.

### Account screen

`AccountScreen.kt`

- **Design-system consistency**: Good — `DsScaffold`, real back-arrow `DsTopBar`, `DsLoadingIndicator`
  for `AuthStateUi.Loading`, full `@PreviewLightDark` coverage across every state.
- **Visual polish**: `AccountSuccessContent`/`SignedInContent` buttons
  (`AccountScreen.kt:168, 189, 194`) don't set `fillMaxWidth()`, unlike the sign-in/sign-up forms'
  buttons which do (`SignInFormContent.kt:93`, `SignUpFormContent.kt:86`) — button widths will
  visibly differ screen-to-screen.
- **Missing UI states**: Well covered otherwise — sign-out loading/error, sign-in/up errors all
  handled.
- Minor: `contentAlignment = Alignment.TopCenter` on the outer `Box` (`AccountScreen.kt:104`) is
  dead code since every child is already `fillMaxWidth()`.

### Auth forms (SignInFormContent / SignUpFormContent / GoogleSignInButton)

- **Design-system consistency**: Forms correctly use `Ds*` wrappers throughout.
  `GoogleSignInButton.kt:33` uses a raw M3 `OutlinedButton` — there's no `DsOutlinedButton` in the
  design system at all, so this is a real gap, though the file's own comment documents it as a
  deliberate "native-feeling" choice, not an oversight.
- **Missing UI states**: Google sign-in's local `isGoogleSignInLaunching` flag disables the button
  but shows no spinner (unlike every other loading affordance in the app, which uses
  `DsButtonWithLoading`) — inconsistent loading feedback.
- **Visual polish**: Password validation (`isValidPassword`, `AuthFormValidation.kt:10`) only checks
  `length >= 6` with no live strength/requirements hint — user gets no guidance until a failed
  submit.

### Main / Hub screen

`MainScreen.kt`, `MainViewModel.kt`, `ContributionMapper.kt`

- **Design-system consistency**: `JournalEntryListItem`/`HabitListItem`
  (`MainScreen.kt:317-335, 362-377`) bypass `DsCard` entirely — raw `Column`/`Row` +
  `.clickable()`, no elevation/container/boundary. No `DsHorizontalDivider` between list rows either
  — only 8dp padding separates consecutive entries. Section spacing is asymmetric: `HabitSection`
  wraps its whole `Column` in 24dp padding on all sides (`MainScreen.kt:344`) while `JournalSection`
  applies padding per-child with none on its title (`MainScreen.kt:251`) — visibly different top
  offsets for two structurally parallel sections. No screen in the app uses typography hierarchy
  (see cross-cutting summary above) but it's most visible here since headers sit directly next to
  list-item bodies with zero differentiation.
- **Missing UI states**: No loading state — the seeded initial `MainUiState`
  (`MainViewModel.kt:59-75`) has empty lists before Room emits, so a returning user with real data
  can see a flash of the "No entries yet"/"No habits yet" empty state before real data lands. The
  `combine(...)` of the four core data flows (`MainViewModel.kt:93-129`) has no `.catch`/error
  mapping — unlike the sign-out path, which does map failures to `MainUiEvent.ShowError`
  (`MainViewModel.kt:195-199`). No dedicated "brand-new user, zero everything" treatment — it's just
  two stacked empty-state strings with nothing pointing at the FAB.
- **UX/flow friction**: Tapping a habit row navigates to habit *detail*
  (`MainViewModel.kt:227-229`), not to logging today's check-in — but the row displays
  `todayStatus` right next to the name (`MainScreen.kt:375`), which looks actionable/tappable even
  though tapping it doesn't toggle or log anything. `HabitCheckInStatusUiState.NotLogged` renders as
  an empty string (`MainScreen.kt:381`) — a not-yet-logged habit shows nothing next to its name,
  reading as a rendering glitch rather than an intentional state. Neither list has any visual cue
  (chevron/icon) that rows are navigable, compounding the missing-`DsCard` finding above.
- **Visual polish**: The two sections split screen height 50/50 unconditionally
  (`MainScreen.kt:131-132`) regardless of content volume — a user with many journal entries and one
  habit gets their journal list cramped while the habit section wastes half the screen on empty-state
  text. The Habit section has no contribution/history visualization at all (unlike Journal's grid),
  making it look thinner/less finished by comparison.
- **Strength worth preserving**: The FAB's single-vs-multi-action behavior
  (`MainScreen.kt:210-232`) is well-designed — auto-fires the sole action when only one exists,
  expands into multiple `DsExtendedFloatingActionButton`s otherwise.

### Add Journal Entry screen (+ "help me start")

`AddJournalEntryScreen.kt`, `AddJournalEntryViewModel.kt`

- **Design-system consistency**: Strong — full `Ds*` adoption, `DsSpacing` tokens, `stringResource`
  everywhere, `@PreviewLightDark` for both main content and the dialog. Minor: outer `Column`
  (`AddJournalEntryScreen.kt:107-135`) achieves spacing via per-child padding rather than a parent
  `Arrangement.spacedBy`, unlike the dialog's own `Column` (`AddJournalEntryScreen.kt:193`).
- **Missing UI states**: The "help me start" error message renders as plain `DsText`
  (`AddJournalEntryScreen.kt:221-223`) with no error color — easy to miss that generation failed.
  Once the 3x regeneration cap (FR-009) is hit, the regenerate chip just goes disabled with no label
  explaining why and no visible remaining-attempts counter
  (`AddJournalEntryScreen.kt:216-219`) — a real discoverability gap, not just polish. The regenerate
  chip itself has no in-flight spinner during a regenerate call (only the confirm button in the
  INPUT step has one).
- **UX/flow friction**: "Help me start" chip only renders for signed-in users
  (`AddJournalEntryScreen.kt:116-125`) — a signed-out user gets no hint the feature exists or that
  signing in would unlock it. No way to go from the PREVIEW step back to INPUT (e.g. to try a
  different tone) without a full dialog restart — though the regeneration cap correctly survives a
  restart (`resetForNewSession`, `AddJournalEntryViewModel.kt:190-200`, intentionally preserves
  `regenerationsUsed`).

### Journal Entry Detail screen (+ "help me refine")

`JournalEntryDetailScreen.kt`, `JournalEntryDetailViewModel.kt`

- **Design-system consistency**: Same strong `Ds*`/`DsSpacing`/`stringResource`/preview pattern as
  Add Journal Entry. The read view renders date + body via two bare `DsText` calls with no spacing
  and no typographic distinction (`JournalEntryDetailScreen.kt:117-129`) — the clearest
  "unfinished-looking" spot found in the whole journal flow. The edit-mode `DsTextField` has no
  `label` at all, unlike the equivalent field on Add Journal Entry.
- **Missing UI states (real gaps, not just polish)**:
  - **No loading state on open.** `isLoading` is correctly set by the ViewModel
    (`JournalEntryDetailViewModel.kt:44-53, 66-74`) but never read in the Compose content — the
    screen renders blank until `entry != null`.
  - **No not-found/load-failure state.** `getJournalEntry(id) ?: return@launch`
    (`JournalEntryDetailViewModel.kt:66`) leaves `isLoading = true` forever if the entry can't be
    found — blank screen forever, only a back button as an escape.
  - **24h edit-window lock (FR-006) is visually silent.** When `isEditable = false`, the Edit icon
    is simply omitted (`JournalEntryDetailScreen.kt:183-189`) with no lock icon or explanatory label
    — the user only discovers the rule reactively (missing Edit button, or a snackbar if they race
    the window closing).
  - The `MAX_REFINE_TEXT_LENGTH = 8000` gate on "help me refine" visibility
    (`JournalEntryDetailScreen.kt:141-144`) is the same kind of silent gate — chip just disappears
    past 8000 chars with no counter or explanation.
  - Same regenerate-cap/error-styling gaps as "help me start" (see above) — the two AI dialogs are
    near-duplicate implementations (`AddJournalEntryScreen.kt:184-278` vs.
    `JournalEntryDetailScreen.kt:225-316`), so any fix needs to land in both places today.
- **Strength worth preserving**: The race between an in-flight refine and the 24h window closing is
  explicitly handled (`JournalEntryDetailViewModel.kt:217-235`, documented in a code comment) — solid
  engineering, though the resulting UX (dialog vanishes, replaced by a generic snackbar) could be a
  more explicit "your edit window just closed" moment.

### Create Habit screen

`CreateHabitScreen.kt`, `CreateHabitViewModel.kt`

- **Design-system consistency**: Solid — full `Ds*` adoption. Only one preview state exists
  (`CreateHabitScreen.kt:170-185`) — no `isSaving`/`saveError`/disabled-save variants, unlike Habit
  Detail's multi-state `PreviewParameterProvider`.
- **Missing UI states**: `DsTextField` supports `isError`/`supportingText`
  (`DsTextField.kt:20-21`) but the name field never sets them
  (`CreateHabitScreen.kt:102-109`) — an empty-name state gives zero inline feedback beyond the
  disabled Save button.
- **UX/flow friction**: There is no explicit binary-vs-scale picker — `isBinary` is purely derived
  from `scaleSteps == 2` (`CreateHabitUiState.kt:15-17`, a deliberate design per its own code
  comment), but nothing in the UI explains this; the only signal is the live preview flipping
  between a Done/Not-done row and a numeric one as the stepper value changes. Back/Cancel both
  discard typed name/description with zero confirmation.

### Log Check-In screen

`LogHabitCheckInsScreen.kt`, `LogHabitCheckInsViewModel.kt`

- **Design-system consistency**: The "done" chip's dark/light colors are picked manually in the
  screen via `isSystemInDarkTheme()` (`LogHabitCheckInsScreen.kt:147-151`), duplicating
  theme-branching logic the design system already centralizes elsewhere (compare
  `contributionLevelColors()` in `DsContributionGrid.kt:82-84`). The `AlreadyLogged` chip renders as
  a real, rippling `FilterChip` with no `onClick` override — looks tappable but does nothing.
- **Missing UI states**: If the user has zero habits, the `LazyColumn` renders nothing — no "No
  habits yet — create one first" empty state/CTA (`LogHabitCheckInsScreen.kt:116-129`) — a real
  dead end for a new user who taps "Log Habits" before creating any habit.
- **Strength worth preserving**: Date selection is well-scoped — only today/yesterday are tappable,
  and the extra visible date cards are non-interactive centering only
  (`userScrollEnabled = false`, `DsDateStrip.kt:57`) — solid, discoverable support for FR-004's
  "log for yesterday" case with no traps.

### Habit Detail screen

`HabitDetailScreen.kt`, `HabitDetailViewModel.kt`

- **Design-system consistency**: Full `Ds*` adoption, best preview coverage of the three habit
  screens (a `PreviewParameterProvider` with 3 states including `saveError`). One hardcoded
  magic number: `.heightIn(max = 400.dp)` on the edit-sheet `LazyColumn`
  (`HabitDetailScreen.kt:310`), not a token.
- **Missing UI states (the most significant gaps in the habit flow)**:
  - `isLoading` is fully plumbed from the ViewModel (defaults `true`, `HabitDetailViewModel.kt:42`)
    but **never read** in the screen — first open flashes empty name/rows/grid with no loading
    indicator.
  - `isDeletingHabit`/`isDeletingCheckIn` are likewise **never read** — both delete dialogs
    (`HabitDetailScreen.kt:207-269`) use plain `DsAlertDialog`, which has no loading slot at all, so
    an in-flight delete gives no spinner/disabled feedback.
  - The edit `DsBottomSheet` never passes `saveEnabled` (defaults `true`,
    `DsBottomSheet.kt:35`), so Save is always tappable even with nothing pending — inconsistent with
    Create Habit and Log Check-In, which both correctly gate Save.
  - **FR-006 (24h edit window) is enforced but genuinely unclear.** A locked row renders via the
    same generic low-contrast "disabled" visual language (`DsSelectableCard.kt:47-59`) used for any
    disabled control anywhere in the app — no lock icon, no label, no proactive copy. The only
    explicit string for the rule (`habit_detail_edit_window_expired_error`) only fires reactively via
    snackbar in a narrow race condition.
  - No "no check-ins yet" empty state on the contribution timeline — a brand-new habit just renders
    a wall of `NONE`-level cells with no messaging.
- **Visual polish**: `DsContributionGrid`/`DsContributionTimeline` have **no legend** (no
  "Less → More" key) and **no month/day labels** — a first-time viewer has no way to interpret color
  intensity or locate a month beyond scroll position. This is the flagship visualization of the
  detail screen and genuinely hurts at-a-glance legibility. Cells also have no tap/long-press
  affordance to reveal the exact date/value.

## Code References

- `app/src/main/java/pl/luczka/todaywas/core/designsystem/theme/DsTypography.kt:10-17` — only
  `bodyLarge` is defined; every other type-scale slot is default/commented out. Root cause of the
  app-wide "no heading hierarchy" finding.
- `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingScreen.kt:129-152` — the bottom-bar
  "Continue"/"Skip" pair that overlaps with in-form CTAs during `SIGN_IN`/`SIGN_UP`/`DATA_SYNC_REVIEW`.
- `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingViewModel.kt:354-363` and
  `app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt:254-263` — sync failures
  silently swallowed, no error surfaced.
- `app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt:317-335,362-377` — list rows bypass
  `DsCard`/`DsHorizontalDivider`.
- `app/src/main/java/pl/luczka/todaywas/ui/main/MainViewModel.kt:93-129` — core data `combine()` has
  no error handling.
- `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailScreen.kt:117-129,183-189`
  — no loading state on open; silent 24h-lock affordance.
- `app/src/main/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryScreen.kt:184-278` and
  `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailScreen.kt:225-316` —
  near-duplicate "help me start"/"help me refine" dialog implementations.
- `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailScreen.kt` (whole file) —
  `isLoading`/`isDeletingHabit`/`isDeletingCheckIn` never read; 24h-lock uses generic disabled style.
- `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/contribution/DsContributionGrid.kt`
  and `DsContributionTimeline.kt` — no legend, no month/day labels.
- `app/src/main/java/pl/luczka/todaywas/ui/habit/logcheckin/LogHabitCheckInsScreen.kt:116-129` — no
  empty state when the user has zero habits.

## Architecture Insights

- **`Ds*` component discipline is genuinely well-followed** — across all ~15 screens/flows
  surveyed, only one raw-primitive leak was found (`GoogleSignInButton`'s `OutlinedButton`, a
  documented deliberate choice) and one raw theme-branch (`LogHabitCheckInsScreen`'s manual
  dark/light chip color). This confirms the project's `lessons.md` conventions are holding in
  practice, not just in a handful of showcase screens.
- **ViewModel state often outruns what the Compose layer reads.** Three separate screens
  (`JournalEntryDetailScreen`, `HabitDetailScreen` ×2 flags) have fully correct, tested-looking
  `isLoading`/`isDeleting*` state in their `UiState` that the screen composable simply never
  consumes. This looks like a recurring pattern (state added to the ViewModel/UiState during
  implementation, but the corresponding Compose branch was never wired in) rather than isolated
  mistakes — worth a systematic pass across all screens' loading/deleting flags, not just the ones
  flagged here.
- **The 24h edit-window rule (FR-006, a Hard Rule in CLAUDE.md) is correctly enforced at the data
  layer everywhere it was checked, but has no shared UI treatment.** Every screen that touches it
  invented its own (non-)explanation — Journal Detail omits the Edit button silently, Habit Detail
  uses the generic disabled style, and the only explicit copy is a reactive snackbar reachable only
  via a narrow race. A single shared "locked" affordance (icon + label + optionally a proactive
  hint) would fix this once instead of per-screen.
- **AI-assist entry points (FR-009/FR-010) use a "silent gate" pattern consistently**: sign-in
  requirement, regeneration cap, and refine text-length cap all hide their trigger chip outright
  with no explanation, rather than showing a disabled state with a reason. This is a deliberate-looking
  pattern (used the same way in two independent files) rather than an oversight, but it's a real
  discoverability cost worth reconsidering as a pair.

## Historical Context (from prior changes)

- `context/changes/ux-refinements/` (status `impl_reviewed`, created 2026-08-11) is the change that
  built the current hub main screen (bottom-nav removal, single generic account icon regardless of
  sign-in state — `context/changes/ux-refinements/change.md:17`). It documents no known open
  main-screen issues beyond what shipped, so the Main-screen findings above (missing `DsCard`,
  asymmetric section spacing, 50/50 fixed split, no loading/error state) are all new since that
  change closed. As previously flagged in `context/changes/ui-improvements/change.md`, this prior
  change was never archived — still out of scope for this change, just noted again here.
- `context/foundation/lessons.md` conventions (Ds* component usage, `strings.xml`-only text,
  `@PreviewLightDark` on every screen/component, cross-layer mapper files) are the yardstick used to
  evaluate "design-system consistency" above — the findings are gaps *relative to* those already-
  documented rules, not new conventions being proposed.

## Related Research

None — this is the first research artifact for `ui-improvements`.

## Open Questions

- Should the typography-hierarchy fix (filling in `DsTypography`'s missing type-scale slots) be
  scoped as a design-system-level task at the start of this change, since nearly every screen's
  "visual polish" finding traces back to it? Doing it once centrally vs. screen-by-screen will
  materially change how the eventual plan is sequenced.
- Should the 24h edit-window "locked" affordance (Journal Detail + Habit Detail) become a single
  shared `:core:designsystem` pattern/component, given both screens independently reinvented (or
  under-invented) the same treatment?
- Should the "help me start"/"help me refine" dialogs be de-duplicated into one shared composable as
  part of this UI pass, or is that refactor out of scope (UI-only change, not a structural one)?
- The onboarding bottom-bar "Continue"/"Skip" overlapping-CTA problem is the single most confusing
  flow issue found — does the user want this treated as a priority-one fix, or folded in alongside
  the rest at whatever order the plan settles on?

## Next Step

This research is meant to seed a screen-by-screen collaborative pass, not a finished backlog — the
user wants to add their own improvement ideas per screen (visual/flow/other) before this moves to
`/10x-plan`. Suggested order to walk through together, matching the user flow: Onboarding (Welcome →
Account setup → All Set) → Account/Auth → Main/Hub → Add Journal Entry → Journal Entry Detail →
Create Habit → Log Check-In → Habit Detail.
