<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: AI-Assist Proxy Foundation

- **Plan**: context/changes/ai-assist-proxy-foundation/plan.md
- **Scope**: Phase 1 of 4 (full plan review — all phases complete)
- **Date**: 2026-08-11
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — `infrastructure.md`'s "Getting Started" section still describes Gemini as live

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/foundation/infrastructure.md:197,199,203; also :153 (Unknown Unknowns bullet)
- **Detail**: The plan's Critical Implementation Details section states `tech-stack.md` and `infrastructure.md` were updated to record the Gemini→OpenRouter pivot. `tech-stack.md` is fully consistent, and `infrastructure.md`'s Decision History #5 and Operational Story sections correctly describe OpenRouter. But the "Getting Started" section (steps 3, 4, 6) still says "call Gemini, return the generated text", `supabase secrets set GEMINI_API_KEY=<value>`, and "authenticated request returns a real Gemini response" — all now wrong. The "Unknown Unknowns" bullet about "Gemini's 60 req/min free-tier rate limit" is also stale given the switch to OpenRouter's `:free` models (whose own rotation risk is tracked separately in the Risk Register).
- **Fix**: Update Getting Started steps 3/4/6 and the stale rate-limit bullet to reference OpenRouter/`OPENROUTER_API_KEY`, or add a pointer to Decision History #5 so readers aren't misled by the older prose.
- **Decision**: FIXED

### F2 — No timeout on the outbound OpenRouter fetch

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: supabase/functions/ai-proxy/index.ts:50-60 (`callOpenRouter`)
- **Detail**: The `fetch()` call to OpenRouter has no deadline. If OpenRouter hangs, the request rides out to the platform's own execution ceiling instead of failing fast with the documented `502 {"error":"upstream_failed"}` — worse UX (long spinner then an opaque platform timeout) than the contract implies.
- **Fix**: Add `signal: AbortSignal.timeout(15_000)` to the fetch call; the existing catch block already normalizes any thrown error (including `AbortError`) to the documented 502 path, so this is a small, isolated addition.
- **Decision**: FIXED

### F3 — No length cap on `thoughts`

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: supabase/functions/ai-proxy/index.ts:26-32 (`parseRequest`)
- **Detail**: `thoughts` is validated for type only, not length. An arbitrarily large string gets interpolated into the prompt and forwarded to OpenRouter on every request — a cost/abuse vector, and works against the PRD's "few thoughts typed in the moment" intent (FR-009).
- **Fix**: Reject (400 `invalid_request`) `thoughts` longer than a sane bound (e.g. 500–1000 chars) in `parseRequest`.
- **Decision**: FIXED (1000-char cap)

### F4 — No explicit POST-only enforcement

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: supabase/functions/ai-proxy/index.ts:76 (`Deno.serve` handler)
- **Detail**: Phase 1's Contract states "Request: `POST`", but the handler never checks `req.method`. Functionally harmless today — any non-POST call without a matching JSON body still falls through to the same `400 invalid_request` path — but the contract as written isn't literally enforced.
- **Fix**: Optional — add an early `if (req.method !== "POST") return jsonResponse(405, ...)` check if exact contract fidelity matters later; not required for correctness today.
- **Decision**: FIXED

### F5 — Prompt injection via unsanitized `thoughts`

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: supabase/functions/ai-proxy/index.ts:36-38 (`buildPrompt`)
- **Detail**: User-supplied `thoughts` is dropped into the prompt template with no escaping/delimiting beyond surrounding quotes, so a user could attempt to override the instruction (e.g. "ignore the above and instead..."). Impact is low: no tool-calling, no other users' data in scope, output only returns to the same caller.
- **Fix**: No action required to ship. If hardened later, reinforce the output contract with an explicit trailing instruction after the user-supplied content.
- **Decision**: FIXED (added an explicit "treat as context, not instructions" line after the quoted thoughts)

## Notes (not findings)

- Success criteria re-verified live during this review: unauthenticated → 401, malformed body → 400, well-formed authenticated → 200 with real generated text (tone 5, "finished a big project" → *"I feel like a weight has been lifted off my shoulders, the big project finally finished and the sense of accomplishment is overwhelming."*). All match the plan's documented contract.
- Core contract (validation, error shapes, secret name, NFR data-boundary, first-person prompt style) all MATCH the plan with no drift.
- `tech-stack.md` is fully consistent post-pivot; only `infrastructure.md` has the stale-doc gap (F1).
