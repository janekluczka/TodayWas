# Architecture Cleanup — Package Restructuring Implementation Plan

## Overview

`app/src/main/java/pl/luczka/todaywas/data/` is currently two flat packages —
`data/local/` (8 files: entities, DAOs, database, Hilt module all mixed together) and
`data/repository/` (32 files: repository interfaces, impls, remote data sources, remote DTOs,
entity/DTO mappers, error/state mappers, and generic call-wrapper utils all mixed together).
Repository interfaces also live in `data/`, not `domain/`, inverting the usual Clean Architecture
dependency direction (domain should define the contract; data should implement it). `domain/model/`
similarly mixes real data-shape models with three stateless business-rule "calculator" objects.
Two `ui/habit/` files and one `ui/auth/` file are domain→UI mapper/util logic sitting loose in
their feature package instead of a dedicated subpackage.

This plan splits each of those flat packages into responsibility-scoped subpackages, moves the 5
repository interfaces into a new `domain/repository/` package, and moves the 3 domain calculators
into a new `domain/util/` package — a purely structural refactor with **no behavior change**. Every
touched file's package declaration is updated to match its new directory, and every importer across
the `:app` module is updated to match. `app/src/test/` mirrors `app/src/main/` 1:1 today and keeps
doing so after this change — test files (including `Fake*` test doubles) move alongside the
main-source files they cover.

## Current State Analysis

- `data/local/` (package `pl.luczka.todaywas.data.local`, 8 files): 4 `@Entity` classes, 4 `@Dao`
  interfaces, 1 `@Database` (`TodayWasDatabase`), 1 Hilt `@Module` (`DatabaseModule`) providing the
  database + DAOs.
- `data/repository/` (package `pl.luczka.todaywas.data.repository`, 32 files): 5 repository
  interfaces, 5 repository impls, 3 remote-data-source interfaces + 3 impls (Supabase Postgrest
  wrappers for Habit/HabitCheckIn/Journal), 5 remote DTOs, 6 mapper files (3 entity↔domain, 3
  dto↔domain), 3 more mapper-shaped files (`AuthErrorMapper`, `AuthStateMapper`,
  `AiAssistErrorMapper`), 2 generic call-wrapper utils (`RemoteCall`, `SafeDbCall`), and the Hilt
  `RepositoryModule` (`@Binds` for all 5 repos + all 3 remote data sources).
- `AuthRepositoryImpl` and `AiAssistRepositoryImpl` call `SupabaseClient` directly (`supabase.auth.*`,
  `supabase.functions.invoke(...)`) rather than going through a `Remote*DataSource` like
  Habit/Journal — confirmed by reading both files. This asymmetry is **out of scope**; both impls
  move as-is, unchanged internally.
- `domain/model/` (18 files) mixes real data-shape models with `EditWindow.kt`,
  `HabitContributionCalculator.kt`, and `JournalContributionCalculator.kt` — stateless
  business-rule/algorithm objects, not data models. `EditWindowExpiredException.kt` is a standalone
  exception with no sealed-error type alongside it, unlike `AuthError.kt`/`AiAssistError.kt` which
  each already bundle a sealed model + an exception type in one file — that bundling convention is
  left untouched.
- `ui/habit/HabitDetailMapper.kt` and `ui/habit/LogHabitCheckInsMapper.kt` map domain types
  (`HabitCheckIn`, `Habit`, `HabitCheckInBoard`) to `HabitDetailRowUiState` /
  `HabitCheckInRowUiState` — both of which are declared inside the feature's own
  `HabitDetailUiState.kt` / `LogHabitCheckInsUiState.kt`, not as standalone shared types. Confirmed
  by reading all four files.
- `ui/auth/AuthFormValidation.kt` holds plain validation functions
  (`isValidEmail`/`isValidPassword`/`isValidRepeatPassword`) plus an
  `AuthErrorUiState.message()` `@Composable` string-mapping extension — not a domain→UI mapper.
- No path-sensitive build config exists (`app/build.gradle.kts` has no `sourceSets` block, no
  jacoco, no ktlint path scoping; `.editorconfig` scopes only by `*.{kt,kts}` extension) — confirmed
  by direct research. Moving files/packages needs no `build.gradle.kts` or `.editorconfig` changes.
- `app/src/test/java/pl/luczka/todaywas/` mirrors `app/src/main/.../` 1:1 today (every test file's
  package path matches its main-source counterpart's, including the 8 `Fake*` test doubles that
  live in `data/repository/` mirroring the interfaces they fake) — confirmed by direct research.
- CLAUDE.md's "Project Structure" section documents the current flat layout
  (`data/local/`, `data/repository/` — repository interfaces + impls, entity↔domain mappers) and
  will be stale the moment this plan lands unless updated.

## Desired End State

`app/src/main/java/pl/luczka/todaywas/` has:

