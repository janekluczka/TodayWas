# Repository Guidelines

TodayWas is a native Android app (Kotlin + Jetpack Compose) — low-effort daily journaling and
optional habit tracking with a GitHub-contribution-style history view. See
@context/foundation/prd.md for full product scope and @context/foundation/tech-stack.md for stack
decisions.

## Hard rules

- Journal/habit entries are editable only within 24 hours of creation (FR-006); after that they
  become read-only for editing. Enforce this at write time, not just in the UI. Deletion is the
  one exception — a journal entry, habit, or habit check-in can be deleted at any time regardless
  of age, by design (see `context/changes/entry-delete/`).
- Core journaling and habit tracking must work with zero account (FR-008) — never gate them behind
  sign-in.
- AI features never read journal or habit history, with one named exception: "help me refine"
  additionally sends the text of the single journal entry actively being refined — and only that
  entry, only for that one request, never persisted or logged beyond it (FR-010). "help me start"
  is unchanged — inputs are only the tone pick and the optional thoughts typed in the moment
  (FR-009). Both require a signed-in account (Supabase session); core journaling/habit-tracking
  above stays account-free.

## Project Structure

Multi-module Gradle project: `:app` (application, package `pl.luczka.todaywas`) and
`:core:designsystem` (reusable Material3 wrappers, package
`pl.luczka.todaywas.core.designsystem`; must never depend on `:app`).

```
app/src/main/java/pl/luczka/todaywas/
  data/
    local/
      entity/         — Room entities
      dao/            — Room DAOs
      database/       — the Room database class
    remote/
      dto/            — remote-service-serializable payload types
      api/            — remote data source interfaces + impls
    repository/       — repository implementations only
    mapper/           — entity/dto↔domain mappers + other data-layer error/state mappers
    util/             — generic data-layer helpers
  di/                 — every Hilt @Module in the app
  domain/
    model/            — domain models
    usecase/          — one use case per action, invoked from ViewModels
    util/             — stateless business-rule/algorithm objects
    repository/       — repository interfaces
  ui/
    model/            — UI-facing models only (*UiState.kt / *Ui.kt)
    mapper/           — domain→UI mapper functions targeting a shared ui/model/ type
    <feature>/        — XxxIntent/UiState/UiEvent/ViewModel/Screen per screen or flow
      <subflow>/      — when a feature bundles more than one independent flow

core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/
  components/<type>/  — DsXxx Material3 wrappers, grouped like Material3's own groupings
  theme/              — the app's Compose theme + its semantic color/type tokens
  tokens/             — reusable design tokens (spacing, etc.) beyond the theme itself
  preview/            — shared preview providers
```

Conventions the tree above doesn't spell out:

- A Hilt `@Module` never lives inside a `data/` subpackage — it goes in `di/`, alongside every
  other module in the app.
- `data/repository/` holds only implementations; the interfaces they implement live in
  `domain/repository/`, not alongside their impl.
- `ui/mapper/` is a flat sibling of `ui/model/`, not nested inside it — same relationship as
  `data/mapper/` to `data/local/`/`data/remote/`.
- A flow's own mapper/util file (mapping to a type private to that flow, not a shared `ui/model/`
  type) sits flat alongside its `Intent`/`UiState`/etc. files — it only earns a nested
  `mapper/`/`util/` subpackage once there's more than one such file to group.
- When a feature package bundles more than one independent flow, it splits into
  `ui/<feature>/<subflow>/` — one subpackage per flow, each with its own full
  `Intent`/`UiEvent`/`UiState`/`ViewModel`/`Screen` set. A constant or type genuinely shared across
  two subflows of the same feature is declared in one subflow and imported cross-package by the
  other, rather than duplicated.
- Tests: `app/src/test/` (JUnit4 unit), `app/src/androidTest/` (instrumented).
- `context/foundation/` — PRD, tech-stack, roadmap, lessons.md (see "Working in this repo" below).
- `context/changes/<change-id>/` — in-flight change docs; archived to `context/archive/` when done.

## Architecture & Kotlin Conventions

Baseline Android guidance comes from the `android-guidelines` skill (agent-toolkit `android`
plugin, official developer.android.com recommendations). This file only adds project facts and the
deliberate deviations listed under "Android guideline overrides" below.

