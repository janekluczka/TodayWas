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

## Every screen and design-system component ships with light/dark previews

- **Context**: Every screen and :core:designsystem component
- **Problem**: Phase 4's components/screens were built with no previews, so verifying appearance required a working emulator (which wasn't available in this environment) instead of just opening the file.
- **Rule**: Every new screen and :core:designsystem component ships with @PreviewLightDark preview(s) covering light and dark mode. When a composable has multiple meaningful variants/states (enabled/disabled, selected/unselected, error/loading/success, multi-step), use @PreviewParameter with a PreviewParameterProvider instead of copy-pasting one preview per variant. Design-system previews use a plain MaterialTheme wrapper (DesignSystemPreviewTheme), never :app's TodayWasTheme, since :core:designsystem must not depend on :app.
- **Applies to**: plan, plan-review, implement, impl-review
