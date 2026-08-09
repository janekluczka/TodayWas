<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Supabase Auth Foundation

- **Plan**: context/changes/supabase-auth-foundation/plan.md
- **Scope**: Full plan (Phases 1-5)
- **Date**: 2026-08-09
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 4 warnings, 2 observations

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

### F1 — Google sign-in has no re-entrancy guard during the Credential Manager round-trip

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/auth/SignInFormContent.kt:77-107
- **Detail**: `GoogleSignInButton`'s `enabled = !state.isSubmitting`, but `isSubmitting` only flips to `true` inside the ViewModel's `onSignInGoogleIdTokenReceived` — i.e. *after* `CredentialManager.getCredential(...)` already returns. While the system credential picker is in flight (an async, potentially multi-second call), the button stays enabled with no loading indicator, so a double-tap can launch two concurrent `getCredential` calls.
- **Fix**: Track a local `isLaunching` state (or disable the button) as soon as the click handler starts the coroutine, before awaiting the Credential Manager result.
- **Decision**: FIXED — added local `isGoogleSignInLaunching` state, disables the button immediately on tap and resets in a `finally` block.

### F2 — Sign-out failure is silently swallowed, no loading state

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/account/AccountViewModel.kt:196-203
- **Detail**: `onSignOutClicked()` has no failure branch — if `signOut()` fails, nothing happens: no `ShowError` event, no submitting/loading flag, and `AccountScreen.kt`'s `SignedInContent` uses a plain `DsButton` (not `DsButtonWithLoading`, unlike every sign-in/sign-up path). Inconsistent with the file's own error-handling convention; the user gets zero feedback on a failed tap.
- **Fix**: Mirror `applySignInResult`/`applySignUpResult` — surface a `ShowError` event on failure, and track a submitting flag for the sign-out button.
- **Decision**: FIXED — added `isSigningOut` to `AccountUiState`, `ShowError` on failure, `SignedInContent` now uses `DsButtonWithLoading`. New test added.

### F3 — GOOGLE_WEB_CLIENT_ID is optional, but the plan's Phase 1 contract and Progress 1.3 wording say "fails loudly without the three keys"

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: app/build.gradle.kts:20-45; context/changes/supabase-auth-foundation/plan.md (Phase 1 contract + Progress 1.3)
- **Detail**: `SUPABASE_URL`/`SUPABASE_ANON_KEY` use `requiredLocalProperty` (throws `GradleException`), but `GOOGLE_WEB_CLIENT_ID` uses `optionalLocalProperty` (silently `""`) — code deliberately relies on this, since `SignInFormContent.kt` checks `BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()` and fails gracefully rather than crashing the build. This is the *correct* engineering call (matches the "external prerequisite doesn't block Phases 1-4" note) but contradicts the literal Phase 1 contract text and the checked Progress item 1.3 ("the three keys"), and isn't documented as an intentional deviation the way the two other redesign notes are.
- **Fix**: Update `plan.md`'s Phase 1 contract and Progress 1.3 wording to say the two Supabase keys fail loudly and `GOOGLE_WEB_CLIENT_ID` is optional-with-graceful-fallback — a short note, same style as the existing "Sign-up name/Terms fields dropped" addendum.
- **Decision**: FIXED — added an "Addendum (post-implementation)" note under Phase 1's Contract in plan.md.

