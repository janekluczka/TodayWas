# CI/CD Pipeline — Plan Brief

> Full plan: `context/changes/ci-cd-pipeline/plan.md`

## What & Why

Wire up GitHub Actions CI/CD that `tech-stack.md` already decided on (`ci_provider: github-actions`)
but never implemented: an Android lint/test/build pipeline, and auto-deploy for the `ai-proxy`
Supabase Edge Function on push to `master`. Both were flagged repeatedly by `/10x-health-check` as
the last real gap before the project is fully "healthy."

## Starting Point

`.github/` currently has only a PR template and Dependabot config — zero workflows. The Android
build/lint/test suite is stable locally (262/262 tests, ktlint clean). The `ai-proxy` Edge Function
is already live in production, but was deployed by hand via the Supabase MCP server during a prior
change, not via CI — so there's no `supabase/config.toml` and no repeatable deploy pipeline.

## Desired End State

Every push and PR to `master` gets an automatic Android lint/test/build check. Every push to
`master` that touches the Edge Function's code automatically redeploys it — no more manual
`supabase functions deploy` runs. Neither job fires on changes irrelevant to it.

## Key Decisions Made

| Decision                          | Choice                                             | Why (1 sentence)                                                                 | Source |
| ---------------------------------- | --------------------------------------------------- | --------------------------------------------------------------------------------- | ------ |
| Android CI trigger                 | Push + PR to `master`                              | Catches breakage before merge, not just after.                                    | Plan   |
| CI Supabase credentials             | Dummy placeholder values                           | No JVM unit test touches the real Supabase client; avoids provisioning secrets CI doesn't need. | Plan   |
| Path scoping                       | Yes, per-job via `dorny/paths-filter`              | Avoids full Android rebuilds on doc/function-only commits and vice versa.         | Plan   |
| Workflow structure                 | One file, two jobs (`android`, `deploy-edge-function`) | One unified CI status per commit/PR; GitHub Actions has no native per-job path trigger, so a `changes` job computes flags both other jobs gate on. | Plan   |
| Edge-function deploy trigger       | Push to `master` only, scoped to `supabase/functions/**` | Matches `tech-stack.md`'s `ci_default_flow: auto-deploy-on-merge`; `config.toml` dropped from the filter since it doesn't exist (deploy was done via MCP, not CLI). | Plan   |
| Post-deploy verification           | Rely on CLI exit code only                         | No health-check endpoint to smoke-test against; matches current manual-deploy risk profile. | Plan   |
| Orphaned `deployment-plan.md`      | Fold into this plan, then move to `context/archive/` | Matches repo convention (archive is read-only); closes the "second inconsistent CI/CD doc" gap `change.md` flagged. | Plan   |
| Branch protection                  | Out of scope                                       | Separate manual GitHub-settings step, not a repo file; noted as a follow-up.      | Plan   |

## Scope

**In scope:**
- `.github/workflows/ci.yml` with `changes`, `android`, and `deploy-edge-function` jobs
- Placeholder `local.properties` written in CI (Android job only needs non-blank values to build)
- `SUPABASE_ACCESS_TOKEN` / `SUPABASE_PROJECT_REF` as new GitHub repo secrets (human step)
- Moving `context/changes/deployment/deployment-plan.md` to `context/archive/2026-08-16-deployment/`

**Out of scope:**
- Branch protection / required status checks
- Post-deploy smoke testing
- `connectedAndroidTest` in CI (needs an emulator)
- Release signing / Play Store publishing
- Any change to the `ai-proxy` function's code, contract, or `OPENROUTER_API_KEY` secret

## Architecture / Approach

One workflow file. A cheap `changes` job runs `dorny/paths-filter` to compute two boolean outputs
(`android`, `edge-function`) from the diff. The `android` job (ktlint → unit tests → debug build)
gates on the `android` output and runs on both push and PR. The `deploy-edge-function` job
(`supabase/setup-cli` → `supabase functions deploy ai-proxy`) gates on the `edge-function` output
**and** `github.event_name == 'push'`, so it never fires on a PR.

## Phases at a Glance

| Phase                                                        | What it delivers                                              | Key risk                                                                 |
| -------------------------------------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------------------------- |
| 1. Android CI job                                             | `.github/workflows/ci.yml` with path-scoped lint/test/build   | `gradlew` isn't executable in git (`100644`) — must `chmod +x` in the job |
| 2. Edge-function deploy job + retire `deployment-plan.md`     | Auto-deploy on push to `master`; orphaned doc archived         | CLI behavior without a `config.toml` should be reconfirmed against current Supabase docs at implementation time |

**Prerequisites:** None beyond the two GitHub repo secrets (Phase 2, human step).
**Estimated effort:** ~1 session across 2 phases.

## Open Risks & Assumptions

- Assumes `supabase functions deploy` still works without a `supabase/config.toml` on the CLI
  version `supabase/setup-cli@v1` resolves to `latest` — flagged in the plan's Critical
  Implementation Details to reconfirm at implementation time rather than assume.
- Pushing to `master` to verify the deploy job (Phase 2 manual verification) requires explicit
  confirmation each time per your standing preference — not a silent step.

## Success Criteria (Summary)

- A PR touching only `app/**` shows a green Android check; a docs-only commit triggers neither job.
- A push to `master` touching `supabase/functions/ai-proxy/**` auto-redeploys the function, visible
  in `supabase functions logs ai-proxy`.
- `context/changes/deployment/` no longer exists; its content lives in this plan and the archive.
