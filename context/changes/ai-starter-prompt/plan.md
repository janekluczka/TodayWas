# AI Starter Prompt Implementation Plan

## Overview

Implement S-07: a "help me start" entry point on the Add Journal Entry screen that lets a
signed-in user pick a tone (5-point scale), optionally add a few thoughts, and get an
AI-generated, editable starting prompt inserted into the entry — regenerable up to 3 times per
entry — by calling the already-deployed `ai-proxy` Supabase Edge Function (F-02,
`context/archive/2026-08-11-ai-assist-proxy-foundation/`). This is the first Android app code for
AI-assist; F-02 shipped only the server-side proxy.

## Current State Analysis

- `ai-proxy` is live (`context/archive/2026-08-11-ai-assist-proxy-foundation/plan.md`): `POST`
  `{"tone": 1-5, "thoughts"?: string}` → `200 {"text": string}` / `400
  {"error": "invalid_request"}` / `401` (platform-level, before the handler runs) / `502
  {"error": "upstream_failed"}`. Gated by `verify_jwt = true` — no app-side auth header logic
  needed beyond having a live Supabase session.
- `AddJournalEntryScreen.kt`/`AddJournalEntryViewModel.kt`
  (`app/src/main/java/pl/luczka/todaywas/ui/journal/`) is a plain `@HiltViewModel` with
  `AddJournalEntryUiState(availableSlots, selectedSlot, text, isSaving, saveError)`, a
  `DsTextField` bound to `text` via `TextChanged`, and `onIntent`/`events` MVI wiring. This is the
  sole integration point (per the Scope decision below).
- `SupabaseModule.kt` (`app/src/main/java/pl/luczka/todaywas/di/`) installs only `Auth` and
  `Postgrest` — no `Functions` plugin, no generic HTTP client anywhere in the app.
- `AuthRepository`/`AuthRepositoryImpl` expose `observeAuthState(): Flow<AuthState>` and
  `currentUserId(): String?` but no access-token getter — not needed here since the chosen network
  approach (see Key Discoveries) never touches the raw JWT from app code.
- No `@Serializable` DTO exists in the app module yet — `kotlinx.serialization` is already applied
  (used transitively by supabase-kt) and is the established convention to extend.
- `data/repository/RemoteCall.kt`'s `remoteCall {}` helper and `AuthErrorMapper.kt`'s
  `Throwable.toAuthError()` are the established "wrap a Supabase SDK call, map its exception to a
  typed domain error" pattern (`AuthRepositoryImpl.kt:52-59`) — this plan's `AiAssistRepositoryImpl`
  follows the same shape.
- `ui/onboarding/` (`OnboardingUiState.kt`, `AccountSubStep.kt`) is the precedent for a multi-step
  flow living inside its host screen's single ViewModel/UiState/Intent files, per
  `context/foundation/lessons.md`'s "a flow gets its own ViewModel once it's a screen" rule — this
  dialog has no Nav3 entry, so it stays embedded per that same rule.
- `PreferencesScreen.kt` shows sign-in state gating a section via `AuthStateUi` — the pattern this
  plan reuses (hide, don't redirect, per the Sign-in UX decision below).
- `core/designsystem/.../segmentedbuttons/DsSegmentedRow.kt` already renders a 1-5 scale picker
  (used today for scale-habit check-ins) — direct fit for the 5-point tone scale.
  `core/designsystem/.../dialogs/DsAlertDialog.kt` wraps M3 `AlertDialog`'s
  `title`/`text`/`confirmButton`/`dismissButton` slots, per `lessons.md`'s custom-dialog rule.

### Key Discoveries:

- supabase-kt's `Functions` plugin (`io.github.jan-tennert.supabase:functions-kt`, version-managed
  by the existing `supabaseBom = "3.7.0"`) auto-attaches the current session's Bearer token to
  `supabase.functions.invoke(...)` calls and derives the function URL from the already-configured
  `SupabaseClient` — no new `local.properties`/`BuildConfig` entry, no `AuthRepository` token
  getter needed (per the Network decision below).
