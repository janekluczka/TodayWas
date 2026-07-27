# Onboarding Focus Pick — Plan Brief

> Full plan: `context/changes/onboarding-focus-pick/plan.md`

## What & Why

Implement FR-001: a one-time, four-step onboarding popup (Welcome → Focus selection → Account →
All set), persisted locally. A persistent "Skip" action is available on every step; back always
navigates to the previous step (exiting the app from Welcome). This is roadmap slice `S-01` — the
only prerequisite for the north star (`S-02`, daily journaling) — and the first feature built in
this repo, so it also establishes the app's data/domain/presentation architecture, Hilt-based DI,
and the repo's first Gradle module split (`:core:designsystem`).

## Starting Point

The codebase is the unmodified Android Studio Compose template: `MainActivity` renders a static
"Hello Android" greeting. No navigation, persistence, ViewModel, or DI exists yet.

## Desired End State

On first launch, a non-dismissible popup walks the user through Welcome → Focus selection → Account
(informational, "Create account" is a no-op for now) → All set. "Skip" is visible throughout: before
a focus is confirmed it defaults to "Both"; after, it preserves whatever was already chosen. Back
always returns to the previous step, exiting the app from Welcome. The choice persists across
restarts and the flow is never shown again. "Change focus" in the top bar lets the user revise it
later via a normal dismissible dialog showing only the Focus-selection step.

## Key Decisions Made

| Decision                          | Choice                                             | Why (1 sentence)                                                                 | Source |
| ---------------------------------- | --------------------------------------------------- | --------------------------------------------------------------------------------- | ------ |
| Popup treatment                    | Non-dismissible modal Dialog over dimmed main screen | Matches the "popup over main screen" product intent exactly.                      | Plan   |
| Main-screen backdrop                | Minimal empty-state shell, built now                 | Gives the popup something real to sit over; becomes `S-02`'s foundation, not throwaway. | Plan   |
| Persistence                        | Room (not DataStore)                                 | The focus choice must be part of `S-06`'s future account-sync payload — one database is simpler than two. | Plan   |
| Onboarding structure               | Four steps: Welcome → Focus selection → Account → All set | Explicit product decision — gives orientation and a clean landing around the required decision and the optional nudge. | Plan   |
| Skip                                | Persistent "Skip" button on every step, no confirmation | Explicit product decision, replacing the earlier back-press-triggered confirmation — a deliberate button tap doesn't need a second confirmation. | Plan   |
| Skip's effect                      | Defaults to "Both" only if no focus is confirmed yet; otherwise preserves the existing choice | Skip must never silently overwrite a real choice the user already made.           | Plan   |
| Back-press behavior                | Always navigates to the previous step; exits the app from Welcome | Explicit product decision, replacing the earlier per-step-specific back behavior with one uniform rule. | Plan   |
| Re-pick scope                      | In scope — "Change focus" top-bar action, Focus-selection step only | Avoids a UX dead-end for users who change their mind; explicit product decision.  | Plan   |
| Write-failure handling             | Retry once silently, then inline error + "Try again"  | Local disk-write failures are rare but real; avoids silently losing the user's choice. | Plan   |
| Testing scope                      | Unit tests only (Robolectric for Room, fakes for ViewModel/use cases) | Matches project's current zero-test baseline and the `speed` roadmap goal; no instrumented Compose test. | Plan   |
| Architecture                       | Data → Domain (use cases) → Presentation (single `MainViewModel`) | Explicit product/architecture decision — one ViewModel per screen, not per widget; use cases wrap repository calls. | Plan   |
| DI                                 | Hilt                                                   | Explicit product decision to establish Hilt as the project's DI framework starting with this slice. | Plan   |
| Account-creation nudge             | Informational step; "Create account" is a no-op       | Lets the user know saving progress is possible without pulling `F-01`'s real Supabase/Google auth work into this slice. | Plan   |
| Component library                  | `TodayWas*` wrappers for every Material3 component this slice touches, not just buttons | Explicit product decision — establishes the full design-system contract from the first screen rather than piecemeal later. | Plan   |
| Component module                   | New `:core:designsystem` Gradle module, not a package inside `:app` | Explicit product decision for modularity — `:app` depends on it, never the reverse. | Plan   |
| Theme location                     | `ui/theme/` (`TodayWasTheme`) stays in `:app`, not moved into `:core:designsystem` | Compose theming is ambient (`CompositionLocal`), so the components module never needs to import it — no circular-dependency risk either way, this was a scope choice. | Plan   |

`TodayWasTheme` already exists in `ui/theme/`, so the wrappers follow that naming precedent
(`TodayWasButton`, `TodayWasText`, ...) rather than a generic `App*` prefix — a naming call made
directly, not asked, since it's low-stakes and the precedent already exists in the repo. Worth
flagging plainly: combining "`:core:designsystem`" as the module name with "theme stays in `:app`"
means the module doesn't actually contain a theme, just components — a deliberate scope choice, not
an inconsistency to fix later.

## Scope

**In scope:**
- Room database + entity/DAO for the onboarding/focus state
- Hilt DI bootstrap (`Application` class, database/repository modules, `@HiltViewModel`)
- `OnboardingRepository`, two use cases, `MainViewModel` (4-step state machine, Skip, back-navigation)
- New `:core:designsystem` Gradle module — `settings.gradle.kts`/version-catalog/`build.gradle.kts`
  wiring, plus `TodayWasButton`/`TextButton`/`Text`/`Scaffold`/`TopBar`/`IconButton`/`Icon`/`Dialog`/
  `RadioOption`/`LoadingIndicator`, one wrapper per Material3 component this slice uses
