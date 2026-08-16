# CI/CD Pipeline Implementation Plan

## Overview

Wire up the two pieces of CI/CD `tech-stack.md` already decided (`ci_provider: github-actions`,
`ci_default_flow: auto-deploy-on-merge`) but never implemented: an Android lint/test/build job, and
GitHub Actions auto-deploy for the `ai-proxy` Supabase Edge Function. Both live as two jobs in a
single new workflow file, each independently path-scoped so an Android-only commit never triggers a
function redeploy and vice versa. The orphaned `context/changes/deployment/deployment-plan.md` is
folded into this plan and retired to `context/archive/` once its remaining real work (its Phase 4)
is covered here.

## Current State Analysis

- `.github/` contains only `PULL_REQUEST_TEMPLATE.md` and `dependabot.yml` — no
  `.github/workflows/` directory exists at all.
- `./gradlew ktlintCheck`, `./gradlew testDebugUnitTest` (262/262), and `./gradlew assembleDebug`
  all pass cleanly and reliably on `master` as of the `health-check-fixes` merge — no known
  blocker to wiring CI.
- The `ai-proxy` Edge Function (`supabase/functions/ai-proxy/index.ts`) is already live, deployed
  by hand via the Supabase MCP server's `deploy_edge_function` tool during the archived
  `ai-assist-proxy-foundation` change — **not** via the Supabase CLI. Consequently there is no
  `supabase/config.toml` anywhere in the repo; the function currently runs on the CLI/platform
  default `verify_jwt = true`, matching what `deploy_edge_function` was explicitly called with. The
  live secret is `OPENROUTER_API_KEY` (already set on the linked project via the dashboard) — the
  Gemini-era `GEMINI_API_KEY` referenced in the original `deployment-plan.md` is stale and does not
  need to be touched by this change.
- `deployment-plan.md`'s Phases 0–3 and 5 are superseded by that MCP-based deploy (no local CLI
  install/link/init ever happened, and verification already happened via curl in the archived
  change). Only its **Phase 4** — GitHub Actions auto-deploy — is still real, unstarted work.
- `app/build.gradle.kts` reads `local.properties` (gitignored) at Gradle **configuration time** via
  a `requiredLocalProperty()` helper that calls `error()` if `SUPABASE_URL` or `SUPABASE_ANON_KEY`
  is missing — this means every Gradle invocation (including `ktlintCheck`, which touches no
  Supabase code) fails immediately on a clean CI checkout unless `local.properties` is created
  first.
- `gradlew`/`gradlew.bat` are tracked at file mode `100644` (not `100755`) — committed from a
  Windows dev machine — so a Linux Actions runner needs an explicit `chmod +x gradlew` before
  `./gradlew` will execute.
- Default branch is `master` (confirmed via `git remote show origin`), not `main` as
  `tech-stack.md`'s prose literally says.
- No Robolectric/JVM unit test touches the Hilt/Supabase object graph (`SupabaseModule`'s
  `provideSupabaseClient` is a lazy `@Provides`, only Room-backed DAO tests use Robolectric) — so
  CI never needs a *working* Supabase URL/key, only non-blank strings.

### Key Discoveries:

- `dorny/paths-filter@v3` is the standard pattern for per-job path scoping inside a single
  workflow file — GitHub Actions' native `on.push.paths`/`on.pull_request.paths` apply to the
  entire workflow, not to individual jobs, so achieving "Android job skips docs-only commits, edge
  function job skips Android-only commits" in one file requires a preliminary `changes` job whose
  outputs gate the other two jobs via `if:`.
- `actions/setup-java@v4` has a built-in `cache: gradle` option — no separate cache action needed.
- `supabase/setup-cli@v1` + `supabase functions deploy <name> --project-ref <ref>` is the documented
  deploy path; recent CLI versions bundle without Docker, so no Docker-in-Docker setup is needed on
  the runner.
- `ktlintCheck` is already a root-aggregating task (both `:app` and `:core:designsystem` apply the
  ktlint plugin individually), matching the single command `CLAUDE.md` documents.

## Desired End State

A single `.github/workflows/ci.yml` runs on every push and pull request targeting `master`:

- An `android` job (ktlint → unit tests → debug build) runs whenever the push/PR touches
  Android-relevant paths, and reports a check on the commit/PR.