MVI per screen: a ViewModel exposes `uiState: StateFlow<XxxUiState>` and a single
`fun onIntent(intent: XxxIntent)` dispatching a sealed `XxxIntent` to private `onXxx()` handler
methods (no per-action public methods). When the state derives from data streams, build it with
`combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.

No ViewModel→UI event streams (`Channel`/`SharedFlow`). Model outcomes as state instead:

- Navigation-only taps (open a detail, cancel, back with no logic) never reach the ViewModel —
  the screen calls its navigation lambda directly.
- Navigation that follows logic (saved, deleted, expired) is a `pendingNavigation: XxxNavigation?`
  field in the UI state; the screen reacts in `LaunchedEffect(uiState.pendingNavigation)` and then
  sends `XxxIntent.NavigationHandled`, which clears it.
- One-off messages are a `userMessage` field shown in a snackbar and cleared by
  `XxxIntent.UserMessageShown`.

Never expose a `domain.model` type through `UiState`/`Intent`/`Event` — always map to a `ui.model`
type first, in its own dedicated `*Mapper.kt` file (e.g. `JournalEntryMapper.kt`,
`FocusMapper.kt`), not inline in the ViewModel. The same rule applies one layer down: entity↔domain
mapping lives in its own `*EntityMapper.kt`, never as a private method inside the repository.

A flow (dialog/wizard) can live inside its host screen's ViewModel while it's small; once it
becomes its own top-level screen (own nav entry, own lifecycle) it gets its own `ui/<feature>/`
package and ViewModel. Root and screen-level navigation uses Navigation 3 (`NavDisplay`) — verify
the current API shape before coding, it moves fast.

Custom dialogs build on Material3's slotted components (e.g. `AlertDialog`'s
`title`/`text`/`confirmButton`/`dismissButton`) rather than a bare `Dialog` + manual layout, unless
the content genuinely doesn't fit any slotted shape.

All user-facing Compose text goes in `strings.xml` via `stringResource(R.string.xxx)` — never
hardcoded (preview-only sample text is exempt). Every screen and `:core:designsystem` component
ships `@PreviewLightDark` preview(s) (use `@PreviewParameter` for multiple states instead of
copy-pasted previews), wrapped in `DsTheme` (from `:core:designsystem`'s `theme/` package) — the
same theme the real app renders with, since the theme lives in the design system module, not `:app`.

## Android guideline overrides

Deliberate deviations from the `android-guidelines` baseline — follow these over it.

- UI actions: a single `onIntent(Intent)` entry point per ViewModel (MVI) instead of per-action
  methods — one dispatch surface per screen.
- Domain layer: mandatory, one use case per action, and ViewModels go through use cases rather
  than calling repositories directly — keeps the 24h edit rule (FR-006) and similar business rules
  in one testable place.
- Use cases depend on repositories only, never on other use cases (official guidance allows
  use-case chains) — shared logic goes into a combinator use case built on the repository.
- Design-system components use the `Ds` prefix (`DsButton`, `DsTopBar`) to keep them apart from the
  Material3 components they wrap.

## Coding Style & Naming Conventions

Kotlin, ktlint-enforced (official style, with project overrides in `@.editorconfig`:
`@Composable` functions stay PascalCase, a blank line is allowed after a class's opening brace,
`class Foo @Inject constructor(...)` stays on one line, `val x = call(` keeps its args wrapped
below rather than after a line break, and a multi-line parameter list can still share its closing
`) : Type =` with the expression). Compile target is Java 17 (`@app/build.gradle.kts`). Dependency
versions live in `@gradle/libs.versions.toml` — add new dependencies there, not as inline version
strings.

Additional project style, not ktlint-enforced but followed by convention (`ktlintFormat` does not
fix these — check by hand):

- A call with more than one named argument: one argument per line, opening paren on the same line
  as the call.
- Composable functions: `modifier: Modifier = Modifier` is the first optional parameter (after the
  required ones, before other optional ones and a trailing content lambda), per the Compose API
  guidelines and lint's `ModifierParameter`; a modifier chain of more than one call wraps one call
  per line.
- `@Immutable`/`@Stable` only where value-equality skipping is wanted and `equals()` is cheap —
  strong skipping is on by default, so don't annotate every UI state class by habit, and be
  careful with models holding large lists.
- Prefer collapsing `val x = when (...) { ... }` directly into the assignment over a separate
  mutable variable.

## Testing Guidelines

JUnit4 for local unit tests in `app/src/test/java/pl/luczka/todaywas/`; AndroidX Test + Espresso for
instrumented tests in `app/src/androidTest/`. No coverage threshold is enforced yet.

## Build, Test, and Development Commands

- `./gradlew.bat testDebugUnitTest` — run local unit tests.
- `./gradlew.bat ktlintCheck` — lint check; must pass before commit.
- `./gradlew.bat ktlintFormat` — auto-fix formatting.
- `./gradlew.bat assembleDebug` — build a debug APK.
- `./gradlew.bat connectedAndroidTest` — instrumented tests (requires a connected device/emulator).

## Commit & Pull Request Guidelines

Commit messages follow Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `style:`
observed in history). No CI pipeline or PR template exists yet — see
@context/foundation/health-check.md.

## Security & Configuration Tips

`local.properties` is gitignored — put local API keys (Supabase, Gemini) there once wired up, never
hardcode them.

## Working in this repo (10xDevs workflow)

This project uses the 10xDevs skill set for planning and implementation. Before planning or
implementing a change, check @context/foundation/lessons.md — an append-only register of recurring
rules (the Architecture & Kotlin Conventions above are drawn from it) — and whichever
`context/foundation/` doc is relevant (`prd.md`, `tech-stack.md`, `roadmap.md`). New work starts
with `/10x-new` under `context/changes/<change-id>/` (identity file `change.md`, plus
frame/plan/review artifacts); foundation docs are edited in place, never copied; a completed change
is archived with `/10x-archive` into `context/archive/`, which is read-only by convention.
