# Supabase Auth Foundation — Plan Brief

> Full plan: `context/changes/supabase-auth-foundation/plan.md`

## What & Why

Wire a real Supabase Auth scaffold — email/password + native Google sign-in — into TodayWas. This
is F-01 in the roadmap: a foundation slice that unlocks S-06 (account creation + local-data sync)
and F-02 (the AI-assist proxy, which authenticates via the caller's Supabase session). No existing
feature depends on sign-in yet; core journaling/habit-tracking stay fully account-free per FR-008.

## Starting Point

The Supabase project already exists and is empty (no tables/migrations). The app has no
networking client, no secrets-reading mechanism, and no auth/session code anywhere. Onboarding's
`ACCOUNT_INFO` step already has a stubbed, no-op "Create account" button with a comment marking it
as F-01's job. There is no bottom navigation — `MainScreen` is the sole post-onboarding screen.

## Desired End State

A signed-out user can sign up or sign in — with email/password or Google — from either the new
Preferences tab or inline within onboarding's `ACCOUNT_INFO` step, and see the same signed-in state
reflected in both places. A signed-in user can sign out from Preferences. Onboarding always reaches
its final `ALL_SET` screen, whether the user signed in, signed up, or skipped entirely.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Google sign-in method | Native Credential Manager, not browser OAuth redirect | Better in-app UX, no manifest deep-link scaffolding needed, directly supported by Supabase's Kotlin SDK docs | Plan |
| Session token storage | Supabase SDK's built-in storage, not custom-encrypted | Zero extra code; acceptable risk for a solo MVP with short-lived, rotatable tokens | Plan |
| Email-auth scope | Sign-up + sign-in only, no password reset | Matches F-01's "minimal session-check contract" outcome text; reset deferred | Plan |
| Sign-in entry points | Bottom-nav Preferences tab **and** embedded inline in onboarding | User wants both a persistent surface and in-flow account creation for new users | Plan |
| Onboarding UX | Sign-up/sign-in nested inside `ACCOUNT_INFO`, not a redirect away from onboarding; `Next` always reaches `ALL_SET` | User wants onboarding to stay self-contained regardless of auth outcome | Plan |
| Session-check contract shape | Small sealed `AuthState` (`Loading`/`SignedIn`/`SignedOut`) via `Flow`, not a bare `Boolean` | Matches this codebase's domain→UI mapping convention; gives F-02/S-06 what they'll need later | Plan |
| Error UX | Field-level validation + one banner for server/network errors, mapped from Supabase error codes | Clear UX without hardcoding raw SDK exception text as user-facing copy | Plan |
| Secrets | `buildConfigField` reading `local.properties` directly in Gradle, no secrets plugin | Matches `CLAUDE.md`'s already-stated approach; zero new dependencies | Plan |
| Testing | Unit-test `AuthRepository`'s callers (ViewModels) via a hand-written fake; skip unit-testing `AuthRepositoryImpl` itself | Supabase's `Auth` plugin is too large an SDK interface to hand-fake economically | Plan |
| Auth-form code reuse | One shared stateless `AuthFormContent` composable used by both Preferences and onboarding; state/logic stays per-host-ViewModel | Avoids duplicating the Credential Manager wiring while respecting this codebase's "flow lives in its host screen's ViewModel" boundary | Plan |

## Scope

**In scope:**
- Supabase client + secrets plumbing (Gradle deps, `BuildConfig`, Hilt module)
- `AuthRepository`/`AuthState`/`AuthError` + use cases
- New 4-tab bottom navigation (Home/Journal/Habits/Preferences); Journal/Habits are empty
  placeholders
- Preferences screen: email sign-up/sign-in, Google sign-in, signed-in account view, sign-out
- Onboarding's `ACCOUNT_INFO` step: inline sign-up/sign-in sub-flow reusing the same form

**Out of scope:**
- Password reset / forgot-password
- Real Journal/Habits tab content
- Account settings beyond sign-in/out (no profile editing/deletion)
- Encrypted-at-rest session storage
- Anything F-02 (AI proxy) or S-06 (data sync) specific

## Architecture / Approach

`AuthRepository` wraps Supabase's `Auth` plugin behind this project's normal repository seam; a
`SessionStatus → AuthState` mapper isolates the SDK type from the domain. One shared stateless
`AuthFormContent` composable (owns the Credential Manager call) is rendered by two independent
screens — `PreferencesScreen` and `OnboardingScreen` — each with its own ViewModel/state/intents
calling the same five use cases underneath. `MainShellScreen` wraps the unchanged `MainScreen`
inside a new bottom-nav Scaffold using local tab-selection state, not a nested `NavDisplay`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Supabase client + secrets | Gradle deps, `BuildConfig` secrets, injectable `SupabaseClient` | Guessing dependency versions instead of checking current ones |
| 2. Auth domain + data layer | `AuthState`/`AuthError`, `AuthRepository`/`Impl`, use cases, `FakeAuthRepository` | Supabase SDK error-code strings may differ from assumed values |
| 3. Bottom navigation shell | 4-tab nav wrapping unchanged `MainScreen` + placeholders | None significant — pure UI restructuring |
| 4. Shared auth-form UI + Preferences | Reusable form (email+Google), Preferences screen | Credential Manager API shape may have moved since docs were fetched |
| 5. Onboarding embedding | Inline sign-up/sign-in sub-flow in `ACCOUNT_INFO`, always reaching `ALL_SET` | Keeping the "always reaches ALL_SET" invariant correct across all sub-states |

**Prerequisites:** Supabase project (already exists). A Google Cloud **Web** OAuth Client ID,
configured in both the app and Supabase's dashboard — human-only step, blocks only Phase 5's
Google-sign-in manual verification, not Phases 1-4.
**Estimated effort:** ~3-4 sessions across 5 phases, solo.

## Open Risks & Assumptions

- Exact current version numbers/API shapes for the Supabase Kotlin SDK, Ktor engine, and
  Credential Manager artifacts weren't pinned — the plan explicitly defers to implementation-time
  verification rather than guessing, consistent with how this project already handles Navigation 3.
- Supabase `errorCode` string constants (`user_already_exists`, `invalid_credentials`,
  `weak_password`) are stated from general Supabase Auth knowledge and should be confirmed against
  the SDK at implementation time.
- The Google OAuth Client ID prerequisite is a real scheduling risk if not created before Phase 5
  starts — flagged explicitly so it isn't discovered mid-phase.

## Success Criteria (Summary)

- A user can sign up, sign out, and sign back in with email/password from Preferences, against the
  real Supabase project.
- The same is true for Google sign-in, once the OAuth Client ID prerequisite is configured.
- A user can complete the same sign-up/sign-in inline during onboarding without ever getting stuck
  before reaching `ALL_SET`.
- No existing journaling/habit-tracking behavior changes for signed-out users.
