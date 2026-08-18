package pl.luczka.todaywas.data.mapper

import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.util.SyncMeta
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate

fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    id = id,
    date = LocalDate.parse(date),
    text = text,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) },
)

fun JournalEntryEntity.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isDeleted = deletedAt != null,
)
