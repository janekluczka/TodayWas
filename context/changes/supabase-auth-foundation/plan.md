# Supabase Auth Foundation Implementation Plan

## Overview

Wire a real Supabase Auth scaffold (email/password + native Google sign-in) into TodayWas, behind
a new bottom-navigation shell (Home / Journal / Habits / Preferences). Preferences hosts
sign-in/sign-up/sign-out for anyone who skipped or wants to manage their account later; onboarding's
already-stubbed `ACCOUNT_INFO` step gets the same sign-up/sign-in flow embedded inline, ending on
`ALL_SET` either way. This is F-01 in the roadmap — a foundation slice with no other feature
depending on sign-in yet (core journaling/habit-tracking remain fully account-free per FR-008).

## Current State Analysis

- The Supabase project already exists and is empty (`https://ibftrzalfdmqztmiuvnj.supabase.co`, 0
  tables, 0 migrations) — the roadmap's "human-only Supabase project" blocker is resolved.
- No networking client (Ktor/Retrofit/OkHttp), no `BuildConfig`/secrets-reading mechanism, no
  auth/session code exists anywhere in the app today — clean slate, nothing to migrate.
- Onboarding's `ACCOUNT_INFO` step already exists with a single "Create account" CTA
  (`OnboardingIntent.CreateAccountClicked`) that is a literal no-op today, with a comment: "Real
  account creation is F-01's job, deferred" (`OnboardingViewModel.kt:47`). No "sign in" option
  exists there yet.
- There is no bottom navigation anywhere in the app; `MainScreen` is the sole post-onboarding
  destination, pushed to directly from the root `NavDisplay`'s single back stack
  (`TodayWasApp.kt:49-57`). `MainScreen` owns its own `DsScaffold` (topBar + FAB) —
  self-contained, not designed to be embedded.
- Prior slices (S-01/S-02/S-03) deliberately built nothing that assumes an account exists; PRD's
  Access Control section requires core features to work identically regardless of sign-in state.

### Key Discoveries

- Hilt has two established module patterns: `object` + `@Provides` for framework/SDK construction
  (`DatabaseModule.kt`, `ClockModule.kt`) and `abstract class` + `@Binds` for repository
  interface→impl bindings (`RepositoryModule.kt`). A new Supabase client follows the first; a new
  `AuthRepository` binding follows the second.
- `core/designsystem` already has `DsNavigationBar`/`DsNavigationBarItem`
  (`components/navigation/DsNavigationBar.kt`) and text-field wrappers (`DsTextField`,
  `DsFilledTextField`) — no new design-system components are needed for this slice.
- Use cases in this codebase are thin one-liners:
  `class XUseCase @Inject constructor(private val repository: XRepository) { operator fun
  invoke(...) = repository.x(...) }` (`ObserveOnboardingStateUseCase.kt`). Per
  [[feedback_usecase_depends_on_repository_only]], a use case only ever depends on a repository,
  never on another use case.
- Repository tests use hand-written fakes of the project's own repository/DAO interfaces
  (`OnboardingRepositoryImplTest.kt`'s `FakeDao`), not a mocking library — none is a project
  dependency (no MockK/Mockito in `libs.versions.toml`).
- Supabase's Kotlin SDK docs confirm native Android Google sign-in goes through
  `androidx.credentials` (Credential Manager): build a `GetGoogleIdOption` with a **Web** OAuth
  client ID (not the Android client), wrap it in a `GetCredentialRequest`, call
  `CredentialManager.create(context).getCredential(...)`, extract the ID token, then call
  `supabase.auth.signInWithIdToken(provider = Google, idToken = ...)`. Supabase's own
  `ComposeAuth` plugin wraps this in one composable (`rememberSignInWithGoogle`), but that
  conflates the Credential Manager call with the Supabase call inside a Composable — this plan
  does the two steps explicitly instead, so the Supabase call stays behind `AuthRepository` like
  every other data access in this codebase (see Critical Implementation Details).
- `supabase.auth.sessionStatus` is a `Flow<SessionStatus>` with four cases:
  `Authenticated`, `LoadingFromStorage`, `NetworkError`, `NotAuthenticated` — session persistence
  and refresh are handled by the SDK automatically; there is no reason to write custom storage.

## Desired End State

A signed-out user can open Preferences (bottom nav) or onboarding's `ACCOUNT_INFO` step, create an
account or sign in with email/password or Google, and see their signed-in state reflected in both
places. A signed-in user can sign out from Preferences. No existing screen changes behavior based
on sign-in state — journaling and habit tracking remain fully usable either way.

Verification: build succeeds, unit tests pass, and manual sign-up/sign-in/sign-out via both email
and Google succeed against the real (empty) Supabase project, from both entry points.

## What We're NOT Doing

- Password reset / forgot-password flow (deferred — no F-01 outcome text requires it).
- Real content for the Journal and Habits bottom-nav tabs (explicitly deferred by the user to a
  later change — this slice adds them as empty placeholder destinations only).
- Account settings beyond sign-in/sign-out (no profile editing, no account deletion).
- Server-side enforcement of anything — this slice is 100% client-side Supabase Auth usage.
- F-02 (AI-assist proxy), S-06 (local-data sync on account creation) — both explicitly build on
  top of this foundation's `AuthState` contract, not part of it.
- Encrypting the session token at rest beyond the Supabase SDK's own default storage (a deliberate
  choice from questioning, not an oversight).

