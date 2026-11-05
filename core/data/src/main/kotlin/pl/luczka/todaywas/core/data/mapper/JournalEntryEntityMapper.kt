package pl.luczka.todaywas.core.data.mapper

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.luczka.todaywas.core.data.util.SyncMeta
import pl.luczka.todaywas.core.database.entity.JournalEntryEntity
import pl.luczka.todaywas.core.domain.model.JournalEntry

fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    id = id,
    date = LocalDate.parse(date),
    text = text,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it) },
)

fun List<JournalEntryEntity>.toDomain(): List<JournalEntry> = map { it.toDomain() }

fun Flow<List<JournalEntryEntity>>.toDomain(): Flow<List<JournalEntry>> = map { it.toDomain() }

fun JournalEntryEntity.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isDeleted = deletedAt != null,
)

fun List<JournalEntryEntity>.toSyncMeta(): List<SyncMeta> = map { it.toSyncMeta() }
