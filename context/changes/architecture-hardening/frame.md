# Frame Brief: Architecture hardening — questioning inherited decisions

> Framing step before /10x-plan. This document captures what is *actually*
> at issue, separated from what was initially assumed.

## Reported Observation

`architecture-hardening`'s compiled scope (`change.md`) isn't a blank slate — it carries three
decisions inherited from earlier changes, each embedded as if settled:

- **(a)** Item 1's prescribed fix is specifically a WorkManager-driven outbox (`NetworkType.CONNECTED`
  + `Result.retry()`), decided during `sync-strategy-rework`'s frame pass.
- **(b)** Item 3's untested repositories (`AuthRepositoryImpl`/`AiAssistRepositoryImpl`) inherit
  `arch-cleanup`'s explicit decision to keep them depending on the concrete `SupabaseClient` rather
  than adding a `Remote*DataSource` interface — a decision made for a pure-reorg change, not a
  testing change.
- **(c)** Item 2 names only `MainViewModel`/`HabitDetailViewModel`, but `research.md` found the same
  `domain/util`-direct-call anti-pattern in four more files (6 files, 8 call sites total).

## Initial Framing (preserved)

- **User's stated cause or approach**: some of these carried-over decisions may no longer make
  sense now that we're looking at them fresh, and should be re-litigated before locking a plan
  around them.
- **User's proposed direction**: run a framing pass to question them rather than assume they're
  settled.
- **Pre-dispatch narrowing**: user selected all three candidate decisions plus "investigate
  broadly" (not narrowing to one), and confirmed the angle is **technical correctness**, not
  timing/priority — i.e. "is the previously chosen approach actually right," not "is now the time
  to do this."

## Dimension Map

The three inherited decisions map to three independent dimensions — each investigated on its own
because none share code or evidence with the others:

1. **Sync-trigger cadence** — WorkManager's guarantees (persistence across process death,
   connectivity-gated retry) only matter if nothing else already re-covers dropped pushes.
   ← initial framing questioned the WorkManager prescription
2. **Supabase SDK testability surface** — `arch-cleanup` assumed testing requires either accepting
   the concrete client (skip real coverage) or adding a new abstraction (reopen the decision). A
   third option may already exist in the SDK.
   ← initial framing questioned the no-new-abstraction call
3. **`domain/util` violation uniformity** — item 2's two-file cut assumes the other four sites are
   meaningfully different or separable. If they're the same violation with no technical coupling
   forcing a narrower cut, the scope itself was arbitrary.
   ← initial framing questioned the compiled scope's completeness
4. **FK-cascade / non-transactional apply loop** (items 4/5) — not investigated further. These are
   freshly diagnosed concrete bugs from `research.md` with a clear technical fix already sketched
   in code comments, not inherited "decisions" — nothing to reframe.
5. **Repository composition (local CRUD + remote sync bundled in one class)** — surfaced mid-frame,
   not from the original three: `HabitRepositoryImpl`/`JournalRepositoryImpl` each interleave plain
   local Room CRUD with sync-specific concerns (mutex, push-in-background, `syncWithRemote()`
   merge/GC) in the same class and the same methods. This is the class item 1's WorkManager outbox
   would land in — worth checking whether that's the right shape to build on, or whether the
   underlying decision (one class doing both jobs) should change first.
   ← surfaced by discussion, not part of the original pre-dispatch narrowing

## Hypothesis Investigation

