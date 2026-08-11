package pl.luczka.todaywas.ui.journal

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

const val MAX_REGENERATIONS = 3

@Immutable
data class HelpMeStartUiState(
    val isVisible: Boolean = false,
    val step: HelpMeStartStep = HelpMeStartStep.INPUT,
    val selectedTone: JournalPromptToneUiState? = null,
    val thoughts: String = "",
    val generatedText: String? = null,
    val isGenerating: Boolean = false,
    val error: AiAssistErrorUiState? = null,
    val regenerationsUsed: Int = 0,
)
