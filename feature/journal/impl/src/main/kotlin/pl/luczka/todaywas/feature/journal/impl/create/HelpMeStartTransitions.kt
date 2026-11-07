package pl.luczka.todaywas.feature.journal.impl.create

import pl.luczka.todaywas.domain.aiassist.model.AiPromptResult
import pl.luczka.todaywas.feature.journal.impl.mapper.toAiAssistErrorUiState
import pl.luczka.todaywas.feature.journal.impl.model.JournalPromptToneUiState
import pl.luczka.todaywas.feature.journal.impl.model.hasRemainingToday
import pl.luczka.todaywas.feature.journal.impl.model.remainingTodayAfter

// Preserves remainingToday — see its doc comment on HelpMeStartUiState — so it only resets when a new ViewModel
// instance is created.
internal fun HelpMeStartUiState.resetForNewSession(
    isVisible: Boolean,
    step: HelpMeStartStep = HelpMeStartStep.INPUT,
): HelpMeStartUiState = copy(
    isVisible = isVisible,
    step = step,
    selectedTone = null,
    thoughts = "",
    generatedText = null,
    isGenerating = false,
    error = null,
)

// The tone to generate with, or null when a generate/regenerate can't start right now.
internal fun HelpMeStartUiState.requestableTone(): JournalPromptToneUiState? =
    selectedTone?.takeIf { !isGenerating && hasRemainingToday(remainingToday) }

internal fun HelpMeStartUiState.requestStarted(): HelpMeStartUiState = copy(
    isGenerating = true,
    error = null,
)

internal fun HelpMeStartUiState.applyResult(result: Result<AiPromptResult>): HelpMeStartUiState = result.fold(
    onSuccess = { prompt ->
        copy(
            step = HelpMeStartStep.PREVIEW,
            generatedText = prompt.text,
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
