# Architecture Hardening — Plan Brief

> Full plan: `context/changes/architecture-hardening/plan.md`
> Frame brief: `context/changes/architecture-hardening/frame.md`
> Research: `context/changes/architecture-hardening/research.md`

## What & Why

Closes five technical-debt items surfaced across three prior changes but never independently
planned: no-retry background sync, ViewModels bypassing use cases for `domain/util` calls, zero
test coverage on two repositories and a mapper, a dormant remote FK cascade, and a
non-transactional sync-apply loop. `frame.md` re-examined the decisions inherited from prior
changes before this plan locked them in: the WorkManager fix and `arch-cleanup`'s no-new-abstraction
call both held up under scrutiny; item 2's originally-scoped two files turned out to be an
under-scoped compilation, not a deliberate cut — widened to all 8 call sites; and a new
repository-decomposition step was added as the foundation item 1's fix should build on, rather than
landing WorkManager inline in the current combined-responsibility class.

## Starting Point

`HabitRepositoryImpl`/`JournalRepositoryImpl` mix plain Room CRUD with sync orchestration (mutex,
fire-and-forget background push with no retry, full-reconciliation `syncWithRemote()`) in one
class each. WorkManager isn't in the project. 8 call sites across 6 files bypass
`domain/usecase/` for `domain/util` logic. `AuthRepositoryImpl`/`AiAssistRepositoryImpl`/
`HabitMapper` have zero tests. The remote `habit_check_ins.habit_id` FK still cascades.

## Desired End State

Local persistence and sync orchestration live in separate classes, connected by a WorkManager-backed
`SyncScheduler` with real connectivity-gated retry. Every `domain/util` call goes through a use
case. `AuthRepositoryImpl`, `AiAssistRepositoryImpl`, and `HabitMapper` have test coverage. The
remote FK no longer cascades, and `syncWithRemote()`'s apply step is transactional.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| WorkManager outbox mechanism | Enqueue a full `syncWithRemote()` resync per write | Reuses existing merge logic entirely; WorkManager's own persisted queue gives durability without a bespoke outbox table | Plan |
| Repository interface shape | Split into `HabitRepository`/`JournalRepository` + shared `Syncable` | Makes the CRUD-vs-sync boundary explicit at the type level, no consumer breakage | Plan |
| Local/remote data split | Extract `LocalHabitDataSource`/`LocalJournalDataSource`, not two DI-selectable repos | Hilt bindings resolve once; sign-in is a live runtime toggle, so the split must be internal composition | Frame |
| `AuthRepositoryImpl`/`AiAssistRepositoryImpl` testability | `MockEngine`-backed real `SupabaseClient`, no new abstraction | Verified viable with a live-executed test during framing; `arch-cleanup`'s decision doesn't need reopening | Frame |
| FK-cascade migration | Apply directly via Supabase MCP, no checked-in migration files | Consistent with the 4 existing migrations; introducing migration-file infra is a separate project | Plan |
| Item 2 scope | Widen from 2 files to 6 (8 call sites) | The 2-file cut was an incomplete compilation, not a deliberate decision; the other 6 sites are cheaper to fix and 2 are literally duplicated logic | Frame |
| `HabitMapper` testability | Add an explicit `today: LocalDate` parameter | Matches `ContributionMapperTest`'s fixed-time-injection convention | Plan |

## Scope

**In scope:** repository decomposition, WorkManager outbox, FK-cascade fix, transactional
sync-apply loop, 8 `domain/util` call sites across 6 files, test coverage for 2 repositories + 1
mapper.

**Out of scope:** checked-in Supabase migration infrastructure, an outbox table, changing
write-ordering (local-first vs remote-first), `RemoteAuthDataSource`/`RemoteAiAssistDataSource`,
`HabitMapper`'s actual status-derivation logic (confirmed accurate, untouched).

## Architecture / Approach

`HabitRepositoryImpl`/`JournalRepositoryImpl` become thin composing layers over a new
`Local*DataSource` (plain CRUD, mirrors the existing `Remote*DataSource` pattern) and a
`SyncScheduler` (wraps WorkManager, mirrors `TransactionRunner`'s wrap-the-framework-class
pattern). A single `SyncWorker` reuses the existing `SyncLocalDataUseCase`. Six ViewModels/mappers
gain small use-case dependencies replacing direct `domain/util` calls — three trivial wrappers
(`Clock`-only or no dependency) and two `Flow`-returning use cases for the contribution
calculators.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Repository decomposition | `Local*DataSource` + `Syncable` split, transactional apply loop | Largest diff; touches every write path in both repositories |
| 2. FK-cascade fix | Remote migration dropping the cascade | Applied directly against production schema — low risk, well-understood fix |
| 3. WorkManager outbox | Real retry/durability for background sync | New dependency + Hilt wiring; manifest misconfiguration crashes app startup |
| 4. `domain/util` → use cases | 8 call sites across 6 files wrapped | Two dual-mode ViewModels (sync initial value + reactive flow) need careful handling |
| 5. Test coverage | New tests for 2 repos + 1 mapper | `MockEngine`-backed `SupabaseClient` setup is net-new, not copied from an existing exemplar |

**Prerequisites:** none beyond what's already in the repo — no external service changes besides
the Phase 2 Supabase migration.
**Estimated effort:** ~5 sessions across 5 phases; Phase 1 and Phase 3 are the largest.

## Open Risks & Assumptions

- WorkManager/Hilt-Work version pins (2.11.2 / 1.2.0) were confirmed current via web search during
  planning — re-verify if implementation starts significantly later.
- Phase 1's `Local*DataSource` extraction is a large mechanical diff; no behavior change is
  intended, but every write path in both repositories is touched, so regression risk is
  concentrated there — the phase's manual verification step matters more than usual.
- Phase 3's offline/force-stop manual verification steps require deliberately degrading network
  conditions on a real device/emulator — budget time for this rather than treating it as optional.

## Success Criteria (Summary)

- All five original debt items are closed and verified per-phase.
- No regressions in core journaling/habit-tracking (FR-008: must keep working with zero account).
- A signed-in write reaches remote even when made offline or immediately followed by a force-stop.
