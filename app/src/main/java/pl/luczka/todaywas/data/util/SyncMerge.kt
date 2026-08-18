package pl.luczka.todaywas.data.util

import java.time.Duration
import java.time.Instant

data class SyncMeta(
    val id: String,
    val updatedAt: Instant,
    val isDeleted: Boolean,
)

data class SyncMergeDecision(
    val pushIds: Set<String>,
    val applyIds: Set<String>,
)

// How long a tombstoned row survives before GC hard-deletes it, both locally and remotely.
val TOMBSTONE_GC_WINDOW: Duration = Duration.ofDays(30)

// Decides, per row, whether the local or remote copy wins - pushIds are local rows to upsert
// remotely, applyIds are remote rows to upsert locally. Tombstone supremacy is enforced here
// independently of the server-side trigger: a local device's own stale, unsynced edit must not
// resurrect a row that's already deleted remotely, even if its local updatedAt is numerically
// newer than the tombstone's server timestamp (see plan.md's Critical Implementation Details).
fun mergeForSync(
    local: List<SyncMeta>,
    remote: List<SyncMeta>,
): SyncMergeDecision {
    val localById = local.associateBy { it.id }
    val remoteById = remote.associateBy { it.id }
    val ids = localById.keys + remoteById.keys

    val pushIds = mutableSetOf<String>()
    val applyIds = mutableSetOf<String>()

    for (id in ids) {
        val localMeta = localById[id]
        val remoteMeta = remoteById[id]
        when {
            localMeta != null && remoteMeta == null -> pushIds += id
            localMeta == null && remoteMeta != null -> applyIds += id
            localMeta != null && remoteMeta != null -> when {
                remoteMeta.isDeleted -> applyIds += id
                localMeta.isDeleted -> pushIds += id
                localMeta.updatedAt > remoteMeta.updatedAt -> pushIds += id
                else -> applyIds += id
            }
        }
    }

    return SyncMergeDecision(pushIds = pushIds, applyIds = applyIds)
}