```
data/
  local/
    entity/      — HabitEntity, HabitCheckInEntity, JournalEntryEntity, UserPreferencesEntity
    dao/         — HabitDao, HabitCheckInDao, JournalEntryDao, UserPreferencesDao
    database/    — TodayWasDatabase
  remote/
    dto/         — HabitRemoteDto, HabitCheckInRemoteDto, JournalEntryRemoteDto,
                   AiPromptRequestDto, AiPromptResponseDto
    api/         — RemoteHabitDataSource(+Impl), RemoteHabitCheckInDataSource(+Impl),
                   RemoteJournalDataSource(+Impl)
  repository/    — AuthRepositoryImpl, HabitRepositoryImpl, JournalRepositoryImpl,
                   OnboardingRepositoryImpl, AiAssistRepositoryImpl
  mapper/        — HabitEntityMapper, HabitCheckInEntityMapper, JournalEntryEntityMapper,
                   HabitRemoteMapper, HabitCheckInRemoteMapper, JournalEntryRemoteMapper,
                   AuthErrorMapper, AuthStateMapper, AiAssistErrorMapper
  util/          — RemoteCall, SafeDbCall
di/              — DatabaseModule, RepositoryModule (joins ClockModule, CoroutineScopeModule,
                   SupabaseModule — all Hilt modules live here, none embedded in data/ subpackages)
domain/
  model/         — unchanged contents minus the 3 calculators (18 → 15 files)
  usecase/       — unchanged (not in scope)
  util/          — EditWindow, HabitContributionCalculator, JournalContributionCalculator
  repository/    — AuthRepository, HabitRepository, JournalRepository, OnboardingRepository,
                   AiAssistRepository
ui/
  model/         — UI models only (UiState/Ui types), incl. new ContributionUiState.kt
                   (ContributionGridUiState, ContributionWindowUiState, ContributionGridType,
                   split out of the old ContributionMapper.kt)
  mapper/        — every domain→UI mapper function formerly in ui/model/ (HabitMapper,
                   JournalEntryMapper, ContributionMapper, AuthStateMapper, AuthErrorUiStateMapper,
                   AiAssistErrorUiStateMapper, JournalDateSlotMapper, JournalPromptToneMapper,
                   LocalDataSummaryMapper)
  habit/
    create/      — CreateHabitIntent/UiEvent/UiState/Screen/ViewModel
    detail/      — HabitDetailIntent/UiEvent/UiState/Screen/ViewModel/HabitDetailMapper (flat — a
                   nested mapper/ subpackage was dropped as unnecessary for a single mapper file)
    logcheckin/  — LogHabitCheckInsIntent/UiEvent/UiState/Screen/ViewModel/LogHabitCheckInsMapper (flat)
  journal/
    create/      — AddJournalEntryIntent/UiEvent/UiState/Screen/ViewModel, HelpMeStartStep/UiState
    detail/      — JournalEntryDetailIntent/UiEvent/UiState/Screen/ViewModel, HelpMeRefineStep/UiState
  auth/
    util/        — AuthFormValidation
    (rest unchanged)
  (all other ui/<feature>/ packages unchanged)
```

`app/src/test/java/pl/luczka/todaywas/` mirrors the above exactly, including `Fake*` test doubles
moving to wherever the interface/class they fake now lives.

**Verification of the end state**: `./gradlew.bat ktlintCheck testDebugUnitTest assembleDebug` all
pass with zero source changes to any test's assertions — every moved file's *content* (beyond
`package`/`import` lines) is byte-for-byte unchanged. `git diff --stat` after the whole change should
show renames (`R`), not deletions+additions with logic diffs, for every moved file.

## What We're NOT Doing

- No behavior changes to any class — no logic, no signature changes beyond package moves.
- No new abstractions: `AuthRepositoryImpl` and `AiAssistRepositoryImpl` keep calling
  `SupabaseClient` directly; no `RemoteAuthDataSource`/`RemoteAiAssistDataSource` is introduced.
- No renaming of the `Remote*DataSource` classes (e.g. no `HabitApi` rename) — `data/remote/api/`
  is a directory name, not a type-naming convention change.
- No splitting of `AuthError.kt`/`AiAssistError.kt`'s bundled model+exception shape.
- No touching `domain/usecase/`, `:core:designsystem`, `ui/onboarding/`, `ui/account/`,
  `ui/datasync/`, `MainActivity.kt`, or `TodayWasApplication.kt` beyond import-line fixes where they
  reference a moved type. (`ui/main/` gets import-line fixes only, no restructuring; `ui/journal/`
  and `di/` are explicitly in scope per Phases 6–8 and Phases 1/4 respectively.)
- No new tests. Existing tests are the regression safety net — same assertions, same coverage,
  just relocated alongside the code they test.

## Implementation Approach

Each phase moves a self-contained group of files: change each file's `package` declaration to match
its new directory, physically move the file (prefer `git mv` to preserve rename history in `git
log --follow`), then fix every importer across `app/src/main/` and `app/src/test/` that references
a moved type. Given the number of files, a scripted repo-wide find/replace on fully-qualified import
lines (e.g. `pl.luczka.todaywas.data.repository.HabitRepository` →
`pl.luczka.todaywas.domain.repository.HabitRepository`) per moved type is more reliable than editing
imports by hand file-by-file — but the source of truth for "did this work" is the build, not the
script. Run `ktlintFormat` (fixes import ordering/grouping after the moves), then `ktlintCheck` and
`testDebugUnitTest` at the end of every phase before moving to the next — catching a missed import
in a 6-file phase is fast; catching it after all 6 phases land is not.

## Phase 1: `data/local` split — entity / dao / database

### Overview

Split the flat `data/local/` (8 files, 1 package) into `data/local/entity/`, `data/local/dao/`,
`data/local/database/`.

### Changes Required:

#### 1. Entities → `data/local/entity/`

**Files**: `HabitEntity.kt`, `HabitCheckInEntity.kt`, `JournalEntryEntity.kt`,
`UserPreferencesEntity.kt`

**Intent**: Isolate Room `@Entity` table definitions into their own subpackage.

**Contract**: `package pl.luczka.todaywas.data.local` → `pl.luczka.todaywas.data.local.entity` on
all 4 files. No other content changes.

#### 2. DAOs → `data/local/dao/`

**Files**: `HabitDao.kt`, `HabitCheckInDao.kt`, `JournalEntryDao.kt`, `UserPreferencesDao.kt`

**Intent**: Isolate Room `@Dao` interfaces into their own subpackage. Each DAO's method signatures
reference entity types from `data.local.entity`, so each needs a new import added alongside its
package-line change.

