---
project: "TodayWas"
version: 1
status: draft
created: 2026-07-25
updated: 2026-08-11
prd_version: 1
main_goal: speed
top_blocker: time
---

# Roadmap: TodayWas

> Derived from `context/foundation/prd.md` (v1) + auto-researched codebase baseline.
> Edit-in-place; archive when superseded.
> Slices below are listed in dependency order. The "At a glance" table is the index.

## Vision recap

TodayWas replaces the effort-vs-guilt tradeoff of existing journaling/habit apps with low-effort
daily reflection, optional habit logging, and a GitHub-contribution-style history view that rewards
presence over time without punishing missed days. The app works fully offline with no account
required; an optional account adds cross-device sync and an AI-assisted writing prompt.

## North star

**S-02: User journals about their day and sees it on the main screen** — the smallest end-to-end
flow that proves the core hypothesis (presence-based tracking without streak guilt), since the
Vision frames daily journaling as the primary act and habit tracking as optional.

> A reader-facing note on what "north star" means here: the smallest end-to-end slice whose
> successful delivery would prove the core product hypothesis — placed as early as its
> Prerequisites allow because everything else only matters if this works.

## At a glance

| ID   | Change ID                    | Outcome (user can …)                                              | Prerequisites  | PRD refs             | Status   |
| ---- | ----------------------------- | ------------------------------------------------------------------ | -------------- | --------------------- | -------- |
| F-01 | supabase-auth-foundation       | (foundation) Supabase project + email/Google sign-in scaffold live | —              | FR-007, Access Control section | done    |
| S-01 | onboarding-focus-pick          | complete onboarding and pick a focus (journaling / habits / both)  | —              | FR-001                | done    |
| S-02 | journal-daily-entry            | add a daily journal entry and see it on the main screen            | S-01           | US-01, FR-003, FR-005, FR-008 | done |
| S-03 | habit-create-and-checkin       | create a habit and log check-ins for it, seen on the main screen   | S-01           | US-02, FR-002, FR-004, FR-005, FR-008 | done |
| S-04 | edit-within-24h-window         | edit a recent journal entry or habit check-in within 24 hours      | S-02, S-03     | US-03, FR-006         | done |
| S-05 | detailed-contribution-history  | drill into a detailed contribution history for a journal or habit  | S-02, S-03     | FR-011                | done |
| S-06 | account-creation-and-sync      | create an account and have existing local data sync automatically  | F-01, S-02, S-03 | US-04, FR-007        | done |
| F-02 | ai-assist-proxy-foundation     | (foundation) Server-side AI proxy live, JWT-verified via Supabase  | F-01           | FR-009, FR-010, NFR (journal content never reaches AI-assisted features) | proposed |
| S-07 | ai-starter-prompt              | tap "help me start" and get a tone-matched AI starter prompt       | F-02, S-02     | US-05, FR-009         | proposed |
| S-08 | ai-refine-entry                | tap "help me refine" and get AI-assisted refinement of an entry    | F-02, S-02     | FR-010                | proposed |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still lives in
the dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme               | Chain                                             | Note                                                                 |
| ------ | -------------------- | -------------------------------------------------- | --------------------------------------------------------------------- |
| A      | Core loop            | `S-01` → `S-02` / `S-03` (parallel) → `S-04` / `S-05` (parallel) | Contains the north star (`S-02`); all must-have slices, sequenced first given the `speed` goal. |
| B      | Account & sync        | `F-01` → `S-06`                                    | Independent of Stream A until `S-06`, which joins Stream A at `S-02`/`S-03`. |
| C      | AI assist             | `F-02` → `S-07` / `S-08` (parallel)                | Joins Stream B at `F-01` and Stream A at `S-02`; lowest priority — `S-08` traces to a nice-to-have FR. |

## Baseline

What's already in place in the codebase as of `2026-07-25` (auto-researched + user-confirmed).
Foundations below assume these are present and do NOT re-scaffold them.

