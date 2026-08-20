package pl.luczka.todaywas.data.mapper

import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto
import pl.luczka.todaywas.data.util.SyncMeta
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant

fun JournalEntry.toRemoteDto(userId: String): JournalEntryRemoteDto = JournalEntryRemoteDto(
    id = id,
    userId = userId,
    date = date.toString(),
    text = text,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString(),
)

// Collapses the toDomain().toRemoteDto(userId) hop callers would otherwise need when pushing
// local entities straight to remote.
fun JournalEntryEntity.toRemoteDto(userId: String): JournalEntryRemoteDto = toDomain().toRemoteDto(userId)

fun List<JournalEntryEntity>.toRemoteDto(userId: String): List<JournalEntryRemoteDto> = map { it.toRemoteDto(userId) }

fun JournalEntryRemoteDto.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    date = date,
    text = text,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() },
)

fun List<JournalEntryRemoteDto>.toEntity(): List<JournalEntryEntity> = map { it.toEntity() }

fun JournalEntryRemoteDto.toSyncMeta(): SyncMeta = SyncMeta(
    id = id,
    updatedAt = Instant.parse(updatedAt),
    isDeleted = deletedAt != null,
)

fun List<JournalEntryRemoteDto>.toSyncMeta(): List<SyncMeta> = map { it.toSyncMeta() }
