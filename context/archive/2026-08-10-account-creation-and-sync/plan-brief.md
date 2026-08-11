# Account Creation and Sync — Plan Brief

> Full plan: `context/changes/account-creation-and-sync/plan.md`

## What & Why

When a user creates a Supabase account (or signs into one), their existing local journal/habit
data should automatically become part of that account — no manual export/import, matching
US-04/FR-007. Beyond the one-time upload, remote becomes the account's source of truth going
forward: new writes push to Postgres transparently, and the app pulls remote → local on cold
start, giving basic reinstall/new-device restore.

## Starting Point

F-01 (`supabase-auth-foundation`, archived) already ships working email/Google Supabase Auth
with an `AuthRepository`/`AuthState` contract, but the Supabase project has zero Postgres tables
and the app has zero sync/remote concept anywhere. Journal/habit data lives only in local Room,
keyed by autoincrement `Long` ids with no remote-id equivalent.

## Desired End State

Sign up (or sign in) with existing local data present → a review screen shows counts ("3 journal
entries, 2 habits, 11 check-ins") → confirming uploads it all to a new RLS-protected Postgres
schema. From then on, journaling/habit-tracking feel exactly the same, but every write quietly
backs up to the cloud, and reinstalling the app (or a new device, same account) recovers
everything on next launch. Signing out clears the local copy.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Local ID scheme | Migrate `Long` autoincrement → client-generated UUID `String` on all 3 entities | Local id can BE the remote Postgres PK directly, no separate id-mapping column needed |
| Schema-bump data safety | Accept destructive `fallbackToDestructiveMigration` wipe for this bump | Pre-release app, no real user data at risk yet; avoids writing/testing a real Room `Migration` under time pressure |
| Sync scope | Not one-time — remote becomes source of truth: push-per-write (background, silent) + pull on sign-in and every cold start | User's explicit call: "push new data there and retrieve it from there" |
| Read path after sign-in | Local Room stays the read source, synced with remote (not remote-primary reads) | Keeps the app fully offline-usable even when signed in |
| Consent UX | A review screen with data counts, not silent auto-upload | User wants visibility into what's being uploaded before it happens; deviates from PRD's literal "no additional action" wording (US-04 updated in Phase 4) |
| Push-failure UX | Silent background retry, no visible sync-status indicator | Matches "transparent, no network-gated core journaling" product ethos |
| Sign-out behavior | Clear local Room (synced tables), not leave it in place | Avoids stale cross-account data confusion on a shared/reused device |
| Sync orchestration | A `UseCase` with multiple repository dependencies, not a new "Coordinator" component | No new architectural vocabulary; the existing use-case-depends-on-repository rule doesn't forbid multiple repos |
| Push hook location | Inside `JournalRepositoryImpl`/`HabitRepositoryImpl` (repo depends on `AuthRepository`) | Every existing use case/ViewModel stays unchanged; sync becomes transparent at the storage seam |

## Scope

**In scope:**
- UUID migration for journal/habit/check-in ids
- New Postgres schema (3 tables, RLS) + Postgrest wiring
- Push-per-write (background) + bulk `syncWithRemote()` (push-all, pull-all)
- Review screen (counts, confirm/skip) wired into both onboarding and Account sign-up/sign-in
- Sign-out clearing of synced local tables
- Launch-time pull for basic reinstall/new-device restore

**Out of scope:**
- Realtime/live sync, conflict-resolution UI (remote always wins on pull, no merge prompts)
- A visible sync-status indicator
- Handling a second account's leftover local data from a pre-this-feature install
- Any AI-assist (F-02) work

## Architecture / Approach

Repositories (`JournalRepositoryImpl`/`HabitRepositoryImpl`) gain a Postgrest-backed
`RemoteXDataSource` dependency (fakeable, mirroring the existing DAO-fake testing pattern) plus
`AuthRepository` (for the current user id) and an app-scoped `CoroutineScope` (for fire-and-forget
pushes). A new `SyncLocalDataUseCase`/`GetLocalDataSummaryUseCase`/`ClearSyncedLocalDataUseCase`
each depend on 2-3 repositories directly (a new but rule-compliant shape for this codebase's
use cases). The review screen is one more step in `AccountViewModel`'s and `OnboardingViewModel`'s
existing auth state machines (`AccountStep`/`AccountSubStep`), not a new top-level screen —
following F-01's own precedent of duplicating a small sub-flow across two ViewModels rather than
sharing one.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. UUID migration + Postgres schema | Ids become UUIDs everywhere; empty RLS-protected tables exist | Widest blast radius (touches nav keys, every screen keyed by id) but purely mechanical |
| 2. Remote data sources + repo sync | `syncWithRemote()`/push-per-write capability, no UI yet | Getting the fakeable-interface layering right so existing repo tests still work |
| 3. Sign-out clearing | Sign-out wipes synced tables, preserves onboarding prefs | Accidentally wiping onboarding/focus data too |
| 4. Review screen + hook-in | The actual user-visible feature; PRD US-04 updated | Getting the "skip vs already-synced vs empty" branching right in two ViewModels |
| 5. Launch-time pull | Basic reinstall/new-device restore | Must stay silent/non-blocking on every cold start |

**Prerequisites:** F-01 (done), S-02/S-03 (done) — journal/habit data and auth both exist.
**Estimated effort:** Large — realistically the biggest slice in the roadmap so far; multiple
sessions across 5 phases, ahead of the Aug 31 deadline with F-02/S-07/S-08 still queued.

## Open Risks & Assumptions

- Accepting destructive local-data loss on this schema bump is fine *only* because the app has
  no real users yet — this assumption breaks the moment the app is actually distributed.
- "Remote wins, no per-row timestamp" is simple but means a local edit made while offline, not
  yet pushed, gets silently overwritten if a pull runs before the next push retry succeeds — an
  accepted gap given no conflict-resolution scope.
- The review screen re-appearing after every sign-out+sign-back-in (since sign-out resets the
  flag) is a minor repeated-prompt UX cost, accepted as simpler than per-account flag tracking.

## Success Criteria (Summary)

- Signing up/in with local data present shows accurate counts and uploads everything with no
  data loss or duplication.
- Existing local-first UX (instant saves, offline journaling) is unchanged — sync is invisible
  when it works and invisible when it silently retries.
- Reinstalling the app and signing into the same account restores prior data without a manual
  export/import step.
