<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Journal Edit Screen & AI-Assist Unification Implementation Plan

- **Plan**: context/changes/journal-edit-screen-refine/plan.md
- **Scope**: Phase 5 of 5 (full plan)
- **Date**: 2026-09-13
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 4 observations
- **Triage**: F1 fixed, F2 fixed, F3 fixed, F4 skipped (pre-existing, out of scope)

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Two test names not renamed after intent rename

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence / Pattern Consistency
- **Location**: app/src/test/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryViewModelTest.kt:240,257
- **Detail**: Phase 5 renamed `AddJournalEntryIntent.ToneSelected`/`ThoughtsChanged` to `HelpMeStartToneSelected`/`HelpMeStartThoughtsChanged`. The two test bodies were correctly updated to dispatch the renamed intents, but their backtick-quoted names still read `` `should update selectedTone when ToneSelected is dispatched` `` and `` `should update thoughts when ThoughtsChanged is dispatched` `` — cosmetic only, no functional or coverage gap.
- **Fix**: Rename the two test methods to `` `should update selectedTone when HelpMeStartToneSelected is dispatched` `` and `` `should update thoughts when HelpMeStartThoughtsChanged is dispatched` ``.
- **Decision**: FIXED

### F2 — No client-side length cap mirroring the server's thoughts limit

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/edit/HelpMeRefineStep.kt:9 (MAX_REFINE_TEXT_LENGTH); supabase/functions/ai-proxy/index.ts:35 (MAX_THOUGHTS_LENGTH = 1000)
- **Detail**: The edge function caps `thoughts` at 1000 chars, the same way it caps `text` at 8000. The client mirrors the 8000-char text cap (`MAX_REFINE_TEXT_LENGTH`, used to disable the refine entry point) but has no equivalent client-side check for `thoughts` in either the start or refine flow. A user typing over 1000 chars into a thoughts field gets a generic "Something went wrong" instead of a clear local message.
- **Fix**: Add a client-side `MAX_THOUGHTS_LENGTH` constant mirroring the server's, and disable Generate/Refine (or show inline guidance) once thoughts exceed it — same pattern already used for `MAX_REFINE_TEXT_LENGTH`.
- **Decision**: FIXED — added `MAX_THOUGHTS_LENGTH = 1000` to `create/HelpMeStartUiState.kt`, cross-imported into `edit/`; both `HelpMeStartBottomSheet`'s Generate button and `HelpMeRefineBottomSheet`'s Refine button now also require `thoughts.length <= MAX_THOUGHTS_LENGTH`.

### F3 — help-me-start's onGenerate has no 24h-expiry re-check, unlike onRefine

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/edit/EditJournalEntryViewModel.kt (onGenerate vs. onRefine)
- **Detail**: `onRefine()` explicitly re-checks `isEditable(entry.createdAt)` after the network round-trip and discards the result if the 24h window closed mid-flight. `onGenerate()` (help-me-start, also reachable on the Edit screen once text is cleared) has no equivalent check. Not a data-safety bug — `SaveClicked` always re-validates via `UpdateJournalEntryUseCase` and correctly falls back to `Discarded` on `EditWindowExpiredException`, so nothing is silently lost — but it's an asymmetry between two structurally identical flows that could read as an oversight to a future maintainer.
- **Fix**: Either add the same `isEditable` re-check to `onGenerate` for consistency, or leave a one-line comment explaining why it's intentionally omitted (worst case is just a wasted round-trip, caught at Save).
- **Decision**: FIXED — `onGenerate` now re-checks `isEditable(entry.createdAt)` after the async call and emits `Discarded` on expiry, mirroring `onRefine` exactly. New regression test added: `` `should discard the result and emit Discarded when the edit window closes during a generate` ``.

### F4 — Pre-existing: env-var force-unwraps outside try/catch in ai-proxy

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: supabase/functions/ai-proxy/index.ts:140-142
- **Detail**: `Deno.env.get("SUPABASE_URL")!` / `Deno.env.get("SUPABASE_ANON_KEY")!` sit outside the try/catch wrapping `callOpenRouter`. If either secret were ever unset, this throws uncaught (generic Deno 500) instead of the function's own `jsonResponse(502, ...)` shape. This predates this change (from `ai-assist-proxy-foundation`) and isn't touched by the refine/thoughts work — flagged only for completeness, doesn't affect this plan's verdict.
- **Fix**: Wrap the `createClient` call (or the env-var reads) in the same try/catch as the OpenRouter call, if ever revisited.
- **Decision**: SKIPPED — pre-existing, out of this change's scope; left for a separate change.

## What checked out cleanly

- **Plan Adherence**: every file across all 5 phases matches its planned contract almost verbatim — exact field names, constructor parameter order, event semantics, and the subtle "expired mid-flow routes to Discarded, not read-only" behavior are all implemented exactly as specified.
- **Scope Discipline**: file-touch list across all commits matches the plan's file list exactly. No unplanned files, no habit-tracking changes, no instrumented tests added, `EditWindow`/`UpdateJournalEntryUseCase`/`DAILY_AI_ASSIST_LIMIT` untouched. The `BackHandler` addition (not itemized in the plan's file-by-file contract) is directly implied by the plan's own manual verification step 5.8 and the bug found during manual testing — not scope creep.
- **Safety & Quality**: prompt-injection mitigation (delimiting user text, "treat as content not instructions") applied consistently to both `thoughts` and `text` in both prompt builders. `generateJob`/`refineJob` cancellation handled correctly and covered by a dedicated `CancellationException`-propagation test. Save-then-discard logic has a synchronous `isSaving` guard (no double-submit) and correctly distinguishes `EditWindowExpiredException` (silent discard) from generic failures (visible error). No force-unwraps or hardcoded secrets in changed Kotlin files.
- **Architecture**: `edit/` and `create/` cross-import exactly per the plan's "one canonical owner per shared type" division — no stray direct dependencies. MVI conventions (sealed Intent, single `onIntent`, buffered-Channel UiEvent, mapper-based domain→UI conversion) followed identically to existing screens.
- **Success Criteria**: `ktlintCheck`, `testDebugUnitTest` (82 tests across the three journal ViewModel test classes), `compileDebugKotlin`, and `assembleDebug` all pass. Manual verification was performed live on an emulator for every phase (not rubber-stamped) — including a real bug found and fixed (system back gesture bypassing the discard dialog) and a genuine end-to-end AI-output check after an unrelated OpenRouter model deprecation was fixed along the way.