### F4 — navigation-schema.md still describes sign-up collecting name/Terms fields that were dropped

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/supabase-auth-foundation/navigation-schema.md
- **Detail**: `plan.md`'s own "Sign-up name/Terms fields dropped" section documents that first/last name and a Terms checkbox were removed from `SIGN_UP`, but `navigation-schema.md`'s sub-step diagrams (both the onboarding and Account-screen versions) still describe `SIGN_UP` as collecting those fields. Same class of doc drift already found and fixed once in `plan.md` this session — just not propagated to this second doc.
- **Fix**: Update navigation-schema.md's SIGN_UP diagram text to match the shipped email/password/repeat-password-only form.
- **Decision**: FIXED — updated both SIGN_UP diagram boxes; also caught and fixed two more instances of the same drift discovered while in the file (stale inline "Back" button references, and stale "system back skips CHOICE" note — both predate this session's back-navigation consolidation).

### F5 — Google sign-in cancellation is reported as a generic error

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: app/src/main/java/pl/luczka/todaywas/ui/auth/SignInFormContent.kt:100-104
- **Detail**: `GetCredentialException` (which includes user-cancellation) and `GoogleIdTokenParsingException` both funnel into `onGoogleSignInFailed()`, showing the generic "Unknown" error snackbar even when the user simply dismissed the system picker. Not a safety bug, just a rough UX edge on a common path.
- **Fix**: Distinguish cancellation (no-op, no snackbar) from real failures if checking for `GetCredentialCancellationException` is cheap to add.
- **Decision**: FIXED — added a `GetCredentialCancellationException` catch before the general `GetCredentialException` catch, no-op (no error shown).

### F6 — AuthState.SignedIn.userId is currently unused

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: app/src/main/java/pl/luczka/todaywas/data/repository/AuthStateMapper.kt
- **Detail**: Populated in the mapper but grep confirms no reads anywhere in `:app`. Very likely intentional groundwork for future sync (US-04's account-creation data sync), not a defect — flagging only so it isn't mistaken for dead code later.
- **Fix**: No action needed; leave as-is, documented here for future reference.
- **Decision**: ACCEPTED — confirmed intentional groundwork, no action taken.

## Notable non-findings (verified clean)

- No leftover `AuthFormContent`/`AuthFormMode`/`AuthFormUiState` files or references anywhere — clean deletion per the redesign.
- No raw exception or stack trace ever reaches the UI — every failure path maps through the closed `AuthError` → `AuthErrorUiState` chain.
- The Google Credential Manager call lives only in the Composable (`SignInFormContent.kt`); `AuthRepositoryImpl` only ever sees a raw `idToken: String` — the plan's "Critical Implementation Detail" about keeping this out of the repository is honored.
- No hardcoded secrets anywhere — `SUPABASE_URL`/`SUPABASE_ANON_KEY`/`GOOGLE_WEB_CLIENT_ID` all come from `BuildConfig`.
- No logging of credentials/tokens/emails (`Log.d/e/i/w/v`, `println`, `printStackTrace` all absent from `pl.luczka.todaywas`).
- The stale-step-after-transition bug class (fixed in commit 9f26493 for `AccountScreen`) does **not** recur in `OnboardingScreen` — it checks `authState is SignedIn` before branching on the local `accountSubStep`, and onboarding has no sign-out path to trigger the original failure mode through anyway.
- Pattern compliance is strong throughout: MVI shape matches `HabitDetailViewModel`; every mapper lives in its own file; Hilt module patterns (`SupabaseModule` object+`@Provides` vs `RepositoryModule` abstract class+`@Binds`) mirror `DatabaseModule`/existing repository bindings; every new screen/component ships `@PreviewLightDark` wrapped in `DsTheme`; no hardcoded UI strings outside previews.
- `PreferencesScreen` stays a thin summary card and never duplicates `AccountScreen`'s auth-form UI.

## Automated Verification

- `./gradlew.bat testDebugUnitTest` — PASS
- `./gradlew.bat ktlintCheck` — PASS
- `./gradlew.bat assembleDebug` — PASS

## Manual Verification

All Phase 1/4/5 manual items (1.3, 1.4, 4.4-4.7, 5.4-5.9) were re-verified this session against the real Supabase project, including a full Google sign-in round trip (real Credential Manager flow, real Supabase OIDC token exchange, confirmed via Supabase auth logs and `auth.users` SQL queries). See plan.md's Progress section — all checked.
