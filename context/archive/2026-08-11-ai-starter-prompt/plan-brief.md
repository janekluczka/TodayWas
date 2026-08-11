# AI Starter Prompt — Plan Brief

> Full plan: `context/changes/ai-starter-prompt/plan.md`

## What & Why

Implement S-07: a "help me start" entry point on the Add Journal Entry screen. A signed-in user
picks a tone (5-point scale), optionally types a few thoughts, and gets an AI-generated, editable
starting prompt inserted into the entry — regenerable up to 3 times. This is the first Android app
code for AI-assist; the server-side proxy it calls (F-02) is already live and deployed.

## Starting Point

`ai-proxy`, a Supabase Edge Function, is deployed and JWT-gated (`context/archive/2026-08-11-ai-
assist-proxy-foundation/`): `POST {tone, thoughts?}` → `200 {text}` / `400 invalid_request` / `401`
/ `502 upstream_failed`. `AddJournalEntryScreen`/`ViewModel` exists (plain `@HiltViewModel`, a
`DsTextField` bound to `text`) but has no AI-assist UI. The Supabase client only has `Auth` and
`Postgrest` installed — no `Functions` plugin, no generic HTTP client anywhere in the app yet.

## Desired End State

A signed-in user on the Add Journal Entry screen sees "help me start," taps it, picks a tone
(+ optional thoughts), generates a prompt, and can accept it (replacing the entry text), regenerate
(up to 3 times), or cancel. Signed-out users don't see the entry point at all.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Network mechanism | supabase-kt `Functions` plugin | Auto-attaches the session token and derives the URL from the existing client — zero new config, zero `AuthRepository` changes. | Plan |
| Wizard state ownership | Embedded in `AddJournalEntryViewModel`/`UiState` | No Nav3 entry exists for this dialog; matches `lessons.md`'s rule and the onboarding precedent. | Plan |
| Entry-point scope | Add-entry screen only | Matches US-05's wording exactly; edit-within-24h is left to a future "help me refine" (S-08). | Plan |
| Sign-in gating | Hide the entry point when signed out | Never shown = never an unexpected redirect; simplest UI. | Plan |
| Regenerate cap persistence | Persists for the whole screen visit | Closes the "close/reopen dialog to reset count" loophole within one visit. | Plan |
| Text overwrite | Always available; explicit "Use this" replaces text | Supports the common "wrote a sentence, then got stuck" case; no silent data loss since accept is deliberate. | Plan |
| Failed-generation attempt counting | Failures don't consume a regenerate attempt | Protects the user from the free-tier OpenRouter model's documented flakiness (tech-stack.md). | Plan |
| Dialog layout | Single step: tone + thoughts + generate together | Fewest taps, matches this feature's low-effort premise. | Plan |

## Scope

**In scope:** tone/error domain models, `AiAssistRepository` calling `ai-proxy` via the `Functions`
plugin, the request/response DTOs, the "help me start" dialog UI embedded in
`AddJournalEntryScreen`/`ViewModel`, sign-in gating, the client-side regenerate cap, unit tests, and
manual end-to-end verification against the live function.

**Out of scope:** "help me refine" (S-08), any AI-assist UI on the edit-within-24h detail screen,
server-side cap enforcement, an `AuthRepository` access-token getter, any new
`local.properties`/`BuildConfig` entry, offline retry/queueing, analytics.

## Architecture / Approach

`AddJournalEntryViewModel` gains an `ObserveAuthStateUseCase` collection (drives whether the entry
point renders) and a `RequestJournalStarterPromptUseCase` call (drives the dialog). The dialog's own
state (`HelpMeStartUiState`: step, tone, thoughts, generated text, loading, error, regenerate count)
is a nested field on `AddJournalEntryUiState`, following the same "embed a small flow in its host
ViewModel" pattern onboarding already established. The repository layer is a thin, unit-tested-only-
at-the-error-mapping-level wrapper around `supabase.functions.invoke("ai-proxy")` — matching how
`RemoteJournalDataSourceImpl` is untested at that same layer today.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Domain & data layer | `AiAssistRepository` calling the live `ai-proxy` function, tone/error models, use case | supabase-kt `Functions` plugin's exact API/failure contract must be confirmed at implementation time, not assumed |
| 2. UI | Dialog wired into Add-entry screen, sign-in gating, regenerate cap, tests, manual E2E verification | Regenerate-cap state must survive dialog close/reopen without resetting — an easy state-management slip |

**Prerequisites:** F-02 (`ai-assist-proxy-foundation`, done) — live `ai-proxy` function.
**Estimated effort:** ~1-2 sessions across 2 phases.

## Open Risks & Assumptions

- The exact exception type/shape `supabase-kt` 3.7.0's `Functions.invoke()` throws on a non-2xx
  response is unconfirmed until implementation time — the error-mapping logic depends on it.
- The underlying `ai-proxy` function calls a free-tier OpenRouter model documented as prone to
  rotation/rate-limiting without notice (`tech-stack.md`) — a manual-verification flake risk
  outside this plan's control.

## Success Criteria (Summary)

- A signed-in user can generate, regenerate (up to 3 times), and accept an AI-starter prompt into a
  new journal entry.
- A signed-out user never sees the entry point.
- A failed generation shows an inline error and never consumes a regenerate attempt.
