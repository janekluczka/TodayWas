---
project: todaywas
researched_at: 2026-07-24
recommended_platform: Cloudflare Workers
runner_up: Supabase Edge Functions
context_type: mvp
tech_stack:
  language: typescript
  framework: cloudflare-workers
  runtime: v8-isolate-edge
---

## Scope note

`/10x-infra-research`'s default framing is "which platform hosts the app" — Cloudflare Workers,
Vercel, Netlify, Fly.io, Railway, Render. That doesn't apply to TodayWas: the app itself is a native
Android APK distributed via internal/sideload (no hosting platform involved), and the backend is
Supabase, an already-hosted managed platform with no separate deploy decision.

The real open infra question was narrower: **where does the AI-proxy function run** — the
server-side piece that holds the Gemini API key and proxies "help me start" / "help me refine"
requests (FR-009, FR-010), so the key is never embedded in the distributed APK where it would be
trivially extractable. This document scopes to that single decision.

## Recommendation

**Run the AI proxy on Cloudflare Workers.**

Both Cloudflare Workers and Supabase Edge Functions pass all five agent-friendly criteria cleanly
(CLI-first, managed/serverless, agent-readable docs, stable deploy API, MCP support). The initial
lead was Supabase Edge Functions for "fewer moving parts" (same vendor as Auth/Postgres), but the
anti-bias cross-check surfaced a real risk — cold-start latency on a synchronous, user-facing tap
("help me start" → wait → prompt appears) reading as the app freezing — significant enough to swap
to Cloudflare Workers' faster, more battle-tested edge runtime despite the second-vendor overhead.

## Platform Comparison

Narrowed to two candidates rather than the usual three — this is a single proxy-function decision,
not a full app-hosting choice, and a third pick would be padding rather than real signal.

| Criterion                     | Cloudflare Workers                                      | Supabase Edge Functions                                        |
| ----------------------------- | ------------------------------------------------------- | -------------------------------------------------------------- |
| CLI-first maintenance         | Pass — `wrangler` deploy/tail/rollback                  | Pass — `supabase functions deploy`/`logs`                      |
| Managed / serverless          | Pass — fully managed V8-isolate edge runtime            | Pass — fully managed Deno edge runtime                         |
| Agent-readable docs           | Pass — publishes `llms.txt` + markdown source on GitHub | Pass — markdown docs on GitHub (no confirmed `llms.txt`)       |
| Stable deployment API         | Pass — `wrangler deploy`, deterministic                 | Pass — `supabase functions deploy`, deterministic              |
| MCP / first-class integration | Pass — MCP servers across docs, Workers, observability  | Pass — official Supabase MCP Server (schema, migrations, logs) |

### 1. Cloudflare Workers (Recommended)

Sub-5ms cold starts across 330+ edge locations — the deciding factor for a synchronous, user-facing
tap. Free tier: 100,000 requests/day, more than sufficient at this app's scale. Costs $5/month only
if usage crosses the free tier (unlikely given the capped 3-regenerations/entry limit). Requires a
second vendor account and CLI (`wrangler`) alongside Supabase.

### 2. Supabase Edge Functions (runner-up)

Same vendor as Auth/Postgres already in use — one CLI, one dashboard, no new account. Free tier:
500,000 invocations/month, shared account-wide with any other functions added later. Lost the
recommendation on cold-start risk for this specific synchronous UX pattern, and because it shares a
failure domain with the core data layer (a Supabase incident would take down journaling and the AI
feature together).

## Anti-Bias Cross-Check: Cloudflare Workers

### Devil's Advocate — Weaknesses

1. Second vendor/account to manage alongside Supabase — separate billing, dashboard, and CLI
   (`wrangler`) on top of the Supabase CLI already needed for Auth/DB.
2. Secrets now split across two vaults (Cloudflare Workers Secrets + Supabase secrets), raising
   drift/rotation-gap risk.
3. Free tier CPU limit is 10ms/invocation — fine for a bare passthrough, tight if the proxy does any
   real work (e.g. shaping a prompt from multiple journal entries).
4. The proxy still needs to fetch the user's recent journal entries from Supabase to personalize the
   prompt (FR-009) — so it isn't actually vendor-independent; there's still a Worker→Supabase
   network hop, just now across an extra vendor boundary instead of staying internal to one.
5. No native Postgres driver support in Workers' stateless edge model — journal-entry lookups go
   through Supabase's HTTP Data API (PostgREST), not a drop-in detail.

### Pre-Mortem — How This Could Fail

The team deployed the Gemini-proxy Worker on Cloudflare, separate from Supabase. Six months later,
it was a disaster. The Worker needed the user's recent journal entries to personalize prompts, so
every "help me start" tap triggered a Worker-to-Supabase network hop before the Worker could even
call Gemini — the exact latency problem they'd tried to avoid, just relocated across two vendors
instead of one. Debugging a slow prompt meant checking Cloudflare's dashboard, Supabase's dashboard,
and Google's Gemini console — three places for one feature. The Gemini key lived in Cloudflare
Workers Secrets, completely separate from the Supabase secrets used everywhere else; during a rushed
rotation after the free tier ran low, the developer updated the wrong vault, leaving a stale key
live for two days because nothing failed loudly — it just quietly retried. What was meant to be a
simple proxy became the most fragile, most vendor-spanning part of the app.

