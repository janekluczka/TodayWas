# Architecture Cleanup — Plan Brief

> Full plan: `context/changes/arch-cleanup/plan.md`

## What & Why

`data/local/` and `data/repository/` are each one flat package holding everything from Room
entities to Supabase DTOs to repository interfaces — 40 files with no internal grouping. Repository
interfaces also live in `data/` instead of `domain/`, inverting the normal dependency direction.
This plan splits both into responsibility-scoped subpackages (entity/dao/database,
remote-dto/remote-api, repository/mapper/util) and moves repository interfaces + business-rule
calculators into `domain/`. Purely structural — no behavior change.

## Starting Point

166 main-source files + ~35 mirrored test files under `pl.luczka.todaywas`, all compiling and
passing today. `data/local/` (8 files) and `data/repository/` (32 files) are flat single packages.
`domain/model/` mixes real models with 3 stateless calculator objects. Two mapper files and one
util file sit loose inside `ui/habit/` and `ui/auth/` instead of a dedicated subpackage.

## Desired End State

Every file lands in a package named after what it actually is — entities, DAOs, the database
module, remote DTOs, remote data sources, repository impls, mappers, generic call-wrappers, domain
models, use cases, domain utils, repository interfaces, and two feature-scoped UI subpackages. The
app builds, lints, and passes its full existing test suite exactly as before — same behavior, better
organized.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
|---|---|---|---|
| Domain calculators (`EditWindow`, `HabitContributionCalculator`, `JournalContributionCalculator`) | Move to new `domain/util/` | They're stateless business-rule algorithms, not data models | Plan |
| `EditWindowExpiredException.kt` | Stays in `domain/model/` | Matches the existing convention of `AuthError.kt`/`AiAssistError.kt` bundling exceptions with models | Plan |
| `AuthRepositoryImpl`/`AiAssistRepositoryImpl` calling `SupabaseClient` directly | Leave as-is, just relocate | Adding new `Remote*DataSource` abstractions would be a design change, not a move — out of scope | Plan |
| Repository interfaces | New `domain/repository/` package | Mirrors the existing `domain/model/`+`domain/usecase/` subpackage convention; standard Clean Architecture placement | Plan |
| `Remote*DataSource` class names | Unchanged, only relocated to `data/remote/api/` | Renaming would ripple into every call site/binding/fake for no structural benefit; "DataSource" is also the more accurate name (they wrap Postgrest tables, not a REST API) | Plan |
| `ui/habit` mapper files (`HabitDetailMapper`, `LogHabitCheckInsMapper`) | Nest in `ui/habit/mapper/`, stay feature-scoped | Their target row-UiState types are feature-owned, not shared — promoting to `ui/model/` would invert the usual dependency direction | Plan |
| `AuthFormValidation.kt` | Nest in `ui/auth/util/` | It's validation/formatting logic, not a domain→UI mapper, so it doesn't belong in `ui/model/` | Plan |
| CLAUDE.md | Update Project Structure section as the final phase | It documents the old flat layout; leaving it stale would mislead the next session | Plan |
| `DatabaseModule.kt`/`RepositoryModule.kt` (Hilt modules) | Move to `di/`, not a `data/` subpackage | `di/` already holds `ClockModule`/`CoroutineScopeModule`/`SupabaseModule` — every Hilt module belongs in one place, not scattered inside `data/` | User |

## Scope

**In scope:** Directory/package restructuring of `data/`, `domain/`, and the two flagged `ui/`
files; mirrored test-file moves; CLAUDE.md's Project Structure section.

**Out of scope:** Any behavior/logic change; renaming `Remote*DataSource` classes; extracting new
remote-data-source abstractions for Auth/AiAssist; splitting `AuthError`/`AiAssistError`'s bundled
exception shape; `domain/usecase/`, `:core:designsystem`, or any other `ui/<feature>/` package not
named above; new tests.

## Architecture / Approach

Six phases, each a self-contained group of file moves: (1) `data/local` split, (2) `data/remote`
split, (3) `domain/repository` + `domain/util` extraction (widest blast radius — touches every use
case and repo impl), (4) `data/repository` consolidation into impls + `mapper/` + `util/`, (5)
`ui/habit/mapper` + `ui/auth/util`, (6) CLAUDE.md update + full verification. Each file's `package`
line is updated to match its new directory, then every importer across `:app` is fixed. Each phase
ends with `ktlintFormat` → `ktlintCheck` → `testDebugUnitTest` → `assembleDebug` before moving on.

## Phases at a Glance

| Phase | What it delivers | Key risk |
|---|---|---|
| 1. `data/local` split | entity/dao/database subpackages, `DatabaseModule` → `di/` | DAO↔entity cross-imports missed |
| 2. `data/remote` split | dto/api subpackages | Impl→util (`RemoteCall`) import breaks until Phase 4 lands |
| 3. `domain/repository` + `domain/util` | Interfaces + calculators relocated | Widest blast radius — every use case, most ViewModels |
| 4. `data/repository` consolidation | mapper/ + util/ extracted, impls trimmed, `RepositoryModule` → `di/` | Mapper-function call sites scattered across 5 impls |
| 5. `ui/habit/mapper` + `ui/auth/util` | Two feature subpackages | Screen/ViewModel call-site imports for moved mapper functions |
| 6. Docs + full verification | CLAUDE.md updated, full suite green | None — verification-only |

**Prerequisites:** Clean working tree on `feature/arch-cleanup` (already created). No schema,
dependency, or config changes needed first.
**Estimated effort:** ~200 files touched across 6 phases; mechanical but wide — each phase is
independently buildable and testable.

## Open Risks & Assumptions

- A scripted find/replace on fully-qualified import lines is assumed for efficiency, but the build
  (not the script) is the actual verification — every phase ends with a full compile + test run.
- `HabitDetailScreen.kt`/`HabitDetailViewModel.kt` and their Log-check-ins counterparts aren't
  confirmed by file-read to be the only callers of the two `ui/habit` mapper extension functions —
  Phase 5's implementer should grep for `toHabitDetailRows(` / `.toRows(` to catch any other caller.

## Success Criteria (Summary)

- `./gradlew.bat ktlintCheck testDebugUnitTest assembleDebug` all pass after every phase and at the
  end.
- Every moved file's `git diff` shows only `package`/`import` line changes — zero logic diffs.
- CLAUDE.md's Project Structure section matches the plan's Desired End State.
