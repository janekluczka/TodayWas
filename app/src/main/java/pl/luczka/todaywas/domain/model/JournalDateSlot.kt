package pl.luczka.todaywas.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class JournalDateSlot {
    TODAY,
    YESTERDAY,
}
