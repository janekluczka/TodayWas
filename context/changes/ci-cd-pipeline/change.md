---
change_id: ci-cd-pipeline
title: Set up GitHub Actions CI for the app plus Edge Function auto-deploy
status: implementing
created: 2026-08-16
updated: 2026-08-16
archived_at: null
---

## Notes

Two things are open, both flagged repeatedly by `/10x-health-check` and `tech-stack.md`
(`ci_provider: github-actions`, decided but never implemented):

1. **Android app CI** — no lint/test/build pipeline exists yet. `./gradlew ktlintCheck` and
   `./gradlew testDebugUnitTest` both pass cleanly and reliably locally (262/262, no known
   flakiness as of the `health-check-fixes` fixes merged into `master`), so there's no known
   blocker to wiring this up.

2. **Edge Function auto-deploy** — `context/changes/deployment/deployment-plan.md` (orphaned: no
   `change.md`, never went through `/10x-new` → `/10x-plan` → `/10x-implement`) already specifies
   Phase 4 for this: a path-scoped GitHub Actions job using `supabase/setup-cli`, triggered on push
   to `main`, scoped to `supabase/functions/**` and `supabase/config.toml` so Android-only commits
   don't trigger a redeploy and vice versa. **Phases 0–3 and 5 of that doc are already done** — the
   `ai-proxy` Edge Function exists at `supabase/functions/ai-proxy/` and was implemented/deployed
   manually via the separately-tracked `ai-assist-proxy-foundation` change (archived
   2026-08-11). Only Phase 4 (the CI wiring itself) is still real, unstarted work.

   One correction needed while folding this in: the doc's Phase 3 references `GEMINI_API_KEY`, but
   `tech-stack.md` documents the project switched from Gemini to OpenRouter during
   `ai-assist-proxy-foundation` (Gemini's Google Cloud project had billing enabled with depleted
   credits, not a genuine free-tier key). Whatever secret name is actually set via
   `supabase secrets set` today should be confirmed against the live Supabase project /
   `ai-proxy` implementation, not assumed from this stale doc.

Once this change's plan is written and implemented, `context/changes/deployment/deployment-plan.md`
should be folded in as context here and then retired (either merged into this change's own docs, or
moved to `context/archive/` as superseded) rather than left as a second, inconsistent CI/CD doc.
