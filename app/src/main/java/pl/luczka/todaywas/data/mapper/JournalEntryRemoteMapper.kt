package pl.luczka.todaywas.data.mapper

import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.data.remote.dto.JournalEntryRemoteDto
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant

fun JournalEntry.toRemoteDto(userId: String): JournalEntryRemoteDto = JournalEntryRemoteDto(
    id = id,
    userId = userId,
    date = date.toString(),
    text = text,
    createdAt = createdAt.toString(),
    updatedAt = createdAt.toString(),
    deletedAt = null,
)

fun JournalEntryRemoteDto.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    date = date,
    text = text,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
)
