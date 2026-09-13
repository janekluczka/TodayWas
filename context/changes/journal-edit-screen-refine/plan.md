# Journal Edit Screen & AI-Assist Unification Implementation Plan

## Overview

Replace journal editing's in-place bottom sheet (toggled by an `isEditing` flag inside
`JournalEntryDetailScreen`) with a dedicated Edit Journal Entry screen that mirrors Add Journal
Entry's layout — a fixed, non-interactive date display instead of the TODAY/YESTERDAY picker, and
the existing entry text pre-filled. Along the way, unify "help me start" and "help me refine" into
one consistent, contextual pattern used identically on both Add and Edit screens: show "help me
start" while the text field is empty, "help me refine" the moment it has content — including giving
refine the same server-enforced daily quota start already uses (dropping its separate, redundant
client-side `MAX_REGENERATIONS` cap) and adding an optional "thoughts about the refinement" input to
refine, matching start's optional thoughts field.

## Current State Analysis

- **Add Journal Entry** (`app/src/main/java/pl/luczka/todaywas/ui/journal/create/`) is already a
  full screen (`AddJournalEntryScreen.kt`), reached via `AddJournalEntryKey`. It shows a
  `DsDateStrip` (TODAY/YESTERDAY only), a full-bleed text field, and — only while the field is
  empty — a `StarterPromptList` overlay (three canned tone lines + a "Help me start" CTA chip). The
  "help me start" flow (`HelpMeStartUiState.kt`, `HelpMeStartStep.kt`) opens a `DsModalBottomSheet`
  with a tone picker, optional "thoughts" field, and generate/regenerate/use actions, gated by the
  server's real `remainingToday` daily quota (`AddJournalEntryViewModel.kt:182-202`).
- **Editing** happens entirely inside `JournalEntryDetailScreen.kt`
  (`app/src/main/java/pl/luczka/todaywas/ui/journal/detail/`) via a local `isEditing: Boolean` on
  `JournalEntryDetailUiState` that swaps in a `DsBottomSheet` (`JournalEntryDetailScreen.kt:131-164`).
  It never touches the nav back stack — there is no `EditJournalEntryKey` in `TodayWasKey.kt`.
- **"Help me refine"** already exists (FR-010 is implemented, not missing) inside that same edit
  bottom sheet, as a `DsAlertDialog` (`HelpMeRefineDialog`, `JournalEntryDetailScreen.kt:226-286`).
  It has a tone picker but no "thoughts" input, and caps regenerations with a client-side
  `regenerationsUsed < MAX_REGENERATIONS` (`JournalEntryDetailViewModel.kt:212`) — a constant
  imported from `create/HelpMeStartUiState.kt:10`, which itself no longer uses it (a comment there
  already flags it as dead for Add's own purposes).
- **The daily quota is server-side and shared**: `supabase/functions/ai-proxy/index.ts` enforces one
  `DAILY_AI_ASSIST_LIMIT` (10) counter per user per day across both start and refine calls
  (`increment_ai_assist_usage` Postgres RPC), returning `remaining` in every response. Refine's
  client-side `MAX_REGENERATIONS = 3` cap is therefore redundant with — and inconsistent with — the
  real limit the server already enforces and already returns.
- **The edge function currently rejects text+thoughts together**: `parseRequest` in
  `supabase/functions/ai-proxy/index.ts:48-50` explicitly treats "start" (`thoughts`) and "refine"
  (`text`) as mutually exclusive request shapes. `AiPromptRequestDto` (the Android→function payload)
  already has both fields as independent optionals, so no DTO shape change is needed — only the
  function's validation and prompt-building need to accept both together, and
  `AiAssistRepository.refineJournalEntry` / `RequestJournalRefinementPromptUseCase` need a `thoughts`
  parameter threaded through.
- **`DsDateStrip`** (`core/designsystem/.../components/pickers/DsDateStrip.kt`) is an infinite
  `HorizontalPager` with no display-only mode — every rendered card's tappability is driven by
  `isSelectable(date)`. Passing `isSelectable = { it == fixedDate }` would already pin the visual
  selection, but the fixed date's own card would still render as `enabled = true` (tappable, though
  a no-op) — a real (if small) added `interactive` flag is needed for it to read as pinned rather
  than "tap to change nothing."
- **Detail's entry load is one-shot, not reactive**: `GetJournalEntryUseCase`/`JournalRepository.getEntry`
  is a single suspend call, not a `Flow`. Today this is invisible because Detail's own ViewModel
  performs the save and immediately patches its own `_uiState` (`JournalEntryDetailViewModel.kt:128-136`).
  Once Save moves to a separate Edit screen/ViewModel, popping back to Detail will **not** pick up
  the new text automatically — Detail's ViewModel instance is retained across the nav round-trip
  (`rememberViewModelStoreNavEntryDecorator()` in `TodayWasApp.kt:46`), so its one-time `init` load
  never reruns. This plan fixes it explicitly (see Critical Implementation Details).

### Key Discoveries:

