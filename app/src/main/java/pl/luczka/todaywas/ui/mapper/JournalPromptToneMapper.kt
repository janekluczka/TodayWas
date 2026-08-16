package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.domain.model.JournalPromptTone
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

fun JournalPromptTone.toUiState(): JournalPromptToneUiState = when (this) {
    JournalPromptTone.VERY_BAD -> JournalPromptToneUiState.VERY_BAD
    JournalPromptTone.BAD -> JournalPromptToneUiState.BAD
    JournalPromptTone.NEUTRAL -> JournalPromptToneUiState.NEUTRAL
    JournalPromptTone.GOOD -> JournalPromptToneUiState.GOOD
    JournalPromptTone.VERY_GOOD -> JournalPromptToneUiState.VERY_GOOD
}

fun JournalPromptToneUiState.toDomain(): JournalPromptTone = when (this) {
    JournalPromptToneUiState.VERY_BAD -> JournalPromptTone.VERY_BAD
    JournalPromptToneUiState.BAD -> JournalPromptTone.BAD
    JournalPromptToneUiState.NEUTRAL -> JournalPromptTone.NEUTRAL
    JournalPromptToneUiState.GOOD -> JournalPromptTone.GOOD
    JournalPromptToneUiState.VERY_GOOD -> JournalPromptTone.VERY_GOOD
}