### Unknown Unknowns

- Cloudflare Workers can't hold a persistent Postgres connection — journal-entry lookups need
  Supabase's HTTP Data API, not a native driver, which isn't obvious from Cloudflare's marketing
  docs.
- The 10ms free-tier CPU limit is easy to hit once the proxy does more than bare passthrough.
- Two separate secret vaults, no unified rotation workflow — has to be built manually.
- No single trace/log view across Android app → Worker → Supabase → Gemini → back; debugging means
  correlating timestamps across three dashboards.
- Cloudflare's advertised sub-5ms cold start only covers its own compute — the effective latency now
  includes a second network hop to Supabase that wasn't there in the co-located option.

## Operational Story

- **Preview deploys**: not applicable in the traditional PR-preview sense — this is a single
  low-traffic function, not a web app with per-branch preview URLs. `wrangler deploy --dry-run`
  validates a build before pushing live.
- **Secrets**: the Gemini API key lives in Cloudflare Workers Secrets
  (`wrangler secret put GEMINI_API_KEY`), set once and read via environment binding in the Worker.
  Never present in the Android app or committed to git. Rotation is a manual `wrangler secret put` —
  no automatic reminder exists; treat this as a standing to-do, not a one-time setup step.
- **Rollback**: `wrangler rollback` reverts to the previous deployed version; near-instant, no data
  migration concerns since the Worker is stateless (all persistent data stays in Supabase/Room).
- **Approval**: deploying/updating the Worker and rotating the Gemini key are the two actions that
  matter here — both are safe for a solo dev to run directly since there's no team or production
  traffic yet; no human-approval gate needed at this scale, but treat key rotation as a task to
  actually schedule, not one to do reactively only after an incident.
- **Logs**: `wrangler tail` streams live logs from the deployed Worker; Cloudflare's dashboard also
  provides a request log view. Read-only, no write access needed for observability.

## Risk Register

| Risk                                                                                                   | Source                              | Likelihood | Impact | Mitigation                                                                                                                                                                               |
| ------------------------------------------------------------------------------------------------------ | ----------------------------------- | ---------- | ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Proxy still round-trips to Supabase for journal context, undermining the vendor-independence rationale | Devil's advocate                    | H          | M      | Accept the extra hop; benchmark actual end-to-end latency (Worker → Supabase Data API → Gemini → back) before shipping, not just Cloudflare's advertised cold start.                     |
| Secret sprawl across two vaults (Cloudflare + Supabase) with no unified rotation workflow              | Devil's advocate / Unknown unknowns | M          | M      | Write down a rotation checklist (which key lives where) in `context/foundation/` once keys are actually provisioned; revisit if this becomes recurring friction.                         |
| 10ms free-tier CPU limit gets tight if prompt-shaping logic grows                                      | Devil's advocate                    | L          | L      | Keep the Worker's logic to request-shaping + pass-through; move any heavier logic to Supabase if it grows. Monitor via `wrangler tail` during early testing.                             |
| No cross-vendor request tracing; debugging a slow/failed prompt means correlating 3 dashboards         | Pre-mortem / Unknown unknowns       | M          | L      | Log a request ID at the Android app layer and echo it through the Worker's logs so at least manual correlation is possible.                                                              |
| Gemini's 60 req/min free-tier limit is global to the API key, not per-user                             | Research finding                    | L          | M      | Not a Cloudflare-specific risk (applies equally to any hosting choice) — monitor usage once real users are live; add basic per-request throttling in the Worker if it becomes a problem. |

## Getting Started

1. `npm create cloudflare@latest todaywas-ai-proxy -- --type hello-world` — scaffold a minimal
   Worker (verify this is still the current `create-cloudflare` invocation at implementation time;
   Cloudflare's CLI scaffolding flags have changed across versions — don't copy this verbatim
   without checking `npm create cloudflare@latest -- --help` first).
2. `wrangler secret put GEMINI_API_KEY` — store the Gemini API key server-side, never in the Android
   app.
3. Implement the Worker: accept a tone + optional user-thoughts payload from the Android app, fetch
   recent journal entries from Supabase's Data API (PostgREST) for personalization context, call the
   Gemini API, return the generated prompt.
4. `wrangler deploy` — ship it; note the returned Worker URL and wire it into the Android app's
   network layer (not yet built — this is a prerequisite the app's networking code will depend on).
5. `wrangler tail` while testing the first few "help me start" taps end-to-end from a real device.

## Out of Scope

The following were not evaluated in this research:

- Docker image configuration
- CI/CD pipeline setup for the Worker itself (the app's own CI is already decided — GitHub Actions,
  per `tech-stack.md`)
- Production-scale architecture (multi-region, HA, DR) — not relevant at this MVP's traffic level
- The Android app's own distribution (already decided in `tech-stack.md`: internal/sideload for the
  MVP, Play Store deferred)
- Supabase's own hosting (already a given — it's a managed platform, not a decision point)
