package pl.luczka.todaywas.feature.journal.impl.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.luczka.todaywas.core.domain.model.AiAssistError
import pl.luczka.todaywas.core.domain.model.AiAssistException
import pl.luczka.todaywas.core.domain.model.AiPromptResult
import pl.luczka.todaywas.feature.journal.impl.model.AiAssistErrorUiState
import pl.luczka.todaywas.feature.journal.impl.model.JournalPromptToneUiState

class HelpMeStartTransitionsTest {

    private val inPreview = HelpMeStartUiState(
        isVisible = true,
        step = HelpMeStartStep.PREVIEW,
        selectedTone = JournalPromptToneUiState.GOOD,
        thoughts = "A walk.",
        generatedText = "Generated prompt.",
        isGenerating = true,
        error = AiAssistErrorUiState.UPSTREAM_FAILED,
        remainingToday = 2,
    )

    @Test
    fun `should clear the session but keep remainingToday when resetForNewSession`() {
        // Act
        val reset = inPreview.resetForNewSession(isVisible = false)

        // Assert
        assertEquals(HelpMeStartUiState(remainingToday = 2), reset)
    }

    @Test
    fun `should open on the given step when resetForNewSession with a step`() {
        // Act
        val reset = inPreview.resetForNewSession(
            isVisible = true,
            step = HelpMeStartStep.SIGNED_OUT,
        )

        // Assert
        assertEquals(
            HelpMeStartUiState(
                isVisible = true,
                step = HelpMeStartStep.SIGNED_OUT,
                remainingToday = 2,
            ),
            reset,
        )
    }

    @Test
    fun `should return the selected tone when requestableTone and the quota is unknown`() {
        // Arrange
        val state = HelpMeStartUiState(selectedTone = JournalPromptToneUiState.BAD)

        // Act
        val tone = state.requestableTone()

        // Assert
        assertEquals(JournalPromptToneUiState.BAD, tone)
    }

    @Test
    fun `should return null when requestableTone and no tone is selected`() {
        // Arrange
        val state = HelpMeStartUiState(remainingToday = 3)

        // Act
        val tone = state.requestableTone()

        // Assert
        assertNull(tone)
    }

    @Test
    fun `should return null when requestableTone and a request is in flight`() {
        // Arrange
        val state = HelpMeStartUiState(
            selectedTone = JournalPromptToneUiState.BAD,
            isGenerating = true,
        )

        // Act
        val tone = state.requestableTone()

        // Assert
        assertNull(tone)
    }

    @Test
    fun `should return null when requestableTone and none remain today`() {
        // Arrange
        val state = HelpMeStartUiState(
            selectedTone = JournalPromptToneUiState.BAD,
            remainingToday = 0,
        )

        // Act
        val tone = state.requestableTone()

        // Assert
        assertNull(tone)
    }

    @Test
    fun `should set isGenerating and clear error when requestStarted`() {
        // Arrange
        val state = HelpMeStartUiState(error = AiAssistErrorUiState.UNKNOWN)

        // Act
        val started = state.requestStarted()

        // Assert
        assertEquals(HelpMeStartUiState(isGenerating = true), started)
    }

    @Test
    fun `should move to PREVIEW with the text and remainingToday when applyResult succeeds`() {
        // Act
        val applied = inPreview.applyResult(
            Result.success(
                AiPromptResult(
                    text = "New prompt.",
                    remainingToday = 1,
                ),
            ),
        )

        // Assert
        assertEquals(
            inPreview.copy(
                generatedText = "New prompt.",
                isGenerating = false,
                error = null,
                remainingToday = 1,
            ),
            applied,
        )
    }

    @Test
    fun `should set the error and keep remainingToday when applyResult fails`() {
        // Act
        val applied = inPreview.applyResult(Result.failure(AiAssistException(AiAssistError.NetworkUnavailable)))

        // Assert
        assertEquals(
            inPreview.copy(
                isGenerating = false,
                error = AiAssistErrorUiState.NETWORK_UNAVAILABLE,
            ),
            applied,
        )
    }

    @Test
    fun `should set remainingToday to 0 when applyResult fails with the daily limit`() {
        // Act
        val applied = inPreview.applyResult(Result.failure(AiAssistException(AiAssistError.DailyLimitReached)))

        // Assert
        assertEquals(
            inPreview.copy(
                isGenerating = false,
                error = AiAssistErrorUiState.DAILY_LIMIT_REACHED,
                remainingToday = 0,
            ),
            applied,
        )
    }

    @Test
    fun `should set the UNKNOWN error when applyResult fails with another exception`() {
        // Act
        val applied = inPreview.applyResult(Result.failure(IllegalStateException()))

        // Assert
        assertEquals(AiAssistErrorUiState.UNKNOWN, applied.error)
    }
}
