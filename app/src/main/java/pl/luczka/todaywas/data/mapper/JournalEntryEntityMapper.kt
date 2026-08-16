package pl.luczka.todaywas.data.mapper

import pl.luczka.todaywas.data.local.entity.JournalEntryEntity
import pl.luczka.todaywas.domain.model.JournalEntry
import java.time.Instant
import java.time.LocalDate

fun JournalEntryEntity.toDomain(): JournalEntry = JournalEntry(
    id = id,
    date = LocalDate.parse(date),
    text = text,
    createdAt = Instant.ofEpochMilli(createdAt),
)
