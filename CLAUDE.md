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

Multi-module Gradle project, split the house-style way (`android-guidelines` →
`house-style.md`, "Modules"). Each module's package and namespace is
`pl.luczka.todaywas.<module path>`:

- `:app` — `Application`, `MainActivity`, the root `NavDisplay` (assembles every feature's
  `xxxEntries(navigator)`) and `RootViewModel`; package `pl.luczka.todaywas`.
- `:feature:<name>:api` / `:feature:<name>:impl` for `account`, `habit`, `journal`, `main`,
  `onboarding` — `api` holds only the `NavKey`s and `Navigator.navigateToXxx()` functions; `impl`
  holds the screens, ViewModels, the feature's strings and its entry builder. A feature reaches
  another only through that one's `api`.
- `:core:domain` — domain models, repository interfaces, use cases, business rules.
- `:core:data` — repository implementations, mappers, sync; `:core:database` — Room entities,
  DAOs, the database, migrations and the exported schemas (`core/database/schemas/`);
  `:core:network` — Supabase client, DTOs, remote data sources.
- `:core:ui` — UI models, mappers and composables shared by more than one feature, plus their
  strings (referenced from features as `CoreUiR`); `:core:navigation` — the `Navigator`;
  `:core:testing` — shared repository fakes.
- `:core:designsystem` — `Ds*` Material3 wrappers and `DsTheme`; depends on no other module.
- `build-logic/` (convention plugins), `:ktlint-rules` (custom ktlint rules) and
  `:architecture-tests` (Konsist house-style tests) — synced from agent-toolkit by the
  `android-quality-setup` skill. Never edit them here; change them in agent-toolkit and re-sync.

Other locations:

- Tests: `<module>/src/test/` (JUnit4 unit), `app/src/androidTest/` (instrumented).
- `context/foundation/` — PRD, tech-stack, roadmap, lessons.md (see "Working in this repo" below).
- `context/changes/<change-id>/` — in-flight change docs; archived to `context/archive/` when done.

## Architecture & Kotlin Conventions

Android guidance comes from the `android-guidelines` skill (agent-toolkit `android` plugin):
official developer.android.com recommendations plus my house style (`references/house-style.md`) —
the package layout, MVI with a pure-router `onIntent`, state-based navigation and messages, the
mandatory flat domain layer, mapping in dedicated `*Mapper.kt` files, `Ds` components, M3 slotted
dialogs, previews, and `should … when …` tests. `:architecture-tests` enforces the parts it marks as
checked. This file only adds project facts, plus one convention not in the house style yet:

- Prefer collapsing `val x = when (...) { ... }` directly into the assignment over a separate
  mutable variable.

## Coding Style & Naming Conventions

Kotlin with ktlint (layout, `android_studio` style plus the shared `@.editorconfig`) and detekt
(code smells, with compose-rules, on the shared `@config/detekt/detekt.yml`). Both configs are
synced from agent-toolkit by `android-quality-setup` — change them there and re-sync, never only
here (a local edit is overwritten on the next sync). `ktlintFormat` is the source of truth for
layout; when `ktlintCheck` reports an overlong line it can't fix, restructure the code (extract a
variable, split a chain) instead of wrapping at an operator. Fix detekt findings; a rule that
doesn't fit gets changed in the shared config with a comment, not baselined. Compile target is
Java 17 (`@app/build.gradle.kts`). Dependency versions live in `@gradle/libs.versions.toml` — add
new dependencies there, not as inline version strings.

## Testing Guidelines

JUnit4 for local unit tests in each module's `src/test/kotlin/`, next to the code they cover;
AndroidX Test + Espresso for instrumented tests in `app/src/androidTest/`. No coverage threshold is
enforced yet.

## Build, Test, and Development Commands

- `./gradlew.bat testDebugUnitTest` — run local unit tests.
- `./gradlew.bat :ktlint-rules:test` — run the custom ktlint rules' tests.
- `./gradlew.bat :architecture-tests:test` — house-style architecture tests; must pass before commit.
- `./gradlew.bat ktlintCheck` — lint check; must pass before commit.
- `./gradlew.bat ktlintFormat` — auto-fix formatting.
- `./gradlew.bat detekt` — static analysis (code smells + compose-rules); must pass before commit.
- `./gradlew.bat assembleDebug` — build a debug APK.
- `./gradlew.bat connectedAndroidTest` — instrumented tests (requires a connected device/emulator).

## Commit & Pull Request Guidelines

Commit messages follow Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `style:`
observed in history). CI (`.github/workflows/ci.yml`, GitHub Actions) runs ktlint, detekt, Android
Lint, the unit tests, both JVM modules' tests and the debug build — not the instrumented tests; no
PR template exists yet — see @context/foundation/health-check.md.

## Security & Configuration Tips

`local.properties` is gitignored — put local API keys (Supabase, Gemini) there once wired up, never
hardcode them.

## Working in this repo (10xDevs workflow)

This project uses the 10xDevs skill set for planning and implementation. Before planning or
implementing a change, check @context/foundation/lessons.md — an append-only register of recurring
rules (the generic ones are promoted into the agent-toolkit house style) — and whichever
`context/foundation/` doc is relevant (`prd.md`, `tech-stack.md`, `roadmap.md`). New work starts
with `/10x-new` under `context/changes/<change-id>/` (identity file `change.md`, plus
frame/plan/review artifacts); foundation docs are edited in place, never copied; a completed change
is archived with `/10x-archive` into `context/archive/`, which is read-only by convention.
