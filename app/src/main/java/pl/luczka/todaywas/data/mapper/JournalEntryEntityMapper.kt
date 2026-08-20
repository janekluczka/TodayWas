package pl.luczka.todaywas.data.mapper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

fun List<JournalEntryEntity>.toDomain(): List<JournalEntry> = map { it.toDomain() }

fun Flow<List<JournalEntryEntity>>.toDomain(): Flow<List<JournalEntry>> = map { it.toDomain() }

fun JournalEntryEntity.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isDeleted = deletedAt != null,
)

fun List<JournalEntryEntity>.toSyncMeta(): List<SyncMeta> = map { it.toSyncMeta() }
