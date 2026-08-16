# Entry Delete Implementation Plan

## Overview

Add per-item delete for journal entries, habits, and habit check-ins — closing the CRUD gap found
by the 10xDevs MVP certification check (`.claude/prompts/mvp-check.md`): Delete only exists today as
a whole-table wipe, never a per-item user action.

## Current State Analysis

Journal entries, habits, and habit check-ins each support Create/Read/Update, but Delete only
exists as a whole-table `clearAll()` per DAO, wired solely to sign-out/local-data-clear
(`JournalRepositoryImpl.clearLocal()`, `HabitRepositoryImpl` lines 123-124) — never a per-item
user action. No `.delete()` call exists anywhere against Supabase, and the live project's RLS
policies (`journal_entries`, `habits`, `habit_check_ins`, confirmed via direct query) only cover
`SELECT`/`INSERT`/`UPDATE` — a remote delete call would silently affect 0 rows today.

### Key Discoveries:

- `JournalRepositoryImpl.syncWithRemote()` (`app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt:68-78`)
  pushes all local rows via `upsert`, then pulls **all** remote rows back and upserts them
  locally. A row deleted only locally, with no matching remote delete, gets **resurrected** on the
  next sync for a signed-in user. Remote delete is therefore a correctness requirement, not
  optional polish, once we ship delete at all.
- The remote FK `habit_check_ins.habit_id → habits.id` is already `ON DELETE CASCADE` (confirmed
  via `pg_constraint`), so deleting a habit remotely auto-removes its check-ins. Room has no FK
  between `HabitEntity` and `HabitCheckInEntity` at all, so the local cascade must be done by hand.
- `UpdateJournalEntryUseCase`/`UpdateHabitCheckInUseCase` are the templates for the new Delete use
  cases: thin wrappers around a repository call, with `EditWindow.isEditable` as the only gate —
  Delete drops that gate per the scope decision below.
- `HabitDetailScreen`'s edit bottom sheet (`app/src/main/java/pl/luczka/todaywas/ui/habit/detail/HabitDetailScreen.kt:193`,
  `editableRows = uiState.rows.filter { it.eligibleForEdit }`) only lists rows within the 24h edit
  window. Since check-in delete has no such window, this filter must widen (see Critical
  Implementation Details) or older check-ins would be undeletable from the UI.
- No local Supabase migration files exist anywhere in the repo (`supabase/` only holds the
  `ai-proxy` edge function) — all schema/RLS changes so far were applied directly to the live
  project via the Supabase MCP `apply_migration` tool (see
  `context/archive/2026-08-10-account-creation-and-sync/plan.md`, Phase 1). This change follows the
  same practice rather than introducing local migration files as a new convention.
