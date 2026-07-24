---
project: "Today Was"
checked_at: 2026-07-24T14:50:13Z
health_status: needs-attention
context_type: brownfield
language_family: java
stack_assessment_available: false
checks_run:
  - lockfile
  - dependency_audit
  - outdated_deps
  - test_runner
  - ci_cd
  - configuration
audit_findings:
  critical: 0
  high: 0
  moderate: 0
  low: 0
test_runner_detected: true
ci_provider: null
recommended_fixes: 5
---

> **Adaptation note**: `/10x-health-check`'s built-in dispatch tables (marker files, audit tools,
> lockfile formats) cover JS/Python/Rust/Go/Ruby/PHP/.NET/Dart — there is no Gradle/Kotlin entry.
> `language_family: java` is the closest schema enum (JVM/Gradle ecosystem); checks below were run
> directly against the Gradle project rather than through the skill's built-in dispatch commands.
> Same as `context/foundation/tech-stack.md`'s deviation note for the same underlying reason.

## Dependency Health

### Lockfile

```
Status: no traditional lockfile (gradle.lockfile not present/enabled)
Package manager: Gradle (version catalog: gradle/libs.versions.toml)
```

Gradle doesn't use a lockfile by default the way npm/cargo do. `gradle/libs.versions.toml` already
pins an exact version string for every dependency (no floating ranges), which gives equivalent
reproducibility for this project's purposes. Gradle's native dependency locking
(`./gradlew dependencies --write-locks`) is an optional extra layer on top, not a gap — not flagged
as a fix.

### Security Audit

```
Tool: skipped — no built-in audit tool for Kotlin/Gradle projects (matches the skill's own "Java, Dart: skip" line)
Recommended external tool: OWASP dependency-check-gradle plugin, or enable GitHub Dependabot alerts on the janekluczka/TodayWas remote once pushed
```

No audit was run — 0/0/0/0 above reflects "not checked," not "checked clean." Treat this as an open
question, not a clean bill of health.

### Outdated Dependencies

```
Packages with major version gaps: unable to determine precisely (no network dependency-resolution check run)
```

One thing stands out on inspection of `gradle/libs.versions.toml`: `agp` (9.3.1), `kotlin` (2.2.10),
and `compose-bom` (2026.02.01) are current/recent, but `coreKtx` (1.10.1), `lifecycleRuntimeKtx`
(2.6.1), `activityCompose` (1.8.0), and `espressoCore` (3.5.1) look noticeably older by comparison —
version numbers from a much earlier AndroidX release cycle sitting alongside a bleeding-edge
AGP/Kotlin/Compose BOM. Worth a pass through Android Studio's version catalog update suggestions
(right-click `libs.versions.toml` → "Show Gradle Version Catalog Updates" or the built-in lint
check) to confirm whether these are intentionally pinned or just stale from the initial scaffold.

## Test Suite

```
Test runner: JUnit 4 (local unit tests) + AndroidX Test/Espresso (instrumented tests)
Tests found: 1 unit test (ExampleUnitTest — stock Android Studio placeholder), 1 instrumented test (ExampleInstrumentedTest — stock placeholder)
Test execution: passing (testDebugUnitTest ran clean: 1 test, 0 failures, 0 errors)
```

```
Configuration: app/build.gradle.kts (testImplementation/androidTestImplementation blocks), no dedicated test config file needed for JUnit4
Framework: JUnit 4.13.2 (unit), AndroidX Test 1.1.5 + Espresso 3.5.1 (instrumented)
```

Instrumented tests were not attempted — they require a connected device/emulator, unavailable in
this environment. Both test files are still the unmodified Android Studio template stubs
(`addition_isCorrect`, `useAppContext`) — no project-specific test coverage exists yet, expected at
this stage since no feature code has been written.

## CI/CD

```
Provider: not detected
Configuration: not found
```

