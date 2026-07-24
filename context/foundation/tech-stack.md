---
starter_id: android-kotlin-compose # NOTE: not a key in 10x-tech-stack-selector's starter-registry.yaml — no native-Kotlin/Android card exists (mobile only lists dart/flutter and js/expo). Placeholder documenting the existing Android Studio skeleton already scaffolded in this repo. /10x-bootstrapper cannot consume this value.
package_manager: gradle
project_name: todaywas
hints:
  language_family: java # closest schema enum to Kotlin/JVM; no dedicated `kotlin` value exists in the hand-off schema
  team_size: solo
  deployment_target: internal-sideload
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: best-effort
  path_taken: custom
  quality_override: false
  self_check_answers:
    typed: true
    from_official_starter: true
    conventions: true
    docs_current: true
    can_judge_agent: true
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: true
  has_background_jobs: false
---

## Why this stack

TodayWas is a solo, 3-week after-hours Android MVP with a hard 2026-08-31 deadline, and the repo
already has a native Kotlin + Jetpack Compose skeleton scaffolded by Android Studio — no registry
starter exists for this ecosystem, so this is a documented custom pick rather than a registry match.
Local persistence uses Room (SQLite) as the default for on-device journal/habit data, matching
FR-008's no-account-required guarantee. FR-007's optional account + auto-sync uses Supabase (Auth +
Postgres) — solid Kotlin SDK support, Google Sign-In compatible, and avoids building/maintaining a
custom backend solo within the timeline. FR-009/FR-010's AI-generated tone-based prompts use Google
Gemini Flash: its free tier (60 req/min) very likely covers the entire MVP's AI usage at zero cost,
given the capped 3-regenerations-per-entry limit and ~500-1000 target users. Deployment is
internal/sideload only for the MVP — no Play Store listing yet, revisited after the deadline. CI
runs on GitHub Actions with build+test on every merge to main; actual release signing/publishing
stays a manual step since there's no store listing yet. All five self-check points came back clean
(official Android Studio Compose template, typed, convention-based, current docs), so no
quality-override friction is expected despite the missing registry card.
