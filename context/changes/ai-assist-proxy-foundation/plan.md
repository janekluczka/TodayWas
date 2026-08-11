# AI-Assist Proxy Foundation Implementation Plan

## Overview

Implement and deploy the `ai-proxy` Supabase Edge Function — a stateless server-side proxy that
calls Google Gemini Flash on behalf of the future "help me start" / "help me refine" features
(FR-009, FR-010), gated entirely by Supabase's built-in JWT verification (`verify_jwt = true`) so
the Gemini API key never ships in the Android APK and no custom auth code is needed. This is the
F-02 roadmap Foundation: no Android app code changes, no user-facing screen — S-07 (`ai-starter-
prompt`) and S-08 (`ai-refine-entry`) build the tappable UI on top of this in later changes.

This plan folds `context/changes/deployment/deployment-plan.md`'s infra steps directly into its
phases rather than treating deployment as a separate prerequisite artifact, per this change's
scope note.

## Current State Analysis

- No `supabase/` directory exists anywhere in the repo yet — none of `deployment-plan.md`'s Phase
  0/1 steps have run.
- No CI/CD exists in the repo at all (`.github/workflows/` is empty) — `deployment-plan.md`'s
  Phase 4 assumed reusing an existing Android-app CI pipeline that was never actually built.
- The Supabase project from F-01 (`supabase-auth-foundation`) is live and already linked via this
  session's Supabase MCP server (`https://ibftrzalfdmqztmiuvnj.supabase.co`); no Edge Functions are
  deployed yet (`list_edge_functions` returns empty).
- The Android app's existing Supabase wiring (`SupabaseModule.kt`, `local.properties` →
  `BuildConfig`) is unaffected by this change — the Gemini key lives server-side only, never in
  `local.properties` or `BuildConfig`.
- `AuthRepository`/`AuthRepositoryImpl` (`app/src/main/java/pl/luczka/todaywas/data/repository/`)
  expose auth state and sign-in/up/out, but no access-token getter — irrelevant here since no app
  code calls the function yet; a future S-07 change adds that.

### Key Discoveries:

- The Supabase MCP server exposes `deploy_edge_function` (deploys straight from file contents,
  no local CLI needed) and `get_logs(service: "edge-function")`, but has **no tool for setting
  secrets** — `GEMINI_API_KEY` can only be set via the Supabase CLI or the dashboard's Edge
  Functions → Secrets UI. No MCP path exists to avoid this human step.
