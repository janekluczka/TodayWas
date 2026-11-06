# Entry Delete — Plan Brief

> Full plan: `context/changes/entry-delete/plan.md`

## What & Why

The 10xDevs MVP certification check (`.claude/prompts/mvp-check.md`) flagged that Delete is missing
from CRUD — journal entries, habits, and habit check-ins only support Create/Read/Update, with
Delete existing solely as a whole-table wipe used for sign-out. This change adds real per-item
delete for all three. (Split off from the original combined `mvp-certification-fixes` change — the
retroactive `test-plan.md` work moved to `architecture-hardening`, and the root `README.md` is a
separate small standalone item.)

## Starting Point

Journal entries, habits, and habit check-ins each already support Create/Read/Update through a
consistent layered architecture (DAO → repository → use case → ViewModel → UI). Delete exists only
as `clearAll()` per DAO — a whole-table wipe used for sign-out, never a per-item user action. No
`.delete()` call exists against Supabase anywhere, and live RLS policies don't permit DELETE today.

## Desired End State

A user — signed in or not — can delete a journal entry, a habit (cascading to its check-ins), or a
single check-in, from the same screens they already view/edit them in, via a Delete icon and a
red-confirm-button warning dialog — available regardless of the item's age.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Remote delete scope | Full support — new RLS DELETE policies + `.delete()` calls in all 3 remote data sources | Skipping it isn't just incomplete — `syncWithRemote()` pulls all remote rows back on every sync, so a locally-deleted-but-remotely-alive row gets resurrected for signed-in users |
| Delete without an account | Local delete always works, unconditionally, for a signed-out user | CLAUDE.md hard rule (FR-008): core journaling/habit-tracking must work with zero account — delete mirrors the existing Add/Update pattern where the local write never depends on sign-in status |
| EditWindow gate on delete | None — delete works regardless of entry age | User's explicit call: delete should stay available for cleanup/privacy even after the 24h window closes, unlike Update |
| Delete confirmation UX | Icon button + `DsAlertDialog`, no swipe gesture | Matches the existing Edit-icon-in-top-bar pattern and the dialog component already used for "help me refine" — no new UI primitive |
| Destructive dialog styling | `DsTextButton` gains a `colors: ButtonColors` override (mirroring `DsButton`, which already has one); each delete dialog passes an error-tinted value directly | `DsAlertDialog`'s `confirmButton` is already a plain composable slot, so no dialog-level `type` parameter is needed — each caller just recolors its own button |
| Undo after confirming | None — permanent immediately | Matches the app's existing no-soft-delete simplicity bias |
| Per-day check-in delete location | Inside the existing per-date edit bottom sheet (widened to also show non-editable-but-logged rows) | Reuses the screen where per-day values are already edited instead of a new UI surface |
| Habit delete confirmation copy | Names the cascading check-in count explicitly | Sets correct expectations before an irreversible, unbounded-size deletion, especially with no undo |
| Weak `syncMutex` push pattern | Reused as-is for delete's background push, not fixed here | The fix belongs in `architecture-hardening`, which touches add/update/delete's push call sites together rather than making delete inconsistent with the other two |

## Scope

**In scope:** per-item delete for journal entries, habits, and habit check-ins (data + domain + UI
+ tests), a `colors` override on `DsTextButton` so destructive dialogs can render a red confirm
button, the Supabase RLS change needed for remote delete to work.

**Out of scope:** `context/foundation/test-plan.md` and root `README.md` (separate changes),
soft-delete/undo, bulk/multi-select delete, a new habit list/management screen, any change to
Update's existing 24h `EditWindow` gate, local Supabase migration file infrastructure, any change to
`DsAlertDialog` itself, fixing the `syncMutex`/`tryLock()` background-push pattern (carried over
as-is; see `architecture-hardening`).

## Architecture / Approach

Each entity's delete mirrors its existing Update flow layer-for-layer, so most of this change is
pattern-following, not new architecture — including working fully offline/without an account, since
that already falls out of how every existing write already works. The one genuinely new data-layer
primitive is a Supabase `.delete()` call, added once per `Remote*DataSource`. Local habit-cascade (no
Room FK exists) is two sequential DAO calls rather than a cross-DAO transaction, consistent with how
the rest of the repository layer already works.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Supabase RLS delete policies | Live DELETE policies on all 3 tables | Production database change — requires explicit go-ahead at implementation time |
| 2. Design system — `DsTextButton` color override | A reusable way to render any text button in Material's error color | None — single additive parameter, no existing call site changes behavior |
| 3. Journal entry delete | Full delete flow, DAO → UI, works with zero account | None major — closely mirrors Update |
| 4. Habit + check-in delete | Full delete flow for both, with local cascade, works with zero account | Widening the edit-sheet row filter is easy to get subtly wrong (must show non-editable-but-logged rows) |

**Prerequisites:** none beyond repo access and Supabase MCP access for Phase 1.
**Estimated effort:** ~1-2 sessions — Phases 1-2 are quick, Phases 3-4 are the bulk of the work
(mirrored across 3 entities).

## Open Risks & Assumptions

- Phase 1's RLS policy naming/shape assumes the existing policies follow exactly the
  `<table>_<verb>_own` / `USING (user_id = auth.uid())` pattern confirmed via direct query — if the
  live project has since changed, re-verify before applying.
- The local habit-cascade (sequential DAO calls, no transaction) leaves a narrow, low-odds window
  where check-ins are deleted but the habit delete then fails — accepted as out of scope for MVP
  transaction infrastructure, not a hidden gap.
- Delete's background remote push reuses the existing `syncMutex`/`tryLock()` pattern, which
  `architecture-hardening`'s research flagged as weak (a skipped push has no guaranteed retry). This
  plan doesn't fix it — see that change instead.

## Success Criteria (Summary)

- A user can delete any journal entry, habit, or check-in from its detail screen, confirmed via a
  red-confirm-button dialog, with no way for it to reappear after a sync.
- This works fully for a user with zero account — no delete path depends on being signed in.
