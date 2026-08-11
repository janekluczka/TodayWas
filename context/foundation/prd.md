---
project: "TodayWas"
version: 1
status: draft
created: 2026-07-24
context_type: greenfield
product_type: mobile
target_scale:
  users: large
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 3
  hard_deadline: 2026-08-31
  after_hours_only: true
---

## Vision & Problem Statement

Journaling and habit tracking today force a tradeoff between effort (long-form journals) and
pressure (streak-based trackers that punish gaps with guilt). TodayWas removes both frictions:
low-effort daily reflection that still accommodates users who want to write more, optional habit
logging, and a GitHub-contribution-style history view — shown per journal and per habit — that
displays presence over time without penalizing missed days.

The insight: consistency tracking doesn't need to be gamified with streaks that reset to zero and
guilt-trip on a miss. A presence-based history (like a contribution graph) rewards showing up over
time without punishing the days you don't.

## User & Persona

People who want to start or maintain a daily journaling habit, and/or want to stick to habits
they're tracking, seeking a lightweight way to do that on their own device. The common thread is
valuing simplicity and non-judgmental consistency tracking over rigor or gamification — someone who
has bounced off heavier journal apps or pressure-inducing streak trackers before.

## Success Criteria

### Primary

- A user can complete onboarding, create a habit, journal about their day and separately log a habit
  check-in — as two independent actions, neither requiring the other — see each reflected on the
  main screen as its own colored contribution cell, and edit either. Entirely local, no account
  required.

### Secondary

- Optional email/Google account creation and cloud sync of data works reliably.

### Guardrails

- Local on-device data is never lost, corrupted, or silently deleted without user consent.
- Core journaling/habit-tracking is never gated behind forced account creation or login.

## User Stories

### US-01: User journals about their day

- **Given** a user with no account
- **When** they add a journal entry for today, independent of any habit tracking
- **Then** the main screen shows the new journal entry and a colored cell for today on the journal's
  contribution view

#### Acceptance Criteria

- The journal entry is saved locally without requiring sign-in and without requiring any habit to be
  logged
- The main screen reflects the update immediately after saving
- The user can later open the entry to edit it within the allowed edit window

### US-02: User logs a habit check-in

- **Given** a user with at least one tracked habit and no account
- **When** they log a check-in/value for that habit, independent of journaling
- **Then** the main screen shows a colored cell for that day on the habit's contribution view

#### Acceptance Criteria

- The habit check-in is saved locally without requiring sign-in and without requiring a journal
  entry
- The main screen reflects the update immediately after saving
- The user can later open the habit to edit the check-in within the allowed edit window

### US-03: User edits a recent entry within the edit window

- **Given** a journal entry or habit check-in created less than 24 hours ago
- **When** the user opens it
- **Then** they can edit and save changes

#### Acceptance Criteria

- Edits are only permitted within 24 hours of creation
- After 24 hours, the entry/check-in is still viewable but read-only (no edit controls)
- The edit window applies independently to each entry/check-in

### US-04: User creates an account after using the app locally

- **Given** a user with existing local journal entries and habit data, and no account
- **When** they create an account (email or Google)
- **Then** all existing local data automatically uploads/syncs to the account, with no manual
  export/import step

#### Acceptance Criteria

- No existing data is lost or duplicated during the upload
- After creating (or signing into) the account, the user sees a one-time review screen showing
  how much local data exists (journal entries, habits, check-ins) and confirms before it
  uploads — no separate export/import step is required beyond that one confirmation
- The main screen shows the same data before and after account creation

### US-05: User gets an AI-generated starter prompt

- **Given** a user creating a new journal entry
- **When** they tap "help me start", pick a tone on a 5-point scale (very bad / bad / neutral / good
  / very good), and optionally type a few thoughts
- **Then** a prompt reflecting that tone (and incorporating their thoughts, if given) is generated
  and inserted into the entry as an editable starting point

#### Acceptance Criteria

- Tone is a 5-point scale, not just good/bad/neutral
- The optional thoughts field is used as input to the generated prompt when provided
- Regenerate is available up to 3 times per entry, after which regeneration is disabled for that
  entry
- The inserted prompt remains fully editable — a starting point, not locked text

## Functional Requirements

### Onboarding

Onboarding has no focus-pick step. The main screen always shows both journaling and habit tracking
together (see FR-005), so a new user sees the app's full tracking capability right away rather than
committing to one before trying it. Onboarding is Welcome → optional account setup → All set.

### Journaling

- FR-003: User can add a daily journal entry (free text). Priority: must-have
  > Socrates: Counter-argument considered: a blank text box has its own "blank-page problem" and
  > doesn't fully solve for low effort. Resolution: kept as the base capability; the blank-page
  > problem is addressed separately by FR-009 (AI starter prompt) and FR-010 (AI refinement), not by
  > changing FR-003 itself.

### Habit Tracking

- FR-002: User can create a new habit to track, choosing binary (0/1) or a scale value. Priority:
  must-have
  > Socrates: No counter-argument raised; stands as written.
- FR-004: User can log a check-in/value for a tracked habit, for today or a past date (not
  restricted to same-day entry). Priority: must-have
  > Socrates: Counter-argument considered: requiring same-day logging recreates the pressure of
  > streak-based trackers. Resolution: revised — logging is not restricted to the current day; users
  > can complete yesterday's (or an earlier) journal/habit entries retroactively.

