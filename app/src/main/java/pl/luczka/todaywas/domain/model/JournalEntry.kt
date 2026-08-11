package pl.luczka.todaywas.domain.model

import java.time.Instant
import java.time.LocalDate

data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val text: String,
    val createdAt: Instant,
)
