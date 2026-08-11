# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## A Result-returning wrapper should not be re-wrapped in another runCatching by its callers

- **Context**: `data/repository/HabitRepositoryImpl.kt` / `JournalRepositoryImpl.kt`'s push-in-background methods, and any future caller of `RemoteCall.kt`'s `remoteCall {}` helper.
- **Problem**: `remoteCall {}` already catches all non-`CancellationException` exceptions internally and returns `Result.failure` — it never throws except `CancellationException`. Callers (`pushHabitInBackground`, `pushCheckInsInBackground`, `pushInBackground`) wrapped the call in an outer `runCatching {}` anyway, which is redundant and — worse — silently swallows `CancellationException`, a general coroutines anti-pattern (cancellation should always propagate).
- **Rule**: Once a helper already returns `Result`, callers must not re-wrap it in `runCatching` — call it directly. If cancellation-safety matters at the call site, catch `CancellationException` explicitly and rethrow rather than adding a blanket `runCatching`.
- **Applies to**: plan, implement, impl-review

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
  **Update (journal-daily-entry)**: per-entry ViewModel scoping stopped being optional the moment
  a screen's ViewModel takes per-navigation data via Hilt assisted injection (e.g.
  `AddJournalEntryViewModel`'s `availableSlots`) and can be re-entered more than once. Without
  `rememberViewModelStoreNavEntryDecorator()`, revisiting the same destination type reuses the
  *first* visit's cached ViewModel instance — the assisted-injected constructor args from later
  visits are silently ignored, and any retained state (typed text, etc.) leaks across visits. This
  surfaced as a real bug (re-opening "Add entry" for a second day showed the first day's stale
  text and stale addable-slots). Add `lifecycle-viewmodel-navigation3` and wire
  `rememberViewModelStoreNavEntryDecorator()` (alongside `rememberSaveableStateHolderNavEntryDecorator()`)
  as soon as any screen reached via Nav3 needs a ViewModel constructed from per-entry data.
- **Applies to**: plan, plan-review, implement, impl-review

## Cross-layer mapping (entity↔domain, domain↔UI) lives in its own dedicated file

- **Context**: Any repository or ViewModel mapping between two layers' data shapes
- **Problem**: The first draft of `JournalRepositoryImpl` mapped `JournalEntryEntity` to
  `JournalEntry` inline as a private extension function inside the repository class itself.
- **Rule**: Put mapping extension functions (`fun XxxEntity.toDomain(): Xxx`, `fun Xxx.toUiState(): XxxUi`, etc.) in their own file named after what they map (e.g. `JournalEntryEntityMapper.kt`), not as a private method buried inside the repository/ViewModel class that happens to use them. Applies at every layer boundary — entity→domain in repositories, domain→UI-facing shape in ViewModels.
- **Applies to**: plan, plan-review, implement, impl-review

## Every screen and design-system component ships with light/dark previews

- **Context**: Every screen and :core:designsystem component
- **Problem**: Phase 4's components/screens were built with no previews, so verifying appearance required a working emulator (which wasn't available in this environment) instead of just opening the file.
- **Rule**: Every new screen and :core:designsystem component ships with @PreviewLightDark preview(s) covering light and dark mode. When a composable has multiple meaningful variants/states (enabled/disabled, selected/unselected, error/loading/success, multi-step), use @PreviewParameter with a PreviewParameterProvider instead of copy-pasting one preview per variant. Both screen and design-system previews wrap in DsTheme (core.designsystem.theme) — the app's real theme lives in :core:designsystem, not :app, so there's no separate placeholder preview theme to keep in sync.
- **Applies to**: plan, plan-review, implement, impl-review

## Tests are named `should [outcome] when [scenario]` in backticks and structured as commented AAA

- **Context**: All JUnit4 tests under `app/src/test/`
- **Problem**: Test names like `` `NameChanged updates name` `` or `` `SaveClicked failure sets saveError and does not emit Saved` `` describe the stimulus but not the expected outcome, and test bodies mixed setup, action, and assertion inline with no visual separation — both readable enough at a glance but slower to scan for what a failure actually means.
- **Rule**: Test method names use backtick-quoted `` `should [expected outcome] when [scenario]` `` (e.g. `` `should set saveError and not emit Saved when SaveClicked fails` ``). Test bodies are structured as Arrange/Act/Assert with a `// Arrange`, `// Act`, `// Assert` comment above each section — an exception to the project's default no-comments rule, since these three are structural labels, not "what does this code do" explanations. Omit a section's comment (and the section itself) when a test has nothing to arrange (e.g. it only uses class-level fixture fields) — don't write an empty labeled block.
- **Applies to**: plan, plan-review, implement, impl-review
