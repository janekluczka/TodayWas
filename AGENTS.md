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

Single-module Gradle project. App code lives in `app/src/main/java/pl/luczka/todaywas/` (package
`pl.luczka.todaywas`), with the Compose theme in `ui/theme/`. Tests live in `app/src/test/` (JUnit4
unit) and `app/src/androidTest/` (instrumented). Project context docs (PRD, tech-stack decisions,
health-check) live in `context/foundation/`.

## Build, Test, and Development Commands

- `./gradlew.bat testDebugUnitTest` — run local unit tests.
- `./gradlew.bat ktlintCheck` — lint check; must pass before commit.
- `./gradlew.bat ktlintFormat` — auto-fix formatting.
- `./gradlew.bat assembleDebug` — build a debug APK.
- `./gradlew.bat connectedAndroidTest` — instrumented tests (requires a connected device/emulator).

## Coding Style & Naming Conventions

Kotlin, ktlint-enforced (official style; `standard:function-naming` disabled for `@Composable`
functions — see @.editorconfig). Compile target is Java 17 (@app/build.gradle.kts). Dependency
versions live in @gradle/libs.versions.toml — add new dependencies there, not as inline version
strings.

## Testing Guidelines

JUnit4 for local unit tests in `app/src/test/java/pl/luczka/todaywas/`; AndroidX Test + Espresso for
instrumented tests in `app/src/androidTest/`. No coverage threshold is enforced yet.

## Commit & Pull Request Guidelines

Commit messages follow Conventional Commits (`feat:`, `fix:`, `chore:` observed in history). No CI
pipeline or PR template exists yet — see @context/foundation/health-check.md.

## Security & Configuration Tips

`local.properties` is gitignored — put local API keys (Supabase, Gemini) there once wired up, never
hardcode them.