**Contract**: `package pl.luczka.todaywas.data.local` → `pl.luczka.todaywas.data.local.dao`, plus
`import pl.luczka.todaywas.data.local.entity.<EntityName>` for whichever entity each DAO's methods
reference.

#### 3. Database → `data/local/database/`; DI module → `di/`

**Files**: `TodayWasDatabase.kt` → `data/local/database/`; `DatabaseModule.kt` → `di/`

**Intent**: Isolate the `@Database` definition in `data/local/database/`. `DatabaseModule.kt` is
Hilt wiring, not a data-layer concern — it joins `di/ClockModule.kt`, `di/CoroutineScopeModule.kt`,
`di/SupabaseModule.kt` instead of living inside a `data/` subpackage, so every Hilt module in the
app lives in one place.

**Contract**: `TodayWasDatabase.kt`: `package pl.luczka.todaywas.data.local` →
`pl.luczka.todaywas.data.local.database`; its `@Database(entities = [...])` annotation array needs
each entity's import updated to `data.local.entity`, and its abstract DAO-accessor methods need each
DAO's import updated to `data.local.dao`. `DatabaseModule.kt`: `package pl.luczka.todaywas.data.local`
→ `pl.luczka.todaywas.di`; its `@Provides` methods need the same DAO import updates plus a new
import for `TodayWasDatabase` (`data.local.database.TodayWasDatabase`, no longer same-package).

#### 4. Test mirrors

**Files**: `app/src/test/.../data/local/HabitCheckInDaoTest.kt` → `data/local/dao/`,
`HabitDaoTest.kt` → `data/local/dao/`, `JournalEntryDaoTest.kt` → `data/local/dao/`,
`TodayWasDatabaseTest.kt` → `data/local/database/`

**Intent**: Keep the 1:1 test/main package mirror.

