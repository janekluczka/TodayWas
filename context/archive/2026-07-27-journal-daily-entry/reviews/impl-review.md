<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Journal Daily Entry Implementation Plan

- **Plan**: context/changes/journal-daily-entry/plan.md
- **Scope**: All 4 phases (full plan review)
- **Date**: 2026-07-27
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — Day-strip bypasses the design system's TodayWasRadioOption for a bespoke HorizontalPager

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryScreen.kt:136-217 (`DayStrip`/`DayCard`)
- **Detail**: The plan's Phase 4 Contract for the add-entry screen called for reusing the existing `TodayWasRadioOption` component as a Today/Yesterday pair. What's actually built is a bespoke `HorizontalPager`-based calendar strip (`Int.MAX_VALUE` pages indexed by epoch-day, `userScrollEnabled = false`, `BoxWithConstraints` inset math for centering). This was explicitly directed across several live UI-iteration rounds and is confirmed correctly implemented — `available` gating (`:168`) correctly restricts taps to only TODAY/YESTERDAY, so "no backfill beyond yesterday" still holds. The concern isn't correctness, it's that this is real added complexity + a design-system bypass that was never written back into the plan or recorded as a deliberate decision anywhere (`change.md` has no notes on it).
- **Fix A ⭐ Recommended**: Update plan.md's Phase 4 §4 Contract to describe the day-strip as built, and add a one-line note (in `change.md` or a lesson) on why a bespoke picker was chosen over `TodayWasRadioOption`.
  - Strength: Keeps the plan as an accurate record without touching already-tested, user-approved code; matches this repo's own precedent (onboarding-focus-pick added a superseding phase section when its UI diverged this much from the original plan).
  - Tradeoff: Doesn't address the "should `TodayWasRadioOption` have sufficed" architectural question — just documents the outcome.
  - Confidence: HIGH — you already manually tested and accepted this exact UI; reverting it now would be pure churn.
  - Blind spot: None significant.
- **Fix B**: Reassess whether the simpler `TodayWasRadioOption` pair would have been sufficient and simplify.
  - Strength: Less code, fewer moving parts (no pager state, no `BoxWithConstraints`), stays inside the existing design-system vocabulary.
  - Tradeoff: Throws away UI you explicitly asked for and already tested working — real rework for a already-accepted feature.
  - Confidence: LOW — you clearly wanted the richer calendar-strip feel ("like a clock pager on iOS"), so this likely isn't actually the right call.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — plan.md Phase 4 §3/§4 updated with "As built" notes documenting the FAB, day-strip, top-bar Save, and Snackbar decisions.

### F2 — plan.md's Phase 4 Contract text is stale relative to the actual UI (FAB, top-bar Save, Snackbar)

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/journal-daily-entry/plan.md:319-341 (Phase 4 §3 "Main screen" and §4 "Add-entry screen" Contract text)
- **Detail**: The plan still describes a plain `TodayWasButton` "Add entry" in the Journal section and a bottom `TodayWasButtonWithLoading` save action with inline error text. Actual implementation (confirmed correct in both review passes): an expandable FAB (`MainFab`, `MainScreen.kt:84-130`, gated on the same `addableSlots`/`focus` logic the plan intended) and Save moved into the `TodayWasTopBar` `actions` slot with errors surfaced via `TodayWasSnackbarHost` (`AddJournalEntryScreen.kt:78-84, 99-106`). Functionally faithful to the plan's intent, just a different, user-directed widget choice that the plan document never caught up to.
- **Fix**: Update plan.md's Phase 4 §3/§4 Contract prose to describe the FAB, top-bar Save, and Snackbar as built (same edit as F1's Fix A covers both).
- **Decision**: FIXED via Fix A (same edit as F1).

