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

**Correction (post-write):** FR-009 originally (incorrectly) specified that the AI prompt was
personalized using "the user's own recent journal entries." That was wrong — per-PRD correction,
"help me start" only ever uses the tone pick and the optional in-the-moment thoughts text; it never
reads journal history or habit data. `context/foundation/prd.md` FR-009 and the Business Logic
section have been corrected to match. This changes the proxy's shape: it's a pure stateless
passthrough (tone + optional thoughts → Gemini → response), with no Supabase round-trip at all. The
cross-check findings below reflect the corrected, simpler design.

## Recommendation

**Run the AI proxy on Cloudflare Workers.**

Both Cloudflare Workers and Supabase Edge Functions pass all five agent-friendly criteria cleanly
(CLI-first, managed/serverless, agent-readable docs, stable deploy API, MCP support). The initial
lead was Supabase Edge Functions for "fewer moving parts" (same vendor as Auth/Postgres), but the
anti-bias cross-check surfaced a real risk — cold-start latency on a synchronous, user-facing tap
("help me start" → wait → prompt appears) reading as the app freezing — significant enough to swap
to Cloudflare Workers' faster, more battle-tested edge runtime despite the second-vendor overhead.
Now that the proxy is confirmed to be a pure stateless passthrough (no journal-history lookup), the
second-vendor overhead is the only real remaining downside — there's no Supabase coupling to give
back in exchange, which makes Cloudflare Workers a cleaner win than the first pass suggested.

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
3. As a bare stateless passthrough with no request authentication of its own, the Worker has no
   built-in way to tell a legitimate app request from any other HTTP client that discovers its
   public URL — it's an open proxy to Gemini's API unless request auth is deliberately added.
4. The "regenerate up to 3 times per entry" business rule (FR-009) has no server-side enforcement
   point once the proxy doesn't touch Supabase or any per-entry state — as designed, it's a
   client-side-only constraint in the Android app, trivially bypassable by any caller that skips the
   app.
5. Free tier CPU limit is 10ms/invocation — very unlikely to bind for a bare passthrough (mostly I/O
   wait on the Gemini call, not CPU), but worth confirming once request-auth/validation logic is
   added on top.

### Pre-Mortem — How This Could Fail

The team deployed the Gemini-proxy Worker on Cloudflare as a bare stateless passthrough — tone and
optional thoughts in, generated prompt out. Six months later, it was a disaster, but not for the
reason anyone expected. Because the Worker had no way to verify a request came from the real app,
someone found the Worker's URL in the APK's decompiled network layer within a week of a small public
mention, and started hitting it directly with scripted requests — no journal entry, no regeneration
cap, just free API calls running straight into the Gemini free tier's rate limit within days. The "3
regenerations per entry" rule, which existed only in the Android app's local state, meant nothing to
a client that skipped the app entirely. What was meant to be a two-hour proxy setup became a
scramble to add request signing and rate limiting after the free tier was already exhausted by a
stranger — the kind of guard that should have shipped on day one, not been patched on after the
fact.

### Unknown Unknowns

- The Worker has no way to distinguish a legitimate app request from any other HTTP client hitting
  its public URL — without request authentication (e.g. a shared secret header, or verifying the
  caller's Supabase session), it's effectively an open proxy to Gemini for anyone who finds the URL.
- The regeneration cap (FR-009: max 3 per entry) has no server-side enforcement point in a stateless
  design that never touches Supabase — it's a UI-only constraint today, not a real limit.
- The 10ms free-tier CPU limit was the headline concern in the original (journal-history-fetching)
  design; for a bare passthrough it's very unlikely to bind — the real cost/abuse risk shifted from
  compute limits to unauthenticated request volume.
- Outbound `fetch` calls to Gemini's API count against Workers' own subrequest limits (plan-
  dependent) — worth confirming headroom once real traffic patterns are known.
- Removing the Supabase dependency makes this Worker small and easy to reason about, but that
  simplicity can also mean it gets under-engineered relative to the abuse surface it exposes — a
  public endpoint that spends someone else's (Google's) API quota by design.

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

| Risk                                                                                              | Source                                           | Likelihood | Impact | Mitigation                                                                                                                                                                                              |
| ------------------------------------------------------------------------------------------------- | ------------------------------------------------ | ---------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Worker is an unauthenticated open proxy to Gemini — anyone who finds the URL can call it directly | Devil's advocate / Pre-mortem / Unknown unknowns | H          | H      | Require a shared secret header (or verify the caller's Supabase session/anon key) before forwarding to Gemini. Build this in from the first version, not as a follow-up.                                |
| "3 regenerations per entry" cap (FR-009) has no server-side enforcement in a stateless design     | Devil's advocate / Unknown unknowns              | M          | M      | Accept as a client-side-only guard for the MVP given the timeline; note explicitly that it's bypassable, and revisit if abuse is observed. Combined with request auth above, this bounds the real risk. |
| Secret sprawl across two vaults (Cloudflare + Supabase) with no unified rotation workflow         | Devil's advocate / Unknown unknowns              | M          | M      | Write down a rotation checklist (which key lives where) in `context/foundation/` once keys are actually provisioned; revisit if this becomes recurring friction.                                        |
| Gemini's 60 req/min free-tier limit is global to the API key, not per-user                        | Research finding                                 | L          | M      | Monitor usage once real users are live; the request-auth mitigation above also bounds this by blocking non-app traffic.                                                                                 |
| 10ms free-tier CPU limit                                                                          | Devil's advocate                                 | L          | L      | Very unlikely to bind for a bare passthrough plus auth check; monitor via `wrangler tail` during early testing.                                                                                         |

## Getting Started

1. `npm create cloudflare@latest todaywas-ai-proxy -- --type hello-world` — scaffold a minimal
   Worker (verify this is still the current `create-cloudflare` invocation at implementation time;
   Cloudflare's CLI scaffolding flags have changed across versions — don't copy this verbatim
   without checking `npm create cloudflare@latest -- --help` first).
2. `wrangler secret put GEMINI_API_KEY` — store the Gemini API key server-side, never in the Android
   app. Also set a second secret (e.g. `wrangler secret put APP_SHARED_SECRET`) for request
   authentication — see risk register above; this is not optional.
3. Implement the Worker: verify the request-auth secret/header first and reject anything that
   doesn't match, then accept a tone + optional user-thoughts payload from the Android app, call the
   Gemini API, and return the generated prompt. No Supabase calls — this is a pure passthrough.
4. `wrangler deploy` — ship it; note the returned Worker URL and wire it into the Android app's
   network layer (not yet built — this is a prerequisite the app's networking code will depend on).
   The app must send the shared secret/auth header on every call.
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
