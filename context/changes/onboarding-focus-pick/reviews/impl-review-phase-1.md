<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Onboarding Focus Pick Implementation Plan

- **Plan**: context/changes/onboarding-focus-pick/plan.md
- **Scope**: Phase 1 of 4
- **Date**: 2026-07-26
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Plan references the deprecated Hilt Compose ViewModel path

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Plan Adherence
- **Location**: `gradle/libs.versions.toml`, `app/build.gradle.kts` (Phase 1 §1/§2 of the plan)
- **Detail**: The plan's Phase 1 §1/§2 contracts call for `androidx-hilt-navigation-compose` /
  `implementation(libs.androidx.hilt.navigation.compose)`, and neither was actually added in this
  phase (the dependency isn't consumed until Phase 4's `hiltViewModel()`, so nothing broke). While
  researching this during Phase 1, I found that `androidx.hilt:hilt-navigation-compose`'s
  `hiltViewModel()` is now deprecated in favor of a new artifact,
  `androidx.hilt:hilt-lifecycle-viewmodel-compose` (package `androidx.hilt.lifecycle.viewmodel.compose`),
  specifically so apps that don't use Navigation Compose (this one doesn't — onboarding is a Dialog
  overlay, not a nav destination) avoid an unnecessary transitive dependency on `androidx.navigation`.
  I never went back to correct the plan text after finding this, so as written the plan still points
  Phase 4 at the deprecated path.
- **Fix**: Update the plan's Phase 1 §1/§2 text and the Phase 3/4 sections that reference
  `hiltViewModel`/`hilt-navigation-compose` to instead reference `androidx.hilt:hilt-lifecycle-viewmodel-compose`
  (`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`). Add the actual dependency when Phase 3/4
  needs it — Phase 1 correctly doesn't need it yet.
  - Strength: Fixes the plan before Phase 4 acts on stale guidance; avoids adopting a deprecated API
    on day one and avoids an unnecessary `androidx.navigation` transitive dependency this project
    deliberately doesn't need.
  - Tradeoff: None significant — pure plan-text correction, no code exists yet that depends on the
    old path.
  - Confidence: HIGH — confirmed via the artifact's own migration guidance found during Phase 1 research.
  - Blind spot: Haven't verified `hilt-lifecycle-viewmodel-compose` 1.3.0's exact API surface beyond
    the `hiltViewModel()` signature already used to plan Phase 4's `MainScreen`/`OnboardingDialog`.
- **Decision**: FIXED — plan.md updated (Phase 1 §1/§2 version-catalog text, Phase 3's `MainViewModel`
  Contract) to reference `hilt-lifecycle-viewmodel-compose` / `androidx.hilt.lifecycle.viewmodel.compose`.

### F2 — `androidx-test-core` added without being called out in the plan

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: `gradle/libs.versions.toml`, `app/build.gradle.kts:66`
- **Detail**: `androidx.test:core` (for `ApplicationProvider`, used by the Robolectric round-trip
  test the plan itself requires in Phase 1 §Success Criteria) was added but isn't named anywhere in
  the plan's Phase 1 §1 "Version catalog" contract. It's a justified, necessary addition — Robolectric
  cannot get an application `Context` without it — just undocumented.