### F3 — lessons.md's Navigation 3 lesson is now stale/contradicted by this change's own bug fix

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: context/foundation/lessons.md:60-62 ("Root/screen-level navigation uses Navigation 3...")
- **Detail**: The existing lesson says to "Skip `lifecycle-viewmodel-navigation3`/`entryDecorators`... until per-entry ViewModel scoping... is actually needed." This change hit exactly that threshold: without per-entry scoping, revisiting "Add entry" reused the first visit's `AddJournalEntryViewModel` (stale text, stale `availableSlots` — a real, user-discovered bug). The fix (`rememberViewModelStoreNavEntryDecorator()` + the new dependency, `TodayWasApp.kt:7,36`) is correctly wired, but the lesson text still reads as if this is *never* needed, which will mislead the next reader.
- **Fix**: Update the lesson's rule text to note that per-entry ViewModel scoping becomes necessary the moment a screen's ViewModel takes per-navigation data (like `AddJournalEntryViewModel`'s assisted-injected `availableSlots`) and gets re-entered — this change is the concrete example to cite.
- **Decision**: FIXED — appended an "Update (journal-daily-entry)" note to the lesson explaining the threshold and citing this bug as the concrete example.

### F4 — androidx.lifecycle version skew in the version catalog

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: gradle/libs.versions.toml:7,21 (`lifecycleRuntimeKtx = "2.6.1"` vs. the new `lifecycleViewmodelNav3 = "2.11.0"`)
- **Detail**: Both are `androidx.lifecycle` group artifacts, five major-ish releases apart, with no lifecycle BOM in place to reconcile them. Gradle will resolve the shared group to a single version for anything transitively overlapping, but manually straddling two release trains this far apart is fragile — a future dependency bump could hit a resolution surprise that's hard to diagnose.
- **Fix A ⭐ Recommended**: Bump `lifecycleRuntimeKtx` to track closer to `2.11.x` now, while the gap is still small and well-understood.
  - Strength: Cheap right now (one version-catalog line); removes the skew before it compounds with future lifecycle-family additions.
  - Tradeoff: Touches a dependency used app-wide (`lifecycle-runtime-ktx`), so it's worth a quick full rebuild+test pass rather than assuming it's a no-op.
  - Confidence: MED — androidx.lifecycle artifacts are usually safe to align, but not verified against this specific version jump.
  - Blind spot: Haven't checked androidx.lifecycle's 2.6→2.11 changelog for breaking API changes that might affect existing onboarding/root ViewModels.
- **Fix B**: Leave as-is for now, revisit when the next lifecycle-family dependency is added.
  - Strength: Zero risk today — current build is green.
  - Tradeoff: Kicks the can; the skew only grows if left alone.
  - Confidence: MED — reasonable if time-boxed given the 3-week deadline.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — consolidated both artifacts under one `androidxLifecycle = "2.11.0"` version alias in libs.versions.toml (rather than just bumping the old alias separately, to prevent this exact skew recurring). Verified with a clean rebuild: `testDebugUnitTest`, `ktlintCheck`, `assembleDebug` all pass, all 12 test files green with zero failures.

### F5 — MainFab's multi-action expand/collapse branch is unreachable today

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt:84-130 (`MainFab`)
- **Detail**: `actions.size` can currently only ever be 0 or 1 (habit tracking's action doesn't exist until S-03), so the `expanded`/speed-dial branch never actually triggers today. This was a deliberate forward-looking choice you asked for explicitly ("ready to become a real speed-dial once S-03 adds one") and there's already a comment noting habit tracking has no destination yet — so this is background, not a defect.
- **Fix**: No action needed now; if S-03 ends up taking a different shape for its main-screen entry point, revisit whether this FAB shape was the right bet.
- **Decision**: FIXED — added an explicit code comment above the `expanded` state noting the branch is currently unreachable by design and becomes live automatically once a second action exists (`MainScreen.kt`).

### F6 — Dates render as raw ISO strings instead of a formatted display

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/JournalEntryDetailScreen.kt:50, app/src/main/java/pl/luczka/todaywas/ui/main/MainScreen.kt:166
- **Detail**: Both render `LocalDate.toString()` directly (e.g. "2026-07-27") rather than a human-formatted date. The plan's Phase 4 intent for the detail screen said "the date formatted for display" — functionally harmless, just not what was written.
- **Fix**: Format via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` (or similar) at both call sites when this becomes user-facing polish worth doing.
- **Decision**: FIXED — both call sites now format via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)`.

### F7 — `availableSlots.first()` has no defensive fallback for an empty list

- **Severity**: 👁️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/journal/AddJournalEntryViewModel.kt:37
- **Detail**: `selectedSlot = availableSlots.first()` throws `NoSuchElementException` if constructed with an empty list. Currently unreachable — `MainScreen`'s FAB only fires `AddEntryClicked` when `addableSlots` is non-empty, and the nav key snapshots that same list — but there's no guard if that invariant is ever broken by a future nav-restoration or deep-link path.
- **Fix**: Use `availableSlots.firstOrNull()` with a safe fallback, or assert the precondition explicitly so a future violation fails loudly with a clear message instead of a generic exception.
- **Decision**: FIXED — added `require(availableSlots.isNotEmpty())` in an `init` block with a message naming the invariant and its owner (MainScreen's FAB), so a future violation fails loudly and clearly instead of a generic `NoSuchElementException`.
