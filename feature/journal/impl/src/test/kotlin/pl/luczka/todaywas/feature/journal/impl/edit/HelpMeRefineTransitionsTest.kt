package pl.luczka.todaywas.feature.journal.impl.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.luczka.todaywas.domain.aiassist.model.AiAssistError
import pl.luczka.todaywas.domain.aiassist.model.AiAssistException
import pl.luczka.todaywas.domain.aiassist.model.AiPromptResult
import pl.luczka.todaywas.feature.journal.impl.model.AiAssistErrorUiState
import pl.luczka.todaywas.feature.journal.impl.model.JournalPromptToneUiState

class HelpMeRefineTransitionsTest {

    private val inPreview = HelpMeRefineUiState(
        isVisible = true,
        step = HelpMeRefineStep.PREVIEW,
        selectedTone = JournalPromptToneUiState.GOOD,
        thoughts = "Shorter.",
        refinedText = "Refined text.",
        isGenerating = true,
        error = AiAssistErrorUiState.UPSTREAM_FAILED,
        remainingToday = 2,
    )

    @Test
    fun `should clear the session but keep remainingToday when resetForNewSession`() {
        // Act
        val reset = inPreview.resetForNewSession(isVisible = true)

        // Assert
        assertEquals(
            HelpMeRefineUiState(
                isVisible = true,
                remainingToday = 2,
            ),
            reset,
        )
    }

    @Test
    fun `should return the selected tone when requestableTone and the quota is unknown`() {
        // Arrange
        val state = HelpMeRefineUiState(selectedTone = JournalPromptToneUiState.NEUTRAL)

        // Act
        val tone = state.requestableTone()

        // Assert
        assertEquals(JournalPromptToneUiState.NEUTRAL, tone)
    }

    @Test
    fun `should return null when requestableTone and no tone is selected`() {
        // Arrange
        val state = HelpMeRefineUiState(remainingToday = 3)

        // Act
        val tone = state.requestableTone()

        // Assert
        assertNull(tone)
    }

    @Test
    fun `should return null when requestableTone and a request is in flight`() {
        // Arrange
        val state = HelpMeRefineUiState(
            selectedTone = JournalPromptToneUiState.NEUTRAL,
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
        val state = HelpMeRefineUiState(
            selectedTone = JournalPromptToneUiState.NEUTRAL,
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
        val state = HelpMeRefineUiState(error = AiAssistErrorUiState.UNKNOWN)

        // Act
        val started = state.requestStarted()

        // Assert
        assertEquals(HelpMeRefineUiState(isGenerating = true), started)
    }

    @Test
    fun `should move to PREVIEW with the text and remainingToday when applyResult succeeds`() {
        // Act
        val applied = inPreview.applyResult(
            Result.success(
                AiPromptResult(
                    text = "New text.",
                    remainingToday = 1,
                ),
            ),
        )

        // Assert
        assertEquals(
            inPreview.copy(
                refinedText = "New text.",
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
