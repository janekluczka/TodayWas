package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState

fun JournalDateSlot.toUiState(): JournalDateSlotUiState = when (this) {
    JournalDateSlot.TODAY -> JournalDateSlotUiState.TODAY
    JournalDateSlot.YESTERDAY -> JournalDateSlotUiState.YESTERDAY
}

fun JournalDateSlotUiState.toDomain(): JournalDateSlot = when (this) {
    JournalDateSlotUiState.TODAY -> JournalDateSlot.TODAY
    JournalDateSlotUiState.YESTERDAY -> JournalDateSlot.YESTERDAY
}
