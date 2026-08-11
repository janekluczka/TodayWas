---
project: todaywas
researched_at: 2026-07-24
recommended_platform: Supabase Edge Functions
runner_up: Cloudflare Workers
context_type: mvp
tech_stack:
  language: typescript
  framework: supabase-edge-functions
  runtime: deno-edge
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

## Decision history

This decision changed three times after the initial research pass — recorded here rather than
silently overwritten, since the reasoning at each step still matters:

1. **FR-009 corrected first.** It originally (incorrectly) said the AI prompt was personalized using
   "the user's own recent journal entries." Corrected: "help me start" only ever uses the tone pick
   and the optional in-the-moment thoughts text — never journal history or habit data.
   `context/foundation/prd.md` FR-009 and Business Logic reflect this. This made the proxy a pure
   stateless passthrough (tone + optional thoughts → Gemini → response), with no need to touch
   Supabase's data layer at all.
2. **Initial recommendation: Cloudflare Workers.** Supabase Edge Functions was the first lead
   ("fewer moving parts," same vendor as Auth/Postgres), but the anti-bias cross-check surfaced a
   cold-start-latency risk on the synchronous "help me start" tap significant enough to swap to
   Cloudflare's faster edge runtime.
3. **Reverted to Supabase Edge Functions.** User call: a second vendor/account/CLI is real setup
   cost for a solo 3-week MVP, and that cost outweighs the cold-start difference at this app's
   traffic level. Confirmed along the way: Supabase's own recommended auto-deploy path for Edge
   Functions is GitHub Actions (via `supabase/setup-cli`) — there's no dashboard-native Git
   integration parallel to Cloudflare's Workers Builds. Since GitHub Actions is already wired up for
   the Android app's CI, reusing it here is the lowest-friction option available, not a compromise.
4. **AI-assist gated to logged-in users (final).** FR-009/FR-010 now require a signed-in account
   (`context/foundation/prd.md` Access Control updated). This replaces the custom shared-secret auth
   scheme entirely: Supabase Edge Functions' **built-in JWT verification** (`verify_jwt = true`, the
   default) becomes the real authentication mechanism — the Android app sends the user's existing
   Supabase session token, and Supabase verifies it before the function code runs. This closes the
   open-proxy risk more robustly than a shared secret would (a real account is a meaningfully higher
   bar than a string extracted from an APK), and it further reinforces staying on Supabase —
   Cloudflare Workers would need to manually re-implement verification against Supabase-issued JWTs,
   which is more work, not less.
5. **AI upstream swapped from Gemini directly to OpenRouter (2026-08-11, during
   `ai-assist-proxy-foundation` implementation).** The provisioned Gemini API key turned out to
   belong to a billing-enabled Google Cloud project with depleted prepayment credits, not a
   zero-cost AI Studio key — the `429 RESOURCE_EXHAUSTED` response was reproducible and unrelated
   to code. Rather than debug that project's billing setup, the function was pointed at
   OpenRouter's OpenAI-compatible `chat/completions` endpoint using a `:free`-suffixed model. This
   does **not** change the hosting decision above (still Supabase Edge Functions) — only which
   upstream LLM API the function calls internally; the `{tone, thoughts} → {text}` contract and
   `verify_jwt` gate are unaffected. It does add a second AI vendor/account beyond Supabase, and
   OpenRouter's free-tier models are known to rotate/rate-limit without notice — a new standing
   risk, tracked below.

## Recommendation

**Run the AI proxy on Supabase Edge Functions, gated to signed-in users.**

Both platforms pass all five agent-friendly criteria cleanly (see comparison below); the deciding
factors were operational simplicity for a solo MVP (one vendor, one CLI, no second account) and,
after the login-gating decision, the fact that JWT-based auth is native to Supabase and would be
extra work to replicate on Cloudflare.

## Platform Comparison

| Criterion                     | Supabase Edge Functions (Recommended)                          | Cloudflare Workers                                      |
| ----------------------------- | -------------------------------------------------------------- | ------------------------------------------------------- |
| CLI-first maintenance         | Pass — `supabase functions deploy`/`logs`                      | Pass — `wrangler` deploy/tail/rollback                  |
| Managed / serverless          | Pass — fully managed Deno edge runtime                         | Pass — fully managed V8-isolate edge runtime            |
| Agent-readable docs           | Pass — markdown docs on GitHub (no confirmed `llms.txt`)       | Pass — publishes `llms.txt` + markdown source on GitHub |
| Stable deployment API         | Pass — `supabase functions deploy`, deterministic              | Pass — `wrangler deploy`, deterministic                 |
| MCP / first-class integration | Pass — official Supabase MCP Server (schema, migrations, logs) | Pass — MCP servers across docs, Workers, observability  |

### 1. Supabase Edge Functions (Recommended)

