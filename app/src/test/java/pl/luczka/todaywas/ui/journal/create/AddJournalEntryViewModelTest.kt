package pl.luczka.todaywas.ui.journal.create

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import pl.luczka.todaywas.domain.repository.AiAssistRepository
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.FakeAiAssistRepository
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.repository.JournalRepository
import pl.luczka.todaywas.domain.usecase.AddJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalRefinementPromptUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalStarterPromptUseCase
import pl.luczka.todaywas.ui.journal.edit.HelpMeRefineStep
import pl.luczka.todaywas.ui.journal.edit.MAX_REFINE_TEXT_LENGTH
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class AddJournalEntryViewModelTest {

    private fun viewModel(
        repository: JournalRepository = FakeJournalRepository(),
        authRepository: AuthRepository = FakeAuthRepository(
            initialState = AuthState.SignedIn(userId = "1", email = "person@example.com"),
        ),
        aiAssistRepository: AiAssistRepository = FakeAiAssistRepository(),
        random: Random = Random(0),
    ) = AddJournalEntryViewModel(
        observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(repository),
        observeAuthState = ObserveAuthStateUseCase(authRepository),
        addJournalEntry = AddJournalEntryUseCase(repository),
        requestJournalStarterPrompt = RequestJournalStarterPromptUseCase(aiAssistRepository),
        requestJournalRefinementPrompt = RequestJournalRefinementPromptUseCase(aiAssistRepository),
        random = random,
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
    fun `should expose one starterPrompts entry per tone with a variant picked from random on initial state`() =
        runTest {
            // Arrange & Act
            val viewModel = viewModel(random = Random(seed = 42))

            // Assert
            val prompts = viewModel.uiState.value.starterPrompts
            assertEquals(
                JournalStarterPromptTone.entries.toSet(),
                prompts.map { it.tone }.toSet(),
            )
            assertTrue(prompts.all { it.variant in 0 until STARTER_PROMPT_VARIANT_COUNT })
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
    fun `should open helpMeStart at SIGNED_OUT step when HelpMeStartClicked is dispatched while signed out`() =
        runTest {
            // Arrange
            val viewModel =
                viewModel(authRepository = FakeAuthRepository(initialState = AuthState.SignedOut))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Assert
            assertTrue(viewModel.uiState.value.helpMeStart.isVisible)
            assertEquals(HelpMeStartStep.SIGNED_OUT, viewModel.uiState.value.helpMeStart.step)
        }

    @Test
    fun `should open helpMeStart at INPUT step when HelpMeStartClicked is dispatched while signed in`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Assert
            assertTrue(viewModel.uiState.value.helpMeStart.isVisible)
            assertEquals(HelpMeStartStep.INPUT, viewModel.uiState.value.helpMeStart.step)
        }

    @Test
    fun `should hide helpMeStart and emit NavigateToSignIn when SignInClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel =
                viewModel(authRepository = FakeAuthRepository(initialState = AuthState.SignedOut))
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            val events = mutableListOf<AddJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(AddJournalEntryIntent.SignInClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.helpMeStart.isVisible)
            assertEquals(listOf(AddJournalEntryUiEvent.NavigateToSignIn), events)
            collectJob.cancel()
        }

    @Test
    fun `should update selectedTone when HelpMeStartToneSelected is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))

            // Assert
            assertEquals(
                JournalPromptToneUiState.GOOD,
                viewModel.uiState.value.helpMeStart.selectedTone,
            )
        }

    @Test
    fun `should update thoughts when HelpMeStartThoughtsChanged is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Act
            viewModel.onIntent(
                AddJournalEntryIntent.HelpMeStartThoughtsChanged("made progress on a hard bug"),
            )

            // Assert
            assertEquals(
                "made progress on a hard bug",
                viewModel.uiState.value.helpMeStart.thoughts,
            )
        }

    @Test
    fun `should move to PREVIEW step and set generatedText and remainingToday when GenerateClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "Generated prompt.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(HelpMeStartStep.PREVIEW, helpMeStart.step)
            assertEquals("Generated prompt.", helpMeStart.generatedText)
            assertFalse(helpMeStart.isGenerating)
            assertEquals(9, helpMeStart.remainingToday)
        }

    @Test
    fun `should call the repository only once when GenerateClicked is dispatched twice before the first call completes`() =
        runTest {
            // Arrange
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val aiAssistRepository = FakeAiAssistRepository()
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            advanceUntilIdle()

            // Assert
            assertEquals(1, aiAssistRepository.generateCallCount)
        }

    @Test
    fun `should stay on INPUT step and set error without touching remainingToday when GenerateClicked fails`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.failure(AiAssistException(AiAssistError.NetworkUnavailable))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(HelpMeStartStep.INPUT, helpMeStart.step)
            assertNull(helpMeStart.generatedText)
            assertEquals(AiAssistErrorUiState.NETWORK_UNAVAILABLE, helpMeStart.error)
            assertNull(helpMeStart.remainingToday)
        }

    @Test
    fun `should set remainingToday to 0 when GenerateClicked fails with DailyLimitReached`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.failure(AiAssistException(AiAssistError.DailyLimitReached))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(AiAssistErrorUiState.DAILY_LIMIT_REACHED, helpMeStart.error)
            assertEquals(0, helpMeStart.remainingToday)
        }

    @Test
    fun `should update remainingToday and generatedText when RegenerateClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "First prompt.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "Second prompt.", remainingToday = 8))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(8, helpMeStart.remainingToday)
            assertEquals("Second prompt.", helpMeStart.generatedText)
        }

    @Test
    fun `should set error without changing remainingToday or losing generatedText when RegenerateClicked fails`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "First prompt.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            aiAssistRepository.generateResult =
                Result.failure(AiAssistException(AiAssistError.UpstreamFailed))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals(9, helpMeStart.remainingToday)
            assertEquals("First prompt.", helpMeStart.generatedText)
            assertEquals(AiAssistErrorUiState.UPSTREAM_FAILED, helpMeStart.error)
        }

    @Test
    fun `should not call the repository when RegenerateClicked is dispatched after remainingToday reaches 0`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "Last prompt.", remainingToday = 0))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            val callCountAfterFirstGenerate = aiAssistRepository.generateCallCount

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Assert
            assertEquals(0, viewModel.uiState.value.helpMeStart.remainingToday)
            assertEquals(callCountAfterFirstGenerate, aiAssistRepository.generateCallCount)
        }

    @Test
    fun `should copy generatedText into text and reset helpMeStart except remainingToday when UseGeneratedTextClicked`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "First prompt.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "Generated prompt.", remainingToday = 8))
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
            assertEquals(8, state.helpMeStart.remainingToday)
        }

    @Test
    fun `should preserve remainingToday but reset other fields when HelpMeStartDismissed is dispatched`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "First prompt.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD))
            viewModel.onIntent(AddJournalEntryIntent.GenerateClicked)
            runCurrent()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "Second prompt.", remainingToday = 8))
            viewModel.onIntent(AddJournalEntryIntent.RegenerateClicked)
            runCurrent()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartDismissed)

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertFalse(helpMeStart.isVisible)
            assertNull(helpMeStart.generatedText)
            assertNull(helpMeStart.selectedTone)
            assertEquals(8, helpMeStart.remainingToday)

            // Act again: reopening the sheet must not reset the known remainingToday either
            viewModel.onIntent(AddJournalEntryIntent.HelpMeStartClicked)

            // Assert
            assertEquals(8, viewModel.uiState.value.helpMeStart.remainingToday)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while signed out`() =
        runTest {
            // Arrange
            val viewModel =
                viewModel(authRepository = FakeAuthRepository(initialState = AuthState.SignedOut))
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while the draft is blank`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while the draft exceeds MAX_REFINE_TEXT_LENGTH`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(
                AddJournalEntryIntent.TextChanged("a".repeat(MAX_REFINE_TEXT_LENGTH + 1)),
            )

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should make helpMeRefine visible when HelpMeRefineClicked is dispatched while signed in with a valid draft`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))

            // Act
            viewModel.onIntent(AddJournalEntryIntent.HelpMeRefineClicked)

            // Assert
            assertTrue(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should move to PREVIEW step, set refinedText and remainingToday, and pass thoughts through when RefineClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult =
                Result.success(AiPromptResult(text = "Refined text.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))
            viewModel.onIntent(AddJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                AddJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )
            viewModel.onIntent(
                AddJournalEntryIntent.HelpMeRefineThoughtsChanged("make it shorter"),
            )

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RefineClicked)
            runCurrent()

            // Assert
            val helpMeRefine = viewModel.uiState.value.helpMeRefine
            assertEquals(HelpMeRefineStep.PREVIEW, helpMeRefine.step)
            assertEquals("Refined text.", helpMeRefine.refinedText)
            assertEquals(9, helpMeRefine.remainingToday)
            assertEquals("Today was good.", aiAssistRepository.lastRefineText)
            assertEquals("make it shorter", aiAssistRepository.lastRefineThoughts)
        }

    @Test
    fun `should not call the repository when RegenerateRefineClicked is dispatched after remainingToday reaches 0`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult =
                Result.success(AiPromptResult(text = "Last refinement.", remainingToday = 0))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))
            viewModel.onIntent(AddJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                AddJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )
            viewModel.onIntent(AddJournalEntryIntent.RefineClicked)
            runCurrent()
            val callCountAtZero = aiAssistRepository.refineCallCount

            // Act
            viewModel.onIntent(AddJournalEntryIntent.RegenerateRefineClicked)
            runCurrent()

            // Assert
            assertEquals(0, viewModel.uiState.value.helpMeRefine.remainingToday)
            assertEquals(callCountAtZero, aiAssistRepository.refineCallCount)
        }

    @Test
    fun `should copy refinedText into text when UseRefinedTextClicked`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult =
                Result.success(AiPromptResult(text = "Refined text.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))
            viewModel.onIntent(AddJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                AddJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )
            viewModel.onIntent(AddJournalEntryIntent.RefineClicked)
            runCurrent()

            // Act
            viewModel.onIntent(AddJournalEntryIntent.UseRefinedTextClicked)

            // Assert
            assertEquals("Refined text.", viewModel.uiState.value.text)
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }
}
