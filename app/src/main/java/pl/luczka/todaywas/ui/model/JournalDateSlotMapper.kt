package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.domain.model.JournalDateSlot

fun JournalDateSlot.toUiState(): JournalDateSlotUiState =
    when (this) {
        JournalDateSlot.TODAY -> JournalDateSlotUiState.TODAY
        JournalDateSlot.YESTERDAY -> JournalDateSlotUiState.YESTERDAY
    }

fun JournalDateSlotUiState.toDomain(): JournalDateSlot =
    when (this) {
        JournalDateSlotUiState.TODAY -> JournalDateSlot.TODAY
        JournalDateSlotUiState.YESTERDAY -> JournalDateSlot.YESTERDAY
    }
