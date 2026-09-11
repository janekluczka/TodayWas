package pl.luczka.todaywas.ui.journal.create

import androidx.compose.runtime.Immutable

// Five hardcoded starter lines exist per tone (see AddJournalEntryScreen's resolvedText()) — the
// ViewModel only ever picks which variant index to show, never the resolved text itself, since
// resolving a string resource is a Compose-layer concern (same split as AiAssistErrorUiState).
const val STARTER_PROMPT_VARIANT_COUNT = 5

@Immutable
data class JournalStarterPromptUiState(
    val tone: JournalStarterPromptTone,
    val variant: Int,
)
