package pl.luczka.todaywas.data.repository

import pl.luczka.todaywas.data.local.JournalEntryEntity
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant

fun JournalEntry.toRemoteDto(userId: String): JournalEntryRemoteDto = JournalEntryRemoteDto(
    id = id,
    userId = userId,
    date = date.toString(),
    text = text,
    createdAt = createdAt.toString(),
)

fun JournalEntryRemoteDto.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id,
    date = date,
    text = text,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
)
