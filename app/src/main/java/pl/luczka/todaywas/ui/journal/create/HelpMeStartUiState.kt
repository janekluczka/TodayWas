package pl.luczka.todaywas.ui.journal.create

import androidx.compose.runtime.Immutable
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

// Still used by Journal Entry Detail's "help me refine" (JournalEntryDetailViewModel/Screen),
// which keeps its own client-side regeneration cap for now. Add Journal Entry no longer uses this
// -- see remainingToday below, which reflects the real server-enforced daily quota instead.
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
    // Null until the first generate/regenerate call this session returns -- deliberately not reset
    // by resetForNewSession, so reopening the sheet keeps showing what the last call reported
    // instead of forgetting it. The real cap lives server-side (ai_assist_usage); this is just the
    // client's best-known snapshot of it.
    val remainingToday: Int? = null,
)
