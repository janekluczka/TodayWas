# AI-Assist Proxy Foundation — Plan Brief

> Full plan: `context/changes/ai-assist-proxy-foundation/plan.md`

## What & Why

Deploy `ai-proxy`, a Supabase Edge Function that proxies calls to Google Gemini Flash for the
future "help me start"/"help me refine" journal features (FR-009, FR-010). It's gated entirely by
Supabase's built-in JWT verification, so the Gemini API key never ships in the Android APK and no
custom auth code is needed. This is the F-02 roadmap Foundation — infra only, no app-facing UI yet.

## Starting Point

No `supabase/` directory or CI/CD exists in this repo yet. The Supabase project from F-01 is live
and already linked via this session's MCP server, with zero Edge Functions deployed so far.

## Desired End State

A live, deployed `ai-proxy` function that rejects unauthenticated/malformed requests and, given a
valid signed-in session and a tone + optional thoughts, returns a real Gemini-generated
journal-starter string — verified entirely via curl and MCP tools, no device needed.

## Key Decisions Made

| Decision                     | Choice                                             | Why (1 sentence)                                                              | Source |
| ----------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------- | ------ |
| Deployment mechanism           | MCP `deploy_edge_function`, no local CLI            | Fastest path for a solo, time-constrained MVP; no new local tooling.            | Plan   |
| Function source control        | Commit to `supabase/functions/ai-proxy/`            | Version history and a path to CI later, even though deploy bypasses git.       | Plan   |
| CI/CD                          | Skip entirely for this change                       | No CI exists in this repo at all yet; building one is separate, larger scope.  | Plan   |
| Gemini key handoff             | You set it via Supabase dashboard Secrets UI        | The raw key never passes through chat/agent context — best practice.           | Plan   |
| Test auth (curl verification)  | Disposable test user via curl + `execute_sql`       | Fully automatable; sidesteps the emulator touch-injection limitation.          | Plan   |
| UI scope                       | Proxy only — no Android app code                    | Matches the roadmap's own F-02 (infra) vs. S-07 (UI) split; keeps this shippable fast. | Plan |
| Error response shape           | `{error: "invalid_request"}` / `{error: "upstream_failed"}` | Future S-07/S-08 client needs to distinguish failure causes — cheap to build now. | Plan |

## Scope

**In scope:** the Deno function's implementation, MCP-based deploy, request validation, the Gemini
call and its error handling, curl/MCP-based end-to-end verification, secret hand-off process.

**Out of scope:** any Android app code (tone-picker UI, network call, sign-in CTA — that's
S-07/S-08), local Supabase CLI setup, GitHub Actions auto-deploy, Android app's own CI,
server-side enforcement of the 3-regenerations cap.

## Architecture / Approach

A single dependency-free `Deno.serve` handler behind Supabase's `verify_jwt = true` gate. No
database, no state — request in, one Gemini call out, response out. Deployed straight from this
session via the Supabase MCP server rather than a local CLI/CI pipeline.

## Phases at a Glance

| Phase                                   | What it delivers                                  | Key risk                                            |
| ----------------------------------------- | ---------------------------------------------------- | ------------------------------------------------------ |
| 1. Implement & deploy                     | Deployed function; auth gate confirmed (401)         | Gemini API/model-id specifics may have shifted — confirm at implementation time. |
| 2. Request-validation checks              | 400/502 paths confirmed via a disposable test user   | Auth-schema column names for email confirmation may vary by Supabase version. |
| 3. Set the Gemini secret (human step)     | `GEMINI_API_KEY` live on the project                 | Blocks Phase 4 until you complete this manually.     |
| 4. End-to-end verification + cleanup      | Real Gemini round-trip confirmed; test user removed  | None significant — stateless, low blast radius.      |

**Prerequisites:** F-01 (done); a Supabase MCP session with `execute_sql`/`deploy_edge_function`
access to the linked project (already available this session).
**Estimated effort:** ~1 session across 4 phases, with a pause at Phase 3 for your manual secret
step.

## Open Risks & Assumptions

- Gemini's current Flash model id should be confirmed at implementation time rather than assumed
  from training data — API naming moves fast.
- The `auth.users` confirmation-column name(s) used to bypass email confirmation for the disposable
  test user need confirming against the live schema before writing the SQL.
- No CI means future edits to this function require a manual MCP/CLI redeploy — a known, accepted
  gap for this MVP.

## Success Criteria (Summary)

- An unauthenticated request to `ai-proxy` is rejected (401).
- A signed-in test identity gets a real, tone-matched Gemini response back (200) once the secret is
  set.
- Malformed requests and upstream Gemini failures return the documented, stable error shapes.