- **Frontend:** partial — Jetpack Compose + Material3 BOM scaffolded (`MainActivity.kt`,
  `ui/theme/*`), no journaling/habit screens built yet.
- **Backend / API:** absent — no Supabase client dependency or Edge Function code in this repo yet.
- **Data:** absent — no Room dependency, no entities/DAOs/database class.
- **Auth:** absent — no Supabase Auth or Google Sign-In wiring.
- **Deploy / infra:** absent — no `.github/workflows`; no CI/CD pipeline wired yet.
- **Observability:** absent — no logging/crash-reporting/metrics library beyond default Logcat.

## Foundations

### F-01: Supabase auth scaffold

- **Outcome:** (foundation) A real Supabase project exists with email/Google sign-in wired into the
  app and a minimal session-check contract established — no user-facing screen beyond a functioning
  sign-in flow.
- **Change ID:** supabase-auth-foundation
- **PRD refs:** FR-007, Access Control section
- **Unlocks:** S-06 (account creation + sync), F-02 (AI-assist proxy needs a live session to verify
  against)
- **Prerequisites:** —
- **Parallel with:** S-01
- **Blockers:** Requires a real Supabase project + credentials (Phase 0 of
  `context/changes/deployment/deployment-plan.md`) — human-only step, not agent-executable.
- **Unknowns:** —
- **Risk:** Sequenced independently of the core loop (Stream A) so the human-only account-creation
  step doesn't block journaling/habit work; if delayed, only S-06/F-02/S-07/S-08 stall, not the
  north star.
- **Status:** done

### F-02: AI-assist proxy foundation

- **Outcome:** (foundation) A server-side Supabase Edge Function is deployed that calls Gemini
  Flash and verifies the caller's Supabase session (`verify_jwt = true`) — no Gemini key ever ships
  in the APK. No user-facing screen; this is the callable contract S-07/S-08 build on.
- **Change ID:** ai-assist-proxy-foundation
- **PRD refs:** FR-009, FR-010, NFR (journal content never reaches AI-assisted features)
- **Unlocks:** S-07 (AI starter prompt), S-08 (AI refine)
- **Prerequisites:** F-01
- **Parallel with:** S-04, S-05, S-06
- **Blockers:** Requires a real Gemini API key (human-only account signup), same class of
  prerequisite as F-01's Supabase project.
- **Unknowns:** —
- **Risk:** The privacy NFR (journal content must never reach the AI layer) is a correctness gate
  on this Foundation's implementation, not an optional hardening pass — scoped minimal but not
  skippable.
- **Status:** proposed

## Slices

### S-01: Onboarding and focus pick

- **Outcome:** User completes onboarding and picks a focus (journaling, habit tracking, or both).
- **Change ID:** onboarding-focus-pick
- **PRD refs:** FR-001
- **Prerequisites:** —
- **Parallel with:** F-01
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Smallest possible first slice; sequenced first because the focus pick likely shapes what
  the main screen shows next (journal-only, habit-only, or both).
- **Status:** done

### S-02: Daily journal entry (north star)

- **Outcome:** User adds a daily journal entry, independent of any habit tracking, and sees it
  reflected on the main screen as a colored contribution cell.
- **Change ID:** journal-daily-entry
- **PRD refs:** US-01, FR-003, FR-005, FR-008
- **Prerequisites:** S-01
- **Parallel with:** S-03
- **Blockers:** —
- **Unknowns:** —
- **Risk:** This is the north star — placed immediately after the only slice it depends on so the
  core hypothesis gets validated as early as possible, per the `speed` goal.
- **Status:** done

### S-03: Habit creation and check-in

- **Outcome:** User creates a habit (binary or scale value) and logs check-ins for it, independent
  of journaling, seen on the main screen as a colored contribution cell.