| Hypothesis | Evidence | Verdict |
| --- | --- | --- |
| **1: Self-healing already covers WorkManager's job** | `syncWithRemote()` is only reached via `SyncLocalDataUseCase`, called from exactly 3 places: `MainViewModel.kt:136-140` (once per process, gated on signed-in, fires in `init{}` — doesn't re-fire on navigation or app resume since `MainViewModel` survives in the Nav3 back stack), `AccountViewModel.kt:223,232` and `OnboardingViewModel.kt:311,320` (sign-in/onboarding-completion one-shots + a manual confirm button). No `onResume`/`ProcessLifecycleOwner`/periodic/connectivity-triggered re-sync exists anywhere in the app (confirmed via full-tree search). `syncScope` (`di/CoroutineScopeModule.kt:21-24`) is a plain in-memory `Singleton` `CoroutineScope` with no persistence — dies with the process. `remoteCall` (`data/util/RemoteCall.kt`) has zero retry, unlike `safeDbCall`/`OnboardingRepositoryImpl.upsertWithRetry` which retry once. | **NONE** — self-heal claim not supported; framing was right |
| **2: Concrete `SupabaseClient` blocks real test coverage** | Live-verified (not just read): `createSupabaseClient(...)` accepts `httpEngine` (`SupabaseClientBuilder.kt:45`); both `Auth` and `Functions` plugins share one engine-backed `KtorSupabaseHttpClient`, so a single `MockEngine` fakes both; `install(Auth) { minimalConfig() }` is supabase-kt's own documented escape hatch avoiding platform-storage session managers in JVM tests. A throwaway test constructing a real `SupabaseClient` with `MockEngine` + `minimalConfig()` was written, run (`BUILD SUCCESSFUL`, 2/2 passed — mocked `functions.invoke` round-trip and mocked `auth.signInWith(Email)` error-mapping), then deleted (`git status` confirmed clean). `ktor-client-mock` is already a test dependency at a compatible version — no build-file change needed. | **NONE** — testability doesn't require reopening `arch-cleanup`'s decision |
| **3: Item 2's two-file scope is technically forced** | All 6 call sites are trivial call-and-use (a single boolean/list expression), several *simpler* than the named pair. `AccountViewModel.kt:217` and `OnboardingViewModel.kt:302` contain **literally duplicated** `shouldReviewBeforeSync` branching (same comment, same shape). `EditWindow.freshLoggableDates`/`isEditable` are each reused across 2-3 of the other 4 sites, collapsing them into as few as 3 small use cases (no `Clock`/repository dependency needed beyond what's already in hand). Zero shared code between this group and the two named calculators, so expanding scope doesn't complicate the harder Flow-returning use case work. The "ViewModels can't call `domain/util` directly" rule is **not documented anywhere in `lessons.md`** as it stands today — it's an inferred convention, not a settled one being selectively narrowed. | **STRONG** — the two-file cut is arbitrary; scope should widen |
| **5: One class doing local CRUD + remote sync is the wrong shape to build item 1 on** | Direct read of both files confirms every write method (`createHabit`, `addCheckIns`, `updateCheckIn`, `deleteHabit`, `deleteCheckIn` in `HabitRepositoryImpl.kt:54-149`; `addEntry`, `updateEntry`, `deleteEntry` in `JournalRepositoryImpl.kt:45-81`) interleaves a local DAO write with a conditional `push*InBackground` call inline, and `syncWithRemote()`/mutex/GC-purge logic sits in the same class. A clean split exists: DI can't swap repository *implementations* based on live sign-in state (Hilt bindings are resolved once; auth state is a runtime toggle within one process, and ViewModels must never branch on it per FR-008), so two full DI-selectable repos aren't viable — but extracting local-only CRUD into its own class (mirroring the `RemoteHabitDataSource`/`RemoteJournalDataSource` pattern that already exists for the remote side) and making the composing `HabitRepositoryImpl`/`JournalRepositoryImpl` delegate to it is. The `HabitRepository`/`JournalRepository` *interfaces* also currently bundle CRUD with sync-only members (`syncWithRemote()`, `clearLocal()`), an ISP violation independent of the DI question. | **STRONG (with a caveat)** — viable as internal composition (extract a local-data-source class), not as swappable DI-bound repositories |

## Narrowing Signals

Step 3's evidence was already conclusive across the first three hypotheses (two NONE, one STRONG)
— per the skill's early-exit clause, the Step 4 questioning round was skipped. No further
disambiguation was needed; a single well-aimed investigation per dimension resolved each one.

A fourth question (hypothesis 5) surfaced organically after the initial hand-off point, when the
user asked whether local-only and signed-in sync logic should live in separate repositories. It
was resolved directly from the code (both `Impl` files read in full) rather than through a
dispatched sub-agent, and the user confirmed the resulting recommendation.

## Cross-System Convention

- **Item 1**: matches Google's official offline-first guidance (local DB as source of truth,
  WorkManager + connectivity constraints + exponential backoff for sync orchestration, *Now in
  Android* as reference) — already the basis for the original `sync-strategy-rework` frame
  decision, now reinforced by live evidence that nothing else in this codebase covers the gap.
- **Item 3**: matches supabase-kt's own documented testing pattern (`minimalConfig()` +
  `httpEngine` override) rather than requiring a project-invented abstraction — the SDK already
  anticipated this exact use case.
- **Item 2**: matches this project's own established use-case shape (`GetLocalDataSummaryUseCase`,
  `UpdateJournalEntryUseCase` et al. — small class, `@Inject constructor`, `operator fun invoke`)
  applied to sites it simply hadn't reached yet, not a new pattern.
- **Item 1 (repository shape)**: matches the project's own existing precedent — `data/remote/api/`
  already separates a `Remote*DataSource` abstraction per domain (`RemoteHabitDataSource`,
  `RemoteJournalDataSource`, `RemoteHabitCheckInDataSource`) from the composing repository;
  extracting an equivalent local-side class is the same pattern applied symmetrically, not a new
  one. CLAUDE.md's own module-structure doc already treats `data/repository/` as the orchestration
  layer over data sources, not the data source itself.

## Reframed (or Confirmed) Problem Statement

> **The actual problem to plan around is**: items 1 and 3 keep their originally inherited technical
> direction unchanged — WorkManager-driven outbox is genuinely necessary (self-healing doesn't
> cover it), and `AuthRepositoryImpl`/`AiAssistRepositoryImpl` can be fully tested without
> reopening `arch-cleanup`'s no-new-abstraction decision. **Item 2's scope was too narrow**: the
> real problem isn't "two ViewModels call `domain/util` directly," it's "`domain/util` (all four
> objects) is called directly from UI-layer code at 8 sites across 6 files, including duplicated
> logic between `AccountViewModel` and `OnboardingViewModel`" — and fixing only the two named
> ViewModels would leave a live counterexample of the very pattern being fixed, right next to it.