- A `deploy-edge-function` job runs **only on push to `master`** (never on a PR, matching
  `tech-stack.md`'s `ci_default_flow: auto-deploy-on-merge`) whenever the push touches
  `supabase/functions/**`, deploying `ai-proxy` via the Supabase CLI using repo secrets.
- Neither job runs when the change is irrelevant to it (a docs-only commit runs neither; an
  Android-only commit does not redeploy the function; a function-only commit does not rebuild the
  app).
- `context/changes/deployment/deployment-plan.md` no longer exists at that path — its still-useful
  content is folded into this plan and the file is moved under `context/archive/`.

Verification: push a branch touching only `app/**` and confirm (via GitHub's Checks UI) that
`android` runs and `deploy-edge-function` is skipped; push a branch touching only
`supabase/functions/ai-proxy/**` and confirm the reverse after merge to `master`.

## What We're NOT Doing

- No GitHub branch protection / required-status-checks setup — that's a manual repo-settings step
  or a deliberate future decision, not a plan-owned checked-in item.
- No smoke-test curl step after the edge-function deploy — the `supabase functions deploy` exit
  code is the success signal, consistent with the current manual-deploy risk profile.
- No `supabase/config.toml` creation — the function already runs on the CLI's `verify_jwt = true`
  default with no config file; adding one now is unnecessary and risks a typo silently flipping
  the auth gate.
- No `connectedAndroidTest` (instrumented tests) in CI — requires an emulator/device; the repo's
  instrumented suite is still the stock placeholder anyway (per the latest health check).
- No release signing or Play Store publishing wiring — `tech-stack.md` already scopes that as a
  manual step post-MVP.
- No changes to the `OPENROUTER_API_KEY` secret or the function's request/response contract.
- No real Supabase/Google credentials in CI — the Android job uses placeholder values sufficient
  for compilation only (see Critical Implementation Details).

## Implementation Approach

One new workflow file, `.github/workflows/ci.yml`, with three jobs: a cheap `changes` job that
computes per-area path-change flags via `dorny/paths-filter`, an `android` job gated on the
`android` flag (runs on push+PR), and a `deploy-edge-function` job gated on both the
`edge-function` flag and `github.event_name == 'push'`. `deployment-plan.md`'s remaining relevant
content (the Phase 4 design) is realized directly as the `deploy-edge-function` job; the file itself
is then moved to `context/archive/` since its earlier phases are already superseded by the MCP-based
deploy that actually happened.

## Critical Implementation Details

### GitHub Actions has no native per-job path trigger

`on.push.paths` / `on.pull_request.paths` scope the whole workflow, not one job. Use a preliminary
`changes` job with `dorny/paths-filter@v3` producing named boolean outputs (`android`,
`edge-function`), and gate the other two jobs with `if: needs.changes.outputs.<name> == 'true'`
(string comparison — the action's outputs are strings, not real booleans). The `deploy-edge-function`
job additionally needs `github.event_name == 'push'` in its `if:` since the workflow-level trigger
includes `pull_request` (for the `android` job's benefit) but the deploy must never run on a PR.

### `gradlew` is not executable in the git index

`git ls-files -s gradlew` shows mode `100644` (committed from Windows). The `android` job must run
`chmod +x gradlew` before any `./gradlew` invocation, or every step fails with "Permission denied."

### `local.properties` needs non-blank placeholders, not real secrets

`requiredLocalProperty("SUPABASE_URL")` / `requiredLocalProperty("SUPABASE_ANON_KEY")` throw at
Gradle configuration time if the key is absent — this fires for every task, including `ktlintCheck`.
Since no JVM unit test touches the actual Hilt-provided `SupabaseClient` (see Current State
Analysis), the `android` job writes a `local.properties` with placeholder non-blank strings (e.g.
`SUPABASE_URL=https://ci-placeholder.supabase.co`, `SUPABASE_ANON_KEY=ci-placeholder-anon-key`,
`GOOGLE_WEB_CLIENT_ID=` left blank as already supported by `optionalLocalProperty`). Do not source
real secrets for this — there is no code path in CI that would use a working connection.

### Confirm CLI deploy behavior against current docs before wiring the secret step

`supabase functions deploy <name> --project-ref <ref>` is the documented non-interactive deploy
path (auth via a `SUPABASE_ACCESS_TOKEN` env var, no `supabase link` needed). Confirm at
implementation time whether the pinned `supabase/setup-cli` version's `deploy` command still works
without a `supabase/config.toml` present (it does as of current docs, but CLI behavior here has
already changed once during this project's history — see `infrastructure.md`'s decision log) —
if a future CLI version requires one, create it with `verify_jwt = true` explicitly, never leave it
to default silently.

## Phase 1: Android CI job

### Overview

Add `.github/workflows/ci.yml` with a `changes` job and an `android` job that runs ktlint, unit
tests, and a debug build, path-scoped to Android-relevant files, on every push and PR to `master`.

### Changes Required:

#### 1. CI workflow file (Android job)

**File**: `.github/workflows/ci.yml`

**Intent**: Create the workflow with `on: push`/`pull_request` targeting `master`, a `changes` job
using `dorny/paths-filter@v3` to compute an `android` output covering `app/**`, `core/**`,
`build.gradle.kts`, `settings.gradle.kts`, `gradle/**`, `gradlew`, `gradlew.bat`, and an `android`
job (`needs: changes`, gated on that output) that checks out, sets up JDK 17 via
`actions/setup-java@v4` (`cache: gradle`), writes the placeholder `local.properties`, `chmod +x
gradlew`, then runs `./gradlew ktlintCheck`, `./gradlew testDebugUnitTest`, `./gradlew
assembleDebug` as separate steps (so a failure clearly names which stage broke).

**Contract**: Workflow name `CI`; job id `android`; triggers on `push`/`pull_request` to `master`.
Placeholder `local.properties` values per Critical Implementation Details above. Steps run in order
lint → test → build so the fastest, most common failure (lint) surfaces first.

### Success Criteria:

#### Automated Verification:

- `./gradlew ktlintCheck` passes locally: `./gradlew ktlintCheck`
- `./gradlew testDebugUnitTest` passes locally (262/262): `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug` succeeds locally: `./gradlew assembleDebug`
- `.github/workflows/ci.yml` is valid YAML (e.g. `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`)

#### Manual Verification:

- Push a branch touching only `app/**`, open a PR against `master`, confirm the `android` check
  runs and goes green in GitHub's Checks UI
- Push a commit touching only a `context/**/*.md` file, confirm the `android` job is skipped
  (path filter works as intended)

---

## Phase 2: Edge-function deploy job + retire the orphaned deployment doc

### Overview

Add the `deploy-edge-function` job to the same workflow (path-scoped to `supabase/functions/**`,
push-to-`master`-only), provision the two Supabase repo secrets it needs, and fold
`deployment-plan.md`'s remaining relevance into this plan before moving it to `context/archive/`.

### Changes Required:

#### 1. CI workflow file (edge-function job)

**File**: `.github/workflows/ci.yml`

**Intent**: Extend the `changes` job's filter with an `edge-function` output covering
`supabase/functions/**`, and add a `deploy-edge-function` job (`needs: changes`) gated on both that
output and `github.event_name == 'push'`. It checks out, installs the CLI via
`supabase/setup-cli@v1`, and runs `supabase functions deploy ai-proxy --project-ref
${{ secrets.SUPABASE_PROJECT_REF }}` with `SUPABASE_ACCESS_TOKEN` supplied as a job-level env var
from `secrets.SUPABASE_ACCESS_TOKEN`.

**Contract**: Job id `deploy-edge-function`; runs only for `push` events touching
`supabase/functions/**`; deploys the single named function `ai-proxy` (not a blanket "deploy all,"
since only one function exists and naming it explicitly avoids accidentally deploying a future
function before it's ready).

#### 2. Supabase repo secrets (human step)

**Intent**: You add `SUPABASE_ACCESS_TOKEN` (generate via the Supabase dashboard's account access
tokens page) and `SUPABASE_PROJECT_REF` (`ibftrzalfdmqztmiuvnj`, the already-linked project from
the archived `ai-assist-proxy-foundation` change) as GitHub Actions repository secrets
(Settings → Secrets and variables → Actions). I never see or handle the raw token.

**Contract**: Two repo secrets exist, named exactly `SUPABASE_ACCESS_TOKEN` and
`SUPABASE_PROJECT_REF`, readable by the workflow as `secrets.SUPABASE_ACCESS_TOKEN` /
`secrets.SUPABASE_PROJECT_REF`.

#### 3. Retire `deployment-plan.md`

**Intent**: Its Phases 0–3 and 5 are already superseded by the MCP-based deploy done in
`ai-assist-proxy-foundation`; its Phase 4 is now realized as this phase's workflow job. Move the
file out of the live `context/changes/` tree so it stops reading as an unstarted, inconsistent
second CI/CD doc, per `change.md`'s own note and the health-check's flagged cleanup item.

**Contract**: `git mv context/changes/deployment/deployment-plan.md
context/archive/2026-08-16-deployment/deployment-plan.md` (dated to match this repo's existing
`<date>-<change-id>` archive-folder convention), then remove the now-empty
`context/changes/deployment/` directory.

### Success Criteria:

#### Automated Verification:

- `.github/workflows/ci.yml` is valid YAML and contains both `android` and `deploy-edge-function`
  job ids: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`
- `context/changes/deployment/deployment-plan.md` no longer exists:
  `test ! -e context/changes/deployment/deployment-plan.md`
- `context/archive/2026-08-16-deployment/deployment-plan.md` exists:
  `test -f context/archive/2026-08-16-deployment/deployment-plan.md`
- `context/changes/deployment/` directory no longer exists:
  `test ! -d context/changes/deployment`

#### Manual Verification:

- Confirm `SUPABASE_ACCESS_TOKEN` and `SUPABASE_PROJECT_REF` are set as GitHub Actions repo
  secrets before the next step
- Push a trivial change under `supabase/functions/ai-proxy/` on `master` (confirm with me before
  this push happens), confirm the `deploy-edge-function` job runs and succeeds in GitHub Actions
- Confirm that same push's `android` job did not need to run (or, if it also touched Android
  files, confirm the path-scoping logic behaved as expected for the actual diff)
- Confirm a push touching only `app/**` on `master` does NOT trigger `deploy-edge-function`
- `supabase functions logs ai-proxy` (or the dashboard's realtime log view) shows a fresh
  deployment reflecting the pushed change

---

## Testing Strategy

### Unit Tests:

- None new — this change adds no application code, only CI configuration and a doc move.

### Integration Tests:

- The workflow's own path-scoped triggers are the integration surface; Phase 1 and Phase 2's
  Manual Verification steps (push+PR combinations) are the closest equivalent to an integration
  test available for a GitHub Actions workflow.

### Manual Testing Steps:

1. Open a PR touching only `app/**` — confirm `android` runs green, `deploy-edge-function` does
   not run.
2. Merge that PR (or push directly, with confirmation) — confirm `android` runs again on the push
   to `master`, `deploy-edge-function` still does not run.
3. Push a change touching only `supabase/functions/ai-proxy/index.ts` on `master` (with
   confirmation before pushing) — confirm `deploy-edge-function` runs and succeeds,
   `android` does not run.
4. Push a docs-only change (`context/**/*.md`) — confirm neither job runs.

## Performance Considerations

`actions/setup-java@v4`'s built-in `cache: gradle` avoids re-downloading Gradle dependencies on
every run. No other performance work is in scope — the Edge Function is stateless and low-traffic
per `infrastructure.md`.

## Migration Notes

None — no data model or schema changes.

## References

- Change identity: `context/changes/ci-cd-pipeline/change.md`
- Tech stack decision: `context/foundation/tech-stack.md` (`ci_provider: github-actions`,
  `ci_default_flow: auto-deploy-on-merge`)
- Infra decision + risk register: `context/foundation/infrastructure.md`
- Latest health check: `context/foundation/health-check.md` (CI/CD section, orphaned-doc flag)
- Superseded/folded-in plan: `context/changes/deployment/deployment-plan.md` (moved to
  `context/archive/2026-08-16-deployment/deployment-plan.md` by Phase 2 of this plan)
- Live function source: `supabase/functions/ai-proxy/index.ts`
- Prior deploy history: `context/archive/2026-08-11-ai-assist-proxy-foundation/plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Android CI job

#### Automated

- [x] 1.1 `./gradlew ktlintCheck` passes locally
- [x] 1.2 `./gradlew testDebugUnitTest` passes locally (262/262)
- [x] 1.3 `./gradlew assembleDebug` succeeds locally
- [x] 1.4 `.github/workflows/ci.yml` is valid YAML

#### Manual

- [ ] 1.5 PR touching only `app/**` shows a green `android` check
- [ ] 1.6 Docs-only commit skips the `android` job

### Phase 2: Edge-function deploy job + retire the orphaned deployment doc

#### Automated

- [ ] 2.1 `.github/workflows/ci.yml` valid YAML, contains `android` and `deploy-edge-function` job ids
- [ ] 2.2 `context/changes/deployment/deployment-plan.md` no longer exists
- [ ] 2.3 `context/archive/2026-08-16-deployment/deployment-plan.md` exists
- [ ] 2.4 `context/changes/deployment/` directory no longer exists

#### Manual

- [ ] 2.5 `SUPABASE_ACCESS_TOKEN` and `SUPABASE_PROJECT_REF` confirmed set as GitHub repo secrets
- [ ] 2.6 Push touching `supabase/functions/ai-proxy/` on `master` triggers a successful `deploy-edge-function` run
- [ ] 2.7 That same push's `android` job behavior confirmed correct for the actual diff
- [ ] 2.8 Push touching only `app/**` on `master` does NOT trigger `deploy-edge-function`
- [ ] 2.9 `supabase functions logs ai-proxy` shows a fresh deployment reflecting the pushed change
