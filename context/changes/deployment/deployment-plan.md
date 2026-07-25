# Deployment Plan: AI-Proxy on Supabase Edge Functions

Source decision: `context/foundation/infrastructure.md`. Deploys the server-side proxy that holds
the Gemini API key and serves FR-009/FR-010 ("help me start" / "help me refine"), gated to signed-in
users via Supabase's built-in JWT verification.

## Phase 0 — Prerequisites

- [ ] Create a Supabase project if one doesn't exist yet (free tier) — manual step, human-only, via
      the Supabase dashboard.
- [ ] Install the Supabase CLI.
- [ ] `supabase login` — browser OAuth against the Supabase account.
- [ ] `supabase init` at repo root — scaffolds `supabase/` (config.toml, functions/, migrations/).
- [ ] `supabase link --project-ref <ref>` — links the local repo to the Supabase project created
      above.

## Phase 1 — Scaffold the function

- [ ] `supabase functions new ai-proxy` — generates `supabase/functions/ai-proxy/index.ts` with Deno
      boilerplate.
- [ ] Leave `verify_jwt` at its default (`true`) for this function in `supabase/config.toml` — this
      is the actual auth gate (AI-assist requires a signed-in account per FR-009/FR-010). Confirm
      the exact current config syntax against Supabase CLI docs at implementation time rather than
      assuming from memory — getting this backwards (`false`) would silently reopen an open-proxy
      risk.

## Phase 2 — Minimal proxy implementation

- [ ] Implement the request handler (`Deno.serve`). Supabase's runtime rejects unauthenticated
      requests before the handler runs (via `verify_jwt`), so the handler itself doesn't need to
      check auth — it can read the verified caller's identity from the request context later if
      needed (e.g. for future per-user rate limiting), but that's not required for MVP scope.
- [ ] Accept `{ tone: 1-5, thoughts?: string }` JSON body, call the Gemini API with a prompt built
      from those two inputs only — no journal or habit data (per the corrected FR-009) — and return
      the generated text.
- [ ] Error handling: malformed body → 400, Gemini call failure → 502 with a generic message (never
      leak the Gemini key or raw upstream error to the client).
- [ ] `supabase functions serve ai-proxy` for local testing before first deploy. Note: local testing
      now requires a real (or locally-emulated) Supabase auth session to pass `verify_jwt`, not a
      plain curl call — confirm the local-dev auth flow against current Supabase CLI docs.

## Phase 3 — Secrets

- [ ] `supabase secrets set GEMINI_API_KEY=<value>` — stored server-side against the linked project,
      never in a file or committed to git.

## Phase 4 — Auto-deploy via GitHub Actions (reusing existing CI)

- [ ] Add a new job to the repo's existing GitHub Actions workflow using the official
      `supabase/setup-cli` action, triggered on push to `main`.
- [ ] Scope the trigger with a `paths:` filter to `supabase/functions/**` and
      `supabase/config.toml`, so Android-app-only commits don't trigger a function redeploy and vice
      versa.
- [ ] Add `SUPABASE_ACCESS_TOKEN` and `SUPABASE_PROJECT_REF` as GitHub Actions repo secrets
      (generate the access token via the Supabase dashboard first).
- [ ] Edge case: the CI runner needs `SUPABASE_ACCESS_TOKEN`-based auth rather than the interactive
      `supabase login` used locally in Phase 0.

## Phase 5 — Verification

- [ ] `supabase functions deploy ai-proxy` once manually to get the first live endpoint before
      relying on the GitHub Actions pipeline.
- [ ] `curl` the deployed function with no `Authorization` header — confirm Supabase's built-in JWT
      verification rejects it.
- [ ] `curl` with a valid Supabase session JWT (obtained via a real sign-in) plus a valid
      tone/thoughts payload — confirm a real Gemini response comes back.
- [ ] Push a trivial change under `supabase/functions/ai-proxy/` on `main`, confirm the GitHub
      Actions workflow picks it up and redeploys automatically.
- [ ] `supabase functions logs ai-proxy` (or the dashboard's realtime log view) while running the
      curl tests above, to confirm log visibility works.

## Out of scope for this plan

- Wiring the function URL and sending the user's Supabase session JWT from the Android app's
  networking layer — app-side implementation work, not infra/deployment.
- The Android-side sign-in prompt/CTA shown when a signed-out user taps "help me start" / "help me
  refine" — app-side UX work.
- Server-side enforcement of the "3 regenerations per entry" cap (FR-009) — accepted as a known,
  documented risk in `infrastructure.md`; not solved by this deploy. Technically easier to add later
  since the function has the caller's verified identity available.
- Custom domain — Supabase's default project URL is sufficient for the MVP.

## Verification

Phase 5 is the end-to-end verification: unauthenticated requests proven-rejected by Supabase's own
JWT check (no custom auth code to maintain), an authenticated request proven-working, and a proven
automatic redeploy on push via the reused GitHub Actions pipeline.