## Implementation Approach

Five phases, each independently buildable and testable: (1) Supabase client + secrets, (2) auth
data layer, (3) bottom-nav shell, (4) shared auth-form UI + Preferences screen, (5) onboarding's
inline sign-up/sign-in sub-flow. Phases 1-2 have no UI and can be fully unit-verified. Phase 3 is
pure navigation restructuring with no new business logic. Phases 4-5 both consume the same shared
`AuthFormContent` composable built in Phase 4, so Phase 5 is comparatively small.

## Critical Implementation Details

### Why the Supabase call stays behind AuthRepository instead of using ComposeAuth

Supabase's recommended Android path is the `ComposeAuth` plugin's `rememberSignInWithGoogle`
composable, which internally does both the Credential Manager call and the
`signInWithIdToken` call. This codebase's MVI convention puts all data access behind a
ViewModel-owned repository, never inside a Composable. This plan splits the two steps: the
Composable (in the shared `AuthFormContent`) only runs `CredentialManager.getCredential(...)` and
extracts the raw ID token string; it then calls a plain `(String) -> Unit` callback that each
host ViewModel wires to `AuthRepository.signInWithGoogleIdToken(idToken)`. The `ComposeAuth`
plugin/artifact is therefore not needed — only the `Auth` plugin is installed on the
`SupabaseClient`.

### Deliberate logic duplication between OnboardingViewModel and PreferencesViewModel

Both ViewModels independently own an `AuthFormUiState` (email, password, mode, submitting, errors)
and both dispatch to the same auth use cases. This is intentional, not an oversight: per
[[feedback_viewmodel_self_derives_availability]]-style reasoning and the existing "a flow embedded
in a host screen can live in the host's ViewModel while it's small" pattern
(`context/foundation/lessons.md`), introducing a shared ViewModel used by two unrelated top-level
screens would cross a scoping boundary this codebase doesn't otherwise cross. The shared piece is
the stateless UI (`AuthFormContent`) and the use cases underneath — not a shared ViewModel.

### AuthRepositoryImpl is not unit-tested directly; AuthRepository fakes are

Supabase's `Auth` plugin is a large SDK-owned interface, not a small interface this project
controls — hand-writing a full fake for it (the way `FakeDao` fakes a 4-method DAO) is impractical
and not worth the effort for a solo 3-week MVP. Instead: `AuthRepository` (the interface this
project owns) gets a hand-written `FakeAuthRepository` used to unit-test `OnboardingViewModel` and
`PreferencesViewModel`; `AuthRepositoryImpl` itself is verified manually (Phase 4/5's manual steps
cover every method it exposes). This mirrors how `TodayWasDatabase`/Room itself is never
unit-tested directly — only the repository logic built on top of it is.

### External prerequisite for Google sign-in (blocks Phase 5's manual verification only)

Google native sign-in requires a **Web** OAuth 2.0 Client ID from Google Cloud Console, configured
both as the `serverClientId` in the app's `GetGoogleIdOption` and as the Google provider's Client
ID in Supabase's dashboard (Authentication → Providers → Google). This is a human-only setup step,
same class of prerequisite as the Supabase project itself. It does not block Phases 1-4 (email
auth works without it) — only Phase 5's Google-sign-in manual verification.

### Error messages come from Supabase error codes, not raw exception text

Supabase's Auth REST errors (`AuthRestException`) carry a stable `errorCode` (e.g.
`user_already_exists`, `invalid_credentials`, `weak_password`). To honor both "surface Supabase's
own rejection" (from questioning) and the project's "all user-facing text goes through
`strings.xml`" rule, `AuthRepositoryImpl` maps known `errorCode`s to a small closed
`AuthError` enum; the UI layer resolves each case to a `stringResource`. Unrecognized codes and
network failures (`IOException`, `HttpRequestTimeoutException`) map to a single generic
`AuthError.Unknown`.

## Phase 1: Supabase client + secrets plumbing

