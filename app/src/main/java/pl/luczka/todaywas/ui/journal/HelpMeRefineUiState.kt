package pl.luczka.todaywas.ui.journal

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

@Immutable
data class HelpMeRefineUiState(
    val isVisible: Boolean = false,
    val step: HelpMeRefineStep = HelpMeRefineStep.INPUT,
    val selectedTone: JournalPromptToneUiState? = null,
    val refinedText: String? = null,
    val isGenerating: Boolean = false,
    val error: AiAssistErrorUiState? = null,
    val regenerationsUsed: Int = 0,
)
