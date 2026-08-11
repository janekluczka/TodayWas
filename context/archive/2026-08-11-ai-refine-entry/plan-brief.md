# AI Refine Entry — Plan Brief

> Full plan: `context/changes/ai-refine-entry/plan.md`

## What & Why

Implement FR-010: a "help me refine" entry point on the edit-within-24h journal entry screen. A
signed-in user editing an existing entry picks a tone, and the AI rewrites their current draft text
toward that tone — previewed, then optionally accepted into the local edit buffer (still requiring
an explicit Save, unchanged from today). Before any code, this required resolving a direct conflict
between FR-010's own wording ("refine a journal entry they've written") and the existing hard
rule/NFR stating AI-assist never reads journal content — resolved as a narrow, explicit carve-out
for the single entry actively being refined, and nothing else.

## Starting Point

`ai-proxy` (Supabase Edge Function) currently only supports "help me start" — `POST {tone,
thoughts?}` → a fresh opening line, no text-in/text-out capability. `JournalEntryDetailScreen`'s
edit-within-24h flow (a `DsBottomSheet` with a `DsTextField` bound to `editedText`, persisted only
on explicit Save via `UpdateJournalEntryUseCase`) has no AI-assist entry point and doesn't currently
observe sign-in state. "Help me start" on `AddJournalEntryScreen` is fully implemented and is this
plan's direct template: `AiAssistRepository`, `JournalPromptTone`, a `HelpMeStartUiState`/`Step`
embedded sub-state, and a `DsAlertDialog`-based dialog with a regenerate cap.

## Desired End State

A signed-in user editing a recent entry sees "help me refine" once they've typed something. Tapping
it opens a tone-picker dialog; refining sends the current draft to the extended `ai-proxy` function
and previews the rewrite with Use this / Regenerate / Cancel. Accepting updates the local draft
buffer only — Save is still a separate, explicit step, and the existing 24h-window enforcement is
unchanged.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Refine semantics | True text refinement — draft is sent to AI | User chose this over a no-text-sent "tone regen" alternative, despite the doc conflict it required resolving. | Plan |
| Privacy carve-out scope | Narrow — only the entry actively being refined | Preserves the privacy commitment everywhere else; easiest to defend/audit. | Plan |
| Refine input shape | Tone scale only, no free-form instructions field | Matches the PRD's Business Logic wording (tone scale applies to both start and refine); reuses existing UI with zero new concepts. | Plan |
| Refine UX | Same dialog pattern as "help me start" (generate → preview → accept) | Reuses `HelpMeStartDialog`'s structure and the `generateJob`-cancellation pattern almost verbatim. | Plan |
| Entry point placement | Inside the edit bottom sheet, next to the text field | Co-located with the text it acts on, same as "help me start." | Plan |
| Regenerate cap | Per screen-visit (resets on re-entry), same mechanism as "help me start" | Zero new persistence; same already-accepted client-side-only cap risk. | Plan |
| Edit-window race | Reuse Save's existing expiry handling | Reuses `onSaveClicked`'s already-built expired-window branch — no new UI state needed. | Plan |
| Empty-draft guard | Entry point disabled while draft is blank | "Refine" implies something to refine; avoids a meaningless AI call. | Plan |
| Backend shape | Extend `ai-proxy` with an optional `text` field (one function, two modes) | One deployed function stays the source of truth; no new auth/CORS/deploy wiring. | Plan |

## Scope

**In scope:** CLAUDE.md/PRD wording carve-out, `ai-proxy` refine-mode extension + redeploy +
verification, `AiAssistRepository.refineJournalEntry` + DTO field + use case, `HelpMeRefine`
sub-state wired into `JournalEntryDetailScreen`/`ViewModel`, sign-in gating, regenerate cap, the
edit-window-expiry race, unit tests, manual E2E verification.

**Out of scope:** free-form refine instructions field, server-side cap enforcement, persisted
regenerate counter, auto-save of a refined result, a new `EditWindow.isEditable` use case
abstraction, broadening the privacy carve-out beyond the single actively-refined entry.

## Architecture / Approach

Phase 1 clears the policy and backend blockers (doc wording, extended `ai-proxy`). Phase 2 mirrors
"help me start"'s data-layer phase almost exactly — one shared HTTP call path, a new use case. Phase
3 mirrors its UI phase, embedding a `HelpMeRefine` sub-state in `JournalEntryDetailViewModel` the
same way `HelpMeStart` lives in `AddJournalEntryViewModel`, with one genuinely new piece: re-checking
the 24h window when a refine completes, not just when Save is pressed, folding an expiry discovered
there into the same expired-window UI path Save already has.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Docs + backend proxy | Narrowed doc wording; redeployed `ai-proxy` with a refine mode | Prompt-injection framing for the new `text` field must be as careful as the existing `thoughts` framing |
| 2. Domain & data layer | `AiAssistRepository.refineJournalEntry`, use case, unit tests | Low — near-identical to the already-shipped "help me start" data layer |
| 3. UI | Refine dialog on the edit screen, regenerate cap, edit-window race handling | The edit-window race (window closes mid-refine) is genuinely new logic, not a copy-paste |

**Prerequisites:** `ai-starter-prompt` (done) — the `AiAssistRepository`/dialog pattern this plan
reuses; `ai-assist-proxy-foundation` (done) — the live `ai-proxy` function this plan extends.
**Estimated effort:** ~2 sessions across 3 phases.

## Open Risks & Assumptions

- The `ai-proxy` free-tier OpenRouter model's documented flakiness/rotation risk (`tech-stack.md`)
  applies equally to refine-mode calls.
- Editing `CLAUDE.md` and `prd.md`'s privacy wording is a real policy change, not a pure
  implementation detail — Phase 1 treats it as a first-class deliverable with its own manual
  verification (the doc text itself), not a side effect of the code change.

## Success Criteria (Summary)

- A signed-in user editing a recent entry can refine, regenerate (up to 3 times), and accept a
  rewrite into the local draft buffer, then Save it normally.
- The entry point is hidden signed-out, before editing, or while the draft is blank.
- A refine that completes after the 24h window has closed is discarded and shows the same
  expired-window message Save already shows.
