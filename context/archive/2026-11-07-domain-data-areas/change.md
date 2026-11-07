---
change_id: domain-data-areas
title: Domain data areas
status: archived
created: 2026-11-07
updated: 2026-11-07
archived_at: 2026-11-07T16:24:21Z
---

## Notes

Split `:core:domain` / `:core:data` into per-area modules (agent-toolkit android 0.13.0, PR #16,
house style "Modules"), run with `android-modularize` steps 2–3: one area per commit, full gate
green after each. Package of each module is `pl.luczka.todaywas.<module path>`.

Decisions (2026-11-07): Syncable, the edit window and the Contribution* models go to a base area
`:domain:entry`; SignOut goes to the combining `:domain:sync`; the `SyncScheduler` interface and
the pure `mergeForSync` helper go to `:domain:entry` (implemented in `:data:sync`).

| Domain area | Holds | Depends on |
|---|---|---|
| `entry` (base) | `Syncable`, `EditWindow`, `EditWindowExpiredException`, `IsEditable`, `GetFreshLoggableDates`, `Contribution*`, `SyncScheduler`, `mergeForSync`/`SyncMeta` | — |
| `auth` | `AuthRepository`, `AuthState`, `AuthError`, observe/sign-in/sign-up | — |
| `onboarding` | `OnboardingRepository`, `OnboardingState`, complete/observe, `MarkLocalDataSynced` | — |
| `aiassist` | `AiAssistRepository`, `AiPromptResult`, `AiAssistError`, `JournalPromptTone`, the 2 request use cases | — |
| `journal` | `JournalRepository`, entries, date slots, journal use cases + contribution calculator | entry |
| `habit` | `HabitRepository`, habits, check-ins, board, habit use cases + contribution calculator | entry |
| `sync` (combining) | `SyncLocalData`, `GetLocalDataSummary`, `ShouldReviewLocalDataBeforeSync`, `LocalDataSummary`, `LocalDataSyncPolicy`, `SignOut` | auth, onboarding, journal, habit |

Data modules: `:data:auth`, `:data:onboarding`, `:data:aiassist`, `:data:journal`, `:data:habit`
(each: repository impl, mappers, DI; journal/habit/aiassist also take their remote data sources and
DTOs from `:core:network`), and `:data:sync` (`SyncWorker`, `StartupSync`,
`WorkManagerSyncScheduler`, WorkManager DI). `:core:network` keeps the Supabase client and
`remoteCall`. Last commits delete `:core:domain` / `:core:data`, add `domain/**` and `data/**` to
CI's path filter, and update `CLAUDE.md`.