ℹ No CI/CD configuration detected. You'll set this up in the infrastructure and deployment lesson
([Sprint Zero z Agentem: infrastruktura, walking skeleton i pierwszy deploy (M1L5)](https://platforma.przeprogramowani.pl/external/10xdevs-3/m1-l5)).
For now, a local, working test runner is what matters for agent collaboration — confirmed above.

| Stage      | Status | Notes                                               |
| ---------- | ------ | --------------------------------------------------- |
| Lint       | ✗      | not configured                                      |
| Test       | ✗      | not configured (works locally, not wired to CI)     |
| Build      | ✗      | not configured                                      |
| Type check | n/a    | Kotlin is statically typed; no separate step needed |
| Security   | ✗      | not configured                                      |

## Configuration

### Medium severity

- **No Kotlin linter/formatter (ktlint or detekt)** — Kotlin has no built-in equivalent to
  ESLint/Prettier; without one, an agent's generated code style will drift from whatever conventions
  accumulate ad hoc. Fix: add the `ktlint-gradle` or `detekt` Gradle plugin.
- **`buildTypes.release.optimization.enable = false`** in `app/build.gradle.kts` — release builds
  ship unminified/unobfuscated (no R8 shrinking). Fine for now with no release build in flight, but
  worth revisiting before the first real release build given the 2026-08-31 deadline. Fix: flip to
  `true` once release builds start getting tested, and add ProGuard/R8 keep rules as needed (a
  `rules.keep` file already exists at `app/src/main/keepRules/`).
- **`compileOptions` pinned to `JavaVersion.VERSION_11`** while `compileSdk` targets API 36 and
  AGP/Kotlin are on recent versions — this combination is unusual; recent Android Studio "Empty
  Activity" templates typically default `sourceCompatibility`/`targetCompatibility` to 17. Worth
  confirming this was intentional rather than a template artifact.

### Low severity

- **`.editorconfig`** — no formatting-consistency file across editors. Fix: add one (Android Studio
  can generate a Kotlin-style `.editorconfig` from Preferences → Code Style).
- **`.env.example` / secrets template** — not yet needed (no Supabase/Gemini keys wired up yet per
  `tech-stack.md`), but worth adding once those integrations start, so secrets never get hardcoded
  or accidentally committed. `local.properties` (the conventional place for local API keys in
  Android) is already correctly gitignored.

All other expected configuration is present: `.gitignore` is present and correctly excludes
`local.properties`, build output, and IDE caches; no secrets or generated files are currently
tracked in git.

## Stack Assessment Cross-Reference

No stack-assessment.md found — that skill (`/10x-stack-assess`) is the brownfield equivalent of the
greenfield `/10x-tech-stack-selector`, which was already run instead and produced
`context/foundation/tech-stack.md`. Not applicable here.

## Recommended Fixes

### Fix before agent work (Category A)

#### 1. Add a Kotlin linter/formatter (ktlint or detekt)

- **Impact**: without one, an agent has no enforced style convention to follow or be checked against
  — its output style will be inconsistent with itself run to run.
- **Severity**: medium
- **Effort**: moderate (15-30 min)
- **Fix**: add `org.jlleitschuh.gradle.ktlint` or `io.gitlab.arturbosch.detekt` to the root
  `build.gradle.kts` plugins block, run once to establish a baseline.

#### 2. Confirm `JavaVersion.VERSION_11` compile target is intentional

- **Impact**: an agent reasoning about available Kotlin/Java language features may assume a newer
  baseline (matching the recent AGP/Kotlin versions) than what's actually configured, generating
  code that doesn't compile.
- **Severity**: medium
- **Effort**: quick (< 5 min) to check, moderate if a bump to 17 is needed and touches other config
- **Fix**: open `app/build.gradle.kts`, confirm intent; if it should match a modern template, bump
  `sourceCompatibility`/`targetCompatibility` to `JavaVersion.VERSION_17`.

#### 3. Revisit `release.optimization.enable = false` before first real release build

- **Impact**: not urgent today, but an agent generating release-build tooling later may not think to
  check this flag, and shipping a real Play Store build with minification off is a real regression
  when it happens.
- **Severity**: medium
- **Effort**: quick (< 5 min) to flip, moderate to verify no ProGuard/R8 rule gaps afterward
- **Fix**: set to `true` once the app has real release-build coverage; validate with
  `app/src/main/keepRules/rules.keep`.

#### 4. Add `.editorconfig`

- **Impact**: minor — prevents formatting drift across editors/IDEs, low agent impact on its own but
  cheap to fix alongside the linter setup.
- **Severity**: low
- **Effort**: quick (< 5 min)
- **Fix**: Android Studio → Preferences → Editor → Code Style → Kotlin → "Export to .editorconfig".

#### 5. Set up dependency vulnerability scanning

- **Impact**: currently zero visibility into whether any pinned dependency (e.g. the notably-older
  `coreKtx`/`lifecycleRuntimeKtx`/`activityCompose`/`espressoCore` versions flagged above) carries a
  known CVE.
- **Severity**: low today (no known findings, but none were checked either)
- **Effort**: moderate (15-30 min)
- **Fix**: add the `dependency-check-gradle` plugin, or enable GitHub Dependabot alerts once the
  repo is pushed to `janekluczka/TodayWas`.

### Addressed in upcoming lessons (Category B)

#### No CI/CD pipeline

- **Lesson**:
  [Sprint Zero z Agentem: infrastruktura, walking skeleton i pierwszy deploy (M1L5)](https://platforma.przeprogramowani.pl/external/10xdevs-3/m1-l5)
- **What you'll do there**: turn `tech-stack.md` into a deliberate platform/CI choice and wire up
  the first deploy pipeline.

#### No CLAUDE.md / AGENTS.md

- **Lesson**:
  [Agent Onboarding: Agents.md, AI Rules i feedback loops (M1L4)](https://platforma.przeprogramowani.pl/external/10xdevs-3/m1-l4)
- **What you'll do there**: draft a first AGENTS.md/CLAUDE.md and score it, rather than generating a
  premature stub now.

#### No deployment configuration

- **Lesson**:
  [Sprint Zero z Agentem: infrastruktura, walking skeleton i pierwszy deploy (M1L5)](https://platforma.przeprogramowani.pl/external/10xdevs-3/m1-l5)
- **What you'll do there**: pick a deployment target and produce a deploy plan (the MVP is
  internal/sideload-only for now per `tech-stack.md`).

## Summary

Health status: needs-attention

The project's foundation is solid — Kotlin + Compose from the official Android Studio template, a
working JUnit test runner (verified: 1/1 passing), correctly gitignored secrets/build output, and
dependency versions pinned via the Gradle version catalog. The gaps are typical for a
just-scaffolded solo MVP: no linter/formatter yet, an unusual Java-11 compile target sitting next to
otherwise-current AGP/Kotlin versions worth double-checking, release minification currently
disabled, and zero dependency-vulnerability visibility since no audit tool has been run. None of
these block agent-assisted development today, but the linter and Java-version check are worth 20
minutes before diving into feature work.

Next step: address the linter and Java-version fixes above (quick wins), then proceed to agent
onboarding (M1L4) to draft CLAUDE.md/AGENTS.md.