Two of the three originally-questioned decisions held up under technical-correctness scrutiny —
that's a legitimate frame outcome, not a failure to find something. The one that didn't hold up
(item 2) wasn't really a "decision" being overturned so much as an incomplete compilation being
completed: `change.md` itself already flagged the list as "compiled from cross-references... not
a dedicated audit." The fourth, later-surfaced question (hypothesis 5) adds a shape recommendation
for item 1: build the WorkManager outbox on top of an extracted local-data-source class rather
than inline in the current combined-responsibility repository — viable as internal composition,
not as two DI-selectable repositories, since sign-in is a live runtime toggle Hilt can't branch on
statically.

## Confidence

**HIGH** for all four — each hypothesis has strong evidence (including a live-executed test for
item 3, and a full read of both `Impl` files for item 5), matches an established convention
(including, for item 5, a pattern — `Remote*DataSource` — already live in this exact codebase),
and (for item 2) a decisive duplicated-code signal that doesn't depend on interpretation.

## What Changes for /10x-plan

- **Item 1**: plan the WorkManager-driven outbox, but land it on an extracted local-data-source
  class rather than inline in the current combined repository. Concretely: pull the plain Room CRUD
  out of `HabitRepositoryImpl`/`JournalRepositoryImpl` into new `LocalHabitDataSource`/
  `LocalJournalDataSource` classes (mirroring the existing `RemoteHabitDataSource`/
  `RemoteJournalDataSource` pattern), and let the `Impl` classes become the composing layer that
  delegates local CRUD to the new class and layers push/sync (soon WorkManager-driven) on top. Two
  DI-selectable repository implementations are explicitly ruled out — sign-in is a live runtime
  toggle within one process, and Hilt bindings resolve once. Also worth considering: splitting
  `HabitRepository`/`JournalRepository` into a CRUD interface plus a separate `Syncable`-style
  interface (`syncWithRemote()`, `clearLocal()`), an ISP cleanup independent of the class-extraction
  above.
- **Item 3**: plan test coverage using a `MockEngine`-backed real `SupabaseClient`
  (`install(Auth) { minimalConfig() }` + `install(Functions)`) — simpler than `change.md` implied,
  no new `Remote*DataSource` abstraction needed.
- **Item 2**: widen scope from 2 files to 6 (8 call sites), naturally splitting into two phases —
  a cheap phase introducing ~3 small use cases wrapping `EditWindow.freshLoggableDates`,
  `EditWindow.isEditable`, and `LocalDataSyncPolicy.shouldReviewBeforeSync` (reused across their
  respective call sites), and the existing harder phase wrapping
  `JournalContributionCalculator`/`HabitContributionCalculator` in Flow-returning use cases for
  `MainViewModel`/`HabitDetailViewModel`.

## References

- Source files: `data/repository/HabitRepositoryImpl.kt`, `data/repository/JournalRepositoryImpl.kt`,
  `di/CoroutineScopeModule.kt:21-24`, `data/util/RemoteCall.kt`, `ui/main/MainViewModel.kt:136-140`,
  `ui/account/AccountViewModel.kt:215-232`, `ui/onboarding/OnboardingViewModel.kt:300-320`,
  `di/SupabaseModule.kt:21-28`, `data/repository/AuthRepositoryImpl.kt`,
  `data/repository/AiAssistRepositoryImpl.kt`, `ui/habit/detail/HabitDetailMapper.kt:18,24`,
  `ui/habit/logcheckin/LogHabitCheckInsViewModel.kt:30`,
  `ui/journal/detail/JournalEntryDetailViewModel.kt:73,210`, `domain/util/EditWindow.kt`,
  `domain/util/LocalDataSyncPolicy.kt`, `context/foundation/lessons.md`,
  `domain/repository/HabitRepository.kt`, `data/remote/api/RemoteHabitDataSource.kt`
- Related research: `context/changes/architecture-hardening/research.md`
- Investigation tasks: #7 (sync cadence), #8 (SupabaseClient testability), #9 (item-2 scope
  uniformity). Hypothesis 5 (repository composition) was resolved by direct code read, not a
  dispatched task.

## Minor observations (not full hypotheses, worth noting for planning)

- `remoteCall` (`data/util/RemoteCall.kt`) has zero retry logic at all, unlike `safeDbCall`/
  `OnboardingRepositoryImpl.upsertWithRetry` which retry once — a live-process, non-fatal network
  blip during a background push gets no retry today, reinforcing item 1's urgency.
- `LogHabitCheckInsViewModel.kt:30` calls `Instant.now()` directly instead of an injected `Clock`,
  unlike every other ViewModel in the app — worth fixing incidentally if that file is touched for
  the item 2 widening.
