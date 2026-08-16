<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: CI/CD Pipeline Implementation Plan

- **Plan**: context/changes/ci-cd-pipeline/plan.md
- **Scope**: Phase 1 + Phase 2 of 2 (full plan)
- **Date**: 2026-08-16
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 2 observations

## Method note

The diff for this change is a single code file (`.github/workflows/ci.yml`, 71 lines) plus three
doc moves/adds — under the ≤3-file threshold where the skill's own guidance says to budget minimal
time on pattern-compliance sub-agent work. Given I authored both the plan and the implementation in
this same session and already held full context on both, I reviewed directly rather than spawning
the two parallel sub-agents, and spent the saved effort instead on independently verifying one
suspected defect against GitHub's actual documentation (see F-candidate below) rather than trusting
recalled knowledge.

A near-miss worth recording: I initially suspected `needs.changes.outputs.edge-function` /
`steps.filter.outputs.edge-function` (hyphenated property names accessed via dot notation) was a
parse bug, based on a web search snippet claiming GitHub Actions expressions require bracket
notation for hyphenated properties. Fetching the raw GitHub docs source directly
(`github/docs` repo, `contexts.md`) confirmed the opposite: "the property name must start with a
letter or `_` and contain only alphanumeric characters, `-`, or `_`" — hyphens are explicitly valid
in dot-notation property dereference. No bug. Reported here only because it's exactly the kind of
finding this review exists to catch, and confirming it required going to a primary source rather
than trusting a paraphrase.

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Success Criteria detail

**Automated** (re-run during this review):
- `./gradlew ktlintCheck` — BUILD SUCCESSFUL
- `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL
- `./gradlew assembleDebug` — BUILD SUCCESSFUL
- `.github/workflows/ci.yml` valid YAML, contains `android` and `deploy-edge-function` job ids — confirmed
- `context/changes/deployment/deployment-plan.md` no longer exists — confirmed
- `context/archive/2026-08-16-deployment/deployment-plan.md` exists — confirmed
- `context/changes/deployment/` directory no longer exists — confirmed

**Manual** (Progress section): 1.5, 1.6, 2.5–2.9 remain `[ ]` — intentionally deferred until the
branch is pushed and a PR is opened, per an explicit call made during implementation. Not evidence
of rubber-stamping; consistent with the plan's own "confirm with me before this push happens" note
for 2.6.

## Findings

### F1 — Third-party actions pinned to floating major-version tags

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:17, :67
- **Detail**: `dorny/paths-filter@v3` and `supabase/setup-cli@v1` are pinned to mutable major-version
  tags rather than a commit SHA. `actions/checkout@v4` and `actions/setup-java@v4` (official GitHub
  actions) are lower-risk by comparison. This is standard practice for third-party actions in most
  workflows and wasn't a requirement in the plan — flagging only as a future hardening option, not a
  defect.
- **Fix**: If tighter supply-chain posture matters later, pin to a specific commit SHA (e.g.
  `dorny/paths-filter@de90cc6` — verify the current release's SHA at that time) with a version
  comment.
- **Decision**: FIXED — pinned to `dorny/paths-filter@0e4a8c6effa4802afeda77dc8d303f8176d7dfad # v3`
  and `supabase/setup-cli@ab058987d8d6c725971f6cf9d0b5c98467e30bd1 # v1` (resolved from the `v1`
  branch tip, since `supabase/setup-cli`'s `v1` is a moving branch, not a tag).

### F2 — No explicit least-privilege `permissions:` block

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:1
- **Detail**: The workflow has no top-level `permissions:` key, so it inherits the repo's default
  `GITHUB_TOKEN` permissions. Nothing in this workflow needs write access (no PR comments, no
  pushes, no releases) — an explicit `permissions: contents: read` would follow CI least-privilege
  hardening, though it changes no actual behavior today given what the jobs do.
- **Fix**: Add `permissions:\n  contents: read` at the workflow's top level.
- **Decision**: FIXED — added `permissions:\n  contents: read` at the workflow's top level.

