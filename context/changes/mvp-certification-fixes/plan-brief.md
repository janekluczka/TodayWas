# MVP Certification Fixes — Plan Brief

> Full plan: `context/changes/mvp-certification-fixes/plan.md`

## What & Why

The 10xDevs MVP certification check (`.claude/prompts/mvp-check.md`) scored this project 2/5:
Delete is missing from CRUD (only whole-table wipe exists), there's no `test-plan.md` mapping the
test suite to defined risks, and there's no root `README.md`. This change closes all three gaps.

## Starting Point

Journal entries, habits, and habit check-ins each already support Create/Read/Update through a
consistent layered architecture (DAO → repository → use case → ViewModel → UI). Delete exists only
as `clearAll()` per DAO — a whole-table wipe used for sign-out, never a per-item user action. No
`.delete()` call exists against Supabase anywhere, and live RLS policies don't permit DELETE today.

## Desired End State

A user — signed in or not — can delete a journal entry, a habit (cascading to its check-ins), or a
single check-in, from the same screens they already view/edit them in, via a Delete icon and a
red-confirm-button warning dialog — available regardless of the item's age. A new `test-plan.md`
documents the risks the whole test suite covers. A new root `README.md` explains the project to a
cold reader.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Remote delete scope | Full support — new RLS DELETE policies + `.delete()` calls in all 3 remote data sources | Skipping it isn't just incomplete — `syncWithRemote()` pulls all remote rows back on every sync, so a locally-deleted-but-remotely-alive row gets resurrected for signed-in users |
| Delete without an account | Local delete always works, unconditionally, for a signed-out user | CLAUDE.md hard rule (FR-008): core journaling/habit-tracking must work with zero account — delete mirrors the existing Add/Update pattern where the local write never depends on sign-in status |
| EditWindow gate on delete | None — delete works regardless of entry age | User's explicit call: delete should stay available for cleanup/privacy even after the 24h window closes, unlike Update |
| Delete confirmation UX | Icon button + `DsAlertDialog`, no swipe gesture | Matches the existing Edit-icon-in-top-bar pattern and the dialog component already used for "help me refine" — no new UI primitive |
| Destructive dialog styling | `DsTextButton` gains a `colors: ButtonColors` override (mirroring `DsButton`, which already has one); each delete dialog passes an error-tinted value directly | User's explicit call, refined twice during planning: first an ambient theme-override was considered (rejected — would recolor any button shape uniformly), then a `type` enum on `DsAlertDialog` computing colors through the slot (rejected too — unnecessary, since `confirmButton` is already a plain composable slot and each caller can just pass `colors` straight to its own button) |
| Undo after confirming | None — permanent immediately | Matches the app's existing no-soft-delete simplicity bias; a soft-delete would need its own resurrection-proofing against sync on top of what we're already adding |
| Per-day check-in delete location | Inside the existing per-date edit bottom sheet (widened to also show non-editable-but-logged rows) | Reuses the screen where per-day values are already edited instead of a new UI surface |
| Habit delete confirmation copy | Names the cascading check-in count explicitly | Sets correct expectations before an irreversible, unbounded-size deletion, especially with no undo |
| test-plan.md scope | Whole existing suite (~45 files), retroactively, plus new delete tests | The certification criterion evaluates the whole suite's risk-mapping, not just new code — a delete-only doc would still fail it |

## Scope

**In scope:** per-item delete for journal entries, habits, and habit check-ins (data + domain + UI
+ tests), a `colors` override on `DsTextButton` so destructive dialogs can render a red confirm
button, the Supabase RLS change needed for remote delete to work, `context/foundation/test-plan.md`,
root `README.md`.

**Out of scope:** soft-delete/undo, bulk/multi-select delete, a new habit list/management screen,
any change to Update's existing 24h `EditWindow` gate, local Supabase migration file
infrastructure (this follows the project's existing MCP-only practice), any change to
`DsAlertDialog` itself.

## Architecture / Approach

Each entity's delete mirrors its existing Update flow layer-for-layer, so most of this change is
pattern-following, not new architecture — including working fully offline/without an account, since
that already falls out of how every existing write already works. The one genuinely new data-layer
primitive is a Supabase `.delete()` call, added once per `Remote*DataSource`. Local habit-cascade (no
Room FK exists) is two sequential DAO calls rather than a cross-DAO transaction, consistent with how
the rest of the repository layer already works. On the UI side, `DsAlertDialog`'s `confirmButton`
slot already accepts any composable, so no dialog-level change was needed — each delete dialog just
builds its own `DsTextButton` with an explicit `colors` override, which required adding that
parameter to `DsTextButton` (mirroring `DsButton`, which already has one).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Supabase RLS delete policies | Live DELETE policies on all 3 tables | Production database change — requires explicit go-ahead at implementation time |
| 2. Design system — `DsTextButton` color override | A reusable way to render any text button in Material's error color | None — single additive parameter, no existing call site changes behavior |
| 3. Journal entry delete | Full delete flow, DAO → UI, works with zero account | None major — closely mirrors Update |
| 4. Habit + check-in delete | Full delete flow for both, with local cascade, works with zero account | Widening the edit-sheet row filter is easy to get subtly wrong (must show non-editable-but-logged rows) |
| 5. `test-plan.md` | Retroactive risk-mapping doc for ~45 existing + new tests | Scope creep risk — stick to naming real risks against real test files, not padding |
| 6. `README.md` | Root README | None |

**Prerequisites:** none beyond repo access and Supabase MCP access for Phase 1.
**Estimated effort:** ~2-3 sessions — Phases 1-2 are quick, Phases 3-4 are the bulk of the work
(mirrored across 3 entities), Phases 5-6 are writing, not code.

## Open Risks & Assumptions

- Phase 1's RLS policy naming/shape assumes the existing policies follow exactly the
  `<table>_<verb>_own` / `USING (user_id = auth.uid())` pattern confirmed via direct query — if the
  live project has since changed, re-verify before applying.
- The local habit-cascade (sequential DAO calls, no transaction) leaves a narrow, low-odds window
  where check-ins are deleted but the habit delete then fails — accepted as out of scope for MVP
  transaction infrastructure, not a hidden gap.

## Success Criteria (Summary)

- A user can delete any journal entry, habit, or check-in from its detail screen, confirmed via a
  red-confirm-button dialog, with no way for it to reappear after a sync.
- This works fully for a user with zero account — no delete path depends on being signed in.
- Re-running the certification check's 5 criteria against the repo scores 5/5 (or at minimum closes
  criteria 1, 3, and 5 — the three this change targets).
