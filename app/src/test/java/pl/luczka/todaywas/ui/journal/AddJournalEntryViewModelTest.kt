package pl.luczka.todaywas.ui.journal

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
import pl.luczka.todaywas.data.repository.AiAssistRepository
import pl.luczka.todaywas.data.repository.AuthRepository
import pl.luczka.todaywas.data.repository.FakeAiAssistRepository
import pl.luczka.todaywas.data.repository.FakeAuthRepository
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.domain.model.AiAssistError
import pl.luczka.todaywas.domain.model.AiAssistException
import pl.luczka.todaywas.domain.model.AuthState
import pl.luczka.todaywas.domain.usecase.AddJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalStarterPromptUseCase
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState

@OptIn(ExperimentalCoroutinesApi::class)
class AddJournalEntryViewModelTest {

    private fun viewModel(
        repository: JournalRepository = FakeJournalRepository(),
        authRepository: AuthRepository = FakeAuthRepository(
            initialState = AuthState.SignedIn(userId = "1", email = "person@example.com"),
        ),
        aiAssistRepository: AiAssistRepository = FakeAiAssistRepository(),
    ) = AddJournalEntryViewModel(
        observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(repository),
        observeAuthState = ObserveAuthStateUseCase(authRepository),
        addJournalEntry = AddJournalEntryUseCase(repository),
        requestJournalStarterPrompt = RequestJournalStarterPromptUseCase(aiAssistRepository),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should derive both slots as available and default selectedSlot to TODAY on initial state`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            val state = viewModel.uiState.value

            // Assert
            assertEquals(
                listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY),
                state.availableSlots,
            )
            assertEquals(JournalDateSlotUiState.TODAY, state.selectedSlot)
        }

    @Test
    fun `should update selectedSlot when SlotSelected is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.SlotSelected(JournalDateSlotUiState.YESTERDAY))

            // Assert
            assertEquals(JournalDateSlotUiState.YESTERDAY, viewModel.uiState.value.selectedSlot)
        }

    @Test
    fun `should update text when TextChanged is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))

            // Assert
            assertEquals("Today was good.", viewModel.uiState.value.text)
        }

    @Test
    fun `should clear isSaving and emit Saved when SaveClicked succeeds`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))
            val events = mutableListOf<AddJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(AddJournalEntryIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.addEntryCallCount)
            assertEquals(listOf(AddJournalEntryUiEvent.Saved), events)
            collectJob.cancel()
        }

    @Test
    fun `should set saveError and not emit Saved when SaveClicked fails`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository()
            repository.addEntryResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))
            val events = mutableListOf<AddJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(AddJournalEntryIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(events.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `should emit Cancelled when CancelClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            val events = mutableListOf<AddJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(AddJournalEntryIntent.CancelClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(AddJournalEntryUiEvent.Cancelled), events)
            collectJob.cancel()
        }

    @Test
    fun `should not make helpMeStart visible when HelpMeStartClicked is dispatched while signed out`() =
        runTest {
            // Arrange
            val viewModel = viewModel(authRepository = FakeAuthRepository(initialState = AuthState.SignedOut))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeStart.isVisible)
        }

    @Test
    fun `should update selectedTone when ToneSelected is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Act
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))

            // Assert
            assertEquals(JournalPromptToneUiState.GOOD, viewModel.uiState.value.helpMeStart.selectedTone)
        }

    @Test
    fun `should update thoughts when ThoughtsChanged is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Act
            viewModel.onIntent(AddJournalEntryIntent.ThoughtsChanged("made progress on a hard bug"))

            // Assert
            assertEquals("made progress on a hard bug", viewModel.uiState.value.helpMeStart.thoughts)
        }

    @Test
    fun `should move to PREVIEW step and set generatedText when GenerateClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult = Result.success("Generated prompt.")
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(HelpMeStartStep.PREVIEW, helpMeStart.step)
            assertEquals("Generated prompt.", helpMeStart.generatedText)
            assertFalse(helpMeStart.isGenerating)
            assertEquals(0, helpMeStart.regenerationsUsed)
        }

    @Test
    fun `should stay on INPUT step and set error without touching regenerationsUsed when GenerateClicked fails`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult = Result.failure(AiAssistException(AiAssistError.NetworkUnavailable))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(HelpMeStartStep.INPUT, helpMeStart.step)
            assertNull(helpMeStart.generatedText)
            assertEquals(AiAssistErrorUiState.NETWORK_UNAVAILABLE, helpMeStart.error)
            assertEquals(0, helpMeStart.regenerationsUsed)
        }

    @Test
    fun `should increment regenerationsUsed and update generatedText when RegenerateClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult = Result.success("First prompt.")
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            aiAssistRepository.generateResult = Result.success("Second prompt.")

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(1, helpMeStart.regenerationsUsed)
            assertEquals("Second prompt.", helpMeStart.generatedText)
        }

    @Test
    fun `should set error without incrementing regenerationsUsed or losing generatedText when RegenerateClicked fails`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult = Result.success("First prompt.")
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            aiAssistRepository.generateResult = Result.failure(AiAssistException(AiAssistError.UpstreamFailed))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(0, helpMeStart.regenerationsUsed)
            assertEquals("First prompt.", helpMeStart.generatedText)
            assertEquals(AiAssistErrorUiState.UPSTREAM_FAILED, helpMeStart.error)
        }

    @Test
    fun `should not call the repository when RegenerateClicked is dispatched at the cap`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            repeat(3) {
                viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
                runCurrent()
            }
            val callCountAtCap = aiAssistRepository.generateCallCount

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Assert
            assertEquals(3, viewModel.uiState.value.helpMeStart.regenerationsUsed)
            assertEquals(callCountAtCap, aiAssistRepository.generateCallCount)
        }

    @Test
    fun `should copy generatedText into text and reset helpMeStart except regenerationsUsed when UseGeneratedTextClicked`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult = Result.success("Generated prompt.")
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.UseGeneratedTextClicked)

            // Assert
            val state = viewModel.uiState.value
            assertEquals("Generated prompt.", state.text)
            assertFalse(state.helpMeStart.isVisible)
            assertNull(state.helpMeStart.generatedText)
            assertNull(state.helpMeStart.selectedTone)
            assertEquals(1, state.helpMeStart.regenerationsUsed)
        }

    @Test
    fun `should preserve regenerationsUsed but reset other fields when HelpMeStartDismissed is dispatched`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult = Result.success("Generated prompt.")
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.ToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartDismissed)

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertFalse(helpMeStart.isVisible)
            assertNull(helpMeStart.generatedText)
            assertNull(helpMeStart.selectedTone)
            assertEquals(1, helpMeStart.regenerationsUsed)

            // Act again: reopening the dialog must not reset the cap either
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Assert
            assertEquals(1, viewModel.uiState.value.helpMeStart.regenerationsUsed)
        }
}