Same vendor as Auth/Postgres already in use — one CLI, one dashboard, no new account. Free tier:
500,000 invocations/month, far more than this feature will use. Built-in JWT verification against
Supabase's own Auth system is a direct fit now that AI-assist requires login — no custom auth code
to write or maintain. Auto-deploy reuses the Android app's existing GitHub Actions setup (Supabase
has no dashboard-native Git integration, so GitHub Actions is the standard path here anyway, not a
compromise).

### 2. Cloudflare Workers (runner-up)

Sub-5ms cold starts and a more battle-tested serverless platform generally — the right call if
cold-start latency on the "help me start" tap turns out to matter more in practice than expected.
Would require a second vendor/account/CLI and manually re-implementing verification against
Supabase-issued JWTs (Cloudflare has no native awareness of Supabase sessions). Revisit if
real-world cold-start latency on Supabase Edge Functions proves to be a genuine UX problem.

## Anti-Bias Cross-Check: Supabase Edge Functions (gated to logged-in users)

### Devil's Advocate — Weaknesses

1. Cold-start latency on Supabase's Deno runtime is generally less optimized than Cloudflare's
   V8-isolate model — on the synchronous "help me start" tap, a slow cold start can read as the app
   freezing. Accepted trade for lower setup complexity; worth watching once real usage exists.
2. Shares a failure domain with the core data layer — a Supabase incident takes down journaling,
   habit-tracking, and AI-assist together, since they're all one vendor now.