**Contract**: Same package-line + import fixes as their main-source counterparts.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest --tests "pl.luczka.todaywas.data.local.*"` passes
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows the 8 main files + 4 test files moved with no unintended content diff beyond
  package/import lines (`git diff` on each shows only those lines changed)

---

## Phase 2: `data/remote` split — dto / api

### Overview

Split the remote-facing subset of `data/repository/` (Supabase DTOs and the 3 Postgrest-wrapper
data sources) out into a new `data/remote/` package with `dto/` and `api/` subpackages.

### Changes Required:

#### 1. DTOs → `data/remote/dto/`

**Files**: `HabitRemoteDto.kt`, `HabitCheckInRemoteDto.kt`, `JournalEntryRemoteDto.kt`,
`AiPromptRequestDto.kt`, `AiPromptResponseDto.kt`

**Intent**: Isolate `@Serializable` Supabase/OpenRouter payload shapes into their own subpackage.

**Contract**: `package pl.luczka.todaywas.data.repository` → `pl.luczka.todaywas.data.remote.dto`.

#### 2. Remote data sources → `data/remote/api/`

**Files**: `RemoteHabitDataSource.kt`, `RemoteHabitDataSourceImpl.kt`,
`RemoteHabitCheckInDataSource.kt`, `RemoteHabitCheckInDataSourceImpl.kt`,
`RemoteJournalDataSource.kt`, `RemoteJournalDataSourceImpl.kt`

**Intent**: Isolate the Postgrest-wrapper interfaces + impls (no rename — they stay
`Remote*DataSource`, only the directory is `api/`) into their own subpackage.

**Contract**: `package pl.luczka.todaywas.data.repository` → `pl.luczka.todaywas.data.remote.api`.
Each `*Impl.kt` references its DTO type (moved in step 1 of this phase) and calls
`RemoteCall.kt`'s `remoteCall {}` helper (still in `data.repository` at this point, moves in Phase
4) — both need import updates.

#### 3. Test mirrors

**Files**: `app/src/test/.../data/repository/FakeRemoteHabitDataSource.kt`,
`FakeRemoteHabitCheckInDataSource.kt`, `FakeRemoteJournalDataSource.kt` → `data/remote/api/`

**Intent**: Keep the 1:1 test/main package mirror — these fakes implement the interfaces moved in
step 2.

**Contract**: Package-line + import fixes matching step 2.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (fakes are consumed by repository-impl tests still in
  `data/repository/` at this point in the plan — full suite run confirms nothing broke)
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows the 11 main + 3 test files moved with no unintended content diff

---

## Phase 3: `domain/repository` + `domain/util` extraction

### Overview

Move the 5 repository interfaces from `data/repository/` into a new `domain/repository/` package,
and the 3 business-rule calculator objects from `domain/model/` into a new `domain/util/` package.
This is the phase with the widest blast radius — every use case, every repository impl, and several
ViewModels/mappers import these types.

### Changes Required:

#### 1. Repository interfaces → `domain/repository/`

**Files**: `AuthRepository.kt`, `HabitRepository.kt`, `JournalRepository.kt`,
`OnboardingRepository.kt`, `AiAssistRepository.kt`

**Intent**: Repository contracts belong in `domain/` (the layer that defines what it needs), not
`data/` (the layer that implements it) — standard Clean Architecture placement, matching the
existing `domain/model/` + `domain/usecase/` subpackage convention.

**Contract**: `package pl.luczka.todaywas.data.repository` →
`pl.luczka.todaywas.domain.repository`. Every use case in `domain/usecase/` that constructor-injects
one of these 5 interfaces needs its import updated. Every `*RepositoryImpl.kt` (still in
`data.repository` at this point) needs its `: XxxRepository` supertype import updated. The Hilt
`RepositoryModule.kt`'s 5 `@Binds` signatures need their interface-side import updated (impl-side
imports are unaffected — impls haven't moved yet).

#### 2. Calculators → `domain/util/`

**Files**: `EditWindow.kt`, `HabitContributionCalculator.kt`, `JournalContributionCalculator.kt`

**Intent**: Separate stateless business-rule/algorithm objects from data-shape models.
`EditWindowExpiredException.kt` stays in `domain/model/` unchanged — it follows the existing
convention (already established by `AuthError.kt`/`AiAssistError.kt`) of exceptions living alongside
domain model files, not utils.

**Contract**: `package pl.luczka.todaywas.domain.model` → `pl.luczka.todaywas.domain.util`. Every
importer of `EditWindow.isEditable()`/`.HOURS`, `HabitContributionCalculator`, or
`JournalContributionCalculator` (use cases, ViewModels, `ui/habit/HabitDetailMapper.kt`,
`ui/model/ContributionMapper.kt`, etc.) needs its import updated.

#### 3. Test mirrors

**Files**: `app/src/test/.../data/repository/Fake*.kt` (`FakeAiAssistRepository`,
`FakeAuthRepository`, `FakeHabitRepository`, `FakeJournalRepository`,
`FakeOnboardingRepository`) → `domain/repository/`; `domain/model/EditWindowTest.kt`,
`HabitContributionCalculatorTest.kt`, `JournalContributionCalculatorTest.kt` → `domain/util/`

**Intent**: Keep the 1:1 test/main package mirror.

**Contract**: Package-line + import fixes matching steps 1–2. `domain/model/ContributionWindowTest.kt`
stays put (untouched — `ContributionWindow` isn't moving).

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (full suite — this phase's blast radius spans domain,
  data, and ui test files)
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows the 8 main + 8 test files moved with no unintended content diff
- Spot-check 2–3 use case files and 1 ViewModel to confirm their repository/calculator imports
  resolved to the new `domain.repository`/`domain.util` packages, not stale `data.repository`/
  `domain.model` ones

---

## Phase 4: `data/repository` consolidation — impls, mapper, util

### Overview

With interfaces and calculators already relocated (Phases 1–3), trim `data/repository/` down to
just its 5 impls, move `RepositoryModule.kt` into `di/` (joining `DatabaseModule.kt` from Phase 1),
and extract the remaining mapper and generic-util files into `data/mapper/` and `data/util/`.

### Changes Required:

#### 1. Mappers → `data/mapper/`

**Files**: `HabitEntityMapper.kt`, `HabitCheckInEntityMapper.kt`, `JournalEntryEntityMapper.kt`,
`HabitRemoteMapper.kt`, `HabitCheckInRemoteMapper.kt`, `JournalEntryRemoteMapper.kt`,
`AuthErrorMapper.kt`, `AuthStateMapper.kt`, `AiAssistErrorMapper.kt`

**Intent**: Group all entity↔domain and dto↔domain mapping (plus the Supabase-specific
error/state mappers, which are the same shape — external representation → domain type) into one
mapper package, matching the `*EntityMapper.kt`/dedicated-mapper-file convention already in
`lessons.md`.

**Contract**: `package pl.luczka.todaywas.data.repository` → `pl.luczka.todaywas.data.mapper`. The
3 entity mappers need their entity-type imports updated to `data.local.entity` (moved in Phase 1).
The 3 remote mappers need their DTO-type imports updated to `data.remote.dto` (moved in Phase 2).
Every repository impl that calls these mapper functions needs an import added.

#### 2. Generic call-wrapper utils → `data/util/`

**Files**: `RemoteCall.kt`, `SafeDbCall.kt`

**Intent**: These are generic `Result`-wrapping helpers used by remote data sources and repository
impls, not mapping logic — separate package from `data/mapper/`.

**Contract**: `package pl.luczka.todaywas.data.repository` → `pl.luczka.todaywas.data.util`. Every
`data/remote/api/*Impl.kt` (Phase 2) that calls `remoteCall {}` and every repository impl that calls
`safeDbCall {}` needs an import added.

#### 3. Repository impls stay in `data/repository/`; `RepositoryModule.kt` → `di/`

**Files**: `AuthRepositoryImpl.kt`, `HabitRepositoryImpl.kt`, `JournalRepositoryImpl.kt`,
`OnboardingRepositoryImpl.kt`, `AiAssistRepositoryImpl.kt` (package unchanged, imports fixed);
`RepositoryModule.kt` → `di/` (package changed), following the same rule applied to
`DatabaseModule.kt` in Phase 1 — Hilt modules live in `di/`, not inside a `data/` subpackage.

**Intent**: The 5 impls stay in `data/repository/` — only their imports need fixing now that the
interfaces they implement (Phase 3), the mappers they call (step 1 above), and the utils they call
(step 2 above) have all moved. `RepositoryModule.kt` moves to `di/` alongside `DatabaseModule.kt`.

**Contract**: Impls: no package-line change. Add/update imports: `domain.repository.<Interface>`
(supertype type), `data.mapper.<MapperFile>` (mapper function calls),
`data.util.RemoteCall`/`SafeDbCall` (call-wrapper usage), `data.remote.api.Remote*DataSource`
(constructor-injected dependencies of `HabitRepositoryImpl`/`JournalRepositoryImpl`), `data.local.dao.*`
(constructor-injected DAOs). `RepositoryModule.kt`: `package pl.luczka.todaywas.data.repository` →
`pl.luczka.todaywas.di`; its `@Binds` signatures need both sides' imports added —
`domain.repository.<Interface>` and `data.repository.<Impl>`.

#### 4. Test mirrors

**Files**: `app/src/test/.../data/repository/AiAssistErrorMapperTest.kt`, `AuthErrorMapperTest.kt`,
`AuthStateMapperTest.kt` → `data/mapper/`. `HabitRepositoryImplTest.kt`, `JournalRepositoryImplTest.kt`,
`OnboardingRepositoryImplTest.kt` stay in `data/repository/` (imports fixed only).

**Intent**: Keep the 1:1 test/main package mirror.

**Contract**: Package-line + import fixes matching steps 1–3.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (full suite)
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows the 9 mapper + 2 util files moved to `data/mapper/`/`data/util/`,
  `RepositoryModule.kt` moved to `di/`, the 5 impls + 3 impl-tests + 3 mapper-tests unmoved-but-diffed
  (imports only)
- `data/repository/` now contains exactly 5 files (the impls only)

---

## Phase 5: `ui/habit/mapper` + `ui/auth/util`

### Overview

Nest the two `ui/habit/` mapper files and the one `ui/auth/` util file into feature-scoped
subpackages, since their target types (`HabitDetailRowUiState`, `HabitCheckInRowUiState`) and
consumers are feature-owned rather than shared across `ui/model/`.

### Changes Required:

#### 1. Habit mappers → `ui/habit/mapper/`

**Files**: `HabitDetailMapper.kt`, `LogHabitCheckInsMapper.kt`

**Intent**: Separate domain→UI mapping logic from the rest of the feature package, without
relocating the row UiState types they target (those stay in `HabitDetailUiState.kt`/
`LogHabitCheckInsUiState.kt` — moving only the mapper functions avoids a dependency inversion where
a shared package would depend back on feature-owned types).

**Contract**: `package pl.luczka.todaywas.ui.habit` → `pl.luczka.todaywas.ui.habit.mapper`. Each
file needs an added import for its target row-state type
(`pl.luczka.todaywas.ui.habit.HabitDetailRowUiState` /
`pl.luczka.todaywas.ui.habit.HabitCheckInRowUiState`) plus its existing `domain.model`/`domain.util`
imports (the `EditWindow` import in `HabitDetailMapper.kt` now points at `domain.util`, per Phase
3). `HabitDetailScreen.kt`/`HabitDetailViewModel.kt` and
`LogHabitCheckInsScreen.kt`/`LogHabitCheckInsViewModel.kt` (whichever calls `toHabitDetailRows()`/
`toRows()`) need an import added for the new `ui.habit.mapper` package.

#### 2. Auth validation → `ui/auth/util/`

**Files**: `AuthFormValidation.kt`

**Intent**: Separate plain validation/formatting helpers from the rest of the feature package.

**Contract**: `package pl.luczka.todaywas.ui.auth` → `pl.luczka.todaywas.ui.auth.util`. Every caller
of `isValidEmail`/`isValidPassword`/`isValidRepeatPassword`/`AuthErrorUiState.message()` (likely
`SignInFormContent.kt`, `SignUpFormContent.kt`, `AccountViewModel.kt`, `OnboardingViewModel.kt`, or
their screens) needs an import added.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (full suite)
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows the 3 main files moved with no unintended content diff
- `ui/habit/CreateHabitViewModelTest.kt`, `HabitDetailViewModelTest.kt`,
  `LogHabitCheckInsViewModelTest.kt`, `ui/account/AccountViewModelTest.kt` (none of which move) still
  pass, confirming the new imports resolved correctly in their production code paths

---

## Phase 6: `ui/model` → `ui/mapper` split

### Overview

`ui/model/` (18 files) mixes ~9 UI model files with 9 domain→UI mapper files in one package —
discovered after Phase 5 shipped, when it became clear the same flat-mixing problem this whole plan
targets in `data/` was still present in `ui/`. Split it: models stay in `ui/model/`, every mapper
function moves to a new top-level `ui/mapper/` package (sibling to `ui/model/`, mirroring
`data/mapper/`'s relationship to `data/local/`/`data/remote/`).

### Changes Required:

#### 1. Split `ContributionMapper.kt`

**Files**: `ui/model/ContributionMapper.kt` → new `ui/model/ContributionUiState.kt` (models) +
`ui/mapper/ContributionMapper.kt` (mapper functions)

**Intent**: Unlike the other 8 mapper files, `ContributionMapper.kt` bundles 3 model type
definitions (`ContributionGridUiState`, `ContributionWindowUiState`, `ContributionGridType`) together
with its 6 mapper functions in one file. Pull the 3 model types into a new `ContributionUiState.kt`
that stays in `ui/model/`; the mapper functions (`toUiState()` overloads on `ContributionGrid`,
`ContributionLevel`, `ContributionWindow`; `toDomain()` on `ContributionWindowUiState`; the two
private `toContinuousCells`/`toByMonthCells` helpers) move to `ui/mapper/ContributionMapper.kt`.

**Contract**: `ContributionUiState.kt`: `package pl.luczka.todaywas.ui.model`, contains the 3 type
definitions unchanged. `ContributionMapper.kt`: `package pl.luczka.todaywas.ui.mapper`, adds an
import for `pl.luczka.todaywas.ui.model.{ContributionGridUiState, ContributionWindowUiState,
ContributionGridType}`. The 12 files that reference these 3 types (`ui/main/*`, `ui/habit/*`, the
mapper itself, `ContributionMapperTest.kt`) are unaffected — the types stay in `ui/model/`, only the
mapper functions moved.

#### 2. Move the remaining 8 mapper files → `ui/mapper/`

**Files**: `HabitMapper.kt`, `JournalEntryMapper.kt`, `AuthStateMapper.kt`,
`AuthErrorUiStateMapper.kt`, `AiAssistErrorUiStateMapper.kt`, `JournalDateSlotMapper.kt`,
`JournalPromptToneMapper.kt`, `LocalDataSummaryMapper.kt`

**Intent**: Isolate domain→UI mapping logic from the UI model shapes it targets, matching the
`data/mapper/` precedent from Phase 4.

**Contract**: `package pl.luczka.todaywas.ui.model` → `pl.luczka.todaywas.ui.mapper` on all 8. Each
needs a new import for whichever `ui.model` type(s) it maps to (e.g. `HabitMapper.kt` needs
`HabitUiState`, `HabitCheckInStatusUiState`, `HabitTypeUiState`; `JournalEntryMapper.kt` needs
`JournalEntryUiState`; etc. — the specific model type(s) each file's functions return). Most of
these functions are named `toUiState()`/`toDomain()` — multiple overloads of the same name coexisting
in one package (resolved by receiver type, not by import). Moving them all into the same new
`ui.mapper` package preserves that overload grouping, so every caller's import line changes from
`pl.luczka.todaywas.ui.model.toUiState`/`.toDomain` to `pl.luczka.todaywas.ui.mapper.toUiState`/
`.toDomain` — one mechanical substitution, not per-overload disambiguation.

#### 3. Test mirror

**File**: `app/src/test/.../ui/model/ContributionMapperTest.kt` → `ui/mapper/ContributionMapperTest.kt`

**Intent**: Keep the 1:1 test/main package mirror.

**Contract**: Package-line change; add imports for the 3 model types now in `ui.model`.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (full suite)
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows 9 mapper files moved into `ui/mapper/`, a new `ui/model/ContributionUiState.kt`,
  and every caller of a `ui.model` mapper function (ViewModels/Screens across every feature) diffed
  with import-only changes
- `ui/model/` contains only `*UiState.kt`/`*Ui.kt` model files afterward

---

## Phase 7: `ui/habit` split by sub-flow

### Overview

`ui/habit/` bundles 3 independent flows (create a habit, view/edit a habit's history, log today's
check-ins) flat in one package. Split into `ui/habit/create/`, `ui/habit/detail/`,
`ui/habit/logcheckin/`, each self-contained with its own `Intent`/`UiEvent`/`UiState`/`Screen`/
`ViewModel` (+ its single mapper file, from Phase 5, flattened directly into the flow's package
rather than a nested `mapper/` subpackage — with only one file, the extra directory level added
nothing).

### Changes Required:

#### 1. `ui/habit/create/`

**Files**: `CreateHabitIntent.kt`, `CreateHabitUiEvent.kt`, `CreateHabitUiState.kt`,
`CreateHabitScreen.kt`, `CreateHabitViewModel.kt`

**Intent**: Isolate the create-habit flow into its own subpackage.

**Contract**: `package pl.luczka.todaywas.ui.habit` → `pl.luczka.todaywas.ui.habit.create`.

#### 2. `ui/habit/detail/`

**Files**: `HabitDetailIntent.kt`, `HabitDetailUiEvent.kt`, `HabitDetailUiState.kt`,
`HabitDetailScreen.kt`, `HabitDetailViewModel.kt`, `HabitDetailMapper.kt`

**Intent**: Isolate the habit-detail flow (view/edit history) into its own subpackage.
`HabitDetailMapper.kt` (nested under `mapper/` since Phase 5) flattens directly into `detail/` —
a dedicated `mapper/` subpackage for exactly one file added a directory level without separating
anything.

**Contract**: `package pl.luczka.todaywas.ui.habit`/`pl.luczka.todaywas.ui.habit.mapper` →
`pl.luczka.todaywas.ui.habit.detail` for all 6 files. Since `HabitDetailMapper.kt` is now
same-package as `HabitDetailRowUiState` (declared in `HabitDetailUiState.kt`), its import of that
type is removed rather than updated.

#### 3. `ui/habit/logcheckin/`

**Files**: `LogHabitCheckInsIntent.kt`, `LogHabitCheckInsUiEvent.kt`, `LogHabitCheckInsUiState.kt`,
`LogHabitCheckInsScreen.kt`, `LogHabitCheckInsViewModel.kt`, `LogHabitCheckInsMapper.kt`

**Intent**: Isolate the log-check-ins flow into its own subpackage, with the same flattening
applied to its mapper file.

**Contract**: `package pl.luczka.todaywas.ui.habit`/`pl.luczka.todaywas.ui.habit.mapper` →
`pl.luczka.todaywas.ui.habit.logcheckin` for all 6 files. `LogHabitCheckInsMapper.kt`'s import of
`HabitCheckInRowUiState` is removed (now same-package, declared in `LogHabitCheckInsUiState.kt`).

#### 4. Callers + test mirrors

**Files**: `ui/TodayWasApp.kt` (imports `CreateHabitScreen`, `HabitDetailScreen`,
`LogHabitCheckInsScreen`); `app/src/test/.../ui/habit/CreateHabitViewModelTest.kt` →
`ui/habit/create/`, `HabitDetailViewModelTest.kt` → `ui/habit/detail/`,
`LogHabitCheckInsViewModelTest.kt` → `ui/habit/logcheckin/`

**Intent**: Fix the root nav host's screen imports; keep the 1:1 test/main package mirror.

**Contract**: `TodayWasApp.kt`'s 3 `ui.habit.*Screen` imports updated to their new subpackages. Test
files get matching package-line + import fixes.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (full suite)
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows all `ui/habit/` files moved into `create/`/`detail/`/`logcheckin/` with no
  unintended content diff, and `TodayWasApp.kt` diffed with import-only changes
- `ui/habit/` contains no loose `.kt` files at its root — every file lives under one of the 3
  subpackages

---

## Phase 8: `ui/journal` split by sub-flow

### Overview

`ui/journal/` bundles 2 independent flows (add a new entry, with its "help me start" sub-flow;
view/edit an existing entry, with its "help me refine" sub-flow) flat in one package. Split into
`ui/journal/create/` and `ui/journal/detail/`, each carrying its own help-me-* step/state files.

### Changes Required:

#### 1. `ui/journal/create/`

**Files**: `AddJournalEntryIntent.kt`, `AddJournalEntryUiEvent.kt`, `AddJournalEntryUiState.kt`,
`AddJournalEntryScreen.kt`, `AddJournalEntryViewModel.kt`, `HelpMeStartStep.kt`,
`HelpMeStartUiState.kt`

**Intent**: Isolate the add-entry flow (and its "help me start" sub-state, confirmed used only by
this flow) into its own subpackage.

**Contract**: `package pl.luczka.todaywas.ui.journal` → `pl.luczka.todaywas.ui.journal.create`.

#### 2. `ui/journal/detail/`

**Files**: `JournalEntryDetailIntent.kt`, `JournalEntryDetailUiEvent.kt`,
`JournalEntryDetailUiState.kt`, `JournalEntryDetailScreen.kt`, `JournalEntryDetailViewModel.kt`,
`HelpMeRefineStep.kt`, `HelpMeRefineUiState.kt`

**Intent**: Isolate the entry-detail flow (and its "help me refine" sub-state, confirmed used only
by this flow) into its own subpackage.

**Contract**: `package pl.luczka.todaywas.ui.journal` → `pl.luczka.todaywas.ui.journal.detail`.
`MAX_REGENERATIONS` (a top-level `const val` declared in `create/HelpMeStartUiState.kt`) is shared
by both flows' regenerate-limit checks — discovered at compile time, not caught by static review.
`JournalEntryDetailViewModel.kt` and `JournalEntryDetailScreen.kt` get a cross-package import
(`pl.luczka.todaywas.ui.journal.create.MAX_REGENERATIONS`) rather than a duplicated constant.

#### 3. Callers + test mirrors

**Files**: `ui/TodayWasApp.kt` (imports `AddJournalEntryScreen`, `JournalEntryDetailScreen`);
`app/src/test/.../ui/journal/AddJournalEntryViewModelTest.kt` → `ui/journal/create/`,
`JournalEntryDetailViewModelTest.kt` → `ui/journal/detail/`

**Intent**: Fix the root nav host's screen imports; keep the 1:1 test/main package mirror.

**Contract**: `TodayWasApp.kt`'s 2 `ui.journal.*Screen` imports updated to their new subpackages.
Test files get matching package-line + import fixes.

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintFormat` runs clean, then `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (full suite)
- `./gradlew.bat assembleDebug` succeeds

#### Manual Verification:

- `git status` shows all `ui/journal/` files moved into `create/`/`detail/` with no unintended
  content diff, and `TodayWasApp.kt` diffed with import-only changes
- `ui/journal/` contains no loose `.kt` files at its root — every file lives under one of the 2
  subpackages

---

## Phase 9: Docs + full verification

### Overview

Update CLAUDE.md's "Project Structure" section to describe the new layout, then run the complete
verification suite once more end-to-end.

### Changes Required:

#### 1. CLAUDE.md Project Structure section

**File**: `CLAUDE.md`

**Intent**: Replace the stale flat-layout description
(`data/local/` — Room entities, DAOs...; `data/repository/` — repository interfaces + impls,
entity↔domain mappers) with the new subpackage layout from this plan's "Desired End State", so the
next session reading CLAUDE.md sees accurate paths.

**Contract**: Rewrite the bullet list under "## Project Structure" → the `app/src/main/java/...`
tree to enumerate `data/local/{entity,dao,database}/`, `data/remote/{dto,api}/`, `data/repository/`
(impls + Hilt module only), `data/mapper/`, `data/util/`, `di/` (every Hilt module), `domain/model/`,
`domain/usecase/`, `domain/util/`, `domain/repository/`, `ui/model/` (models only), `ui/mapper/`
(every domain→UI mapper), `ui/habit/{create,detail,logcheckin}/`, `ui/journal/{create,detail}/`, and
`ui/auth/util/` — matching each existing bullet's style (one-line purpose description per
subpackage).

### Success Criteria:

#### Automated Verification:

- `./gradlew.bat ktlintCheck` passes
- `./gradlew.bat testDebugUnitTest` passes (full suite)
- `./gradlew.bat assembleDebug` succeeds
- `./gradlew.bat connectedAndroidTest` — skip if no emulator/device is attached; note as such rather
  than failing the phase

#### Manual Verification:

- CLAUDE.md's Project Structure section accurately reflects every subpackage listed in this plan's
  "Desired End State"
- `git log --follow -- <any moved file>` on 2–3 spot-checked files shows continuous history through
  the move (confirms `git mv`/rename-detection was used, not delete+recreate)

---

## Testing Strategy

### Unit Tests:

No new tests. Every existing test in `app/src/test/java/pl/luczka/todaywas/` moves alongside its
main-source counterpart (see per-phase "Test mirrors" steps) and must pass unchanged — this is the
regression signal for "no behavior changed."

### Integration Tests:

`app/src/androidTest/` has only `ExampleInstrumentedTest.kt` (boilerplate, no project-specific
assertions) — untouched by this plan.

### Manual Testing Steps:

1. After Phase 9, run `./gradlew.bat assembleDebug` and sideload the resulting APK; smoke-test
   journaling (add entry), habit check-in (log a value), and sign-in (if a Supabase project is
   configured locally) to confirm nothing regressed end-to-end beyond what the automated test suite
   already covers.
2. Diff `CLAUDE.md` before/after to confirm no unrelated sections were touched.

## Migration Notes

Not applicable — no data migration, no Room schema change (entity `@Entity`/`@ColumnInfo`
annotations and table names are untouched; only the Kotlin file's package/directory moves).

## References

- Lessons applied: `context/foundation/lessons.md` — "Cross-layer mapping (entity↔domain,
  domain↔UI) lives in its own dedicated file" (motivates `data/mapper/`); "Custom dialogs build on
  Material3's real slot components" and others not directly relevant to this structural-only change.
- Change identity: `context/changes/arch-cleanup/change.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not
> rename step titles. See `references/progress-format.md`.

### Phase 1: `data/local` split — entity / dao / database

#### Automated

- [x] 1.1 ktlintFormat runs clean, then ktlintCheck passes — f714b31
- [x] 1.2 testDebugUnitTest passes for `data.local.*` (261/262; `TodayWasDatabaseTest`'s one failure confirmed pre-existing and unrelated — reproduces identically on unmoved code) — f714b31
- [x] 1.3 assembleDebug succeeds — f714b31

#### Manual

- [x] 1.4 git status shows moved files with no unintended content diff — f714b31

### Phase 2: `data/remote` split — dto / api

#### Automated

- [x] 2.1 ktlintFormat runs clean, then ktlintCheck passes — e575749
- [x] 2.2 testDebugUnitTest passes (full suite) (261/262; same pre-existing `TodayWasDatabaseTest` failure noted in Phase 1) — e575749
- [x] 2.3 assembleDebug succeeds — e575749

#### Manual

- [x] 2.4 git status shows moved files with no unintended content diff — e575749

### Phase 3: `domain/repository` + `domain/util` extraction

#### Automated

- [x] 3.1 ktlintFormat runs clean, then ktlintCheck passes — 19eac82
- [x] 3.2 testDebugUnitTest passes (full suite) (full green — the Phase 1/2 `TodayWasDatabaseTest` failure did not recur, confirming it's flaky rather than a real regression) — 19eac82
- [x] 3.3 assembleDebug succeeds — 19eac82

#### Manual

- [x] 3.4 git status shows moved files with no unintended content diff — 19eac82
- [x] 3.5 Spot-check use case + ViewModel imports resolved to new packages — 19eac82

### Phase 4: `data/repository` consolidation — impls, mapper, util

#### Automated

- [x] 4.1 ktlintFormat runs clean, then ktlintCheck passes — ddc47c4
- [x] 4.2 testDebugUnitTest passes (full suite) (261/262; same pre-existing/flaky `TodayWasDatabaseTest` failure noted in Phase 1) — ddc47c4
- [x] 4.3 assembleDebug succeeds — ddc47c4

#### Manual

- [x] 4.4 git status shows moved/diffed files as expected — ddc47c4
- [x] 4.5 `data/repository/` contains exactly 5 files — ddc47c4

### Phase 5: `ui/habit/mapper` + `ui/auth/util`

#### Automated

- [x] 5.1 ktlintFormat runs clean, then ktlintCheck passes — 376298a
- [x] 5.2 testDebugUnitTest passes (full suite) (261/262; same pre-existing/flaky `TodayWasDatabaseTest` failure noted in Phase 1) — 376298a
- [x] 5.3 assembleDebug succeeds — 376298a

#### Manual

- [x] 5.4 git status shows moved files with no unintended content diff — 376298a
- [x] 5.5 Dependent ViewModel tests still pass — 376298a

### Phase 6: `ui/model` → `ui/mapper` split

#### Automated

- [x] 6.1 ktlintFormat runs clean, then ktlintCheck passes — 1ebbf11
- [x] 6.2 testDebugUnitTest passes (full suite) (261/262; same pre-existing/flaky `TodayWasDatabaseTest` failure noted in Phase 1) — 1ebbf11
- [x] 6.3 assembleDebug succeeds — 1ebbf11

#### Manual

- [x] 6.4 git status shows the 9 mapper files + new ContributionUiState.kt as expected, callers diffed import-only — 1ebbf11
- [x] 6.5 `ui/model/` contains only model files afterward — 1ebbf11

### Phase 7: `ui/habit` split by sub-flow

#### Automated

- [x] 7.1 ktlintFormat runs clean, then ktlintCheck passes — 32c3a66
- [x] 7.2 testDebugUnitTest passes (full suite) (261/262; same pre-existing/flaky `TodayWasDatabaseTest` failure noted in Phase 1) — 32c3a66
- [x] 7.3 assembleDebug succeeds — 32c3a66

#### Manual

- [x] 7.4 git status shows all ui/habit/ files moved into create/detail/logcheckin (mapper files flattened directly into detail/logcheckin, no nested mapper/) with no unintended diff — 32c3a66
- [x] 7.5 `ui/habit/` contains no loose files at its root — 32c3a66

### Phase 8: `ui/journal` split by sub-flow

#### Automated

- [x] 8.1 ktlintFormat runs clean, then ktlintCheck passes — 1b7b260
- [x] 8.2 testDebugUnitTest passes (full suite) (clean pass, no failures) — 1b7b260
- [x] 8.3 assembleDebug succeeds — 1b7b260

#### Manual

- [x] 8.4 git status shows all ui/journal/ files moved into create/detail with no unintended diff — 1b7b260
- [x] 8.5 `ui/journal/` contains no loose files at its root — 1b7b260

### Phase 9: Docs + full verification

#### Automated

- [x] 9.1 ktlintCheck passes — 8248363
- [x] 9.2 testDebugUnitTest passes (full suite) (clean pass, no failures) — 8248363
- [x] 9.3 assembleDebug succeeds — 8248363
- [x] 9.4 connectedAndroidTest — skipped, no device/emulator attached (`adb` not found in this environment) — 8248363

#### Manual

- [x] 9.5 CLAUDE.md Project Structure section matches Desired End State — 8248363
- [x] 9.6 git log --follow confirms rename history preserved on spot-checked files — 8248363
