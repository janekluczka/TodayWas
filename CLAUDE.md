# Repository Guidelines

TodayWas is a native Android app (Kotlin + Jetpack Compose) — low-effort daily journaling and
optional habit tracking with a GitHub-contribution-style history view. See
@context/foundation/prd.md for full product scope and @context/foundation/tech-stack.md for stack
decisions.

## Hard rules

- Journal/habit entries are editable only within 24 hours of creation (FR-006); after that they
  become read-only. Enforce this at write time, not just in the UI.
- Core journaling and habit tracking must work with zero account (FR-008) — never gate them behind
  sign-in.
- AI features ("help me start", "help me refine") never read journal or habit history — inputs are
  only the tone pick and the optional thoughts typed in the moment (FR-009). They also require a
  signed-in account (Supabase session); core journaling/habit-tracking above stays account-free.

## Project Structure

Multi-module Gradle project: `:app` (application, package `pl.luczka.todaywas`) and
`:core:designsystem` (reusable Material3 wrappers, package
`pl.luczka.todaywas.core.designsystem`; must never depend on `:app`).

- `app/src/main/java/pl/luczka/todaywas/`
  - `data/local/` — Room entities, DAOs, `TodayWasDatabase`, Hilt `DatabaseModule`.
  - `data/repository/` — repository interfaces + impls, entity↔domain mappers (`*EntityMapper.kt`).
  - `domain/model/` — domain models.
  - `domain/usecase/` — one use case per action, invoked from ViewModels.
  - `ui/model/` — UI-facing models (`*UiState`) and their domain→UI mappers (`*Mapper.kt`), shared
    across features.
  - `ui/<feature>/` — one package per screen/flow: `XxxIntent.kt`, `XxxUiState.kt`,
    `XxxUiEvent.kt`, `XxxViewModel.kt`, `XxxScreen.kt`.
  - `ui/theme/` — Compose theme.
- `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/` —
  `TodayWasXxx` wrappers over Material3 components; `preview/` — shared preview theme/providers.
- Tests: `app/src/test/` (JUnit4 unit), `app/src/androidTest/` (instrumented).
- `context/foundation/` — PRD, tech-stack, roadmap, lessons.md (see "Working in this repo" below).
- `context/changes/<change-id>/` — in-flight change docs; archived to `context/archive/` when done.

## Architecture & Kotlin Conventions

MVI per screen: a ViewModel exposes `uiState: StateFlow<XxxUiState>`, a single
`fun onIntent(intent: XxxIntent)` dispatching a sealed `XxxIntent` to private `onXxx()` handler
methods (no per-action public methods), and one-shot effects via `val events: Flow<XxxUiEvent>`
backed by a buffered `Channel` — never a single-purpose `Flow<Unit>`.

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
copy-pasted previews); designsystem previews use `DesignSystemPreviewTheme`, never `:app`'s
`TodayWasTheme`.

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
- Composable functions: `modifier: Modifier = Modifier` is always the last parameter; a modifier
  chain of more than one call wraps one call per line.
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