### Overview

Add the Supabase/Ktor/Credential Manager Gradle dependencies, wire `local.properties` secrets
into `BuildConfig`, and provide a Hilt-injectable `SupabaseClient`.

### Changes Required:

#### 1. Gradle version catalog

**File**: `gradle/libs.versions.toml`

**Intent**: Add version entries and library coordinates for: the Supabase Kotlin BOM + `Auth`
module (`io.github.jan-tennert.supabase`), a Ktor client engine for Android (e.g.
`ktor-client-okhttp`), `androidx.credentials:credentials`,
`androidx.credentials:credentials-play-services-auth`, and
`com.google.android.libraries.identity.googleid:googleid` (needed for `GetGoogleIdOption` /
parsing the returned credential).

**Contract**: Follow the existing `[versions]`/`[libraries]` structure. Confirm current stable
version numbers via web search or Maven Central at implementation time rather than assuming —
this project's own lessons.md flags exactly this risk for fast-moving dependencies (Nav3
precedent); do not hardcode a guessed version.

#### 2. App module dependencies

**File**: `app/build.gradle.kts`

**Intent**: Add the new libraries from the version catalog; enable the `buildConfig` build
feature (currently only `compose = true` is set).

**Contract**: `buildFeatures { compose = true; buildConfig = true }`. Add
`implementation(platform(libs.supabase.bom))`, `implementation(libs.supabase.auth)`,
`implementation(libs.ktor.client.okhttp)` (or chosen engine),
`implementation(libs.androidx.credentials)`,
`implementation(libs.androidx.credentials.play.services.auth)`,
`implementation(libs.googleid)`.

#### 3. Secrets → BuildConfig

**File**: `app/build.gradle.kts`

**Intent**: Read `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `GOOGLE_WEB_CLIENT_ID` from
`local.properties` (gitignored, per `CLAUDE.md`'s existing "Security & Configuration Tips") and
expose them as `BuildConfig` fields.

**Contract**: In `android { defaultConfig { ... } }`, load `local.properties` via
`java.util.Properties()` (same mechanism `sdk.dir` already uses implicitly via AGP — write this
explicitly since no prior code does it), then
`buildConfigField("String", "SUPABASE_URL", "\"${'$'}{properties["SUPABASE_URL"]}\"")` and
equivalents for the other two keys. Missing keys should fail the build loudly (not silently
produce an empty string) so a fresh clone without `local.properties` configured gets a clear
error, not a runtime crash deep in `SupabaseModule`.

#### 4. local.properties documentation

**File**: `local.properties` (gitignored — not committed) and, if this repo keeps a
`local.properties.example`/README note, update it; otherwise skip.

**Intent**: Document (to the user, not in a committed file) that `SUPABASE_URL`,
`SUPABASE_ANON_KEY`, and `GOOGLE_WEB_CLIENT_ID` must be added locally before the app builds.

**Contract**: `SUPABASE_URL` and `SUPABASE_ANON_KEY` are retrievable via the Supabase MCP's
`get_project_url` / `get_publishable_keys` tools against the existing project. `GOOGLE_WEB_CLIENT_ID`
comes from the external prerequisite noted in Critical Implementation Details.

#### 5. Hilt Supabase module

**File**: `app/src/main/java/pl/luczka/todaywas/di/SupabaseModule.kt`

**Intent**: Provide a singleton `SupabaseClient` with only the `Auth` plugin installed, matching
`DatabaseModule`/`ClockModule`'s `object` + `@Provides @Singleton` pattern.

**Contract**:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
    }
}
```

#### 6. ProGuard rules

**File**: `app/proguard-rules.pro`

**Intent**: Add the Credential Manager play-services keep rule Google's docs specify, so release
builds don't strip classes the runtime needs.

**Contract**: The `-if class androidx.credentials.CredentialManager` / `-keep class
androidx.credentials.playservices.**` rule pair from Android's Credential Manager setup docs.

### Success Criteria:

#### Automated Verification:

- Project builds: `./gradlew.bat assembleDebug`
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- Build fails with a clear error if `local.properties` is missing the three new keys (not a
  silent empty-string BuildConfig value).
- `SupabaseModule.provideSupabaseClient()` is injectable without crashing at app startup (add a
  temporary log or breakpoint if needed — no UI depends on it yet).

---

## Phase 2: Auth domain + data layer

### Overview

Model auth state, wrap Supabase's `Auth` plugin behind this project's own `AuthRepository`
interface, and expose one use case per action.

### Changes Required:

#### 1. Domain model

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/AuthState.kt`

**Intent**: A sealed domain type representing the three states other code needs to distinguish.

**Contract**: `sealed interface AuthState { data object Loading : AuthState; data class SignedIn(val
userId: String, val email: String?) : AuthState; data object SignedOut : AuthState }`. Per the
questioning decision, both `SessionStatus.NotAuthenticated` and `SessionStatus.NetworkError` map to
`SignedOut` — there is no distinct "expired" state, since no current feature depends on staying
signed in.

#### 2. Auth error type

**File**: `app/src/main/java/pl/luczka/todaywas/domain/model/AuthError.kt`

**Intent**: A small closed set of auth failure reasons the UI can map to `strings.xml` entries,
per the Critical Implementation Details note on error messages.

**Contract**: `sealed interface AuthError { data object EmailAlreadyRegistered : AuthError; data
object InvalidCredentials : AuthError; data object WeakPassword : AuthError; data object
NetworkUnavailable : AuthError; data object Unknown : AuthError }`.

#### 3. AuthRepository interface

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/AuthRepository.kt`

**Intent**: The seam every ViewModel goes through — no ViewModel or Composable ever touches
`SupabaseClient` directly.

**Contract**: `fun observeAuthState(): Flow<AuthState>`; `suspend fun signUpWithEmail(email:
String, password: String): Result<Unit>`; `suspend fun signInWithEmail(email: String, password:
String): Result<Unit>`; `suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit>`;
`suspend fun signOut(): Result<Unit>`. Each `Result`'s failure, when present, wraps an `AuthError`
(not a raw `Throwable`) — see `AuthRepositoryImpl` below.

#### 4. AuthState mapper

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/AuthStateMapper.kt`

**Intent**: Isolate the `SessionStatus` → `AuthState` mapping in its own file, per
[[feedback_ui_state_no_domain_models]]-style layering discipline (cross-layer mapping lives in its
own dedicated file, not inline in the repository).

**Contract**: `fun SessionStatus.toAuthState(): AuthState`, switching on the four `SessionStatus`
subtypes as described in Key Discoveries.

#### 5. Auth error mapper

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/AuthErrorMapper.kt`

**Intent**: Isolate the `Throwable` → `AuthError` mapping (Supabase `errorCode` string matching)
in its own file.

**Contract**: `fun Throwable.toAuthError(): AuthError`. Match `AuthRestException.errorCode`
against `"user_already_exists"` → `EmailAlreadyRegistered`, `"invalid_credentials"` →
`InvalidCredentials`, `"weak_password"` → `WeakPassword`; `IOException`/timeout exceptions →
`NetworkUnavailable`; everything else → `Unknown`. Confirm the exact current `errorCode` string
constants against Supabase's Kotlin SDK source/docs at implementation time rather than guessing.

