# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## ViewModels expose a sealed Intent + onIntent(), and a sealed UiEvent flow

- **Context**: ViewModels under ui/<feature>/
- **Problem**: ViewModels grow ad-hoc public methods per action and single-purpose Flow<Unit> events that don't scale to new event types, drifting from MVI.
- **Rule**: ViewModels expose state via a single `fun onIntent(intent: XxxIntent)` dispatching a sealed `XxxIntent` (no per-action public methods), and a generic sealed `XxxUiEvent` exposed as `val events: Flow<XxxUiEvent>` for one-shot effects (never a single-purpose `Flow<Unit>`). Each state/intent/event/enum type lives in its own file under `ui/<feature>/`.
- **Applies to**: plan, plan-review, implement, impl-review

## All user-facing Compose UI text goes in strings.xml

- **Context**: All Compose UI in :app
- **Problem**: Hardcoded strings in composables (e.g. the first draft of MainScreen.kt/OnboardingDialog.kt) block localization and make copy changes require hunting through composables instead of one strings.xml.
- **Rule**: All user-facing Compose UI text must live in `app/src/main/res/values/strings.xml`, referenced via `stringResource(R.string.xxx)` — never a hardcoded string literal in a composable. Preview-only sample text is exempt since it's never shipped to users.
- **Applies to**: plan, plan-review, implement, impl-review

## Custom dialogs build on Material3's real slot components, not a bare Dialog + manual layout

- **Context**: Any dialog in :core:designsystem/:app
- **Problem**: `TodayWasDialog` was first built on a bare `Dialog`/`BasicAlertDialog` with content
  in one trailing lambda, so it had no background/shape/elevation, and `OnboardingDialog` had to
  hand-roll a `Box` with "Skip" pinned via `Modifier.align(Alignment.TopEnd)` instead of a real
  action row — two rounds of rework on the same file to get ordinary M3 dialog behavior for free.
- **Rule**: Build custom dialogs on the M3 component that already models the shape you need (e.g.
  `AlertDialog`'s `title`/`text`/`confirmButton`/`dismissButton` slots for title+body+actions
  dialogs) instead of a bare `Dialog`/`BasicAlertDialog` plus manual `Surface`/`Box` positioning.
  Only drop to the lower-level primitives when the content genuinely doesn't fit any slotted M3
  component's shape.
- **Applies to**: plan, plan-review, implement, impl-review

## A flow gets its own ViewModel once it's a screen, not an overlay on another screen's ViewModel

- **Context**: Any multi-step flow (dialog, wizard, bottom sheet) hosted by another screen
- **Problem**: Onboarding started as a dialog hosted by `MainScreen`, so its state/intents/events
  lived inside `MainViewModel`. When onboarding was later promoted to its own full screen,
  `MainViewModel` had to be gutted (it existed only to hold onboarding state) and a new
  `OnboardingViewModel` extracted — churn that a screen-scoped ViewModel from the start would have
  avoided.
- **Rule**: A flow embedded in a host screen (dialog, bottom sheet) can reasonably live in the
  host's ViewModel while it's small. The moment it becomes its own top-level screen (own
  navigation entry, own lifecycle), give it its own ViewModel/state/intent/event files in its own
  `ui/<feature>/` package — don't leave screen-scale state living inside a different screen's
  ViewModel just because that's where it started.
- **Applies to**: plan, plan-review, implement, impl-review

## Root/screen-level navigation uses Navigation 3, not ad-hoc state switches

- **Context**: Any top-level screen selection (root routing, future bottom-nav tabs)
- **Problem**: Root routing started as a hand-rolled `RootUiState` sealed interface + `when` in
  `TodayWasApp`. It worked, but a second navigation mechanism would have been needed later for a
  bottom nav bar inside `MainScreen`, rather than reusing the same tool.
- **Rule**: Use Navigation 3 (`androidx.navigation3` — verify the current stable version and API
  shape via docs/web search before coding, it moves fast and guessing versions wastes time on
  dependency-resolution errors) for screen-level navigation, including the root
  Onboarding-vs-Main decision. Keep the root `NavDisplay` minimal; a future bottom nav bar gets
  its own nested Nav3 setup (its own per-tab back stacks) inside whichever screen hosts it — a
  separate concern from root routing, so adding tabs later never requires touching the root
  `NavDisplay`/ViewModel again. Skip `lifecycle-viewmodel-navigation3`/`entryDecorators` and
  `adaptive-navigation3` until per-entry ViewModel scoping or large-screen multi-pane layouts are
  actually needed — the minimal `NavDisplay` setup works fine without them.
- **Applies to**: plan, plan-review, implement, impl-review

## Every screen and design-system component ships with light/dark previews

- **Context**: Every screen and :core:designsystem component
- **Problem**: Phase 4's components/screens were built with no previews, so verifying appearance required a working emulator (which wasn't available in this environment) instead of just opening the file.
- **Rule**: Every new screen and :core:designsystem component ships with @PreviewLightDark preview(s) covering light and dark mode. When a composable has multiple meaningful variants/states (enabled/disabled, selected/unselected, error/loading/success, multi-step), use @PreviewParameter with a PreviewParameterProvider instead of copy-pasting one preview per variant. Design-system previews use a plain MaterialTheme wrapper (DesignSystemPreviewTheme), never :app's TodayWasTheme, since :core:designsystem must not depend on :app.
- **Applies to**: plan, plan-review, implement, impl-review
