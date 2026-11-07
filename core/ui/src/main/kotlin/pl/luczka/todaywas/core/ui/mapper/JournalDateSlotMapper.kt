package pl.luczka.todaywas.core.ui.mapper

import pl.luczka.todaywas.core.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.domain.journal.model.JournalDateSlot

fun JournalDateSlot.toUiState(): JournalDateSlotUiState = when (this) {
    JournalDateSlot.TODAY -> JournalDateSlotUiState.TODAY
    JournalDateSlot.YESTERDAY -> JournalDateSlotUiState.YESTERDAY
}

fun JournalDateSlotUiState.toDomain(): JournalDateSlot = when (this) {
    JournalDateSlotUiState.TODAY -> JournalDateSlot.TODAY
    JournalDateSlotUiState.YESTERDAY -> JournalDateSlot.YESTERDAY
}
