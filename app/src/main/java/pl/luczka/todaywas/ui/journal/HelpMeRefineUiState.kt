package pl.luczka.todaywas.ui.journal

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

// Mirrors ai-proxy's MAX_TEXT_LENGTH (supabase/functions/ai-proxy/index.ts) so the entry point
// disables locally instead of always failing server-side with a generic invalid_request error.
const val MAX_REFINE_TEXT_LENGTH = 8000

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