- `HelpMeStartUiState`/`MAX_REGENERATIONS`/`StarterPromptList` are already cross-imported from
  `create/` into `detail/` today (`JournalEntryDetailScreen.kt:44`,
  `JournalEntryDetailViewModel.kt:27`) — this plan continues that exact precedent rather than
  inventing a new shared package: `create/` keeps owning "start" pieces, the new `edit/` package
  owns "refine" pieces, and each cross-imports the other's when its own screen needs both.
- The tone-label and AI-assist error-message composables are already byte-for-byte duplicated
  between `create/AddJournalEntryScreen.kt` (`toneLabel`, `helpMeStartErrorMessage`) and
  `detail/JournalEntryDetailScreen.kt` (`refineToneLabel`, `helpMeRefineErrorMessage`), reading the
  same `R.string.journal_help_me_start_tone_*`/`_error_*` resources. Dedupe these before building
  the new screen so there's exactly one canonical version to cross-import.

## Desired End State

- Journal editing is a full screen (`EditJournalEntryScreen`, reached via a new
  `EditJournalEntryKey(id)` nav entry), not a bottom sheet. It shows the same layout shape as Add
  Journal Entry: a top bar titled "Edit journal entry" with Save, a `DsDateStrip` pinned
  (non-interactively) to the entry's own date, and the entry's text pre-filled in the writing area.
- On **both** Add and Edit screens: while the text field is empty, a "help me start" entry point
  (canned starter chips + AI CTA) is shown; the moment it has any content, that's replaced by a
  "help me refine" entry point instead. Both open the same bottom-sheet-shaped UI: tone picker,
  optional "thoughts" field, generate/regenerate/use, gated by the same server `remainingToday`
  quota display — no more client-side regeneration cap anywhere.
- Refine sends the existing text, the chosen tone, and the optional thoughts to the AI proxy, which
  now accepts and uses all three together.
- Deleting an entry remains a Detail-screen-only action, reachable at any age, unaffected by any of
  the above.
- Verification: `./gradlew.bat ktlintCheck testDebugUnitTest assembleDebug` all pass; manually,
  Add → type text → chip switches to "Help me refine" → refine incorporates typed thoughts; Detail →
  Edit (only when `isEditable`) → pinned date, pre-filled text, same contextual switch, Save returns
  to Detail showing the updated text immediately (no stale text); back with unsaved changes prompts
  a discard-confirmation dialog; Delete still works from Detail regardless of entry age.

## What We're NOT Doing

- Habit check-in editing is untouched — it keeps whatever pattern it has today. If it has a similar
  bottom sheet, converting it is a separate, symmetrical follow-up change.
- No Delete action is added to the new Edit screen — Delete stays exclusively on Detail, since it
  must remain reachable regardless of the 24h edit window and Edit is only reachable within it.
- No instrumented/Espresso tests are added for the new nav flow — this change is covered by JUnit4
  ViewModel unit tests only, matching the existing test suite's balance for journal flows.
- No change to the 24h edit-window semantics themselves (`EditWindow.isEditable`,
  `UpdateJournalEntryUseCase`'s re-check-at-save) — this plan only changes *where* editing UI lives
  and re-checks that same window from a new call site.
- No change to the daily AI-assist quota's size or server-side enforcement mechanism — only to which
  client-side flows read/display the number the server already returns.

## Implementation Approach

Five phases, each independently buildable and testable, ordered so the app never sits in a broken
intermediate state:

1. Extend the backend (edge function + Android data/domain layers) to accept refine + thoughts
   together — independent of any UI change, verifiable on its own.
2. Add a display-only mode to `DsDateStrip` — a pure design-system addition, no consumers yet.
3. Dedupe the tone-label/error-message helpers and their string resources inside `create/` — a
   behavior-preserving refactor of the existing Add/Detail screens, so there's exactly one canonical
   version to cross-import next.
4. Build the new `edit/` package (screen, ViewModel, state, and the moved-and-extended
   `HelpMeRefineUiState`) in isolation — not yet wired into navigation, so Detail's existing bottom
   sheet keeps working untouched while this is reviewed and unit-tested.
5. Wire it all together: add the nav entry, cut Detail's Edit action over to navigating instead of
   toggling local state, strip Detail's now-dead edit/refine code (fixing the stale-entry-on-return
   bug in the process), and extend Add Journal Entry with the same contextual "help me refine"
   capability.

## Critical Implementation Details

**Timing & lifecycle — Detail must refresh on return from Edit.** `NavDisplay` disposes a
non-top-of-stack entry's composable (state preserved via `rememberSaveableStateHolderNavEntryDecorator()`)
but retains its ViewModel instance (via `rememberViewModelStoreNavEntryDecorator()`). That means
`JournalEntryDetailScreen`'s composable function body — including any `LaunchedEffect(Unit)` — reruns
fresh every time Detail becomes the active screen again (including right after Edit pops), but the
retained `JournalEntryDetailViewModel` instance's `init` block does not. Phase 5 moves the
entry/`isEditable` load out of `init` and into a handler for a new `JournalEntryDetailIntent.ScreenEntered`
intent, dispatched from the screen's existing `LaunchedEffect(Unit)` (alongside the event-collection
effect) every time the composable (re)enters composition — this covers both the first load and every
return from Edit, without depending on Save explicitly signaling Detail.