- **Change ID:** habit-create-and-checkin
- **PRD refs:** US-02, FR-002, FR-004, FR-005, FR-008
- **Prerequisites:** S-01
- **Parallel with:** S-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Habit creation (FR-002) and check-in (FR-004/US-02) are combined into one slice since
  a check-in cannot exist without a habit to log against — the same "create + use" coupling the
  skill treats as one slice rather than two.
- **Status:** done

### S-04: Edit within the 24-hour window

- **Outcome:** User opens a journal entry or habit check-in created less than 24 hours ago and edits
  it; after 24 hours it's viewable but read-only.
- **Change ID:** edit-within-24h-window
- **PRD refs:** US-03, FR-006
- **Prerequisites:** S-02, S-03
- **Parallel with:** S-05, S-06
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Needs both journal and habit entities to exist first since the edit window applies
  identically to both — building it earlier would mean editing nothing.
- **Status:** done

### S-05: Detailed contribution history

- **Outcome:** User drills down from the main screen into a detailed contribution-style history for
  a single journal or habit.
- **Change ID:** detailed-contribution-history
- **PRD refs:** FR-011
- **Prerequisites:** S-02, S-03
- **Parallel with:** S-04, S-06
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Depends on both entity types existing so the detail view has real data to render for
  either journal or habit.
- **Status:** done

### S-06: Account creation and auto-sync

- **Outcome:** User creates an account (email or Google) and all existing local journal/habit data
  automatically uploads/syncs, with no manual export/import step.
- **Change ID:** account-creation-and-sync
- **PRD refs:** US-04, FR-007
- **Prerequisites:** F-01, S-02, S-03
- **Parallel with:** S-04, S-05, F-02
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Needs local journal and habit data to actually exist (US-04's Given clause presupposes
  it) plus the auth scaffold — sequenced after both core-loop slices and F-01.
- **Status:** done

### S-07: AI starter prompt

- **Outcome:** User taps "help me start" on a journal entry, picks a tone on a 5-point scale,
  optionally adds a few thoughts, and gets a tone-matched AI-generated prompt inserted as an
  editable starting point (regenerable up to 3 times).
- **Change ID:** ai-starter-prompt
- **PRD refs:** US-05, FR-009
- **Prerequisites:** F-02, S-02
- **Parallel with:** S-06, S-08
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Gated behind sign-in (FR-009) and the AI proxy Foundation — cannot be built or verified
  before F-02 lands.
- **Status:** proposed

### S-08: AI-assisted refinement

- **Outcome:** User taps "help me refine" on a journal entry they've written and gets AI-assisted
  refinement of it.
- **Change ID:** ai-refine-entry
- **PRD refs:** FR-010
- **Prerequisites:** F-02, S-02
- **Parallel with:** S-06, S-07
- **Blockers:** —
- **Unknowns:**
  - Should this stay in the 3-week MVP scope given the `time` top blocker, or ship as a post-deadline
    stretch goal? — Owner: user. Block: no.
- **Risk:** Lowest-priority slice (FR-010 is nice-to-have, unlike every other slice); sequenced last
  so it never displaces must-have work under time pressure.
- **Status:** proposed

## Backlog Handoff