- Every repository write (`addEntry`, `updateEntry`, `HabitRepositoryImpl`'s equivalents) already
  does the local DB write unconditionally and only *attempts* a remote push when
  `authRepository.currentUserId() != null` (`JournalRepositoryImpl.kt:87-88`) — a signed-out user's
  writes already work fully offline today. Delete follows the exact same shape, so "delete works
  without an account" falls out of mirroring the existing pattern rather than needing new logic.
- `DsAlertDialog.kt` (`core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/dialogs/DsAlertDialog.kt`)
  already takes `confirmButton`/`dismissButton` as plain `@Composable () -> Unit` slots — full
  flexibility, no dialog-level styling to fight. `DsButton` already exposes a `colors: ButtonColors`
  override (`DsButton.kt:22`) but `DsTextButton` doesn't, which is the only real gap: the 3 new
  delete dialogs all use `DsTextButton` for their confirm action (matching the dialog's own existing
  preview, which already uses `DsTextButton` for both buttons), so giving `DsTextButton` the same
  `colors` parameter `DsButton` already has is the entire design-system change needed — each dialog
  passes an error-tinted `ButtonColors` directly to its own confirm button, no changes to
  `DsAlertDialog` itself.

## Desired End State

A user can delete a journal entry, a habit (which also removes its check-ins), or a single habit
check-in, from the same detail screens they already view/edit them in — via a Delete icon and a
red-confirm-button warning dialog, available regardless of the entry's age, with no way for a
deleted row to reappear after a sync. **This works fully for a user with zero account**, per
CLAUDE.md's hard rule (FR-008) — delete is a local DB write first, with the remote side only ever
attempted as a background best-effort push when signed in; a signed-out user's delete never touches
the network and never blocks on it.

Verification: `ktlintCheck`, `testDebugUnitTest`, `assembleDebug` all green; manually delete one of
each entity type in a running build and confirm it disappears from the main screen and does not
return after a manual sync (signed-in device).

## What We're NOT Doing

- No `context/foundation/test-plan.md` or root `README.md` — split into separate changes
  (`architecture-hardening` and a small standalone README item, respectively).
- No soft-delete/undo grace period — confirmed decision: delete is permanent immediately once the
  confirmation dialog is accepted; the dialog is the only safety net.
- No bulk/multi-select delete — one item at a time, matching the existing one-item-at-a-time detail
  screens.
- No local Supabase migration file infrastructure — the RLS change uses the same MCP
  `apply_migration` mechanism as all prior schema changes on this project.
- No new "habit list/management" screen — habit delete lives on the existing `HabitDetailScreen`.
- No change to the 24h `EditWindow` gate on Update — Delete is intentionally unrestricted by age,
  Update stays exactly as it is.
- No `type` parameter or enum added to `DsAlertDialog`, and no change to its `confirmButton` slot
  signature — it already accepts a plain composable, so each delete dialog just builds its own
  `DsTextButton` with an explicit `colors` override (see Phase 2). `DsAlertDialog.kt` itself is not
  touched by this change.

## Implementation Approach

Mirror the existing Update flow's layering (DAO → repository → use case → ViewModel → UI) for each
of the three entities, adding the one genuinely new primitive — a remote `.delete()` call — once
per `Remote*DataSource`. The Supabase RLS change lands first as its own phase, since app code that
calls remote delete is inert (and unverifiable) until the policies exist. Delete is never gated by
`EditWindow` (per the confirmed scope decision), which actually simplifies the use-case layer
relative to Update. Habit deletion's local cascade (deleting check-ins, then the habit) is done as
two sequential DAO calls, not a Room cross-DAO transaction — consistent with how the rest of the
repository layer already operates (no `withTransaction` usage exists anywhere in this codebase
today), and the failure window it leaves (check-ins deleted, habit delete then fails) is a low-odds,
recoverable local-SQLite edge case, not worth new transaction infrastructure for an MVP.

**Note on the follow-up `architecture-hardening` change**: that change's research flagged the same
`syncMutex`/`tryLock()` background-push pattern this plan reuses for delete pushes as a weak spot
(a skipped push has no guaranteed retry). This plan intentionally still mirrors that existing
pattern rather than fixing it here — the fix (and its tests) belongs in `architecture-hardening`,
which touches all push call sites (add/update/delete) at once rather than making delete's push
subtly inconsistent with add/update's.

## Critical Implementation Details

**Ordering: RLS before remote delete calls.** Phase 3 and Phase 4's `RemoteXDataSource.delete()`
calls will compile and run before Phase 1's RLS policies exist, but will silently no-op (0 rows
affected, not an error — Postgres RLS filters rows out rather than raising) until Phase 1 is live.
Phase 1 must be applied (and confirmed via query) before Phase 3/4's manual remote-sync verification
step can meaningfully pass. This ordering is independent of the local-delete path, which works with
or without Phase 1 (see the zero-account note above).

**Widen the habit edit-sheet row filter.** `HabitDetailScreen.kt`'s bottom sheet currently only
shows rows where `eligibleForEdit` is true (within the 24h window). Since check-in delete has no
time restriction, the sheet's row filter must become `it.eligibleForEdit || it.alreadyLogged` so a
check-in outside the edit window still appears (its value editor disabled via `eligibleForEdit`,
but its new delete icon enabled via `alreadyLogged`). Without this, older check-ins would be
deletable in the repository/use-case layer but unreachable from the UI.

**Supabase delete call shape.** No `.delete()` call exists anywhere in this codebase yet. It follows
the same `postgrest.from(TABLE)` + `filter { eq(...) }` shape already used by `fetchAll`:
```kotlin
supabase.postgrest.from(TABLE).delete { filter { eq("id", id) } }
```