**State sequencing — expired mid-flow on Edit routes to discard, not a read-only fallback.** Today,
if the 24h window closes while Detail's edit bottom sheet is open (either at Save or mid-refine),
Detail flips `isEditing` off and keeps showing the same screen read-only
(`JournalEntryDetailViewModel.kt:138-146,222-231`). The new Edit screen has no such non-editing
display mode — it is edit-only. So both cases (Save fails with `EditWindowExpiredException`, or a
refine result lands after the window has closed) are treated as an implicit discard: pop back to
Detail with no error toast (nothing was lost — the original entry is untouched), and rely on Phase
5's `ScreenEntered` refresh to show Detail's now-correctly-non-editable read view. Do not try to
invent a "stay on Edit but read-only" state for this — it doesn't fit the screen's shape.

**Sequencing note for Phases 4→5.** Phase 4 creates `edit/HelpMeRefineUiState.kt` and
`edit/HelpMeRefineStep.kt` as new, extended files while `detail/HelpMeRefineUiState.kt` and
`detail/HelpMeRefineStep.kt` (the old versions) are left untouched and still in use by Detail's
still-functioning bottom sheet. This is deliberate, temporary duplication so Phase 4 is independently
buildable — Phase 5 deletes the old `detail/` copies once Detail's bottom sheet is removed. Don't
"clean this up early" by deleting the old copies in Phase 4.

## Phase 1: Backend — refine accepts thoughts

### Overview

Let "help me refine" send an optional "thoughts about the refinement" string alongside the existing
entry text and tone, both server-side and through the Android data/domain layers. The DTO shape
already supports this; only validation, prompt-building, and the repository/use-case signatures
change.

### Changes Required:

#### 1. Edge function request validation and prompt building

**File**: `supabase/functions/ai-proxy/index.ts`

**Intent**: Allow a request to carry `text` and `thoughts` together (refine-with-guidance), instead
of rejecting that combination, and fold `thoughts` into the refine prompt the same way it's already
folded into the start prompt.

**Contract**: Remove the `if (thoughts !== undefined && text !== undefined) return null;` exclusivity
check in `parseRequest` (`index.ts:48-50`). `buildRefinePrompt` gains an optional `thoughts` parameter
and appends a guidance line to the prompt when present (mirroring `buildStartPrompt`'s existing
`thoughtsLine` construction at `index.ts:56-59`, but framed as refinement guidance rather than "about
their day" context — and still explicitly instructed to be treated as guidance, never as an
instruction to follow outside of rewriting the entry, consistent with the existing prompt-injection
guard). `buildPrompt` passes `request.thoughts` through to `buildRefinePrompt` alongside `request.text`.

#### 2. Android repository/use-case signatures

**File**: `app/src/main/java/pl/luczka/todaywas/domain/repository/AiAssistRepository.kt`

**Intent**: Let callers pass optional refinement thoughts through to the proxy.

**Contract**: `refineJournalEntry(text: String, tone: JournalPromptTone, thoughts: String?): Result<AiPromptResult>` —
add the `thoughts` parameter to the existing signature.

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/AiAssistRepositoryImpl.kt`

**Intent**: Thread the new parameter into the existing request DTO, which already has a `thoughts`
field.

**Contract**: `refineJournalEntry` builds `AiPromptRequestDto(tone = tone.level, text = text, thoughts = thoughts)` —
no DTO shape change needed.

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/RequestJournalRefinementPromptUseCase.kt`

**Intent**: Expose the new parameter to ViewModels.

**Contract**: `invoke(text: String, tone: JournalPromptTone, thoughts: String?): Result<AiPromptResult>`,
delegating straight to the repository.

#### 3. Test doubles and existing use-case test

**File**: `app/src/test/java/pl/luczka/todaywas/domain/repository/FakeAiAssistRepository.kt`

**Intent**: Let tests assert what thoughts value refine was called with.

**Contract**: Add `lastRefineThoughts: String?` (private-set, mirroring the existing `lastThoughts`
for generate), capture it in `refineJournalEntry`, and update the override's signature to match the
new interface.

**File**: `app/src/test/java/pl/luczka/todaywas/domain/usecase/RequestJournalRefinementPromptUseCaseTest.kt`

**Intent**: Cover the new parameter's passthrough.

**Contract**: Update existing test calls' `useCase(...)` invocations for the new 3-arg signature, and
add one test asserting `repository.lastRefineThoughts` reflects a passed-through non-null value.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- App compiles: `./gradlew.bat compileDebugKotlin`

#### Manual Verification:

- Before deploying: confirm with the user that redeploying the live `ai-proxy` Supabase edge
  function is expected and desired for this phase (it's shared production infrastructure).
- After deployment (`supabase functions deploy ai-proxy` or the equivalent MCP tool), call the
  function directly with a `{ tone, text, thoughts }` payload and confirm a 200 response whose text
  reflects the thoughts guidance (e.g. ask for a shorter rewrite and confirm it's shorter).
- Confirm a `{ tone, thoughts }` (start-shaped) and a `{ tone, text }` (refine-without-thoughts)
  request still both succeed exactly as before (no regression to the existing two modes).

---

## Phase 2: Design system — read-only date display

### Overview

Give `DsDateStrip` a display-only mode so a screen can show a single pinned date without any of its
cards reading as tappable, while keeping the exact same visual shape Add Journal Entry already uses.

### Changes Required:

#### 1. Non-interactive mode

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/pickers/DsDateStrip.kt`

**Intent**: Let a caller pin the strip to one date with no tappable affordance anywhere, instead of
relying on `isSelectable` returning `false` for every date but the fixed one (which would still
render that one card as enabled/tappable, just a no-op).

**Contract**: Add `interactive: Boolean = true` (defaulting to today's behavior, so Add Journal
Entry's existing call site is unaffected). When `false`, every card's `available` is forced to
`false` regardless of `isSelectable`, and `onDateSelected` is never invoked. Add a
`@PreviewLightDark` preview covering the non-interactive case alongside the existing preview.

### Success Criteria:

#### Automated Verification:

- Lint passes: `./gradlew.bat ktlintCheck`
- Design system module compiles: `./gradlew.bat :core:designsystem:compileDebugKotlin`

#### Manual Verification:

- Inspect both `DsDateStrip` previews (interactive and non-interactive, light and dark) in Android
  Studio's preview pane — the non-interactive one should read as visually pinned/disabled, not
  tappable.

---

## Phase 3: Dedupe shared AI-assist helpers in `create/`

### Overview

Before building a screen that needs both "start" and "refine" pieces, collapse the two
byte-for-byte-duplicated helper composables (tone label, error message) into one canonical version
in `create/`, and rename the string resources they share to neutral naming so it's clear they're
intentionally shared rather than "refine borrowing start's strings." This phase changes no visible
behavior — Add and Detail keep working exactly as they do today.

### Changes Required:

#### 1. Shared tone-label and error-message helpers

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryScreen.kt`

**Intent**: Make `toneLabel` and `helpMeStartErrorMessage` the one canonical, cross-importable
version (matching the existing precedent of `HelpMeStartUiState`/`MAX_REGENERATIONS` already being
exported from this file), instead of each screen keeping its own copy.

**Contract**: Drop the `private` modifier from `toneLabel`/`helpMeStartErrorMessage` and rename them
to neutral names (e.g. `aiAssistToneLabel`, `aiAssistErrorMessage`) since they'll serve both flows.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailScreen.kt`

**Intent**: Stop duplicating the same logic; use the canonical version instead.

**Contract**: Delete the private `refineToneLabel`/`helpMeRefineErrorMessage` functions and call the
renamed shared functions from `create/` instead (existing cross-import pattern, e.g. how
`MAX_REGENERATIONS` is already imported at `JournalEntryDetailViewModel.kt:27`).

#### 2. Neutral string resource naming

**File**: `app/src/main/res/values/strings.xml`

**Intent**: Reflect that the tone labels, error messages, and the remaining-quota plural are shared
across both flows, not owned by "start."

**Contract**: Rename `journal_help_me_start_tone_*` → `journal_ai_assist_tone_*`,
`journal_help_me_start_error_*` → `journal_ai_assist_error_*`, and the
`journal_help_me_start_remaining_today` plural → `journal_ai_assist_remaining_today`. Update every
reference in `create/AddJournalEntryScreen.kt` and `detail/JournalEntryDetailScreen.kt` accordingly.
Leave flow-specific strings (dialog titles, thoughts-field labels, signed-out messaging, the
generate/refine CTA wording) exactly as they are — only resources already read identically by both
flows get renamed.

### Success Criteria:

#### Automated Verification:

- Unit tests pass (no behavior change expected): `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- App compiles: `./gradlew.bat compileDebugKotlin`

#### Manual Verification:

- Add Journal Entry's "help me start" sheet renders and behaves identically to before (tone labels,
  error messages, remaining-count text all still correct).
- Journal Entry Detail's "help me refine" dialog renders and behaves identically to before, same
  check.

---

## Phase 4: Build the Edit Journal Entry screen

### Overview

Build the new `edit/` package end-to-end — screen, ViewModel, state, and a moved-and-extended
`HelpMeRefineUiState` — as a complete, independently unit-tested unit that isn't yet reachable from
the app (no nav key exists yet). This isolates review of the new screen's correctness from the
disruption of cutting Detail over to it.

### Changes Required:

#### 1. Moved and extended refine state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/edit/HelpMeRefineUiState.kt` (new)

**Intent**: Give refine the same shape as start — an optional thoughts field and a server-driven
remaining-quota count — instead of a client-side regeneration cap.

**Contract**: `HelpMeRefineUiState(isVisible: Boolean = false, step: HelpMeRefineStep = HelpMeRefineStep.INPUT, selectedTone: JournalPromptToneUiState? = null, thoughts: String = "", refinedText: String? = null, isGenerating: Boolean = false, error: AiAssistErrorUiState? = null, remainingToday: Int? = null)`.
Drop `regenerationsUsed`. Keep `MAX_REFINE_TEXT_LENGTH = 8000` (mirrors the edge function's own
input-length limit, unrelated to the regeneration-count cap being removed).

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/edit/HelpMeRefineStep.kt` (new)

**Intent**: Same two-step shape as `HelpMeStartStep` (input vs. preview) — no signed-out step, since
refine (unlike start) is only ever offered on a screen already gated to editing an existing entry
where auth state is checked before the entry point even appears.

**Contract**: `enum class HelpMeRefineStep { INPUT, PREVIEW }` (same as the existing `detail/`
version being superseded).

#### 2. Edit screen MVI files

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/edit/EditJournalEntryUiState.kt` (new)

**Intent**: Hold the entry being edited, its editable draft text, and both AI-assist sub-states —
no `isEditable`/`isEditing` fields, since this screen is only ever reached while editable and is
always "in edit mode."

**Contract**: `EditJournalEntryUiState(isLoading: Boolean, entry: JournalEntryUiState?, text: String, isSaving: Boolean, saveError: Boolean, isDiscardConfirmVisible: Boolean = false, authState: AuthStateUi = AuthStateUi.Loading, helpMeStart: HelpMeStartUiState = HelpMeStartUiState(), helpMeRefine: HelpMeRefineUiState = HelpMeRefineUiState(), starterPrompts: List<JournalStarterPromptUiState> = emptyList())`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/edit/EditJournalEntryIntent.kt` (new)

**Intent**: Cover text editing, save, back/discard, and both AI-assist flows with distinct
per-flow intent names (unlike Detail's old single-flow naming) since this screen hosts both start
and refine simultaneously.

**Contract**: Sealed interface with `TextChanged(text)`, `SaveClicked`, `BackClicked`,
`DiscardConfirmed`, `DiscardDismissed`, `SignInClicked`, plus start-flow intents
(`HelpMeStartClicked`, `HelpMeStartDismissed`, `HelpMeStartToneSelected(tone)`,
`HelpMeStartThoughtsChanged(thoughts)`, `GenerateClicked`, `RegenerateClicked`,
`UseGeneratedTextClicked`) and refine-flow intents (`HelpMeRefineClicked`, `HelpMeRefineDismissed`,
`HelpMeRefineToneSelected(tone)`, `HelpMeRefineThoughtsChanged(thoughts)`, `RefineClicked`,
`RegenerateRefineClicked`, `UseRefinedTextClicked`).

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/edit/EditJournalEntryUiEvent.kt` (new)

**Intent**: Two outcomes, both of which the caller pops the back stack for — kept as two named
events (matching Add's existing `Saved`/`Cancelled` split) for clarity at the call site, not because
they currently behave differently.

**Contract**: `Saved`, `Discarded` (covers explicit discard-confirm, and the expired-mid-flow cases —
see Critical Implementation Details), `NavigateToSignIn`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/edit/EditJournalEntryViewModel.kt` (new)

**Intent**: Assisted-injected by `id` (mirrors `JournalEntryDetailViewModel`'s pattern); loads the
entry and pre-fills `text`; owns both AI-assist flows (start's logic mirrors
`AddJournalEntryViewModel`'s `onGenerate`/`onUseGeneratedTextClicked`/quota handling almost exactly;
refine's logic mirrors the old `JournalEntryDetailViewModel.onRefine`/`onUseRefinedTextClicked`,
updated to use `remainingToday` instead of `regenerationsUsed` and to pass `thoughts` through);
tracks in-flight generate/refine jobs the same way both existing ViewModels do, to drop stale
responses; on `SaveClicked`, calls `UpdateJournalEntryUseCase` and emits `Saved` on success or, on an
`EditWindowExpiredException` failure, emits `Discarded` with no error surfaced (see Critical
Implementation Details); on `BackClicked`, compares current `text` against the original entry's text
— identical text emits `Discarded` immediately, changed text sets `isDiscardConfirmVisible = true`.

**Contract**: `@HiltViewModel(assistedFactory = EditJournalEntryViewModel.Factory::class)` taking
`@Assisted id: String`, `GetJournalEntryUseCase`, `UpdateJournalEntryUseCase`,
`ObserveAuthStateUseCase`, `RequestJournalStarterPromptUseCase`, `RequestJournalRefinementPromptUseCase`,
`IsEditableUseCase`, and `Random` (for starter-prompt variant selection, same as
`AddJournalEntryViewModel`).

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/edit/EditJournalEntryScreen.kt` (new)

**Intent**: Same `DsScaffold` shape as `AddJournalEntryScreen` — top bar with back (triggers
`BackClicked`) and a `DsButtonWithLoading` Save, a fixed date strip below it, then the writing area.
Cross-imports `create/`'s `StarterPromptList`/`HelpMeStartBottomSheet`/`JournalStarterPromptUiState`
for the empty-text path, and hosts its own `HelpMeRefineBottomSheet` (shaped like
`HelpMeStartBottomSheet`: no top bar, tone picker via `DsChoiceFlowRow`, optional thoughts
`DsPlainTextField`, generated-text preview, regenerate chip gated on `remainingToday`, "Use this"/
generate button) for the non-empty-text path — visible only when `authState is SignedIn` and
`text.isNotBlank()` and `text.length <= MAX_REFINE_TEXT_LENGTH`. A `DsDateStrip(interactive = false)`
wrapper (private composable, analogous to `AddJournalEntryScreen`'s private `JournalDateStrip`)
pins to `entry.date`. A `DsAlertDialog` shows when `isDiscardConfirmVisible`, with confirm →
`DiscardConfirmed`, dismiss → `DiscardDismissed`. Ships `@PreviewLightDark` previews for the screen
(empty/start-visible, filled/refine-visible, saving, save-error, discard-dialog-visible states) and
for `HelpMeRefineBottomSheet` (mirroring `HelpMeStartBottomSheet`'s existing preview states).

### Success Criteria:

#### Automated Verification:

- New tests pass: `./gradlew.bat testDebugUnitTest` (includes a new
  `app/src/test/java/pl/luczka/todaywas/ui/journal/edit/EditJournalEntryViewModelTest.kt` covering:
  load pre-fills `text` from the entry; `TextChanged`; `SaveClicked` success emits `Saved` and
  persists via the repository; `SaveClicked` failure sets `saveError`; `SaveClicked` failing with an
  expired window emits `Discarded` with no `saveError`; `BackClicked` with unchanged text emits
  `Discarded` directly; `BackClicked` with changed text shows the discard dialog;
  `DiscardConfirmed`/`DiscardDismissed`; the full start flow — tone/thoughts/generate/regenerate
  capped by `remainingToday`/use-generated-text — mirroring `AddJournalEntryViewModelTest`'s existing
  coverage; the full refine flow — tone/thoughts/refine/regenerate capped by `remainingToday`
  reaching 0/use-refined-text, and that `thoughts` reaches the repository call — adapted from the
  refine-related tests currently in `JournalEntryDetailViewModelTest`; a refine result landing after
  the edit window has closed emits `Discarded` and discards the result, mirroring the old
  mid-refine-expiry test but asserting the new pop-back behavior instead of a flipped `isEditing`
  flag)
- Lint passes: `./gradlew.bat ktlintCheck`
- App compiles: `./gradlew.bat compileDebugKotlin`
- New screen and bottom sheet previews render without errors in Android Studio's preview pane

#### Manual Verification:

- Not yet reachable from the running app (no nav entry exists until Phase 5) — verify via the
  `@PreviewLightDark` previews in Android Studio: empty-text state shows starter chips + "Help me
  start"; filled-text state shows the "Help me refine" entry point instead; light and dark both
  render correctly.

---

## Phase 5: Wire it up — navigation, Detail simplification, Add gets refine

### Overview

Cut the app over to the new screen: add the nav entry, change Detail's Edit action to navigate
instead of toggling local state, remove Detail's now-dead edit/refine code (fixing the stale-entry
bug from Critical Implementation Details along the way), give Add Journal Entry the same contextual
"help me refine" capability, and delete the now fully-unused `MAX_REGENERATIONS` constant.

### Changes Required:

#### 1. Navigation

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasKey.kt`

**Intent**: Add a nav destination for the new screen, keyed by entry id like `JournalEntryDetailKey`.

**Contract**: `data class EditJournalEntryKey(val id: String) : TodayWasKey`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Register the new entry, and change how Detail's Edit action is wired.

**Contract**: Add `entry<EditJournalEntryKey> { key -> EditJournalEntryScreen(id = key.id, onSaved = { backStack.removeLastOrNull() }, onDiscarded = { backStack.removeLastOrNull() }, onNavigateToSignIn = { backStack.add(AccountKey) }) }`.
Change `entry<JournalEntryDetailKey>`'s `JournalEntryDetailScreen` call to also pass
`onEditClicked = { backStack.add(EditJournalEntryKey(id = key.id)) }`.

#### 2. Detail simplification

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailUiState.kt`

**Intent**: Drop everything that only existed to support in-place editing/refining.

**Contract**: Remove `editedText`, `isEditing`, `isSaving`, `saveError`, `authState`, `helpMeRefine`.
Keep `isLoading`, `entry`, `isEditable`, delete-related fields.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailIntent.kt`

**Intent**: Drop everything edit/refine-specific; add the new refresh trigger.

**Contract**: Remove `TextChanged`, `SaveClicked`, `CancelEditClicked`, `HelpMeRefineClicked`,
`HelpMeRefineDismissed`, `ToneSelected`, `RefineClicked`, `RegenerateRefineClicked`,
`UseRefinedTextClicked`. Add `ScreenEntered`. Keep `EditClicked` (repurposed — now navigates, see
below), `BackClicked`, `DeleteClicked`, `DeleteConfirmed`, `DeleteDismissed`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailUiEvent.kt`

**Intent**: Let the ViewModel signal "navigate to Edit" instead of the screen reading a local flag.

**Contract**: Add `NavigateToEdit`, alongside the existing `NavigatedBack`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailViewModel.kt`

**Intent**: Drop the dependencies and logic that only existed for in-place editing/refining; load on
`ScreenEntered` instead of only in `init`, fixing the stale-entry-on-return bug (see Critical
Implementation Details).

**Contract**: Constructor drops `updateJournalEntry`, `requestJournalRefinementPrompt`,
`observeAuthState` (Detail no longer needs auth state at all once refine moves to Edit) — keeps
`id`, `getJournalEntry`, `deleteJournalEntry`, `isEditable`. Move the entry/`isEditable` load from
`init` into a new `onScreenEntered()` handler for `JournalEntryDetailIntent.ScreenEntered`.
`onEditClicked()` sends `JournalEntryDetailUiEvent.NavigateToEdit` instead of setting `isEditing = true`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailScreen.kt`

**Intent**: Remove the edit bottom sheet and refine dialog entirely; wire the new navigation
callback and the refresh-on-enter dispatch.

**Contract**: Add `onEditClicked: () -> Unit` parameter to `JournalEntryDetailScreen`, wired to the
`NavigateToEdit` event. Add `onIntent(JournalEntryDetailIntent.ScreenEntered)` to the existing
`LaunchedEffect(Unit)` block (alongside the events-collection code already there). Delete the
`DsBottomSheet` block (`JournalEntryDetailScreenContent`'s `if (uiState.isEditing)` branch), the
`HelpMeRefineDialog` composable and its call site, and their previews.

**Files to delete**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/HelpMeRefineUiState.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/journal/detail/HelpMeRefineStep.kt` (superseded by the
`edit/` versions built in Phase 4).

#### 3. Add Journal Entry gets contextual refine

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryUiState.kt`

**Intent**: Hold refine state alongside the existing start state.

**Contract**: Add `helpMeRefine: HelpMeRefineUiState = HelpMeRefineUiState()`, importing the type
from `edit/`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryIntent.kt`

**Intent**: Add refine intents; disambiguate the now-shared tone/thoughts intent names from start's.

**Contract**: Rename `ToneSelected`→`HelpMeStartToneSelected`, `ThoughtsChanged`→`HelpMeStartThoughtsChanged`
(both flows now need distinctly-named tone/thoughts intents on this screen). Add
`HelpMeRefineClicked`, `HelpMeRefineDismissed`, `HelpMeRefineToneSelected(tone)`,
`HelpMeRefineThoughtsChanged(thoughts)`, `RefineClicked`, `RegenerateRefineClicked`,
`UseRefinedTextClicked`.

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryViewModel.kt`

**Intent**: Wire the new refine intents using the same pattern as `EditJournalEntryViewModel`'s
refine handling built in Phase 4 (refine here operates on the current unsaved draft `text`, which
works unchanged since `refineJournalEntry` takes arbitrary text, not an entry id).

**Contract**: Add `RequestJournalRefinementPromptUseCase` to the constructor; add a `refineJob: Job?`
tracker alongside the existing `generateJob`; add handlers for all new refine intents, mirroring
`EditJournalEntryViewModel`'s refine logic (no 24h-window re-check needed here, since Add's draft
isn't a persisted entry).

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryScreen.kt`

**Intent**: Show "help me refine" instead of the starter-prompt overlay once there's text.

**Contract**: Change the `if (uiState.text.isEmpty())` branch that shows `StarterPromptList` to an
`if/else` — empty shows `StarterPromptList` as today, non-empty shows a "Help me refine" entry-point
chip (visible only when signed in) that opens the `HelpMeRefineBottomSheet` cross-imported from
`edit/`. Update references to the renamed `HelpMeStartToneSelected`/`HelpMeStartThoughtsChanged`
intents.

#### 4. Delete dead code

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/create/HelpMeStartUiState.kt`

**Intent**: Remove the constant nothing references anymore.

**Contract**: Delete `const val MAX_REGENERATIONS = 3` and its doc comment.

#### 5. Test updates

**File**: `app/src/test/java/pl/luczka/todaywas/ui/journal/detail/JournalEntryDetailViewModelTest.kt`

**Intent**: Match the simplified ViewModel; add coverage for the staleness fix.

**Contract**: Remove tests for `EditClicked`-toggles-`isEditing`, `SaveClicked`, `CancelEditClicked`,
and all `HelpMeRefine*` behavior (superseded by `EditJournalEntryViewModelTest` from Phase 4).
Update the `viewModel(...)` test factory for the reduced constructor. Add a test asserting
`ScreenEntered` re-fetches the entry (e.g.: construct with one entry text, mutate the repository's
stored text directly, dispatch `ScreenEntered`, assert `uiState.value.entry?.text` reflects the
mutated value) — this is the regression test for the stale-entry-on-return bug.

**File**: `app/src/test/java/pl/luczka/todaywas/ui/journal/create/AddJournalEntryViewModelTest.kt`

**Intent**: Cover the new refine capability.

**Contract**: Add a `requestJournalRefinementPrompt`/`FakeAiAssistRepository` refine-flow test set
mirroring the ones added to `EditJournalEntryViewModelTest` in Phase 4 (tone/thoughts/refine/
regenerate-capped-by-remainingToday/use-refined-text), adapted for Add's context (operates on the
draft `text`, no entry/window involved). Update references to the renamed `HelpMeStartToneSelected`/
`HelpMeStartThoughtsChanged` intents in existing tests.

### Success Criteria:

#### Automated Verification:

- All unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`
- Debug APK builds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Add Journal Entry: typing text swaps the starter-chip overlay for a "Help me refine" chip; refine
  incorporates typed thoughts; saving still works as before.
- Main → Detail (entry < 24h old) → tap Edit → lands on the new Edit screen with the date pinned and
  non-interactive, the existing text pre-filled, and the contextual start/refine switch working the
  same way as Add.
- On Edit: clearing all text brings back the starter-prompt overlay (start), typing anything brings
  back "help me refine" (with the thoughts field), refine there uses the pre-filled/edited text.
- Edit → Save → lands back on Detail showing the updated text immediately (no stale text — this is
  the regression check for the staleness fix).
- Edit → back with unsaved changes → discard-confirmation dialog appears; dismiss returns to
  editing with changes intact; confirm discards and returns to Detail showing the original text.
- Detail (entry ≥ 24h old): no Edit icon shown; Delete still works regardless of entry age.
- Detail → Delete still works for both editable and non-editable entries.

## Testing Strategy

### Unit Tests:

- New `EditJournalEntryViewModelTest` (Phase 4) covering load/save/discard/both AI-assist flows/the
  mid-flow-expiry-discards-silently behavior.
- Updated `JournalEntryDetailViewModelTest` (Phase 5) covering the reduced surface plus the new
  `ScreenEntered` staleness-refresh regression test.
- Updated `AddJournalEntryViewModelTest` (Phase 5) covering the new refine flow.
- Updated `FakeAiAssistRepository`/`RequestJournalRefinementPromptUseCaseTest` (Phase 1) covering
  the new `thoughts` parameter passthrough.

### Key edge cases covered by the above:

- Regenerate stops calling the repository once `remainingToday` reaches 0, on both start and refine.
- A refine (or generate) response that lands after its dialog/screen session has been dismissed,
  reopened, or the edit window has closed is discarded, never applied.
- Save failing specifically due to `EditWindowExpiredException` is distinguished from a generic save
  failure (silent discard vs. a `saveError` the user sees).
- Discard-confirmation only appears when the draft actually differs from the original text.

### Manual Testing Steps:

See each phase's Manual Verification above — Phase 5's list is the full end-to-end walkthrough.

## Migration Notes

No data migration — this only changes UI structure and AI-assist request shape. Existing journal
entries are unaffected. The edge function change is backward compatible with the current Android app
(it only *adds* an accepted request shape); deploying it ahead of the Android release, or the other
way around, doesn't break either side mid-rollout.

## References

- Existing precedent for cross-package sharing: `HelpMeStartUiState`/`MAX_REGENERATIONS` imported
  from `create/` into `detail/` today (`JournalEntryDetailScreen.kt:44`,
  `JournalEntryDetailViewModel.kt:27`).
- Start-flow quota/regenerate pattern to mirror for refine:
  `AddJournalEntryViewModel.kt:182-256` (`onGenerate`, `resetForNewSession`, `applyResult`).
- 24h edit-window semantics (unchanged by this plan): `domain/util/EditWindow.kt`,
  `domain/usecase/UpdateJournalEntryUseCase.kt`, `domain/usecase/IsEditableUseCase.kt`.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Backend — refine accepts thoughts

#### Automated

- [x] 1.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck`
- [x] 1.3 App compiles: `./gradlew.bat compileDebugKotlin`

#### Manual

- [x] 1.4 Confirmed with user before deploying the live edge function
- [ ] 1.5 Deployed function returns thoughts-aware refine output
- [ ] 1.6 Start-shaped and refine-without-thoughts requests still succeed unchanged

### Phase 2: Design system — read-only date display

#### Automated

- [ ] 2.1 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 2.2 Design system module compiles: `./gradlew.bat :core:designsystem:compileDebugKotlin`

#### Manual

- [ ] 2.3 Interactive and non-interactive previews verified, light and dark

### Phase 3: Dedupe shared AI-assist helpers in create/

#### Automated

- [ ] 3.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 3.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 3.3 App compiles: `./gradlew.bat compileDebugKotlin`

#### Manual

- [ ] 3.4 Add Journal Entry's help-me-start sheet unchanged
- [ ] 3.5 Journal Entry Detail's help-me-refine dialog unchanged

### Phase 4: Build the Edit Journal Entry screen

#### Automated

- [ ] 4.1 New EditJournalEntryViewModelTest passes: `./gradlew.bat testDebugUnitTest`
- [ ] 4.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 4.3 App compiles: `./gradlew.bat compileDebugKotlin`
- [ ] 4.4 New previews render without errors

#### Manual

- [ ] 4.5 Empty/filled preview states verified in Android Studio, light and dark

### Phase 5: Wire it up — navigation, Detail simplification, Add gets refine

#### Automated

- [ ] 5.1 All unit tests pass: `./gradlew.bat testDebugUnitTest`
- [ ] 5.2 Lint passes: `./gradlew.bat ktlintCheck`
- [ ] 5.3 Debug APK builds: `./gradlew.bat assembleDebug`

#### Manual

- [ ] 5.4 Add Journal Entry contextual start/refine switch verified
- [ ] 5.5 Detail → Edit navigation, pinned date, pre-filled text verified
- [ ] 5.6 Edit's contextual start/refine switch verified
- [ ] 5.7 Edit → Save → Detail shows fresh text immediately (staleness fix verified)
- [ ] 5.8 Discard-confirmation dialog flow verified
- [ ] 5.9 Edit icon absent past 24h; Delete works regardless of age