| Roadmap ID | Change ID                    | Suggested issue title                                    | Ready for `/10x-plan` | Notes                                  |
| ---------- | ----------------------------- | ---------------------------------------------------------- | ---------------------- | ---------------------------------------- |
| F-01       | supabase-auth-foundation       | Set up Supabase project + email/Google sign-in scaffold    | yes                     | Run `/10x-plan supabase-auth-foundation` |
| S-01       | onboarding-focus-pick          | Onboarding: pick a focus (journaling / habits / both)      | yes                     | Run `/10x-plan onboarding-focus-pick`    |
| S-02       | journal-daily-entry            | Add a daily journal entry, shown on the main screen         | no                      | Blocked on S-01                          |
| S-03       | habit-create-and-checkin       | Create a habit and log check-ins                            | no                      | Blocked on S-01                          |
| S-04       | edit-within-24h-window         | Edit journal entries / habit check-ins within 24 hours      | no                      | Blocked on S-02, S-03                    |
| S-05       | detailed-contribution-history  | Detailed contribution history view for a journal or habit   | no                      | Blocked on S-02, S-03                    |
| S-06       | account-creation-and-sync      | Account creation with automatic local-data sync             | no                      | Blocked on F-01, S-02, S-03              |
| F-02       | ai-assist-proxy-foundation     | Deploy Supabase Edge Function AI proxy (JWT-verified)        | no                      | Blocked on F-01                          |
| S-07       | ai-starter-prompt              | "Help me start" AI-generated tone-matched prompt             | no                      | Blocked on F-02, S-02                    |
| S-08       | ai-refine-entry                | "Help me refine" AI-assisted entry refinement                | no                      | Blocked on F-02, S-02                    |

This table is the clean handoff to Jira/Linear or any MCP-backed backlog. Include one row for every
`F-NN` and `S-NN`. It should be compact enough to copy into issues, but it must not duplicate the
detailed roadmap body.

## Open Roadmap Questions

None outstanding — mirrors PRD's `## Open Questions` (none). The one live scope question (whether
S-08 stays in the 3-week window) is slice-local and recorded under S-08's Unknowns, not here.

## Parked

- **Social/sharing features** — Why parked: PRD `## Non-Goals` — single-player, private experience
  by design (no sharing, following, or public profiles).
- **Real-time multi-device sync / conflict resolution** — Why parked: PRD `## Non-Goals` — only
  basic account backup/restore is in scope, not live multi-device collaboration.
- **Gamification (streaks, badges, points, leaderboards)** — Why parked: PRD `## Non-Goals` — the
  contribution view is the only consistency signal by design; this is a core product bet, not an
  oversight.
- **Custom recommendation/scoring ML model** — Why parked: PRD `## Non-Goals` — AI-assisted features
  (FR-009, FR-010) lean on off-the-shelf AI (Gemini Flash) rather than an in-house model.

## Done

(Empty on first generation. `/10x-archive` appends an entry here — and flips that item's `Status`
to `done` — when a change whose `Change ID` matches the item is archived.)

- **S-01: User completes onboarding and picks a focus (journaling, habit tracking, or both).** — Archived 2026-07-27 → `context/archive/2026-07-25-onboarding-focus-pick/`. Lesson: —.
- **S-02: User adds a daily journal entry, independent of any habit tracking, and sees it reflected on the main screen as a colored contribution cell.** — Archived 2026-08-01 → `context/archive/2026-07-27-journal-daily-entry/`. Lesson: —.
- **S-03: User creates a habit (binary or scale value) and logs check-ins for it, independent of journaling, seen on the main screen as a colored contribution cell.** — Archived 2026-08-01 → `context/archive/2026-07-31-habit-create-and-checkin/`. Lesson: —.
- **S-04: User opens a journal entry or habit check-in created less than 24 hours ago and edits it; after 24 hours it's viewable but read-only.** — Archived 2026-08-02 → `context/archive/2026-08-01-edit-within-24h-window/`. Lesson: —.
- **S-05: User drills down from the main screen into a detailed contribution-style history for a single journal or habit.** — Archived 2026-08-03 → `context/archive/2026-08-02-detailed-contribution-history/`. Lesson: —.
- **F-01: (foundation) Supabase project + email/Google sign-in scaffold live.** — Archived 2026-08-10 → `context/archive/2026-08-03-supabase-auth-foundation/`. Lesson: —.
- **S-06: User creates an account (email or Google) and all existing local journal/habit data automatically uploads/syncs, with no manual export/import step.** — Archived 2026-08-11 → `context/archive/2026-08-10-account-creation-and-sync/`. Lesson: —.
