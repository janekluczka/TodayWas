package pl.luczka.todaywas.feature.journal.impl.edit

import pl.luczka.todaywas.core.domain.model.AiPromptResult
import pl.luczka.todaywas.feature.journal.impl.mapper.toAiAssistErrorUiState
import pl.luczka.todaywas.feature.journal.impl.model.JournalPromptToneUiState
import pl.luczka.todaywas.feature.journal.impl.model.hasRemainingToday
import pl.luczka.todaywas.feature.journal.impl.model.remainingTodayAfter

// Preserves remainingToday, mirroring HelpMeStartUiState's resetForNewSession.
internal fun HelpMeRefineUiState.resetForNewSession(isVisible: Boolean): HelpMeRefineUiState = copy(
    isVisible = isVisible,
    step = HelpMeRefineStep.INPUT,
    selectedTone = null,
    thoughts = "",
    refinedText = null,
    isGenerating = false,
    error = null,
)

// The tone to refine with, or null when a refine/regenerate can't start right now.
internal fun HelpMeRefineUiState.requestableTone(): JournalPromptToneUiState? =
    selectedTone?.takeIf { !isGenerating && hasRemainingToday(remainingToday) }

internal fun HelpMeRefineUiState.requestStarted(): HelpMeRefineUiState = copy(
    isGenerating = true,
    error = null,
)

internal fun HelpMeRefineUiState.applyResult(result: Result<AiPromptResult>): HelpMeRefineUiState = result.fold(
    onSuccess = { prompt ->
        copy(
            step = HelpMeRefineStep.PREVIEW,
            refinedText = prompt.text,
            isGenerating = false,
            error = null,
            remainingToday = prompt.remainingToday,
        )
    },
    onFailure = { throwable ->
        val error = throwable.toAiAssistErrorUiState()
        copy(
            isGenerating = false,
            error = error,
            remainingToday = remainingTodayAfter(error, remainingToday),
        )
    },
)