3. Login-gating AI-assist adds a real product-friction point: a user who wants "help me start" but
   hasn't created an account yet has to stop and sign in first. This is a deliberate scope trade-off
   (see FR-009's Socratic note in the PRD), not an oversight, but it's worth the Android app
   surfacing that friction well (a clear sign-in CTA, not a silent failure) rather than treating it
   as a footnote.
4. The "3 regenerations per entry" cap (FR-009) still has no explicit server-side enforcement —
   login-gating doesn't automatically add this. It's now _technically easier_ to add later (the
   function has the caller's verified identity available), but it isn't built by default.
5. Secrets management is simpler now (just `GEMINI_API_KEY`, no `APP_SHARED_SECRET`), but rotation
   is still a manual `supabase secrets set` with no automatic reminder — a standing to-do.

### Pre-Mortem — How This Could Fail

The team deployed the Gemini-proxy function on Supabase, gated behind login via the platform's
built-in JWT verification. Six months later, it wasn't a security disaster — the auth held up fine —
but it was a quieter one. Cold starts on the rarely-invoked AI endpoint routinely added a
second-plus of latency on top of the already-present sign-in requirement, so users who weren't
already signed in saw two frictions stacked: "sign in first," then "wait for it to think." Usage of
the feature quietly stayed near zero, and nobody noticed for months because the metrics dashboard
only tracked successful calls, not abandoned taps. Separately, during a brief Supabase platform
incident, journaling, habit check-ins, and AI-assist all went down at once — a user opening the app
that day saw a single vague error with no way to tell it was a third-party outage rather than a bug
in the app itself. What was meant to be the simple choice quietly cost the team the one feature
meant to differentiate the app from every other journaling app.

### Unknown Unknowns

- Supabase Edge Functions' cold-start behavior for an infrequently-invoked function is generally
  worse than Cloudflare's edge-isolate model — worth actually benchmarking before assuming it's
  fine, especially now that it's stacked behind a sign-in requirement rather than being the only
  friction point.
- Requiring login for AI-assist means usage of the feature is now gated by however many users
  actually create an account (FR-007 is optional) — the real adoption ceiling for "help me start" is
  bounded by sign-in conversion, not just feature quality. Worth tracking sign-in rate alongside
  AI-assist usage once real users exist.
- A Supabase regional outage now takes down journaling, habits, and AI-assist together — no
  independent failure domain. The Android app's error handling should distinguish "Supabase is down"
  from "you're not signed in" from "the AI call failed" — three different states that are easy to
  conflate into one generic error if not designed for explicitly.
- Supabase's free-tier Edge Function invocation budget (500K/month) is shared account-wide with any
  other functions added later — not ring-fenced per feature.
- OpenRouter's free-tier models are shared-pool and rate-limited per model, not per-user —
  login-gating reduces abuse risk but doesn't eliminate the shared ceiling if legitimate usage
  grows (superseded from the original Gemini-specific framing; see Decision History #5).

## Operational Story

- **Preview deploys**: not applicable in the traditional PR-preview sense — a single low-traffic
  function, not a web app with per-branch preview URLs.
- **Secrets**: the OpenRouter API key lives in Supabase's project secrets
  (`OPENROUTER_API_KEY`, set via the dashboard's Edge Functions → Secrets UI or `supabase secrets
  set`), never present in the Android app or committed to git. Rotation is a manual step with no
  automatic reminder — treat as a standing to-do.
- **Auth**: Supabase's built-in `verify_jwt = true` (the Edge Functions default) rejects any request
  without a valid Supabase session token before the function code runs. The Android app must send
  the signed-in user's session token as a Bearer token on every "help me start"/"help me refine"
  call.
- **Rollback**: `supabase functions deploy` re-deploys the previous version's code on revert (no
  built-in one-command rollback equivalent to `wrangler rollback` — confirm current CLI behavior at
  implementation time); stateless function, no data-migration concerns.
- **Approval**: deploying/updating the function and rotating the OpenRouter key are safe for a solo dev
  to run directly at this scale — no team or production traffic yet, no human-approval gate needed
  beyond normal git review. Key rotation should be a scheduled task, not a reactive one.
- **Logs**: `supabase functions logs ai-proxy` or the dashboard's realtime log view, both read-only.

## Risk Register

| Risk                                                                                       | Source                              | Likelihood | Impact | Mitigation                                                                                                                                     |
| ------------------------------------------------------------------------------------------ | ----------------------------------- | ---------- | ------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Cold-start latency on the synchronous "help me start" tap reads as the app freezing        | Devil's advocate / Pre-mortem       | M          | M      | Benchmark actual cold-start time before shipping; consider a loading-state UI treatment regardless, since some latency is expected either way. |
| Login-gating stacks friction (sign-in + AI wait) on top of each other, suppressing usage   | Pre-mortem                          | M          | M      | Android app should surface a clear, low-friction sign-in CTA at the "help me start" entry point, not a dead end.                               |
| Shared failure domain — a Supabase incident takes down journaling, habits, and AI together | Devil's advocate / Unknown unknowns | L          | M      | Android app should distinguish "service unavailable" from "not signed in" from "AI call failed" in its error states, not one generic error.    |
| "3 regenerations per entry" cap (FR-009) has no server-side enforcement                    | Devil's advocate / Unknown unknowns | M          | L      | Accept as a client-side-only guard for the MVP; now easier to add later since the function has verified caller identity. Revisit if abused.    |
| OpenRouter free-tier models rotate/rate-limit without notice                               | Decision history #5                 | M          | M      | Keep the model id in one easily-changeable constant; if a model 404s or degrades, swap to another current `:free` model from openrouter.ai/models. |
| Secret rotation (`OPENROUTER_API_KEY`) is manual with no reminder                           | Unknown unknowns                    | L          | L      | Write down a rotation checklist once the key is actually provisioned; revisit if this becomes recurring friction.                              |

## Getting Started

The full phased plan (prerequisites, scaffolding, secrets, GitHub Actions auto-deploy, and
verification) is written out with checkboxes at `context/changes/deployment/deployment-plan.md`.
Summary:

1. Create a Supabase project (if none exists yet), install the Supabase CLI, `supabase login`,
   `supabase init`, `supabase link --project-ref <ref>`.
2. `supabase functions new ai-proxy` — leave `verify_jwt` at its default (`true`); this is now the
   actual auth gate, so confirm the current config syntax rather than assuming.
3. Implement the function: accept `{ tone, thoughts? }`, call an upstream LLM, return the generated
   text. No custom auth code needed — Supabase rejects unauthenticated requests before the handler
   runs.
4. `supabase secrets set OPENROUTER_API_KEY=<value>` (originally scoped as `GEMINI_API_KEY`; see
   Decision History #5 for why it changed).
5. Add a path-scoped job to the existing GitHub Actions workflow (triggered on
   `supabase/functions/**` changes) using `supabase/setup-cli`, with `SUPABASE_ACCESS_TOKEN` and
   `SUPABASE_PROJECT_REF` as repo secrets.
6. Verify: unauthenticated request rejected, authenticated request returns a real generated
   response, push-to-main triggers an automatic redeploy.

**What actually shipped for `ai-assist-proxy-foundation` differs from steps 1, 2, and 5 above**:
deployment went through the Supabase MCP server's `deploy_edge_function` tool directly rather than
the local CLI, and no GitHub Actions job was built (no CI existed in this repo at all yet — see
`context/changes/ai-assist-proxy-foundation/plan.md` for the full rationale). Steps 1/2/5 remain
here as the originally-researched path, in case a future change revisits CLI/CI-based deploys.

## Out of Scope

The following were not evaluated in this research:

- Docker image configuration
- Production-scale architecture (multi-region, HA, DR) — not relevant at this MVP's traffic level
- The Android app's own distribution (already decided in `tech-stack.md`: internal/sideload for the
  MVP, Play Store deferred)
- Supabase's own hosting (already a given — it's a managed platform, not a decision point)
- Wiring the function URL and session-token auth into the Android app's networking layer, and the
  sign-in CTA UX for signed-out users tapping AI-assist — app-side implementation work, tracked in
  `context/changes/deployment/deployment-plan.md`'s "out of scope" section, not solved here
