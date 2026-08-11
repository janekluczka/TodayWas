<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: AI Starter Prompt Implementation Plan

- **Plan**: context/changes/ai-starter-prompt/plan.md
- **Scope**: Phase 1 of 2, Phase 2 of 2 (full plan)
- **Date**: 2026-08-11
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Stale in-flight generate result can clobber a fresh dialog session

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModel.kt:145-157, 133-135
- **Detail**: `onGenerate` launches `viewModelScope.launch { ... }` without retaining the `Job`. If the user dismisses the dialog or dismisses-and-reopens it while a request is still in flight, `onHelpMeStartDismissed`/`onHelpMeStartClicked` reset `helpMeStart` via `resetForNewSession`, but nothing cancels the still-running coroutine. When that stale call eventually resolves, its `applyResult` still fires and overwrites the *new* session's state — wrong `generatedText`, an unexpected jump to `HelpMeStartStep.PREVIEW`, and (if the stale call was a regenerate) a `regenerationsUsed` increment the new session never earned.
- **Fix A ⭐ Recommended**: Track the coroutine's `Job` in a `private var generateJob: Job?` and cancel it inside `resetForNewSession` (called from dismiss/reopen/use-this).
  - Strength: Actually stops the stale coroutine from running further; the standard Kotlin pattern for "cancel previous work on new user action."
  - Tradeoff: Touches `resetForNewSession`'s call sites (3 handlers) and requires storing mutable ViewModel state outside `_uiState`.
  - Confidence: HIGH — this is the idiomatic fix for exactly this race in a ViewModel.
  - Blind spot: None significant.
- **Fix B**: Tag each request with a monotonically increasing session/generation counter; before calling `applyResult`, compare the result's tag against the current counter and drop it if stale.
  - Strength: No `Job` bookkeeping needed; fits the existing state-only architecture without a new mutable field.
  - Tradeoff: The stale network call still runs to completion (wastes a real API call against the AI proxy) — it's just ignored client-side, not actually cancelled.
  - Confidence: MEDIUM — correct, but a slightly less standard pattern than cancellation for this codebase.
  - Blind spot: Haven't checked whether other ViewModels in this repo already use a similar generation-token pattern to follow as precedent.
- **Decision**: FIXED via Fix A — added `private var generateJob: Job?`, cancelled at the top of `onHelpMeStartClicked`/`onHelpMeStartDismissed`/`onUseGeneratedTextClicked`, captured from `onGenerate`'s `viewModelScope.launch`.

### F2 — `isGenerating` guard is set asynchronously, inside the coroutine it's meant to gate

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModel.kt:145-157
- **Detail**: `onGenerate` checks `helpMeStart.isGenerating` synchronously but only flips it to `true` *inside* `viewModelScope.launch { ... }`, after the coroutine is scheduled rather than before. Two `GenerateClicked`/`RegenerateClicked` intents dispatched back-to-back (a double-tap) can both pass the guard before either coroutine's first line runs, firing two concurrent network calls — and if both are regenerates, `regenerationsUsed` could increment twice for one user action, softly bypassing the 3-regeneration cap. Note: the pre-existing `onSaveClicked` (line 103-126) has the same shape, so this isn't a pattern invented by this change — but it's still a real gap in the new code.
- **Fix**: Move the `_uiState.update { it.copy(helpMeStart = it.helpMeStart.copy(isGenerating = true, error = null)) }` line to before `viewModelScope.launch`, so the guard is set synchronously on the calling thread before any coroutine dispatch — closing the double-tap window entirely.
- **Decision**: FIXED — moved the `isGenerating = true` update to before `viewModelScope.launch`.

### F3 — `MAX_REGENERATIONS = 3` duplicated in two files

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModel.kt:27 and app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt:236
- **Detail**: The same magic number is hardcoded independently in the ViewModel (actual enforcement in `onGenerate`) and the Screen (button-enablement check `uiState.regenerationsUsed < MAX_REGENERATIONS`). If one is changed without the other, the UI's enabled/disabled state and the actual cap enforcement can disagree.
- **Fix**: Define the constant once (e.g. on `HelpMeStartUiState.kt`'s companion, or export it from the ViewModel file) and reference it from both files instead of two private `const val` declarations.
- **Decision**: FIXED — moved `MAX_REGENERATIONS` to a top-level `const val` in `HelpMeStartUiState.kt` (shared package), removed the duplicate `private const val` from both `AddJournalEntryViewModel.kt` and `AddJournalEntryScreen.kt`.

### F4 — `AiAssistErrorMapperTest` only covers 2 of 5 mapping branches

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: app/src/test/java/pl/luczka/todaywas/data/repository/AiAssistErrorMapperTest.kt
- **Detail**: Only `IOException → NetworkUnavailable` and the catch-all `→ Unknown` are tested. `UnauthorizedRestException → NotSignedIn`, `BadRequestRestException → InvalidRequest`, and `RestException → UpstreamFailed` have no test coverage. **This was a deliberate decision during implementation, not an oversight**: those `RestException` subclasses require a real `HttpResponse` in their constructor, which needs a `ktor-client-mock` dependency not present in this project. The existing `AuthErrorMapperTest` precedent has the exact same gap for the same reason (it never constructs a real `AuthRestException` instance either) — this implementation matched that established scope rather than introducing a new test dependency for one test file.
- **Fix**: No action needed unless the project later adds `ktor-client-mock` as a general testing dependency, at which point both `AuthErrorMapperTest` and this file could be extended together.
- **Decision**: FIXED (user chose to fix rather than skip) — added `ktor-client-mock` (same version as `ktorClientOkhttp`, testImplementation-only) and added tests for the 3 remaining branches using a `MockEngine`-backed `HttpResponse`. `AuthErrorMapperTest` was left as-is (out of scope for this finding).

### F5 — No test for concurrent/double-tap `GenerateClicked`

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: app/src/test/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModelTest.kt
- **Detail**: The test file uses `UnconfinedTestDispatcher`, which runs launched coroutines eagerly to their first suspension point — this would mask the F2 race even if a naive test were added. No test currently exercises "tap twice before the first response returns."
- **Fix**: Once F2 is fixed, add a `StandardTestDispatcher`-based test that dispatches `GenerateClicked` twice before advancing the dispatcher, and asserts the fake repository was only called once.
- **Decision**: FIXED — added `` `should call the repository only once when GenerateClicked is dispatched twice before the first call completes` `` using `StandardTestDispatcher(testScheduler)` + `advanceUntilIdle()`. Passes after the F2 fix.

### F6 — Regenerate action rendered as `DsAssistChip` instead of the plan's `DsTextButton`

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt:201
- **Detail**: The plan's Phase 2 contract named `DsTextButton` for the Regenerate action; the implementation uses `DsAssistChip` instead. This is arguably a better choice, not a worse one: `DsAssistChip`'s own `@PreviewLightDark` preview in the design system already uses `text = "Regenerate"` as its canonical example, suggesting this component was the design system's intended fit for exactly this action.
- **Fix**: No code change recommended — keep `DsAssistChip`. Worth a one-line note in the plan or a future design-system doc that this is the established "Regenerate"-style component, so it doesn't look like an unreviewed deviation next time.
- **Decision**: SKIPPED — kept `DsAssistChip` as-is.
