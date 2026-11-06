---
change_id: arch-cleanup
title: Restructure app package layout by responsibility (data/domain/ui subpackages)
status: archived
created: 2026-08-15
updated: 2026-11-06
archived_at: 2026-11-06T16:11:16Z
---

## Notes

Restructure app/src/main/java package layout: split data/local into entity/dao/database, split
data/repository into remote/dto, remote/api, repository (impl+interface), mapper; move repository
interfaces from data to domain; add domain/util if needed; add presentation-layer (ui) mapper/util
separation. Purely structural refactor, no behavior change.