- No existing repository/data-source in this app is unit-tested at the "directly touches
  `SupabaseClient`" layer (`RemoteJournalDataSourceImpl` has no test file) — `AiAssistRepositoryImpl`
  follows that same untested-at-that-layer convention; correctness there is verified manually in
  Phase 2 against the live function instead.
- Every domain `usecase/` file gets its own test file (confirmed via
  `app/src/test/java/pl/luczka/todaywas/domain/usecase/`) — `RequestJournalStarterPromptUseCase`
  gets `RequestJournalStarterPromptUseCaseTest.kt`.

## Desired End State

A signed-in user on the Add Journal Entry screen sees a "help me start" entry point. Tapping it
opens a dialog with a 5-point tone picker and an optional "thoughts" field. Generating calls the
live `ai-proxy` function and shows the result with **Use this** / **Regenerate** / **Cancel**
actions. Accepting replaces the entry's text field content; regenerating is capped at 3 successful
generations per screen visit (failed attempts don't count); the entry point is not shown to
signed-out users. Verified by running the app against the live Supabase project.

## What We're NOT Doing

- No "help me refine" (FR-010/S-08) — separate future change.
- No AI-assist entry point on `JournalEntryDetailScreen` (edit-within-24h) — Add-entry screen only,
  per the Scope decision.
- No server-side enforcement of the 3-regeneration cap — already an accepted MVP risk from F-02's
  plan; enforced client-side only.
- No new `local.properties`/`BuildConfig` entries — the `Functions` plugin derives the endpoint
  from the existing `SupabaseClient` config.
- No access-token getter added to `AuthRepository` — not needed by the chosen network approach.
- No redirect-to-sign-in flow for this feature — the entry point is simply not shown when signed
  out (Sign-in UX decision).
- No persistence of generated prompts or the regenerate counter beyond the ViewModel's in-memory
  `StateFlow` — both reset when `AddJournalEntryViewModel` is recreated (i.e., leaving the screen).
- No offline queueing or auto-retry of failed generations — the user manually re-taps
  Generate/Regenerate.
- No analytics/telemetry on tone picks or AI usage.

## Implementation Approach

Build the domain/data layer first (tone and error models, `AiAssistRepository` calling `ai-proxy`
via the `Functions` plugin, a use case) with unit tests that don't require a device — then wire the
UI: a `HelpMeStart` sub-state embedded in `AddJournalEntryUiState`/`ViewModel`/`Intent`, a dialog
built on `DsAlertDialog` + `DsSegmentedRow` + `DsTextField`, sign-in gating, and the regenerate cap.
Phase 2 ends with manual verification against the live, already-deployed function — no new backend
work.

## Critical Implementation Details

### Confirm the `Functions` plugin's exact API and failure contract before coding Phase 1

`supabase-kt` 3.7.0's `Functions` plugin `invoke()` signature and its behavior on a non-2xx response
(does it throw, what exception type, how to read the status code and the `{"error": "..."}` JSON
body from it) must be confirmed against current docs/source at implementation time, not assumed —
this determines how `AiAssistRepositoryImpl`/`AiAssistErrorMapper` (data-layer) distinguish `400
invalid_request` from `502 upstream_failed` from a plain network failure. This mirrors how the F-02
plan confirmed Gemini's/OpenRouter's REST contract via a live check rather than training-data
assumption, and getting it wrong here silently collapses all three failure modes into "Unknown."

### The regenerate counter survives dialog close/reopen, but nothing else does

Dismissing the dialog (Cancel) or accepting a result (Use this) must reset
`HelpMeStartUiState`'s `step`/`selectedTone`/`thoughts`/`generatedText`/`error` back to their
initial values but leave `regenerationsUsed` untouched — that field only resets when
`AddJournalEntryViewModel` itself is recreated (leaving and re-entering the Add-entry screen), per
the Regen-cap decision. A "reset the dialog" handler that also zeroes this field silently defeats
the cap the moment a user closes and reopens the dialog.

### A failed generation must not advance `step` or touch the counter

Only a *successful* generate/regenerate sets `step = PREVIEW`, stores `generatedText`, and (for
regenerate specifically) increments `regenerationsUsed`. On failure, leave `step` and
`generatedText` exactly as they were — for a first `Generate` failure that means staying on
`INPUT` with an inline error; for a `Regenerate` failure it means staying on `PREVIEW` with the
*previous* successful `generatedText` still visible alongside the error, not discarded.

---

## Phase 1: Domain & data layer

### Overview

Add the tone/error domain models, the `AiAssistRepository` that calls `ai-proxy`, its DTOs, the
`Functions` plugin wiring, and the use case — no UI changes in this phase.

### Changes Required:

#### 1. Tone and error domain models

**Files**: `app/src/main/java/pl/luczka/todaywas/domain/model/JournalPromptTone.kt`,
`AiAssistError.kt`, `AiAssistException.kt`

**Intent**: A 5-value tone enum matching the proxy's `1-5` contract, a sealed error type
distinguishing the proxy's failure modes, and an exception carrying that error type — mirroring
`AuthState`/`AuthError`/`AuthException`.

**Contract**: `JournalPromptTone` has 5 entries (`VERY_BAD`..`VERY_GOOD`) each carrying its `1-5`
integer level. `AiAssistError` is a sealed interface with `InvalidRequest`, `UpstreamFailed`,
`NotSignedIn`, `NetworkUnavailable`, `Unknown` cases. `AiAssistException(val error: AiAssistError)`
extends `Exception`.

#### 2. `AiAssistRepository` + DTOs + error mapper

**Files**: `app/src/main/java/pl/luczka/todaywas/data/repository/AiAssistRepository.kt`,
`AiAssistRepositoryImpl.kt`, `AiPromptRequestDto.kt`, `AiPromptResponseDto.kt`,
`AiPromptErrorDto.kt`, `AiAssistErrorMapper.kt`

**Intent**: `AiAssistRepositoryImpl` calls `ai-proxy` through the `Functions` plugin with only
`{tone, thoughts}` — never journal/habit data, per the NFR — wrapped in the existing `remoteCall {}`
helper (per `lessons.md`: don't re-wrap `remoteCall`'s `Result` in another `runCatching`), with a
`Throwable.toAiAssistError()` mapper analogous to `AuthErrorMapper.kt`'s `toAuthError()`.

**Contract**:
- `AiAssistRepository.generateJournalStarterPrompt(tone: JournalPromptTone, thoughts: String?):
  Result<String>`.
- Request DTO: `@Serializable data class AiPromptRequestDto(val tone: Int, val thoughts: String? =
  null)`. Response DTO: `@Serializable data class AiPromptResponseDto(val text: String)`. Error
  body DTO: `@Serializable data class AiPromptErrorDto(val error: String)`.
- `toAiAssistError()` maps: a 400 response body decoding to `AiPromptErrorDto(error =
  "invalid_request")` → `AiAssistError.InvalidRequest`; a 502 body with `"upstream_failed"` →
  `AiAssistError.UpstreamFailed`; a 401 → `AiAssistError.NotSignedIn`; an `IOException` → 
  `AiAssistError.NetworkUnavailable` (same reasoning as `AuthErrorMapper.kt`'s
  `HttpRequestException`-is-an-`IOException` comment); anything else → `AiAssistError.Unknown`. The
  exact exception type/shape thrown by the `Functions` plugin must be confirmed per the Critical
  Implementation Details note above before finalizing this mapping.

#### 3. `Functions` plugin wiring

**Files**: `gradle/libs.versions.toml`, `app/build.gradle.kts`,
`app/src/main/java/pl/luczka/todaywas/di/SupabaseModule.kt`,
`app/src/main/java/pl/luczka/todaywas/data/repository/RepositoryModule.kt`

**Intent**: Add the `functions-kt` artifact (version-managed by the existing `supabaseBom`),
install the `Functions` plugin alongside `Auth`/`Postgrest`, and bind the new repository.

**Contract**: `libs.versions.toml` gets a `supabase-functions-kt` alias under the existing
`io.github.jan-tennert.supabase` group (no new version key — BOM-managed like `supabase-auth-kt`).
`SupabaseModule.provideSupabaseClient` adds `install(Functions)`. `RepositoryModule` adds `@Binds
abstract fun bindAiAssistRepository(impl: AiAssistRepositoryImpl): AiAssistRepository`.

#### 4. Use case

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/RequestJournalStarterPromptUseCase.kt`

**Intent**: One use case wrapping the repository call, per the "use case depends on repository
only" convention.

**Contract**: `suspend operator fun invoke(tone: JournalPromptTone, thoughts: String?): Result<String>`
— direct passthrough to `AiAssistRepository.generateJournalStarterPrompt`.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat testDebugUnitTest` passes, including new `AiAssistErrorMapperTest.kt` (each
  mapped exception/status → the expected `AiAssistError`) and
  `RequestJournalStarterPromptUseCaseTest.kt`.
- `./gradlew.bat ktlintCheck` passes.
- `./gradlew.bat assembleDebug` succeeds — confirms the Hilt graph resolves with the new `Functions`
  plugin install and repository binding.

---

## Phase 2: UI — dialog, sign-in gating, regenerate cap

### Overview

Wire the "help me start" entry point and its dialog into `AddJournalEntryScreen`/`ViewModel`,
gate it on sign-in state, enforce the client-side regenerate cap, and verify end-to-end against the
live function.

### Changes Required:

#### 1. UI-side tone/error models and mappers

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/model/JournalPromptToneUiState.kt`,
`JournalPromptToneMapper.kt`, `AiAssistErrorUiState.kt`, `AiAssistErrorMapper.kt` (ui.model
package — distinct from the Phase 1 data.repository mapper of the same filename)

**Intent**: UI-facing mirrors of the Phase 1 domain types, per the "never expose a domain.model
type through UiState" rule, each with their own `toDomain()`/`toUiState()` mapper file.

**Contract**: `JournalPromptToneUiState` enum mirrors `JournalPromptTone`'s 5 values.
`AiAssistErrorUiState` mirrors `AiAssistError`'s cases, each mapping to a `strings.xml` message in
the screen (e.g. "Couldn't generate a prompt — check your connection" for
`NetworkUnavailable`/`UpstreamFailed`).

#### 2. `HelpMeStart` sub-state

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/journal/HelpMeStartStep.kt`,
`HelpMeStartUiState.kt`

**Intent**: The dialog's own state, embedded (not a separate ViewModel/nav entry) per the Wizard
decision and `lessons.md`'s flow-ownership rule.

**Contract**: `HelpMeStartStep` is `{ INPUT, PREVIEW }`. `HelpMeStartUiState` holds `isVisible:
Boolean`, `step: HelpMeStartStep`, `selectedTone: JournalPromptToneUiState?`, `thoughts: String`,
`generatedText: String?`, `isGenerating: Boolean`, `error: AiAssistErrorUiState?`,
`regenerationsUsed: Int` — all defaulted so `AddJournalEntryUiState` can add `helpMeStart:
HelpMeStartUiState = HelpMeStartUiState()` and `authState: AuthStateUi = AuthStateUi.Loading` as
trailing defaulted fields (mirroring `OnboardingUiState`'s trailing-defaults style).

#### 3. Intents and ViewModel logic

**Files**: `app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryIntent.kt`,
`AddJournalEntryViewModel.kt`

**Intent**: Add the dialog's intents to the existing sealed `AddJournalEntryIntent` (single file
for the whole screen+dialog flow, matching `OnboardingIntent`'s precedent) and their handlers to
`AddJournalEntryViewModel`, plus observing auth state to drive `authState` and gate the entry
point.

**Contract**:
- New intents: `HelpMeStartClicked`, `HelpMeStartDismissed`, `ToneSelected(tone)`,
  `ThoughtsChanged(thoughts)`, `GenerateClicked`, `RegenerateClicked`, `UseGeneratedTextClicked`.
- Constructor gains `observeAuthState: ObserveAuthStateUseCase` and `private val
  requestJournalStarterPrompt: RequestJournalStarterPromptUseCase`; `init` collects
  `observeAuthState().map { it.toUiState() }` into `authState`, same pattern as the existing
  `availableSlots` collection.
- `GenerateClicked`/`RegenerateClicked` share a private suspend handler parameterized by
  "is this a regenerate", implementing the two Critical Implementation Details above: only a
  successful call sets `step = PREVIEW`/`generatedText`/(if regenerate)
  increments `regenerationsUsed`; `RegenerateClicked` is a no-op once `regenerationsUsed >= 3` or
  while `isGenerating`.
- `HelpMeStartClicked`/`HelpMeStartDismissed`/`UseGeneratedTextClicked` reset `helpMeStart` to a
  fresh `HelpMeStartUiState(regenerationsUsed = current.helpMeStart.regenerationsUsed)` —
  preserving only the counter. `UseGeneratedTextClicked` additionally copies
  `helpMeStart.generatedText` into `text` (reusing the same field `TextChanged` already writes to).

#### 4. Dialog UI and entry point

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt`

**Intent**: A "help me start" affordance shown only when `uiState.authState is
AuthStateUi.SignedIn` (per the Sign-in UX decision — no redirect, just hidden otherwise), and a
`HelpMeStartDialog` composable built on `DsAlertDialog`.

**Contract**: `DsAlertDialog`'s `text` slot holds a `Column` with `DsSegmentedRow` (5 tones,
`allowDeselect = false`) + `DsTextField` (optional thoughts) at `INPUT` step, or the generated text
+ a "Regenerate" `DsTextButton` (disabled at `regenerationsUsed >= 3` or while `isGenerating`) at
`PREVIEW` step, plus an error message row when `helpMeStart.error != null`. `confirmButton` is
"Generate" (`INPUT`, disabled until a tone is picked) or "Use this" (`PREVIEW`); `dismissButton` is
always "Cancel" → `HelpMeStartDismissed`. New `strings.xml` entries for all new user-facing text
(cta, dialog title, 5 tone labels, thoughts label, generate/regenerate/use-this/cancel, one message
per `AiAssistErrorUiState` case).

#### 5. Test doubles and tests

**Files**: `app/src/test/java/pl/luczka/todaywas/data/repository/FakeAiAssistRepository.kt`,
`app/src/test/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModelTest.kt` (extended)

**Intent**: A `FakeAiAssistRepository` mirroring `FakeAuthRepository`'s shape (configurable
result/error, call count), and new `AddJournalEntryViewModelTest` cases covering the full dialog
flow using it plus `FakeAuthRepository`.

**Contract**: New test cases per the `` `should [outcome] when [scenario]` `` convention: tone
selection updates state; thoughts input updates state; a successful `GenerateClicked` sets `step =
PREVIEW` and `generatedText`; a failed `GenerateClicked` keeps `step = INPUT` and sets `error`
without touching `regenerationsUsed`; a successful `RegenerateClicked` increments
`regenerationsUsed` and updates `generatedText`; a failed `RegenerateClicked` sets `error` without
incrementing `regenerationsUsed` or losing the prior `generatedText`; `RegenerateClicked` is a
no-op once `regenerationsUsed == 3`; `UseGeneratedTextClicked` copies `generatedText` into `text`
and resets `helpMeStart` except `regenerationsUsed`; `helpMeStart.isVisible` cannot become `true`
via `HelpMeStartClicked` while `authState` is `SignedOut`.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat testDebugUnitTest` passes, including all new/extended `AddJournalEntryViewModelTest`
  cases above.
- `./gradlew.bat ktlintCheck` passes.
- New `@PreviewLightDark`/`@PreviewParameter` previews for the dialog's `INPUT`/`PREVIEW`/error/
  loading states render (verified by Compose preview compilation, part of `assembleDebug`).

#### Manual Verification:

- Signed in, on the Add Journal Entry screen: tap "help me start", pick each of the 5 tones (with
  and without optional thoughts), confirm the generated text reads as a plausible first-person
  starter (per F-02's already-verified prompt voice) and "Use this" replaces the entry's text field
  content.
- Confirm "Regenerate" is disabled after 3 successful regenerations for that screen visit, and only
  re-enabled after leaving and re-entering the Add-entry screen.
- Force a failure (e.g. airplane mode) mid-dialog: confirm an inline error appears and the
  regenerate count is unchanged.
- Confirm the "help me start" entry point does not render when signed out, and appears once signed
  in.
- Confirm the existing slot-pick/save/cancel flow on the same screen is unaffected.

---

## Testing Strategy

### Unit Tests:

- `AiAssistErrorMapperTest.kt`: each simulated failure (400/401/502/`IOException`/other) maps to
  its expected `AiAssistError`.
- `RequestJournalStarterPromptUseCaseTest.kt`: passthrough to a fake repository, success and
  failure.
- `AddJournalEntryViewModelTest.kt`: full dialog flow (tone/thoughts input, generate/regenerate
  success/failure, cap enforcement, accept, sign-out gating) per Phase 2's Contract above.

### Integration Tests:

- None planned — consistent with this repo's existing convention of no instrumented tests for
  Supabase-adjacent work; Phase 2's manual verification against the live function is the
  closest equivalent.

### Manual Testing Steps:

1. Sign in, open Add Journal Entry, tap "help me start", generate with tone = very good + no
   thoughts, confirm plausible output.
2. Regenerate twice more (3 total generations including the first), confirm "Regenerate" then
   disables.
3. Tap "Use this", confirm the entry's text field now holds the generated text.
4. Reopen "help me start" on the same screen visit, confirm "Regenerate" is still disabled (cap
   persisted), then leave and re-enter the Add-entry screen and confirn the cap is reset.
5. Force a network failure mid-generation, confirm an inline error and an unchanged regenerate
   count.
6. Sign out, confirm the entry point disappears; sign back in, confirm it reappears.

## Performance Considerations

Cold-start latency on the Edge Function's Deno runtime is a known, accepted risk from F-02 — the
dialog's `isGenerating` loading state is the only mitigation planned (no caching, no request
timeout tuning beyond the `Functions` plugin's default).

## Migration Notes

None — no data model or persisted-schema changes; all new state is in-memory ViewModel state.

## References

- F-02 plan (proxy contract, prompt-voice constraints): 
  `context/archive/2026-08-11-ai-assist-proxy-foundation/plan.md`
- PRD: `context/foundation/prd.md` US-05, FR-009, NFR (journal content never reaches AI-assisted
  features)
- Roadmap: `context/foundation/roadmap.md` S-07
- Onboarding multi-step precedent: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/`
- Sign-in gating precedent: `app/src/main/java/pl/luczka/todaywas/ui/preferences/PreferencesScreen.kt`
- Repository/error-mapper precedent:
  `app/src/main/java/pl/luczka/todaywas/data/repository/AuthRepositoryImpl.kt`,
  `AuthErrorMapper.kt`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Domain & data layer

#### Automated

- [x] 1.1 Unit tests pass (`AiAssistErrorMapperTest`, `RequestJournalStarterPromptUseCaseTest`) — 5f1924a
- [x] 1.2 `ktlintCheck` passes — 5f1924a
- [x] 1.3 `assembleDebug` succeeds (Hilt graph resolves) — 5f1924a

### Phase 2: UI — dialog, sign-in gating, regenerate cap

#### Automated

- [x] 2.1 `AddJournalEntryViewModelTest` passes (new dialog-flow cases) — 992ec02
- [x] 2.2 `ktlintCheck` passes — 992ec02
- [x] 2.3 New dialog previews render — 992ec02

#### Manual

- [x] 2.4 5-tone generation spot-check, output reads as plausible first-person starter — 992ec02
- [x] 2.5 "Use this" replaces entry text field content — 992ec02
- [x] 2.6 Regenerate cap disables at 3, resets only on leaving/re-entering the screen (cap-at-3 disabling verified via unit test, not live — live retries were blocked by real OpenRouter free-tier flakiness during manual testing; failures-don't-consume-an-attempt was verified live repeatedly) — 992ec02
- [x] 2.7 Forced failure shows inline error, doesn't consume a regenerate attempt — 992ec02
- [x] 2.8 Entry point hidden signed-out, shown signed-in — 992ec02
- [x] 2.9 Existing save/cancel flow unaffected — 992ec02
