# TodayWas

TodayWas is a native Android app (Kotlin + Jetpack Compose) for low-effort daily journaling and
optional habit tracking. It replaces the usual tradeoff between effort (long-form journals) and
guilt (streak-based habit trackers) with a GitHub-contribution-style history view — shown per
journal and per habit — that rewards presence over time without punishing missed days.

The app works fully offline with no account required. An optional account adds cross-device sync
and AI-assisted writing prompts.

## Features

- **Daily journaling** — a low-effort free-text entry per day, with an at-a-glance contribution
  history.
- **Habit tracking** — create binary or scale-based habits and log check-ins for any day, past or
  present, each with its own contribution history.
- **Contribution-style history** — a main-screen overview plus a detailed, drill-down view per
  journal or habit, with color intensity computed relative to your own historical range.
- **24-hour edit window** — journal entries and habit check-ins can be edited within 24 hours of
  creation, then become read-only (deletion is exempt — any entry or check-in can be deleted at
  any time).
- **Zero-account core experience** — journaling and habit tracking work fully on-device; no
  sign-in is ever required for the core app.
- **Optional account & sync** — sign in with email or Google to back up and sync existing local
  data across devices, with a one-time review screen before the first upload.
- **AI-assisted writing prompts** — "help me start" generates a tone-matched journal prompt from a
  5-point mood scale, and "help me refine" offers AI-assisted rewriting of an entry you've already
  written. Both require a signed-in account; neither reads your journal or habit history beyond
  what you explicitly submit for that one request.

## Module structure

- **`:app`** — the application module (package `pl.luczka.todaywas`).
- **`:core:designsystem`** — reusable Material3 components, theme, and design tokens (package
  `pl.luczka.todaywas.core.designsystem`); never depends on `:app`.

See [`CLAUDE.md`](CLAUDE.md) for the full package layout and architecture/coding conventions
(MVI per screen, mapper placement, Navigation 3, etc.).

## Build & test

```bash
./gradlew.bat testDebugUnitTest    # run local unit tests
./gradlew.bat ktlintCheck          # lint check; must pass before commit
./gradlew.bat ktlintFormat         # auto-fix formatting
./gradlew.bat assembleDebug        # build a debug APK
./gradlew.bat connectedAndroidTest # instrumented tests (requires a connected device/emulator)
```

`local.properties` (gitignored) needs your own Supabase and Gemini/OpenRouter keys once account
sync or AI-assist features are wired up locally — see `local.properties.example`.

Postgres/RLS-level tests live outside Gradle, under `supabase/tests/` — see
[`context/foundation/test-plan.md`](context/foundation/test-plan.md) §6.4 for how to run them.

## Where to learn more

- [`context/foundation/prd.md`](context/foundation/prd.md) — full product scope, user stories, and
  functional requirements.
- [`context/foundation/tech-stack.md`](context/foundation/tech-stack.md) — stack decisions and why.
- [`context/foundation/test-plan.md`](context/foundation/test-plan.md) — the project's test
  strategy: risk map, what's covered, and how to add new tests.
- [`CLAUDE.md`](CLAUDE.md) — architecture, coding conventions, and hard product rules (edit
  windows, account-free core, AI-assist privacy boundaries) that every change in this repo follows.
