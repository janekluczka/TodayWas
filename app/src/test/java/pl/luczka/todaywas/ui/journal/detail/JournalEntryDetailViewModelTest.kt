package pl.luczka.todaywas.ui.journal.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.domain.model.AiAssistError
import pl.luczka.todaywas.domain.model.AiAssistException
import pl.luczka.todaywas.domain.model.AiPromptResult
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.AiAssistRepository
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.FakeAiAssistRepository
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.usecase.DeleteJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.IsEditableUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalRefinementPromptUseCase
import pl.luczka.todaywas.domain.usecase.UpdateJournalEntryUseCase
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class JournalEntryDetailViewModelTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")
    private val entry = JournalEntry(
        id = "1",
        date = LocalDate.of(2026, 8, 1),
        text = "Original text.",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun viewModel(
        repository: FakeJournalRepository = FakeJournalRepository(initialEntries = listOf(entry)),
        clock: Clock = Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneOffset.UTC),
        authRepository: AuthRepository = FakeAuthRepository(
            initialState = AuthState.SignedIn(userId = "1", email = "person@example.com"),
        ),
        aiAssistRepository: AiAssistRepository = FakeAiAssistRepository(),
        id: String = "1",
    ) = JournalEntryDetailViewModel(
        id = id,
        getJournalEntry = GetJournalEntryUseCase(repository),
        updateJournalEntry = UpdateJournalEntryUseCase(repository, clock),
        deleteJournalEntry = DeleteJournalEntryUseCase(repository),
        observeAuthState = ObserveAuthStateUseCase(authRepository),
        requestJournalRefinementPrompt = RequestJournalRefinementPromptUseCase(aiAssistRepository),
        isEditable = IsEditableUseCase(clock),
    )

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceTo(instant: Instant) {
            current = instant
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should load the entry and populate isEditable when constructed within the window`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))

            // Act
            val viewModel = viewModel(repository)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(
                "Original text.",
                viewModel.uiState.value.entry
                    ?.text,
            )
            assertTrue(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `should derive isEditable false when constructed past the 24h window`() =
        runTest {
            // Arrange
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneOffset.UTC)

            // Act
            val viewModel = viewModel(clock = expiredClock)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `should update entry text and clear isEditing when edit then save succeeds`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            runCurrent()

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.TextChanged("Edited text."))
            viewModel.onIntent(JournalEntryDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.isEditing)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(
                "Edited text.",
                viewModel.uiState.value.entry
                    ?.text,
            )
        }

    @Test
    fun `should keep isEditing true and set saveError when save fails generically`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            repository.updateEntryResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            runCurrent()

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.TextChanged("Edited text."))
            viewModel.onIntent(JournalEntryDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.isEditing)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `should clear isEditing and isEditable and set saveError when save fails with an expired window`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.TextChanged("Edited text."))
            // Stub the repository to return the same failure UpdateJournalEntryUseCase would
            // produce on a real mid-session expiry, isolating the ViewModel's own branching logic.
            repository.updateEntryResult = Result.failure(EditWindowExpiredException())

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.isEditing)
            assertFalse(viewModel.uiState.value.isEditable)
            assertTrue(viewModel.uiState.value.saveError)
        }

    @Test
    fun `should show isDeleteDialogVisible when DeleteClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)

            // Assert
            assertTrue(viewModel.uiState.value.isDeleteDialogVisible)
        }

    @Test
    fun `should hide isDeleteDialogVisible when DeleteDismissed is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteDismissed)

            // Assert
            assertFalse(viewModel.uiState.value.isDeleteDialogVisible)
        }

    @Test
    fun `should delete and emit NavigatedBack when DeleteConfirmed succeeds`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)
            val events = mutableListOf<JournalEntryDetailUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteConfirmed)
            runCurrent()

            // Assert
            assertEquals(1, repository.deleteEntryCallCount)
            assertEquals("1", repository.lastDeletedId)
            assertEquals(listOf(JournalEntryDetailUiEvent.NavigatedBack), events)
            collectJob.cancel()
        }

    @Test
    fun `should succeed regardless of the entry's age, unlike Save`() =
        runTest {
            // Arrange - entry is well past its 24h edit window, unlike Save's expiry tests
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneOffset.UTC)
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository, clock = expiredClock)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteConfirmed)
            runCurrent()

            // Assert
            assertEquals(1, repository.deleteEntryCallCount)
        }

    @Test
    fun `should hide the dialog and set deleteError when DeleteConfirmed fails`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            repository.deleteEntryResult = Result.failure(RuntimeException("delete failed"))
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)
            val events = mutableListOf<JournalEntryDetailUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteConfirmed)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isDeleting)
            assertFalse(viewModel.uiState.value.isDeleteDialogVisible)
            assertTrue(viewModel.uiState.value.deleteError)
            assertEquals(emptyList<JournalEntryDetailUiEvent>(), events)
            collectJob.cancel()
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while signed out`() =
        runTest {
            // Arrange
            val viewModel =
                viewModel(authRepository = FakeAuthRepository(initialState = AuthState.SignedOut))
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while not editing`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while the draft is blank`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(
                initialEntries = listOf(entry.copy(text = "")),
            )
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while isEditable is stale-false`() =
        runTest {
            // Arrange
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneOffset.UTC)
            val viewModel = viewModel(clock = expiredClock)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while the draft exceeds MAX_REFINE_TEXT_LENGTH`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(
                initialEntries = listOf(entry.copy(text = "a".repeat(MAX_REFINE_TEXT_LENGTH + 1))),
            )
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should move to PREVIEW step and set refinedText when RefineClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult = Result.success(AiPromptResult(text = "Refined text.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)
            viewModel.onIntent(JournalEntryDetailIntent.ToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.RefineClicked)
            runCurrent()

            // Assert
            val helpMeRefine = viewModel.uiState.value.helpMeRefine
            assertEquals(HelpMeRefineStep.PREVIEW, helpMeRefine.step)
            assertEquals("Refined text.", helpMeRefine.refinedText)
            assertFalse(helpMeRefine.isGenerating)
            assertEquals(0, helpMeRefine.regenerationsUsed)
            assertEquals("Original text.", aiAssistRepository.lastRefineText)
        }

    @Test
    fun `should stay on INPUT step and set error without touching regenerationsUsed when RefineClicked fails`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult =
                Result.failure(AiAssistException(AiAssistError.NetworkUnavailable))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)
            viewModel.onIntent(JournalEntryDetailIntent.ToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.RefineClicked)
            runCurrent()

            // Assert
            val helpMeRefine = viewModel.uiState.value.helpMeRefine
            assertEquals(HelpMeRefineStep.INPUT, helpMeRefine.step)
            assertNull(helpMeRefine.refinedText)
            assertEquals(AiAssistErrorUiState.NETWORK_UNAVAILABLE, helpMeRefine.error)
            assertEquals(0, helpMeRefine.regenerationsUsed)
        }

    @Test
    fun `should increment regenerationsUsed and update refinedText when RegenerateRefineClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult = Result.success(AiPromptResult(text = "First refinement.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)
            viewModel.onIntent(JournalEntryDetailIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(JournalEntryDetailIntent.RefineClicked)
            runCurrent()
            aiAssistRepository.refineResult = Result.success(AiPromptResult(text = "Second refinement.", remainingToday = 9))

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.RegenerateRefineClicked)
            runCurrent()

            // Assert
            val helpMeRefine = viewModel.uiState.value.helpMeRefine
            assertEquals(1, helpMeRefine.regenerationsUsed)
            assertEquals("Second refinement.", helpMeRefine.refinedText)
        }

    @Test
    fun `should not call the repository when RegenerateRefineClicked is dispatched at the cap`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)
            viewModel.onIntent(JournalEntryDetailIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(JournalEntryDetailIntent.RefineClicked)
            runCurrent()
            repeat(3) {
                viewModel.onIntent(JournalEntryDetailIntent.RegenerateRefineClicked)
                runCurrent()
            }
            val callCountAtCap = aiAssistRepository.refineCallCount

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.RegenerateRefineClicked)
            runCurrent()

            // Assert
            assertEquals(3, viewModel.uiState.value.helpMeRefine.regenerationsUsed)
            assertEquals(callCountAtCap, aiAssistRepository.refineCallCount)
        }

    @Test
    fun `should copy refinedText into editedText without touching entry when UseRefinedTextClicked`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult = Result.success(AiPromptResult(text = "Refined text.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)
            viewModel.onIntent(JournalEntryDetailIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(JournalEntryDetailIntent.RefineClicked)
            runCurrent()

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.UseRefinedTextClicked)

            // Assert
            val state = viewModel.uiState.value
            assertEquals("Refined text.", state.editedText)
            assertEquals("Original text.", state.entry?.text)
            assertFalse(state.helpMeRefine.isVisible)
            assertNull(state.helpMeRefine.refinedText)
        }

    @Test
    fun `should discard the result and mirror Save's expired-window handling when the edit window closes during a refine`() =
        runTest {
            // Arrange
            val clock = MutableClock(createdAt.plus(Duration.ofHours(1)))
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult = Result.success(AiPromptResult(text = "Refined text.", remainingToday = 9))
            val viewModel = viewModel(clock = clock, aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.HelpMeRefineClicked)
            viewModel.onIntent(JournalEntryDetailIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            clock.advanceTo(createdAt.plus(Duration.ofHours(25)))

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.RefineClicked)
            runCurrent()

            // Assert
            val state = viewModel.uiState.value
            assertFalse(state.isEditing)
            assertFalse(state.isEditable)
            assertTrue(state.saveError)
            assertFalse(state.helpMeRefine.isVisible)
            assertNull(state.helpMeRefine.refinedText)
        }
}
