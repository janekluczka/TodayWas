package pl.luczka.todaywas.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class JournalDateSlot {
    TODAY,
    YESTERDAY,
}