#### 6. AuthRepositoryImpl

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/AuthRepositoryImpl.kt`

**Intent**: Thin wrapper over `SupabaseClient.auth`, using `runCatching` (no retry — auth
failures are deterministic, not transient, unlike `safeDbCall`'s DB-write retry).

**Contract**: `class AuthRepositoryImpl @Inject constructor(private val supabase:
SupabaseClient) : AuthRepository`. `observeAuthState()` = `supabase.auth.sessionStatus.map {
it.toAuthState() }`. Each write method wraps its single Supabase call in `runCatching { ... }.map
{ Unit }.recoverCatching { throw it }`-style handling that ends in `Result<Unit>` with the failure
mapped via `toAuthError()` (e.g. `.fold(onSuccess = { Result.success(Unit) }, onFailure = {
Result.failure(it.toAuthError().toException()) }` — implementer's choice of exact plumbing, as
long as the caller only ever sees an `AuthError`-wrapping failure, never a raw SDK exception).

#### 7. Repository binding

**File**: `app/src/main/java/pl/luczka/todaywas/data/repository/RepositoryModule.kt`

**Intent**: Bind the new repository, following the existing `@Binds` pattern.

**Contract**: Add `@Binds abstract fun bindAuthRepository(impl: AuthRepositoryImpl):
AuthRepository`.

#### 8. Use cases

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/ObserveAuthStateUseCase.kt`,
`SignUpWithEmailUseCase.kt`, `SignInWithEmailUseCase.kt`, `SignInWithGoogleUseCase.kt`,
`SignOutUseCase.kt`

**Intent**: One thin `operator fun invoke(...)` use case per action, each depending only on
`AuthRepository`, matching `ObserveOnboardingStateUseCase`'s shape exactly.

**Contract**: Each use case's `invoke` signature mirrors the corresponding `AuthRepository`
method 1:1.

#### 9. Fake for tests

**File**: `app/src/test/java/pl/luczka/todaywas/data/repository/FakeAuthRepository.kt`

**Intent**: A hand-written `AuthRepository` fake (mutable `AuthState`, controllable
success/failure per call) for `OnboardingViewModelTest`/`PreferencesViewModelTest` in later
phases — see Critical Implementation Details for why `AuthRepositoryImpl` itself isn't unit-tested.

**Contract**: Backed by a `MutableStateFlow<AuthState>` and simple queued-result fields per method,
mirroring `OnboardingRepositoryImplTest`'s `FakeDao` style.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest`
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- N/A — no UI in this phase; covered by Phase 4/5's manual steps.

---

## Phase 3: Bottom navigation shell

### Overview

Introduce a 4-tab bottom navigation bar (Home / Journal / Habits / Preferences) hosting the
existing `MainScreen` unchanged as "Home", two empty placeholder tabs, and a new (empty-for-now,
filled in Phase 4) Preferences tab.

### Changes Required:

#### 1. Shell screen

**File**: `app/src/main/java/pl/luczka/todaywas/ui/mainshell/MainShellScreen.kt`

**Intent**: A new top-level composable that owns one outer `DsScaffold` with `bottomBar =
DsNavigationBar { ... 4 items ... }`, switching its content between the four tabs via local
`rememberSaveable` enum state — not a full nested `NavDisplay`/back stack, since only Home has
further sub-navigation (unchanged, still root-level — see below) and Journal/Habits/Preferences
have none yet in this slice. This is a deliberate simplification of
[[feedback_...]]`context/foundation/lessons.md`'s "own nested Nav3 setup" note: that note describes
the eventual shape once tabs accumulate real navigable content; a plain tab-state `when` is
sufficient today and avoids building unused nested back-stack machinery. No ViewModel is needed —
tab selection is pure UI state with no data dependency.

**Contract**: `enum class BottomNavTab { HOME, JOURNAL, HABITS, PREFERENCES }`. `MainShellScreen`
takes the same callback parameters `MainScreen` currently takes
(`onAddEntryClicked`/`onJournalEntryClicked`/`onCreateHabitClicked`/`onLogCheckInsClicked`/`onHabitClicked`)
and forwards them unchanged to `MainScreen` when `BottomNavTab.HOME` is selected — these continue
to push onto the **root** back stack exactly as today, so add-entry/detail flows remain full-screen
over the bottom nav (the outer shell's Scaffold is simply not part of that nav entry). Journal and
Habits tabs render a minimal placeholder composable (single centered `DsText`, new string resource
each, e.g. `main_journal_tab_placeholder`/`main_habit_tab_placeholder`) defined privately in this
same file — not worth their own `ui/<feature>/` packages for a single static string.

#### 2. Root wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/TodayWasApp.kt`

**Intent**: Swap the `entry<MainKey>` block from rendering `MainScreen` directly to rendering
`MainShellScreen`.

**Contract**: Same `entry<MainKey> { ... }` block, `MainScreen(...)` call replaced with
`MainShellScreen(...)` passing through the identical lambda set. No `TodayWasKey.kt` change needed
— `MainKey` still means "the post-onboarding app", it now just renders more chrome.

### Success Criteria:

#### Automated Verification:

- Project builds: `./gradlew.bat assembleDebug`
- Existing `MainScreen`-related unit/instrumented tests still pass unchanged (no behavior change
  to `MainScreen` itself).
- Lint passes: `./gradlew.bat ktlintCheck`

#### Manual Verification:

- Launching the app past onboarding shows the bottom nav with 4 tabs; Home shows the existing
  Main screen content unchanged (list, FAB, add-entry navigation all still work).
- Journal and Habits tabs show their placeholder text and nothing else.
- Preferences tab renders (empty/blank is fine until Phase 4).
- No regression in journal/habit add/detail flows reachable from Home.

---

## Phase 4: Shared auth-form UI + Preferences screen

### Overview

Build the one reusable sign-up/sign-in form (email/password + Google) and wire it into the
Preferences tab for both signed-out and signed-in states.

### Changes Required:

#### 1. Shared auth-form UI state + mode

**File**: `app/src/main/java/pl/luczka/todaywas/ui/auth/AuthFormMode.kt`,
`app/src/main/java/pl/luczka/todaywas/ui/auth/AuthFormUiState.kt`

**Intent**: A reusable ui-facing state shape both `OnboardingUiState` and `PreferencesUiState`
nest, per `ui/model/`'s "shared across features" convention — placed under a new `ui/auth/`
package instead since it's paired tightly with the stateless composable that renders it, not a
domain→ui mapper in isolation.

**Contract**: `enum class AuthFormMode { SIGN_UP, SIGN_IN }`. `@Immutable data class
AuthFormUiState(val mode: AuthFormMode, val email: String, val password: String, val
emailError: Boolean, val passwordError: Boolean, val isSubmitting: Boolean, val bannerError:
AuthError?)`, defaulted to `AuthFormMode.SIGN_UP` / empty fields / no errors.

#### 2. Shared auth-form composable

**File**: `app/src/main/java/pl/luczka/todaywas/ui/auth/AuthFormContent.kt`

**Intent**: The one place the Credential Manager call lives. Stateless: takes `AuthFormUiState`
and a callbacks object, renders email/password `DsTextField`s (field-level error text under each,
per questioning), a mode-toggle text button ("New here? Sign up" / "Already have an account? Sign
in"), a submit `DsButtonWithLoading`, a `bannerError`-driven inline error text above the form when
non-null (mapped to a `stringResource` per `AuthError` case), and a "Sign in with Google" button.

**Contract**: Callbacks: `onEmailChanged: (String) -> Unit`, `onPasswordChanged: (String) ->
Unit`, `onModeToggled: () -> Unit`, `onSubmitClicked: () -> Unit`, `onGoogleIdTokenReceived:
(String) -> Unit`, `onGoogleSignInFailed: () -> Unit`. The Google button's `onClick` launches a
coroutine that builds and issues the Credential Manager request:

```kotlin
val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(false)
    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
    .build()
val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()
val result = CredentialManager.create(context).getCredential(context, request)
val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
onGoogleIdTokenReceived(credential.idToken)
```

catching `GetCredentialException`/`GoogleIdTokenParsingException` into `onGoogleSignInFailed()`.
Confirm this exact API shape (class/method names) against current Credential Manager docs at
implementation time — this is a fast-moving Android Jetpack API. Field-level `emailError`/
`passwordError` are simple client-side checks (non-blank email containing `@`, password length ≥
6) evaluated on submit, not on every keystroke.

#### 3. Preferences screen files

**File**: `app/src/main/java/pl/luczka/todaywas/ui/preferences/PreferencesIntent.kt`,
`PreferencesUiState.kt`, `PreferencesViewModel.kt`, `PreferencesScreen.kt`

**Intent**: Standard MVI screen package. No `PreferencesUiEvent.kt` — this screen has no one-shot
navigation/effects to emit for this slice (a tab, not pushed/popped, and errors render inline via
`UiState`); add one later if a real one-shot need appears rather than scaffolding an empty sealed
interface now.

**Contract**: `PreferencesUiState(val authState: AuthStateUi, val authForm: AuthFormUiState)`
where `AuthStateUi` is a new `ui/model/AuthStateUi.kt` ui-facing mirror of domain `AuthState`
(`Loading`/`SignedOut`/`SignedIn(email: String?)`) with its mapper in `ui/model/AuthStateMapper.kt`
— shared with `OnboardingUiState` in Phase 5, hence living in `ui/model/` rather than `ui/auth/`.
`PreferencesIntent` sealed interface: `EmailChanged(value: String)`, `PasswordChanged(value:
String)`, `ModeToggled`, `SubmitClicked`, `GoogleIdTokenReceived(idToken: String)`,
`GoogleSignInFailed`, `SignOutClicked`. `PreferencesViewModel` injects
`ObserveAuthStateUseCase`, `SignUpWithEmailUseCase`, `SignInWithEmailUseCase`,
`SignInWithGoogleUseCase`, `SignOutUseCase`; collects auth state into `uiState.authState` in
`init`; `SubmitClicked` calls sign-up or sign-in based on `authForm.mode`, setting
`isSubmitting`/`bannerError` around the call. `PreferencesScreen` renders `AuthFormContent` when
`authState` is `Loading` or `SignedOut`; when `SignedIn(email)`, renders the email and a sign-out
button instead.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (new `PreferencesViewModelTest` using
  `FakeAuthRepository` from Phase 2: submit success/failure per `AuthError` case, mode toggle,
  sign-out)
- Lint passes: `./gradlew.bat ktlintCheck`
- `PreferencesScreen` ships `@PreviewLightDark` previews covering signed-out (sign-up mode),
  signed-out (sign-in mode, with a `bannerError` set), and signed-in states, wrapped in `DsTheme`

#### Manual Verification:

- From the Preferences tab: sign up with a new email/password against the real Supabase project,
  confirm the row appears in Supabase Auth's dashboard user list.
- Sign out, then sign back in with the same email/password.
- Attempt sign-up with an already-registered email — confirm the friendly "already registered"
  message appears, not a raw exception string.
- Google sign-in works end-to-end **once the external OAuth Client ID prerequisite is in place**
  (see Critical Implementation Details) — if not yet configured, confirm the button at least
  triggers the Credential Manager sheet and fails gracefully (`onGoogleSignInFailed`), not a crash.

---

## Phase 5: Onboarding embedding

### Overview

Give onboarding's `ACCOUNT_INFO` step the same sign-up/sign-in capability, embedded inline via the
shared `AuthFormContent`, without ever blocking the flow from reaching `ALL_SET`.

### Changes Required:

#### 1. Onboarding sub-step state

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/AccountSubStep.kt`

**Intent**: Tracks where within `ACCOUNT_INFO` the user currently is.

**Contract**: `enum class AccountSubStep { CHOICE, FORM }`.

#### 2. Onboarding state/intent additions

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingUiState.kt`,
`OnboardingIntent.kt`

**Intent**: Add the fields/intents needed to drive the embedded sub-flow, reusing the same
`AuthFormUiState`/`AuthStateUi` types from Phase 4.

**Contract**: `OnboardingUiState` gains `accountSubStep: AccountSubStep`, `authState: AuthStateUi`,
`authForm: AuthFormUiState`. `OnboardingIntent` gains: `SignInClicked` (new — alongside the
existing `CreateAccountClicked`, both only valid from `AccountSubStep.CHOICE`, both move to
`AccountSubStep.FORM` setting `authForm.mode` to `SIGN_IN`/`SIGN_UP` respectively),
`BackToChoiceClicked`, plus the same `EmailChanged`/`PasswordChanged`/`ModeToggled`/
`SubmitClicked`/`GoogleIdTokenReceived`/`GoogleSignInFailed` shape `PreferencesIntent` has.

#### 3. Onboarding ViewModel wiring

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingViewModel.kt`

**Intent**: Wire the new intents to the same auth use cases `PreferencesViewModel` uses (inject
`ObserveAuthStateUseCase`, `SignUpWithEmailUseCase`, `SignInWithEmailUseCase`,
`SignInWithGoogleUseCase` — no `SignOutUseCase`, onboarding never signs a user out). Collect auth
state in `init` the same way `PreferencesViewModel` does. **Leave `onNextClicked`'s
`OnboardingStep.ACCOUNT_INFO -> step = ALL_SET` branch exactly as it is today** — this is what
guarantees the flow always reaches `ALL_SET` regardless of whether the user signed in, signed up,
or skipped via `Next`/`Skip` without touching the account step at all.

**Contract**: `CreateAccountClicked`/`SignInClicked` update `accountSubStep = FORM` and
`authForm.mode` accordingly. `BackToChoiceClicked` resets to `accountSubStep = CHOICE`. The
email/password/submit/Google intents mirror `PreferencesViewModel`'s handling 1:1 against the same
use cases.

#### 4. Onboarding screen UI

**File**: `app/src/main/java/pl/luczka/todaywas/ui/onboarding/OnboardingScreen.kt`

**Intent**: Replace `AccountInfoStepBody`'s single-button body with a three-way render based on
`uiState.authState` and `uiState.accountSubStep`.

**Contract**: If `authState is SignedIn` → confirmation text ("Signed in as {email}"), new string
resource. Else if `accountSubStep == CHOICE` → existing description text
(`onboarding_account_description`) plus two buttons: existing `onboarding_account_create_cta`
(→ `CreateAccountClicked`) and a new `onboarding_account_signin_cta` (→ `SignInClicked`). Else
(`FORM`) → `AuthFormContent(state = uiState.authForm, callbacks = ...)` plus a `DsTextButton`
("Back", new string resource) → `BackToChoiceClicked`. Extend
`OnboardingScreenPreviewStateProvider` with cases for `CHOICE`, `FORM` (sign-up and sign-in
modes), and already-`SignedIn`.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (extend `OnboardingViewModelTest` — create it
  if it doesn't exist yet — covering: choice→form transition for both CTAs, back-to-choice, submit
  success/failure, and that `NextClicked` reaches `ALL_SET` from every `accountSubStep`/`authState`
  combination)
- Lint passes: `./gradlew.bat ktlintCheck`
- `OnboardingScreen` previews extended per above, all wrapped in `DsTheme`

#### Manual Verification:

- Fresh install → onboarding → `ACCOUNT_INFO` → tap "Create account" → sign up inline → land on
  `ALL_SET` after tapping Next.
- Fresh install → onboarding → `ACCOUNT_INFO` → tap "Sign in" → sign in with an existing account
  (created in Phase 4's manual testing) → land on `ALL_SET`.
- Fresh install → onboarding → `ACCOUNT_INFO` → tap Next without choosing either → still reaches
  `ALL_SET` (skip path unaffected).
- After finishing onboarding via sign-in, confirm the Preferences tab reflects the same signed-in
  state (shared `AuthState` contract works end-to-end across both entry points).
- Google sign-in from onboarding works the same as from Preferences (same shared composable).

---

## Testing Strategy

### Unit Tests:

- `AuthStateMapperTest` / `AuthErrorMapperTest` — pure function mapping, all `SessionStatus`/
  known-`errorCode` cases plus an unrecognized-code fallback.
- `PreferencesViewModelTest`, `OnboardingViewModelTest` — against `FakeAuthRepository`, covering
  success/failure paths for sign-up, sign-in, Google sign-in, sign-out (Preferences only), and (for
  onboarding) the choice/form/confirmation sub-state transitions and the "Next always reaches
  ALL_SET" invariant.
- `AuthRepositoryImpl` is intentionally not unit-tested — see Critical Implementation Details.

### Integration Tests:

- None planned for this slice — no instrumented tests exist for prior auth-adjacent work either,
  and Credential Manager's real flow isn't practically instrumentable without a real device/Play
  Services and a real Google account.

### Manual Testing Steps:

See each phase's Manual Verification. End-to-end: sign up via email (Preferences), sign out, sign
in via email (onboarding), sign out, sign in via Google (either entry point) — all against the
real Supabase project, confirmed via the Supabase dashboard's Auth user list.

## Performance Considerations

None specific — this is low-frequency, user-initiated auth traffic; no caching or batching
concerns.

## Migration Notes

Not applicable — no existing data model changes; purely additive.

## References

- Prior deferral note: `OnboardingViewModel.kt:47` ("Real account creation is F-01's job,
  deferred")
- Bottom-nav pattern precedent: `context/foundation/lessons.md` §"Root/screen-level navigation
  uses Navigation 3, not ad-hoc state switches"
- AI-proxy's later dependency on this slice's session contract:
  `context/changes/deployment/deployment-plan.md`, `context/foundation/infrastructure.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Supabase client + secrets plumbing

#### Automated

- [x] 1.1 Project builds: `./gradlew.bat assembleDebug` — 1ebd0a2
- [x] 1.2 Lint passes: `./gradlew.bat ktlintCheck` — 1ebd0a2

#### Manual

- [ ] 1.3 Build fails loudly without the three `local.properties` keys
- [ ] 1.4 `SupabaseClient` injects without crashing at startup

### Phase 2: Auth domain + data layer

#### Automated

- [x] 2.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — ed091ce
- [x] 2.2 Lint passes: `./gradlew.bat ktlintCheck` — ed091ce

### Phase 3: Bottom navigation shell

#### Automated

- [x] 3.1 Project builds: `./gradlew.bat assembleDebug` — 953ba6d
- [x] 3.2 Existing `MainScreen` tests still pass unchanged — 953ba6d
- [x] 3.3 Lint passes: `./gradlew.bat ktlintCheck` — 953ba6d

#### Manual

- [x] 3.4 4-tab bottom nav renders; Home unchanged; Journal/Habits show placeholders; Preferences
      renders empty — 953ba6d
- [x] 3.5 No regression in journal/habit add/detail flows from Home — 953ba6d

### Phase 4: Shared auth-form UI + Preferences screen

#### Automated

- [x] 4.1 Unit tests pass: `./gradlew.bat testDebugUnitTest` — 60177df
- [x] 4.2 Lint passes: `./gradlew.bat ktlintCheck` — 60177df
- [x] 4.3 `PreferencesScreen` `@PreviewLightDark` previews (signed-out sign-up, signed-out
      sign-in-with-error, signed-in) — 60177df

#### Manual

- [ ] 4.4 Email sign-up creates a real Supabase Auth user
- [ ] 4.5 Sign-out then sign-in with the same credentials works
- [ ] 4.6 Duplicate-email sign-up shows a friendly error, not a raw exception
- [x] 4.7 Google sign-in works (or fails gracefully pre-OAuth-client-ID) — 60177df

### Phase 5: Onboarding embedding

#### Automated

- [x] 5.1 Unit tests pass: `./gradlew.bat testDebugUnitTest`
- [x] 5.2 Lint passes: `./gradlew.bat ktlintCheck`
- [x] 5.3 `OnboardingScreen` previews extended (choice, form, signed-in confirmation)

#### Manual

- [ ] 5.4 Sign-up inline in onboarding reaches `ALL_SET`
- [ ] 5.5 Sign-in inline in onboarding reaches `ALL_SET`
- [ ] 5.6 Skipping the account step (Next with no choice made) still reaches `ALL_SET`
- [ ] 5.7 Auth state from onboarding is reflected in the Preferences tab afterward
- [ ] 5.8 Google sign-in from onboarding works the same as from Preferences