- `verify_jwt = true` rejects unauthenticated requests at the platform gate *before* the function
  handler runs — the handler itself needs zero auth code (confirmed in
  `context/foundation/infrastructure.md`'s Getting Started section).
- The Supabase MCP server's `execute_sql` tool has direct Postgres access, including the `auth`
  schema — usable both to bypass email-confirmation for a disposable curl-created test user (for
  JWT-based verification) and to delete that user afterward, so no real device/account is needed to
  test the auth-gated path end to end.
- Gemini's REST contract (confirmed via web search, since API versions move fast): `POST
  https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, auth via an
  `x-goog-api-key` header, body shape `{"contents": [{"parts": [{"text": "..."}]}]}`. The exact
  current Flash model id should be confirmed at implementation time (recent search results
  reference `gemini-3.6-flash` as of August 2026) rather than hardcoded from training data.

## Desired End State

A live, deployed `ai-proxy` Edge Function on the linked Supabase project, gated by
`verify_jwt = true`, that:

- Rejects any request without a valid Supabase session JWT (401, platform-level).
- Rejects a malformed body — missing/out-of-range `tone`, wrong types — with `400
  {"error": "invalid_request"}`.
- On a well-formed authenticated request, calls Gemini with a prompt built only from the tone pick
  and optional `thoughts` (never journal/habit data), and returns `200 {"text": "<generated
  prompt>"}`.
- Returns `502 {"error": "upstream_failed"}` if the Gemini call itself fails, without leaking the
  raw upstream error or the API key.

Verification: every behavior above is exercised via curl (a disposable test user provides the JWT)
and confirmed present in `get_logs(service: "edge-function")`; the function source is committed at
`supabase/functions/ai-proxy/index.ts`.

## What We're NOT Doing

- No Android app code — no tone-picker UI, no network call from the app, no `AuthRepository`
  access-token getter, no sign-in CTA. That's S-07 (`ai-starter-prompt`)/S-08 (`ai-refine-entry`).
- No local Supabase CLI install, `supabase init`/`link`, or `supabase/config.toml` — deployment goes
  through the MCP server's `deploy_edge_function` tool directly.
- No GitHub Actions auto-deploy pipeline — no CI exists in this repo yet for either the Android app
  or this function; building one is out of scope here and left as a known gap (future work can
  redeploy manually via MCP/CLI when the function changes).
- No server-side enforcement of the "3 regenerations per entry" cap (FR-009) — accepted MVP risk
  per `infrastructure.md`'s risk register, enforced client-side only when S-07 builds the UI.
- No rate-limiting/abuse protection beyond Supabase's own JWT gate.
- No forced-failure test of the Gemini-call error path after the real key is set — Phase 2 already
  exercises the `502`/`upstream_failed` path for free, before the key exists.

## Implementation Approach

Write a single-file, dependency-free Deno function, deploy it via the Supabase MCP server (no
local CLI), verify the auth-gate and validation paths immediately (they don't need the Gemini key),
pause for you to set the `GEMINI_API_KEY` secret by hand in the Supabase dashboard, then verify the
real success path end-to-end using a disposable curl-created test user for the JWT.

## Critical Implementation Details

### `verify_jwt` ordering means no auth code in the handler

Supabase's Edge Runtime checks the `Authorization` bearer token against its own Auth service and
returns 401 *before* `index.ts`'s handler ever executes when `verify_jwt = true`. The handler
should not attempt to read/validate a JWT itself — by the time it runs, the caller is already a
verified, signed-in Supabase user.

### Disposable test-user auth schema must be confirmed against the live project, not assumed

The exact `auth.users` column(s) marking email-confirmation status can vary by Supabase Auth
version. Before writing the `execute_sql` UPDATE that bypasses email confirmation for the curl test
user, run `list_tables(schemas: ["auth"], verbose: true)` against the live project to confirm the
real column name(s) rather than assuming `email_confirmed_at`/`confirmed_at` from general
knowledge.

### The `{error: "..."}` shape is a contract future changes depend on

S-07/S-08's Android client will branch on this function's error responses to distinguish "you're
not signed in" (401, platform-level) from "bad request" (400 `invalid_request`) from "AI call
failed" (502 `upstream_failed`) — this was an explicit anti-bias risk-register item in
`infrastructure.md` ("Android app should distinguish these, not one generic error"). Keep the two
app-defined codes (`invalid_request`, `upstream_failed`) exactly as named here; a future change
will read them.

---

## Phase 1: Implement and deploy the function

### Overview

Write the Deno Edge Function and deploy it via the Supabase MCP server. Verify the parts of its
behavior that don't require the Gemini secret to exist yet: the platform's auth gate.

### Changes Required:

#### 1. Edge Function source

**File**: `supabase/functions/ai-proxy/index.ts`

**Intent**: A single `Deno.serve` handler that parses `{tone, thoughts?}` from the request body,
validates `tone` is an integer 1–5, maps it to a mood label, builds a Gemini prompt from the label
and optional `thoughts` only (no journal/habit data ever touches this function, per the NFR), calls
Gemini's `generateContent` endpoint using the `GEMINI_API_KEY` secret via `Deno.env.get`, and
returns the generated text.

**Contract**:
- Request: `POST`, JSON body `{"tone": 1-5, "thoughts"?: string}`.
- Success: `200 {"text": string}`.
- Validation failure (missing/non-integer/out-of-range `tone`, non-string `thoughts` when present):
  `400 {"error": "invalid_request"}`.
- Gemini call failure (network error, non-2xx response, timeout): `502 {"error":
  "upstream_failed"}` — never include the raw Gemini error body or the API key in the response.
- No `Authorization`/session handling in the handler — `verify_jwt` (set at deploy time, see below)
  handles that entirely at the platform level.

#### 2. Deploy via MCP

**Intent**: Deploy the function to the live linked Supabase project using the Supabase MCP server's
`deploy_edge_function` tool — no local CLI install.

**Contract**: `deploy_edge_function(name: "ai-proxy", entrypoint_path: "index.ts", verify_jwt:
true, files: [{name: "index.ts", content: <source>}])`. Confirm success via `list_edge_functions`
showing `ai-proxy`.

### Success Criteria:

#### Automated Verification:

- `deploy_edge_function` succeeds and `list_edge_functions` lists `ai-proxy`.
- `curl -X POST <function-url>` with no `Authorization` header returns `401`.

#### Manual Verification:

- None for this phase.

---

## Phase 2: Request-validation checks via a disposable test identity

### Overview

Everything past the auth gate needs a real, valid Supabase session JWT to test — including the
`400` validation path. Script a throwaway test user via curl + `execute_sql`, then use its JWT to
exercise validation and (since the Gemini secret doesn't exist yet) the upstream-failure path, for
free.

### Changes Required:

#### 1. Disposable test-user script

**Intent**: A repeatable sequence (documented in this change, not committed to the app) that signs
up a random-email/password test user via Supabase's REST auth endpoints, bypasses email
confirmation via `execute_sql` if the project requires it, and exchanges credentials for a session
JWT — entirely from the command line, no device/emulator needed (side-steps the touch-injection
limitation noted in the `account-creation-and-sync` change).

**Contract**:
- `POST {SUPABASE_URL}/auth/v1/signup` with `apikey: <anon key>` header, body
  `{"email": "<random>@example.com", "password": "<random>"}`.
- If the returned user isn't already confirmed, `execute_sql` an `UPDATE` against the confirmed
  column(s) found in Phase 0's `list_tables(schemas: ["auth"])` check (see Critical Implementation
  Details).
- `POST {SUPABASE_URL}/auth/v1/token?grant_type=password` with the same credentials → returns
  `access_token`, used as the `Bearer` token for all subsequent curl calls in this and Phase 4.

### Success Criteria:

#### Automated Verification:

- Test-user JWT obtained successfully via the script above.
- Authenticated curl with a malformed body (missing `tone`) returns `400
  {"error": "invalid_request"}`.
- Authenticated curl with a well-formed body, before `GEMINI_API_KEY` is set, returns `502
  {"error": "upstream_failed"}` — confirms the error-shape contract without needing to force a
  failure later.

#### Manual Verification:

- None for this phase.

---

## Phase 3: Set the Gemini secret (human step)

### Overview

The one step in this change that can't be done by MCP or CLI without the raw key passing through
chat context: you create the Gemini API key and set it directly in the Supabase dashboard.

### Changes Required:

#### 1. `GEMINI_API_KEY` secret

**Intent**: You obtain a Gemini API key (Google AI Studio) and add it as a secret named exactly
`GEMINI_API_KEY` via the Supabase dashboard's Project Settings → Edge Functions → Secrets UI, on
the same linked project. I never see or handle the raw key.

**Contract**: A secret named `GEMINI_API_KEY` exists on the linked project, readable by the
function via `Deno.env.get("GEMINI_API_KEY")`. Nothing to verify programmatically beyond Phase 4's
success-path test actually succeeding.

### Success Criteria:

#### Automated Verification:

- None — no MCP/CLI tool can confirm a secret's presence or value.

#### Manual Verification:

- You confirm the secret is set (name matches exactly `GEMINI_API_KEY`) before Phase 4 proceeds.

---

## Phase 4: End-to-end success path and cleanup

### Overview

With the real key live, confirm the actual Gemini round-trip works, confirm it's observable via
logs, and clean up the disposable test user.

### Changes Required:

#### 1. Success-path verification

**Intent**: Re-run the well-formed authenticated request from Phase 2 now that the secret exists,
confirm a real Gemini-generated string comes back, and confirm the invocation is visible in logs.

**Contract**: Authenticated `POST` with `{"tone": 4, "thoughts": "made progress on a hard bug"}` (or
similar) returns `200 {"text": "<non-empty string>"}`. `get_logs(service: "edge-function")`
includes a recent entry for the call.

#### 2. Test-user cleanup

**Intent**: Delete the disposable test user created in Phase 2 so no throwaway account lingers in
the project's auth table.

**Contract**: `execute_sql` `DELETE FROM auth.users WHERE email = '<test email>'`.

### Success Criteria:

#### Automated Verification:

- Authenticated well-formed request returns `200` with a non-empty `text` field.
- `get_logs(service: "edge-function")` shows the recent invocation.
- Test user no longer present (`execute_sql` confirms zero rows for that email).

#### Manual Verification:

- Eyeball the returned prompt text once: it reads as a plausible tone-matched journal starter, and
  contains nothing that looks like it came from journal/habit data (none was ever sent, but a
  sanity check is cheap).

---

## Testing Strategy

### Unit Tests:

- None — this is a single-file, dependency-free Deno function with no existing Deno test tooling in
  this repo (no CLI installed, per the deployment-mechanism decision); curl-based integration
  checks in Phases 1/2/4 are the verification strategy, consistent with this repo's existing
  convention of no instrumented tests for auth-adjacent work (`account-creation-and-sync` plan's
  Testing Strategy).

### Integration Tests:

- Covered by Phases 1, 2, and 4's curl-based Automated Verification — the closest equivalent to
  integration testing available without a local Deno/Supabase CLI toolchain.

### Manual Testing Steps:

1. After Phase 3, spot-check the returned prompt text once for a couple of different tone values
   (e.g. tone 1 vs tone 5) to sanity-check the mood mapping reads correctly — not scripted, just a
   quick eyeball pass alongside Phase 4's automated check.

## Performance Considerations

Cold-start latency on Supabase's Deno runtime is a known, accepted risk for this MVP (see
`infrastructure.md`'s risk register) — not addressed here since no synchronous UI tap depends on it
yet (that's S-07). No caching or warm-up strategy is in scope for this change.

## Migration Notes

None — stateless function, no data migration.

## References

- Deployment plan (folded into this plan): `context/changes/deployment/deployment-plan.md`
- Infra decision: `context/foundation/infrastructure.md`
- PRD: `context/foundation/prd.md` FR-009, FR-010, Access Control, NFR (journal content never
  reaches AI)
- Roadmap: `context/foundation/roadmap.md` F-02
- Prior sibling pattern (curl-based verification without device access):
  `context/archive/2026-08-10-account-creation-and-sync/manual-verification-pending.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Implement and deploy the function

#### Automated

- [x] 1.1 `deploy_edge_function` succeeds; `ai-proxy` appears in `list_edge_functions`
- [x] 1.2 Unauthenticated curl returns 401

### Phase 2: Request-validation checks via a disposable test identity

#### Automated

- [ ] 2.1 Disposable test-user JWT obtained via curl + `execute_sql`
- [ ] 2.2 Authenticated malformed-body request returns 400 `invalid_request`
- [ ] 2.3 Authenticated well-formed request (no secret yet) returns 502 `upstream_failed`

### Phase 3: Set the Gemini secret (human step)

#### Manual

- [ ] 3.1 `GEMINI_API_KEY` secret confirmed set in Supabase dashboard

### Phase 4: End-to-end success path and cleanup

#### Automated

- [ ] 4.1 Authenticated well-formed request returns 200 with non-empty `text`
- [ ] 4.2 `get_logs(service: "edge-function")` shows the recent invocation
- [ ] 4.3 Disposable test user deleted

#### Manual

- [ ] 4.4 Returned prompt text spot-checked across a couple of tone values
