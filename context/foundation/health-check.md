---
project: "Today Was"
checked_at: 2026-08-16T09:18:28Z
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
> Same as `context/foundation/tech-stack.md`'s deviation note for the same underlying reason. The
> repo root also has a small `package.json` (Prettier for Markdown formatting only, `"description":
> "Not a build dependency of the Android app"`) — `npm audit` on it is clean (0 vulnerabilities) but
> it is not the project's real dependency tree; the Gradle version catalog is.

> **Re-run note**: this is a re-run of the 2026-08-11 check (that version was never committed —
> found as an uncommitted working-tree change at the start of this run; its content is preserved in
> `git diff`/history if needed). The main change since then: a large `arch-cleanup` refactor
> (12 commits, merged via PR #20) restructured the package layout to match `CLAUDE.md`'s
> `## Project Structure` section, and a fresh, non-cached test run surfaced **one genuinely failing
> test** that the previous check's cached test run had not caught.

## Dependency Health

### Lockfile

```
Status: no traditional lockfile (gradle.lockfile not present/enabled)
Package manager: Gradle (version catalog: gradle/libs.versions.toml)
```

Unchanged: Gradle doesn't use a lockfile the way npm/cargo do, but `gradle/libs.versions.toml` pins
an exact version string for every dependency (no floating ranges), which gives equivalent
reproducibility. Not flagged as a gap.

### Security Audit

```
Tool: skipped — no built-in audit tool for Kotlin/Gradle projects (matches the skill's own "Java, Dart: skip" line)
Recommended external tool: OWASP dependency-check-gradle plugin, or enable GitHub Dependabot alerts on the janekluczka/TodayWas remote
Docs-tooling package.json: npm audit re-run — 0 vulnerabilities (1 prod + 1 dev dependency, Prettier only)
```

Unchanged: no audit has ever been run against the actual Gradle dependency tree — 0/0/0/0 in the
frontmatter reflects "not checked," not "checked clean." The app has a live Supabase backend and
calls a third-party LLM (OpenRouter) via an Edge Function, so this remains a real gap, not a
theoretical one.

### Outdated Dependencies

```
Packages with major version gaps: 3 (unchanged since the last check)
```

- **`coreKtx`**: 1.10.1 → current AndroidX releases are in the 1.15.x+ range — stale, unchanged.
- **`activityCompose`**: 1.8.0 → current releases are 1.9.x+ — stale, unchanged.
- **`espressoCore`**: 3.5.1 → current releases are 3.6.x+ — stale, unchanged.

`agp` (9.3.1), `kotlin` (2.4.0), `compose-bom` (2026.02.01), and `androidxLifecycle` (2.11.0) remain
current. Worth a pass through Android Studio's version catalog update suggestions to confirm whether
the three still-stale versions are intentionally pinned or just never revisited.

## Test Suite

```
Test runner: JUnit 4 (local unit tests, some Robolectric-backed for Room/DAO coverage) + AndroidX Test/Espresso (instrumented tests)
Tests found: 262 unit tests (fresh, non-cached run, this check)
Test execution: FAILING — 261/262 pass, 1 deterministic failure
```

```
Configuration: app/build.gradle.kts (testImplementation/androidTestImplementation blocks), no dedicated test config file needed for JUnit4
Framework: JUnit 4.13.2 (unit), Robolectric 4.16.1 (DAO/Room tests), AndroidX Test 1.1.5 + Espresso 3.5.1 (instrumented)
```

**New finding this check**: `TodayWasDatabaseTest > should survive recreating the database instance
from the same file when onboarding was completed` fails consistently (re-ran in isolation twice,
same result both times — not flaky) with:

```
android.database.sqlite.SQLiteCantOpenDatabaseException: unable to open database file (code 14 SQLITE_CANTOPEN)
```

The prior (uncommitted) 2026-08-11 report claimed "258/258 passing" — that was Gradle's UP-TO-DATE
cache reporting a stale prior result, not a fresh execution; this check forced a `--rerun` to get a
real signal. The test builds a file-backed Room database via Robolectric, closes it, and reopens it
from the same file path — the reopen fails to open the SQLite connection. Likely a
Robolectric/native
SQLite interaction specific to this machine (Windows, host `LEGION-JANEK`) rather than a logic bug
in
the app itself, but it has not been isolated further. This is the single most important finding in
this check: a red test in the suite undermines the "agent can verify its own changes via the test
suite" guarantee that everything else in this report depends on.

Test count grew from 258 to 262 (+4) since the last check, consistent with the `arch-cleanup`
refactor. Instrumented tests remain the stock `ExampleInstrumentedTest` placeholder — not exercised
in this environment (requires a connected device/emulator), same limitation as before.

ktlint re-checked clean this pass (`./gradlew ktlintCheck`: BUILD SUCCESSFUL, no violations).

## CI/CD

```
Provider: not detected
Configuration: not found — only .github/PULL_REQUEST_TEMPLATE.md exists in .github/
```

Unchanged since the last check. `tech-stack.md` already decided `github-actions` as the intended
provider, and `context/changes/deployment/deployment-plan.md` still specifies a path-scoped GitHub
Actions job for the Supabase Edge Function deploy — but nothing in that plan has been executed, and
the Android app's own build/test/lint CI is still unimplemented.

| Stage      | Status | Notes                                                                             |
|------------|--------|-----------------------------------------------------------------------------------|
| Lint       | ✗      | not configured (ktlint runs locally via `./gradlew ktlintCheck`, not wired to CI) |
| Test       | ✗      | not configured — and would currently fail CI given the red test above             |
| Build      | ✗      | not configured                                                                    |
| Type check | n/a    | Kotlin is statically typed; no separate step needed                               |
| Security   | ✗      | not configured                                                                    |

## Configuration

### Medium severity

- **`buildTypes.release.optimization.enable = false`** in `app/build.gradle.kts` — release builds
  still ship unminified/unobfuscated (no R8 shrinking). Unchanged since the last check. Fix: flip to
  `true` and validate against `app/src/main/keepRules/rules.keep`, then confirm a release build
  still
  runs correctly (Compose + Hilt + kotlinx.serialization all have known R8 edge cases worth
  spot-checking).

### Low severity

- **No `local.properties.example` / secrets template** — `local.properties` itself is present,
  correctly gitignored, and holds real, actively-used Supabase keys. A fresh clone has no documented
  list of which keys `local.properties` must contain (`SUPABASE_URL`, `SUPABASE_ANON_KEY`,
  optionally `GOOGLE_WEB_CLIENT_ID`, per `app/build.gradle.kts`). Fix: add a
  `local.properties.example` with the key names (no real values) and reference it from the README.
- **Orphaned `context/changes/deployment/deployment-plan.md`** — still the only file in that folder,
  with no `change.md`; predates and sits outside this project's `/10x-new` → `/10x-plan` →
  `/10x-implement` → `/10x-archive` convention. Fix: either formalize it into a real change
  (`/10x-new deployment` + fold this doc in as context) if the CI/CD work gets picked up, or move it
  to `context/archive/` if superseded.

Resolved / re-confirmed unchanged this pass:

- ktlint installed and clean (`./gradlew ktlintCheck` re-run this check: passes).
- `compileOptions` on `JavaVersion.VERSION_17`.
- `.editorconfig` present, including the documented `ktlint_standard_function-naming = disabled`
  override for `@Composable` functions.
- `.gitignore` correctly excludes `local.properties`, build output, and IDE caches; no secrets or
  generated files are tracked in git.

## Stack Assessment Cross-Reference

No `stack-assessment.md` found — `/10x-tech-stack-selector` was run instead for this greenfield
project, producing `context/foundation/tech-stack.md`. Not applicable here. Unchanged since the last
check.

## Recommended Fixes

### Fix before agent work (Category A)

#### 1. Fix the failing `TodayWasDatabaseTest`

- **Impact**: a red test in the suite is the most damaging finding here — every other Category A
  item is a "would be nice," but a broken test-as-verification-signal risks an agent either ignoring
  future real regressions (assuming this failure is "expected") or "fixing" it by weakening the
  assertion instead of the underlying cause. Highest priority of anything in this report.
- **Severity**: high
- **Effort**: moderate (15–30 min) to isolate the Robolectric/SQLite reopen issue; possibly quick if
  it's a known Robolectric-on-Windows native-SQLite quirk with a documented workaround
- **Fix**: reproduce with `./gradlew testDebugUnitTest --rerun --tests "*TodayWasDatabaseTest*"`
  (note: a cached run reports stale green results — always pass `--rerun` when the test suite's
  actual pass/fail state matters). Investigate whether the Robolectric SQLite native library needs
  an
  explicit temp-directory or journal-mode override on Windows, or whether the reopened
  `Room.databaseBuilder` call needs an explicit `.setJournalMode(...)` for the test environment.

#### 2. Set up dependency vulnerability scanning

- **Impact**: zero visibility into whether any pinned dependency — including the ones handling live
  Supabase auth/data and OpenRouter LLM calls — carries a known CVE.
- **Severity**: low-to-moderate (no known findings, but none have ever been checked)
- **Effort**: moderate (15-30 min)
- **Fix**: add the `dependency-check-gradle` plugin, or enable GitHub Dependabot alerts on the
  `janekluczka/TodayWas` remote.

#### 3. Revisit `release.optimization.enable = false` before the first real release build

- **Impact**: shipping a real build with minification off is a real regression once it happens; the
  MVP is feature-complete, so this is now realistically near-term.
- **Severity**: medium
- **Effort**: quick (< 5 min) to flip, moderate to verify no ProGuard/R8 rule gaps afterward
- **Fix**: set to `true`; validate against `app/src/main/keepRules/rules.keep` and smoke-test a
  release build runs correctly.

#### 4. Add a `local.properties.example` secrets template

- **Impact**: a fresh clone has no documented list of required keys; onboarding (human or agent) has
  to reverse-engineer `requiredLocalProperty(...)` calls in `app/build.gradle.kts` to find them.
- **Severity**: low
- **Effort**: quick (< 5 min)
- **Fix**: add `local.properties.example` listing `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
  `GOOGLE_WEB_CLIENT_ID` (empty/placeholder values), reference it from the README.

#### 5. Resolve the orphaned `context/changes/deployment/deployment-plan.md`

- **Impact**: the one file in the repo's `context/` tree that doesn't follow the established
  change-folder convention — easy to overlook or mistakenly treat as authoritative/current.
- **Severity**: low
- **Effort**: quick (< 5 min) to decide, moderate if formalized into a full change
- **Fix**: either fold it into a proper `/10x-new deployment` change if the CI/CD work gets picked
  up
  next, or move it into `context/archive/` if it's considered superseded.

### Addressed in upcoming lessons (Category B)

#### No CI/CD pipeline implemented yet

- **Lesson**: covered by Module 1 Lesson 5 (infra research, done) and its output,
  `context/changes/deployment/deployment-plan.md`.
- **What you'll do there**: the plan already specifies a path-scoped GitHub Actions job using
  `supabase/setup-cli` for the Edge Function; the Android app's own build/test/lint CI (per
  `tech-stack.md`'s `ci_provider: github-actions`) is still an open implementation task — and would
  currently need Fix #1 above resolved first, or CI would go red on its first run.

## Summary

Health status: needs-attention

The project's foundation remains strong: the entire MVP roadmap is complete, a substantial
`arch-cleanup` refactor landed cleanly since the last check, ktlint is enforced and clean, and
Kotlin/AGP/Compose BOM are current. What changed this pass is the discovery of a genuinely failing
test (`TodayWasDatabaseTest`, deterministic, not flaky) that a previous cached test run had masked —
that single red test is why this check is `needs-attention` rather than `healthy`, since a broken
verification signal undermines every other agent-assisted change from here. The remaining items are
the same lower-stakes carryovers as before: no dependency vulnerability scanning against the real
Gradle tree, release minification still off, a missing secrets template, and one orphaned planning
doc.

Next step: fix the failing test first (Category A #1) — it's the only finding that actively degrades
agent-assisted development, since agents lean on a green test suite to verify their own changes.
After that, the remaining Category A items are all quick wins, and CI/CD setup (Category B) becomes
safe to pick up next.