- `MainScreen` shell + `OnboardingDialog` (Welcome / Focus-pick / Account / All-set steps, mandatory
  and re-pick modes, persistent Skip button) in `:app`, built entirely from `:core:designsystem`
- "Change focus" re-pick entry point
- One-shot `exitAppEvent` for exiting the app from the Welcome step's back press

**Out of scope:**
- A dedicated settings screen (re-pick reuses the same dialog)
- Real account creation (the Account step's button is a no-op — `F-01`'s job)
- A confirmation dialog on Skip (removed — a labeled button tap is already deliberate)
- Custom design tokens/styling in the component wrappers — they're thin passthroughs onto default
  Material3 for now, not a themed design system yet
- Moving `ui/theme/`/`TodayWasTheme` into `:core:designsystem` — stays in `:app`
- Further module splitting (e.g., `data`/`domain` into their own modules) — one new module only
- A dedicated test source set for `:core:designsystem` — stateless wrappers, nothing to unit test
- Room migrations, cloud sync of the preference itself
- Instrumented Compose UI tests

## Architecture / Approach

Three layers, introduced here for the first time via Hilt: `TodayWasDatabase`/`UserPreferencesDao`
→ `OnboardingRepository` (Data) → `ObserveOnboardingStateUseCase`/`SelectFocusUseCase`/
`SkipOnboardingUseCase` (Domain) → `MainViewModel` (Presentation), consumed by `MainScreen` and
`OnboardingDialog`. Retry-once write resilience lives in the Repository; the skip-defaults-to-Both
business rule lives in `SkipOnboardingUseCase`, but the ViewModel decides *whether* to invoke it at
all based on whether a focus is already confirmed. Mandatory onboarding is four dialog steps
(`WELCOME` → `FOCUS_PICK` → `ACCOUNT_INFO` → `ALL_SET`); re-pick stays on `FOCUS_PICK` only, with no
Skip button. A `Dialog` can't hand back-press straight to the host `Activity`, so exiting from
Welcome goes through a one-shot `exitAppEvent` flow rather than a state flag. Both screens are built
exclusively from the new `:core:designsystem` module's `TodayWas*` wrappers — no raw Material3
composable is used directly in `MainScreen`/`OnboardingDialog`. `:core:designsystem` depends only on
Compose/Material3 (external), never on `:app`; it doesn't need `TodayWasTheme` at compile time since
Compose theming is ambient — `:app` applies `TodayWasTheme` once at the root and every wrapper
picks it up automatically, regardless of which module defined it.

## Phases at a Glance

| Phase                  | What it delivers                                      | Key risk                                                |
| ------------------------ | ------------------------------------------------------- | ---------------------------------------------------------- |
| 1. Data layer (Room + Hilt)| Entity, DAO, database, repository with retry-once writes, Hilt bootstrap | First-ever Room/KSP/Hilt wiring in this repo — version catalog drift risk |
| 2. Domain (use cases)     | Two use cases, skip-defaults-to-Both rule                | Low — thin wrappers, unchanged by the 4-step/Skip rework    |
| 3. Presentation (ViewModel)| Single `MainViewModel`, full 4-step state machine, Skip's dual behavior, back-navigation, `exitAppEvent` | State-machine correctness across mode × step combinations; the one-shot event pattern for app-exit |
| 4. UI                     | New `:core:designsystem` module + 10 `TodayWas*` component wrappers, then `MainScreen` shell + `OnboardingDialog` (4 steps, persistent Skip) built from them, wired into `MainActivity` | The `Dialog`-scoped back-press + one-shot exit-event wiring is non-obvious; first-ever multi-module Gradle wiring in this repo (see plan's Critical Implementation Details) |

**Prerequisites:** None — this is the roadmap's first `ready` slice.
**Estimated effort:** Not estimated per roadmap convention (no time units) — 4 phases, each
independently verifiable.

## Open Risks & Assumptions

- Assumes Room + Hilt + KSP versions all compatible with Kotlin 2.2.10 are resolved at implementation
  time (not pinned in the plan to avoid stating a guessed version number).
- Assumes `fallbackToDestructiveMigration()` remains acceptable until the app ships — must be
  revisited before any real release.
- Assumes it's acceptable that a user who picks a focus and is then killed before reaching the
  Account/All-set steps simply never sees them (no second persisted flag added to guarantee they show).
- Assumes a Compose `Dialog`'s back-press scope genuinely doesn't bubble to the host `Activity` —
  the one-shot `exitAppEvent` design depends on this; worth a quick sanity check early in Phase 4.
- First-ever multi-module Gradle setup in this repo — the `android-library` plugin, module
  namespacing, and cross-module Compose dependency resolution are all new mechanics for this
  project; worth verifying the module builds cleanly before writing all ten components.

## Success Criteria (Summary)

- A first-time user is walked through all four steps in order, or exits early via Skip at any point,
  and the outcome is remembered permanently.
- Skip never overwrites a focus the user already explicitly chose.
- Back press always feels like "go back one step," including exiting the app from the very first
  screen.
- A returning user can change their focus via "Change focus" without re-triggering the full flow.
- All four phases pass their automated checks; Phase 4's manual checklist confirms the end-to-end
  flow works on a real build.