## Phase 1: Supabase RLS delete policies

### Overview

Add `DELETE` RLS policies for `journal_entries`, `habits`, and `habit_check_ins` on the live
Supabase project, matching the existing `SELECT`/`INSERT`/`UPDATE` policy naming and shape
(`<table>_delete_own`, `USING (user_id = auth.uid())`). This is a production database change — stop
and get explicit confirmation before applying, even though it was already discussed during
planning.

### Changes Required:

#### 1. RLS policies for all three tables

**Target**: live Supabase project (via `mcp__supabase__apply_migration`, no local file — same
practice as every prior schema change on this project).

**Intent**: Allow a signed-in user to delete their own rows from all three synced tables, matching
the ownership scoping already used by the existing `SELECT`/`INSERT`/`UPDATE` policies.

**Contract**: One migration adding three policies, following the confirmed naming convention
(`habits_select_own`, `habits_insert_own`, `habits_update_own` → `habits_delete_own`, etc.):

```sql
CREATE POLICY "journal_entries_delete_own" ON journal_entries
  FOR DELETE USING (user_id = auth.uid());

CREATE POLICY "habits_delete_own" ON habits
  FOR DELETE USING (user_id = auth.uid());

CREATE POLICY "habit_check_ins_delete_own" ON habit_check_ins
  FOR DELETE USING (user_id = auth.uid());
```

### Success Criteria:

#### Automated Verification:

- Migration applies cleanly via `mcp__supabase__apply_migration` (no error returned)
- A direct query against `pg_policies` (via `mcp__supabase__execute_sql`) shows all three
  `*_delete_own` policies present, `cmd = 'DELETE'`, `qual` containing `auth.uid()`

#### Manual Verification:

- Explicit user go-ahead obtained before the migration is applied (production database change)
- `mcp__supabase__get_advisors` shows no new security warnings introduced by the policies

---

## Phase 2: Design system — `DsTextButton` color override

### Overview

Add a `colors` override to `DsTextButton` so the three delete dialogs (Phases 3-4) can render their
confirm button in Material's error color. `DsAlertDialog` needs no change at all — `confirmButton`
is already a plain `@Composable () -> Unit` slot, so each dialog builds its own `DsTextButton` with
the colors it wants, same as any other caller-supplied slot content.

### Changes Required:

#### 1. `DsTextButton` gets an explicit `colors` override

**File**: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/buttons/DsTextButton.kt`

**Intent**: Mirror `DsButton`'s existing `colors: ButtonColors = ButtonDefaults.buttonColors()`
parameter (`DsButton.kt:22`) so `DsTextButton` can be recolored by a caller the same way.

**Contract**: Add `colors: ButtonColors = ButtonDefaults.textButtonColors()` and pass it through to
the underlying `TextButton(colors = colors, ...)` call. Purely additive — every existing call site
(including `HelpMeRefineDialog`'s `DsTextButton` usages) keeps its current default appearance
without any change on its side.

### Success Criteria:

#### Automated Verification:

- Lint passes: `./gradlew.bat ktlintCheck`
- Build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- No standalone check needed here — the override is exercised and visually verified as part of
  Phase 3's manual verification (the journal-delete dialog's red confirm button)

---

## Phase 3: Journal entry delete

### Overview

Add per-item delete for journal entries end-to-end: DAO, remote data source, repository, use case,
and a Delete action + confirmation dialog on `JournalEntryDetailScreen`.

### Changes Required:

#### 1. Local delete

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/dao/JournalEntryDao.kt`

**Intent**: Allow deleting a single journal entry by id, alongside the existing whole-table
`clearAll()`.

**Contract**: `@Query("DELETE FROM journal_entries WHERE id = :id") suspend fun deleteById(id: String)`.

#### 2. Remote delete

**File**: `app/src/main/java/pl/luczka/todaywas/data/remote/api/RemoteJournalDataSource.kt` and
`RemoteJournalDataSourceImpl.kt`

**Intent**: Mirror `upsert`/`fetchAll`'s shape for a delete call.