- **Fix**: Add `androidx-test-core` to the plan's Phase 1 §1 contract text as a documentation-only
  correction (it's already correctly implemented in code).
- **Decision**: SKIPPED

### F3 — No Room schema export configured

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: `app/src/main/java/pl/luczka/todaywas/data/local/TodayWasDatabase.kt:6`
- **Detail**: `@Database` defaults to `exportSchema = true`, which is why KSP emits "Schema export
  directory was not provided to the annotation processor so Room cannot export the schema." Once
  real journal/habit data exists (`S-02`/`S-03`), schema history matters for writing real migrations
  instead of relying on `fallbackToDestructiveMigration`. Today, with `version = 1` and no shipped
  data, neither choice is unsafe.
- **Fix A ⭐ Recommended**: Set `exportSchema = false` explicitly in the `@Database` annotation now.
  - Strength: Silences the warning intentionally (documents "we know, not needed yet") rather than by
    omission; matches the plan's own progressive-disclosure stance ("What We're NOT Doing: No Room
    Migration objects... acceptable pre-release").
  - Tradeoff: No schema history captured from v1 — if a future migration needs to diff against v1,
    it has to be reconstructed from the entity source instead of a generated schema JSON.
  - Confidence: HIGH — consistent with the plan's already-stated philosophy for this slice.
  - Blind spot: None significant at this scale (one entity, no shipped data).
- **Fix B**: Configure `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` now and commit the
  generated schema JSON.
  - Strength: Schema history starts from v1, standard recommended Room practice long-term.
  - Tradeoff: Adds build config and a generated-file convention this early, before any real migration
    is even on the roadmap (`S-02`/`S-03` haven't been planned yet).
  - Confidence: MEDIUM — reasonable, but arguably premature given this plan's own "introduce
    technical elements at the moment they're needed" principle.
  - Blind spot: Haven't checked whether `context/changes/*/plan.md` conventions elsewhere in this
    repo already establish a schema-directory pattern to follow.
- **Decision**: FIXED via Fix A — `TodayWasDatabase.kt`'s `@Database` annotation now sets
  `exportSchema = false` explicitly; plan.md's Phase 1 §5 contract updated to match. Re-verified
  `testDebugUnitTest`/`ktlintCheck` green, KSP schema-export warning gone.

### F4 — `Focus.valueOf(it)` unguarded against stale enum data

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Safety & Quality
- **Location**: `app/src/main/java/pl/luczka/todaywas/data/repository/OnboardingRepositoryImpl.kt:22`
- **Detail**: If a persisted `focus` string ever stops matching a current `Focus` enum constant
  (e.g., a future rename without a Room version bump), `Focus.valueOf(it)` throws inside the
  `observeState()` Flow. Low risk today — one release, one enum, no migrations yet.
- **Fix**: Defer — acceptable as-is for this slice; revisit if `Focus` is ever renamed without a
  corresponding schema version bump.
- **Decision**: SKIPPED

### F5 — Retry-once logic retries unconditionally, no backoff

- **Severity**: OBSERVATION
- **Impact**: 🏃 LOW
- **Dimension**: Safety & Quality
- **Location**: `app/src/main/java/pl/luczka/todaywas/data/repository/OnboardingRepositoryImpl.kt:33-43`
- **Detail**: Retries once regardless of exception type (e.g., would retry a logically non-transient
  constraint violation identically to a transient I/O error), with no delay. Matches the plan's
  literal "retry once" contract; just noting the scope of what "retry" means here.
- **Fix**: Defer — acceptable for MVP scope per the plan's own framing (write resilience, not full
  retry policy design).
- **Decision**: SKIPPED

## Verified clean (no findings)

- Retry-once-then-`Result.failure` logic (`OnboardingRepositoryImpl.kt:26-44`) correctly rethrows
  `CancellationException` before falling through to the generic `catch (e: Exception)` in both the
  initial attempt and the retry — avoids the classic Kotlin-coroutines bug of swallowing cancellation.
  Confirmed via `OnboardingRepositoryImplTest.kt`: `upsertCallCount == 2` in both the
  succeeds-on-retry and fails-twice cases — exactly one retry, never unlimited.
- Singleton-row enforcement (`UserPreferencesEntity.kt:8`, `id: Int = 0`) matches the plan's
  by-convention design exactly.
- `RepositoryModule`'s `@Binds` wiring matches the plan's contract exactly.
- `DatabaseModule` (`@Provides`, object) vs. `RepositoryModule` (`@Binds`, abstract class) is
  idiomatic Hilt — `@Provides` is required for the `Room.databaseBuilder(...).build()` construction
  call; `@Binds` is the leaner choice for a pure interface-to-impl binding. Not an inconsistency.
- No Room `Migration` objects — consistent with "What We're NOT Doing."
- Security: no applicable findings — zero network/backend code in this phase, no secrets in any
  changed file.
- All Phase 1 automated success criteria re-verified passing on the current commit (`2db4649`):
  `testDebugUnitTest`, `ktlintCheck`, `assembleDebug` all green.