### Main Screen & History

- FR-005: User can view a main screen showing a simple/compact overview of journal and habit
  activity. Priority: must-have
  > Socrates: Counter-argument considered: a full contribution grid per journal AND per every habit
  > could clutter the main screen as habit count grows. Resolution: revised — main screen stays
  > simple/compact; detailed per-journal/per-habit contribution views move to a separate capability
  > (FR-011).
- FR-011: User can view a detailed contribution-style history for a single journal or habit, drilled
  down from the main screen. Priority: must-have

### Editing

- FR-006: User can open a past entry or habit to view and edit its details, within 24 hours of
  creation; after that it remains viewable but read-only. Priority: must-have
  > Socrates: Counter-argument considered: freely editable history could undermine the authenticity
  > of a journal record. Resolution: kept editing, but bounded — edits are only allowed within a
  > limited time window after the entry is created, not indefinitely.

### Account

- FR-007: User can optionally create an account (email or Google) to save/sync data. Priority:
  must-have
  > Socrates: Counter-argument considered: even optional auth (email + Google OAuth) is real backend
  > complexity for a 3-week MVP. Resolution: kept; accepted because it materially improves long-term
  > usability (safe data across devices/reinstalls) despite the added build cost.
- FR-008: User's data (journal + habits) persists locally without requiring an account. Priority:
  must-have
  > Socrates: Counter-argument considered: without a mandatory backup, a user who never creates an
  > account loses everything on device loss/reset. Resolution: kept; risk explicitly accepted as the
  > tradeoff for not forcing account creation — mitigated by FR-007 being available but optional.

### AI Assistance

- FR-009: User can tap "help me start" on a journal entry, choose a tone on a 5-point scale (very
  bad/bad/neutral/good/very good), optionally add a few thoughts, and receive an AI-generated prompt
  personalized to that tone and the optional thoughts given — not dependent on the user's journal
  history or habit data. Prompt can be regenerated up to 3 times per entry. Requires a signed-in
  account (email or Google, per FR-007); unavailable to users without one. Priority: must-have
  > Socrates: Counter-argument considered: gating an assistive feature behind login adds friction
  > for users who haven't created an account yet. Resolution: kept — the account requirement lets
  > the AI proxy rely on Supabase's own session verification for access control, rather than a
  > separate app-level secret; core journaling/habit-tracking (FR-003, FR-002/FR-004) remain fully
  > available with no account, so this doesn't touch the app's core no-login guarantee.
- FR-010: User can tap "help me refine" on a journal entry they've written to get AI-assisted
  refinement. Requires a signed-in account, same as FR-009. Priority: nice-to-have

## Non-Functional Requirements

- A user's journal content is never accessible to anyone other than the user — not shared with third
  parties, and never read by AI-assisted features beyond what the user actively submits to them. For
  "help me start" (FR-009), that's only the tone pick and any optional thoughts typed in the moment.
  For "help me refine" (FR-010), that additionally includes the text of the single entry the user is
  actively refining — sent only for that one request, never any other entry, never journal history,
  and never logged or reused beyond that request.

## Business Logic

TodayWas computes each day's contribution intensity relative to the user's own historical range for
that specific journal or habit, and — only when the user taps "help me start" or "help me refine" —
presents a 5-point tone scale (very bad, bad, neutral, good, very good) to choose from, then
generates a journal-entry prompt personalized to the chosen tone and any optional thoughts the user
provides. The AI prompt generation does not read the user's journal history or habit-tracking data —
its only inputs are the chosen tone and, for "help me start", the optional thoughts typed in the
moment, or, for "help me refine", the text of the single entry actively being refined.

Inputs are the user's own logged values for a given journal or habit (for intensity calculation,
never data from other users or a fixed global scale) and, separately, the tone pick plus — for
"help me start" — optional thoughts typed at the moment of the request, or — for "help me refine" —
the text of the single entry actively being refined (for prompt personalization — no other journal
history involved). Output is (a) a per-day, per-habit/journal color intensity reflecting where that day
falls in the user's own range, and (b) an on-demand, tone-matched journal prompt or refinement. The
user encounters (a) on the main screen and detail views as colored cells, and (b) only when
explicitly requesting help via "help me start" or "help me refine" on a journal entry — never shown
automatically or unprompted.

## Access Control

The app is usable without login — data lives on-device (local, no server required). An optional
account (email or Google sign-in) can be created to save/sync progress and data. Core journaling and
habit-tracking capabilities are identical regardless of signed-in status — no roles, no tiers on the
core experience. The one exception is AI-assistance (FR-009, FR-010): "help me start" and "help me
refine" require a signed-in account, since the server-side AI proxy authenticates callers via the
user's Supabase session rather than any separate mechanism. A signed-out user can still journal and
track habits fully; they just don't see the AI-assist entry points until they sign in.

## Non-Goals

- No social/sharing features — single-player, private experience (no sharing entries, following, or
  public profiles).
- No cross-device sync beyond basic account backup/restore — no real-time multi-device collaboration
  or conflict resolution.
- No gamification beyond the contribution view — no streaks, badges, points, or leaderboards.
- No custom recommendation/scoring algorithm — AI-assisted features (FR-009, FR-010) lean on
  off-the-shelf AI capability rather than an in-house ML model.

## Open Questions

None outstanding.