**Contract**: `suspend fun delete(id: String): Result<Unit>` on the interface; the impl wraps the
`postgrest.from(TABLE).delete { filter { eq("id", id) } }` call (see Critical Implementation
Details) in the existing `remoteCall {}` helper, same as `upsert`/`fetchAll`.

#### 3. Repository

**File**: `app/src/main/java/pl/luczka/todaywas/domain/repository/JournalRepository.kt` and
`app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt`

**Intent**: Add `deleteEntry(id)` following the exact local-write-then-background-push shape of
`updateEntry` — `safeDbCall { dao.deleteById(id) }`, then (on success) a background push that skips
entirely if signed out, `tryLock`s the existing `syncMutex`, and silently swallows failure (same
retry-on-next-sync philosophy as `pushInBackground`). Unlike `updateEntry`, the background push here
only needs the `id`, not the full entity.

**Contract**: `suspend fun deleteEntry(id: String): Result<Unit>` on the interface, no
`EditWindow`-related comment (unlike `updateEntry`) since delete is never gated.

#### 4. Use case

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/DeleteJournalEntryUseCase.kt`

**Intent**: Thin pass-through to `repository.deleteEntry(id)`, matching this codebase's
one-use-case-per-action convention (`[[feedback_usecase_depends_on_repository_only]]`) even though
there's no gate to enforce — kept for architectural consistency with every other ViewModel-facing
action, and so the "no EditWindow gate" decision is visible/explicit at the use-case layer rather
than only in the repository.

**Contract**: `class DeleteJournalEntryUseCase @Inject constructor(private val repository: JournalRepository) { suspend operator fun invoke(id: String): Result<Unit> }`.

#### 5. UI

**File**: `app/src/main/java/pl/luczka/todaywas/ui/journal/detail/` (`JournalEntryDetailIntent.kt`,
`JournalEntryDetailUiState.kt`, `JournalEntryDetailViewModel.kt`, `JournalEntryDetailScreen.kt`)

**Intent**: Add a Delete icon in `JournalEntryDetailActions` (shown unconditionally, unlike the Edit
icon which is gated by `isEditable` — delete has no such gate, and works with zero account per the
Desired End State above). Tapping it opens a `DsAlertDialog` confirmation whose `confirmButton`
builds a `DsTextButton` with `colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)`
(Phase 2's new parameter), giving it a red confirm button; the dialog otherwise mirrors the
`HelpMeRefineDialog` usage already in this file. Confirming calls the new use case and, on success,
sends the existing `NavigatedBack` event (no new event type needed — deleting and navigating back is
the same outcome as the existing back action). On failure, reuse the existing snackbar mechanism
with a new `journal_detail_delete_error` string.

**Contract**: New intents `DeleteClicked`, `DeleteConfirmed`, `DeleteDismissed` on
`JournalEntryDetailIntent`; new `isDeleteDialogVisible: Boolean` and `isDeleting: Boolean` fields on
`JournalEntryDetailUiState`; new string resources `journal_detail_delete_action`,
`journal_detail_delete_dialog_title`, `journal_detail_delete_dialog_text`,
`journal_detail_delete_confirm_cta`, `journal_detail_delete_cancel_cta`,
`journal_detail_delete_error` in `strings.xml`, following the existing `journal_detail_*` naming
convention.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (new `JournalEntryDaoTest`,
  `JournalRepositoryImplTest` — including a signed-out case asserting `deleteEntry` succeeds
  locally with zero calls to `FakeRemoteJournalDataSource` — `DeleteJournalEntryUseCaseTest`,
  `JournalEntryDetailViewModelTest` cases green, no regressions in existing cases)
- Lint passes: `./gradlew.bat ktlintCheck`
- Build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Create a journal entry, open it, delete it via the new icon + confirmation dialog (red confirm
  button) — it disappears from the entry list and the main screen's contribution cell for that day
- Delete an entry older than 24h (past its edit window) — deletion still succeeds (no EditWindow
  gate)
- **Signed out, no account**: create and delete a journal entry — succeeds entirely offline, no
  network call attempted (verifiable by airplane mode or by confirming no request appears in the
  Supabase project's logs for that action)
- While signed in with Phase 1's policies live: delete an entry, trigger a manual sync, confirm the
  entry does not reappear

---

## Phase 4: Habit and habit check-in delete

### Overview

Add per-item delete for habits (cascading to their check-ins) and for individual habit check-ins,
end-to-end: DAO, remote data source, repository, use cases, and UI on `HabitDetailScreen`.

### Changes Required:

#### 1. Local delete

**File**: `app/src/main/java/pl/luczka/todaywas/data/local/dao/HabitDao.kt` and
`HabitCheckInDao.kt`

**Intent**: Per-row delete for both entities, plus a by-habit bulk delete for check-ins to support
the local cascade.

**Contract**: `HabitDao`: `@Query("DELETE FROM habits WHERE id = :id") suspend fun deleteById(id: String)`.
`HabitCheckInDao`: `@Query("DELETE FROM habit_check_ins WHERE id = :id") suspend fun deleteById(id: String)`
and `@Query("DELETE FROM habit_check_ins WHERE habitId = :habitId") suspend fun deleteByHabitId(habitId: String)`.

#### 2. Remote delete

**File**: `app/src/main/java/pl/luczka/todaywas/data/remote/api/RemoteHabitDataSource.kt`/`Impl.kt`
and `RemoteHabitCheckInDataSource.kt`/`Impl.kt`

**Intent**: Same shape as journal's remote delete. The habit-side call only needs to delete the
habit row — the live `ON DELETE CASCADE` FK removes its check-ins remotely, so no separate remote
bulk-delete-by-habit call is needed (asymmetric with the local side, which has no FK and must do it
by hand).

**Contract**: `suspend fun delete(id: String): Result<Unit>` added to both interfaces/impls, same
`remoteCall { postgrest.from(TABLE).delete { filter { eq("id", id) } } }` shape as journal.

#### 3. Repository

**File**: `app/src/main/java/pl/luczka/todaywas/domain/repository/HabitRepository.kt` and
`app/src/main/java/pl/luczka/todaywas/data/repository/HabitRepositoryImpl.kt`

**Intent**: `deleteHabit(id)` deletes local check-ins for that habit first, then the habit itself
(sequential `safeDbCall`s, not a cross-DAO transaction — see Implementation Approach), then
background-pushes only the habit's remote delete (cascade handles the rest remotely).
`deleteCheckIn(habitId, date)` looks up the existing row via `habitCheckInDao.getByHabitAndDate`
(same lookup `updateCheckIn` already does) to get its `id`, deletes it locally, then
background-pushes the remote delete by that `id`.

**Contract**: `suspend fun deleteHabit(id: String): Result<Unit>` and
`suspend fun deleteCheckIn(habitId: String, date: LocalDate): Result<Unit>` added to the interface,
no `EditWindow`-related comment on either (never gated, unlike `updateCheckIn`).

#### 4. Use cases

**File**: `app/src/main/java/pl/luczka/todaywas/domain/usecase/DeleteHabitUseCase.kt` and
`DeleteHabitCheckInUseCase.kt`

**Intent**: Same thin pass-through pattern as `DeleteJournalEntryUseCase`, one per action per this
codebase's convention.

**Contract**: `DeleteHabitUseCase`: `suspend operator fun invoke(id: String): Result<Unit>`.
`DeleteHabitCheckInUseCase`: `suspend operator fun invoke(habitId: String, date: LocalDate): Result<Unit>`.

#### 5. UI — delete habit

**File**: `app/src/main/java/pl/luczka/todaywas/ui/habit/detail/` (`HabitDetailIntent.kt`,
`HabitDetailUiState.kt`, `HabitDetailViewModel.kt`, `HabitDetailScreen.kt`)

**Intent**: Add a Delete icon to `HabitDetailActions` (shown unconditionally, next to the
conditionally-shown Edit icon; works with zero account, same as journal delete). Confirmation
dialog gives its confirm `DsTextButton` the same error-tinted `colors` override as Phase 3's
journal-delete dialog (Phase 2's new parameter) and names the cascade explicitly — per the confirmed
decision, its text includes the check-in count, read from `uiState.rows.count { it.alreadyLogged }`
(already loaded client-side, no new query needed). Confirming calls `DeleteHabitUseCase` and, on
success, sends the existing `NavigatedBack` event.

**Contract**: New intents `DeleteHabitClicked`, `DeleteHabitConfirmed`, `DeleteHabitDismissed` on
`HabitDetailIntent`; new `isDeleteHabitDialogVisible: Boolean` and `isDeletingHabit: Boolean` fields
on `HabitDetailUiState`; new strings `habit_detail_delete_action`,
`habit_detail_delete_dialog_title`, a `habit_detail_delete_dialog_text_format` taking the check-in
count as a `%1$d` placeholder (matching the existing `habit_detail_today_suffix_format` pattern for
formatted strings), `habit_detail_delete_confirm_cta`, `habit_detail_delete_cancel_cta`,
`habit_detail_delete_error`.

#### 6. UI — delete individual check-in

**File**: same files as above

**Intent**: Widen the edit bottom sheet's row filter (see Critical Implementation Details) so
already-logged rows outside the edit window still appear, then add a per-row delete icon next to
the value editor, enabled whenever `alreadyLogged` is true (independent of `eligibleForEdit`).
Tapping it opens the same-shaped confirmation dialog (same error-tinted confirm-button `colors`)
scoped to that one date; confirming calls `DeleteHabitCheckInUseCase` and removes the row from
local state without closing the whole bottom sheet (the user may be deleting/editing multiple days
in one sheet session).

**Contract**: New intents `DeleteCheckInClicked(date: LocalDate)`, `DeleteCheckInConfirmed`,
`DeleteCheckInDismissed` on `HabitDetailIntent`; new `checkInPendingDelete: LocalDate?` and
`isDeletingCheckIn: Boolean` fields on `HabitDetailUiState`; new strings
`habit_detail_delete_checkin_action`, `habit_detail_delete_checkin_dialog_title`,
`habit_detail_delete_checkin_dialog_text`, reusing the existing `_confirm_cta`/`_cancel_cta`/`_error`
strings added for habit delete where wording is identical.

### Success Criteria:

#### Automated Verification:

- Unit tests pass: `./gradlew.bat testDebugUnitTest` (new `HabitDaoTest`, `HabitCheckInDaoTest`,
  `HabitRepositoryImplTest` — including signed-out cases for both `deleteHabit` and `deleteCheckIn`
  asserting zero calls to the fake remote data sources — `DeleteHabitUseCaseTest`,
  `DeleteHabitCheckInUseCaseTest`, `HabitDetailViewModelTest` cases green, no regressions)
- Lint passes: `./gradlew.bat ktlintCheck`
- Build succeeds: `./gradlew.bat assembleDebug`

#### Manual Verification:

- Create a habit, log two check-ins (one today, one backdated past 24h), delete one check-in via
  the edit sheet — it disappears from the contribution grid; the other check-in is untouched
- Delete a check-in older than 24h — succeeds (no EditWindow gate), confirming the widened row
  filter actually surfaces it
- Delete the habit itself — confirmation dialog shows the correct check-in count and a red confirm
  button; after confirming, the habit and all its check-ins disappear from the main screen
- **Signed out, no account**: create a habit, log a check-in, delete both — succeeds entirely
  offline, no network call attempted
- While signed in with Phase 1's policies live: delete a habit, trigger a manual sync, confirm
  neither the habit nor its check-ins reappear (validates the remote CASCADE assumption end-to-end)

---

## Testing Strategy

### Unit Tests:

- Every new DAO `deleteById`/`deleteByHabitId` method gets a Robolectric-backed DAO test (insert
  then delete then assert absence), mirroring the existing `*DaoTest` structure.
- Every new repository method gets a test with the fake remote data sources
  (`FakeRemoteJournalDataSource`, etc. already exist) covering: success path, **signed-out/no-account
  (local delete succeeds, zero remote calls attempted — the FR-008 zero-account guarantee)**, and
  habit-cascade ordering (check-ins removed before the habit).
- `DsTextButton`'s new `colors` parameter is pure Compose styling — covered by
  `ktlintCheck`/`assembleDebug`, not a dedicated unit test (no existing precedent for testing
  Compose color output in this codebase).
- Every new use case gets a test confirming it has no `EditWindow` gate (delete succeeds for an
  old `createdAt`) — the explicit contrast with `UpdateJournalEntryUseCaseTest`/
  `UpdateHabitCheckInUseCaseTest`'s expiry tests.
- Every new ViewModel delete flow gets a test for: dialog visibility toggling, success →
  `NavigatedBack` emitted, failure → error state set and dialog/screen stays.
- All new tests follow the `` `should [outcome] when [scenario]` `` naming and AAA-comment
  structure from `context/foundation/lessons.md`.

### Manual Testing Steps:

1. Delete a journal entry, a habit, and a habit check-in from a running debug build (each phase's
   manual verification above), confirming each shows a red-confirm-button warning dialog.
2. Repeat step 1 as a signed-out user with no account — every delete must succeed fully offline.
3. With Phase 1 live and a signed-in test account, verify no deleted row reappears after a manual
   sync for any of the three entity types.
4. Confirm deletion works identically for entries both inside and outside the 24h edit window
   (contrast with Update, which is blocked outside the window).

## References

- Related certification check: `.claude/prompts/mvp-check.md`
- Update-flow precedent: `app/src/main/java/pl/luczka/todaywas/domain/usecase/UpdateJournalEntryUseCase.kt`,
  `app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt:57-66`
- Zero-account precedent: `app/src/main/java/pl/luczka/todaywas/data/repository/JournalRepositoryImpl.kt:87-88`
- Existing `colors` param precedent: `core/designsystem/src/main/java/pl/luczka/todaywas/core/designsystem/components/buttons/DsButton.kt:22`
- Prior Supabase schema change (same MCP-only practice): `context/archive/2026-08-10-account-creation-and-sync/plan.md`
- Follow-up change (weak-spot audit + fixes + tests, including this plan's carried-over sync-lock
  concern): `context/changes/architecture-hardening/`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles.

### Phase 1: Supabase RLS delete policies

#### Automated

- [x] 1.1 Migration applies cleanly via `mcp__supabase__apply_migration`
- [x] 1.2 `pg_policies` query confirms all three `*_delete_own` policies present

#### Manual

- [x] 1.3 Explicit user go-ahead obtained before applying (production database change)
- [x] 1.4 `mcp__supabase__get_advisors` shows no new security warnings

### Phase 2: Design system — `DsTextButton` color override

#### Automated

- [ ] 2.1 Lint passes (`ktlintCheck`)
- [ ] 2.2 Build succeeds (`assembleDebug`)

#### Manual

- [ ] 2.3 No standalone check — exercised visually as part of Phase 3's manual verification

### Phase 3: Journal entry delete

#### Automated

- [ ] 3.1 Unit tests pass (`testDebugUnitTest`)
- [ ] 3.2 Lint passes (`ktlintCheck`)
- [ ] 3.3 Build succeeds (`assembleDebug`)

#### Manual

- [ ] 3.4 Create, open, delete an entry via icon + warning confirmation dialog — disappears from list/main screen
- [ ] 3.5 Delete an entry older than 24h — succeeds (no EditWindow gate)
- [ ] 3.6 Signed out, no account: create + delete an entry — succeeds fully offline
- [ ] 3.7 Signed in, Phase 1 live: delete + manual sync — entry does not reappear

### Phase 4: Habit and habit check-in delete

#### Automated

- [ ] 4.1 Unit tests pass (`testDebugUnitTest`)
- [ ] 4.2 Lint passes (`ktlintCheck`)
- [ ] 4.3 Build succeeds (`assembleDebug`)

#### Manual

- [ ] 4.4 Delete one of two check-ins (one backdated) via edit sheet — only that one disappears
- [ ] 4.5 Delete a check-in older than 24h — succeeds, confirming widened row filter works
- [ ] 4.6 Delete a habit — warning dialog shows correct check-in count; habit + check-ins disappear
- [ ] 4.7 Signed out, no account: create a habit + check-in, delete both — succeeds fully offline
- [ ] 4.8 Signed in, Phase 1 live: delete habit + manual sync — habit and check-ins do not reappear
