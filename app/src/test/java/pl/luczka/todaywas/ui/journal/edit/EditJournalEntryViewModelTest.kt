package pl.luczka.todaywas.ui.journal.edit

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
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.IsEditableUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAuthStateUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalRefinementPromptUseCase
import pl.luczka.todaywas.domain.usecase.RequestJournalStarterPromptUseCase
import pl.luczka.todaywas.domain.usecase.UpdateJournalEntryUseCase
import pl.luczka.todaywas.ui.journal.create.STARTER_PROMPT_VARIANT_COUNT
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class EditJournalEntryViewModelTest {

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
        random: Random = Random(0),
        id: String = "1",
    ) = EditJournalEntryViewModel(
        id = id,
        getJournalEntry = GetJournalEntryUseCase(repository),
        updateJournalEntry = UpdateJournalEntryUseCase(repository, clock),
        observeAuthState = ObserveAuthStateUseCase(authRepository),
        requestJournalStarterPrompt = RequestJournalStarterPromptUseCase(aiAssistRepository),
        requestJournalRefinementPrompt = RequestJournalRefinementPromptUseCase(aiAssistRepository),
        isEditable = IsEditableUseCase(clock),
        random = random,
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
    fun `should load the entry and prefill text when constructed`() =
        runTest {
            // Arrange & Act
            val viewModel = viewModel()
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("Original text.", viewModel.uiState.value.text)
            assertEquals(
                "1",
                viewModel.uiState.value.entry
                    ?.id,
            )
        }

    @Test
    fun `should expose one starterPrompts entry per tone with a variant picked from random on initial state`() =
        runTest {
            // Arrange & Act
            val viewModel = viewModel(random = Random(seed = 42))

            // Assert
            val prompts = viewModel.uiState.value.starterPrompts
            assertTrue(prompts.all { it.variant in 0 until STARTER_PROMPT_VARIANT_COUNT })
        }

    @Test
    fun `should update text when TextChanged is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()

            // Act
            viewModel.onIntent(EditJournalEntryIntent.TextChanged("Edited text."))

            // Assert
            assertEquals("Edited text.", viewModel.uiState.value.text)
        }

    @Test
    fun `should update the entry and emit Saved when SaveClicked succeeds`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.TextChanged("Edited text."))
            val events = mutableListOf<EditJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(EditJournalEntryIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.updateEntryCallCount)
            assertEquals("Edited text.", repository.lastUpdatedText)
            assertEquals(listOf(EditJournalEntryUiEvent.Saved), events)
            collectJob.cancel()
        }

    @Test
    fun `should set saveError and not emit an event when SaveClicked fails generically`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            repository.updateEntryResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            runCurrent()
            val events = mutableListOf<EditJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(EditJournalEntryIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(events.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `should emit Discarded without saveError when SaveClicked fails with an expired window`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            repository.updateEntryResult = Result.failure(EditWindowExpiredException())
            val viewModel = viewModel(repository)
            runCurrent()
            val events = mutableListOf<EditJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(EditJournalEntryIntent.SaveClicked)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(listOf(EditJournalEntryUiEvent.Discarded), events)
            collectJob.cancel()
        }

    @Test
    fun `should emit Discarded directly when BackClicked with unchanged text`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()
            val events = mutableListOf<EditJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(EditJournalEntryIntent.BackClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(EditJournalEntryUiEvent.Discarded), events)
            assertFalse(viewModel.uiState.value.isDiscardConfirmVisible)
            collectJob.cancel()
        }

    @Test
    fun `should show the discard dialog when BackClicked with changed text`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.TextChanged("Edited text."))
            val events = mutableListOf<EditJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(EditJournalEntryIntent.BackClicked)

            // Assert
            assertTrue(viewModel.uiState.value.isDiscardConfirmVisible)
            assertTrue(events.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `should hide the discard dialog when DiscardDismissed is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.TextChanged("Edited text."))
            viewModel.onIntent(EditJournalEntryIntent.BackClicked)

            // Act
            viewModel.onIntent(EditJournalEntryIntent.DiscardDismissed)

            // Assert
            assertFalse(viewModel.uiState.value.isDiscardConfirmVisible)
        }

    @Test
    fun `should emit Discarded when DiscardConfirmed is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.TextChanged("Edited text."))
            viewModel.onIntent(EditJournalEntryIntent.BackClicked)
            val events = mutableListOf<EditJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(EditJournalEntryIntent.DiscardConfirmed)
            runCurrent()

            // Assert
            assertEquals(listOf(EditJournalEntryUiEvent.Discarded), events)
            collectJob.cancel()
        }

    @Test
    fun `should open helpMeStart at SIGNED_OUT step when HelpMeStartClicked is dispatched while signed out`() =
        runTest {
            // Arrange
            val viewModel =
                viewModel(authRepository = FakeAuthRepository(initialState = AuthState.SignedOut))
            runCurrent()

            // Act
            viewModel.onIntent(EditJournalEntryIntent.HelpMeStartClicked)

            // Assert
            assertTrue(viewModel.uiState.value.helpMeStart.isVisible)
        }

    @Test
    fun `should move helpMeStart to PREVIEW step and set generatedText when GenerateClicked succeeds`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "Generated prompt.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD),
            )

            // Act
            viewModel.onIntent(EditJournalEntryIntent.GenerateClicked)
            runCurrent()

            // Assert
            val helpMeStart = viewModel.uiState.value.helpMeStart
            assertEquals("Generated prompt.", helpMeStart.generatedText)
            assertEquals(9, helpMeStart.remainingToday)
        }

    @Test
    fun `should copy generatedText into text when UseGeneratedTextClicked`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.generateResult =
                Result.success(AiPromptResult(text = "Generated prompt.", remainingToday = 9))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.HelpMeStartClicked)
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeStartToneSelected(JournalPromptToneUiState.GOOD),
            )
            viewModel.onIntent(EditJournalEntryIntent.GenerateClicked)
            runCurrent()

            // Act
            viewModel.onIntent(EditJournalEntryIntent.UseGeneratedTextClicked)

            // Assert
            assertEquals("Generated prompt.", viewModel.uiState.value.text)
            assertFalse(viewModel.uiState.value.helpMeStart.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while signed out`() =
        runTest {
            // Arrange
            val viewModel =
                viewModel(authRepository = FakeAuthRepository(initialState = AuthState.SignedOut))
            runCurrent()

            // Act
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should not make helpMeRefine visible when HelpMeRefineClicked is dispatched while the draft is blank`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry.copy(text = "")))
            val viewModel = viewModel(repository)
            runCurrent()

            // Act
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)

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

            // Act
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)

            // Assert
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should make helpMeRefine visible when HelpMeRefineClicked is dispatched while signed in with a valid draft`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            runCurrent()

            // Act
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)

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
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeRefineThoughtsChanged("make it shorter"),
            )

            // Act
            viewModel.onIntent(EditJournalEntryIntent.RefineClicked)
            runCurrent()

            // Assert
            val helpMeRefine = viewModel.uiState.value.helpMeRefine
            assertEquals(HelpMeRefineStep.PREVIEW, helpMeRefine.step)
            assertEquals("Refined text.", helpMeRefine.refinedText)
            assertEquals(9, helpMeRefine.remainingToday)
            assertEquals("Original text.", aiAssistRepository.lastRefineText)
            assertEquals("make it shorter", aiAssistRepository.lastRefineThoughts)
        }

    @Test
    fun `should stay on INPUT step and set error without touching remainingToday when RefineClicked fails`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult =
                Result.failure(AiAssistException(AiAssistError.NetworkUnavailable))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )

            // Act
            viewModel.onIntent(EditJournalEntryIntent.RefineClicked)
            runCurrent()

            // Assert
            val helpMeRefine = viewModel.uiState.value.helpMeRefine
            assertEquals(HelpMeRefineStep.INPUT, helpMeRefine.step)
            assertNull(helpMeRefine.refinedText)
            assertEquals(AiAssistErrorUiState.NETWORK_UNAVAILABLE, helpMeRefine.error)
            assertNull(helpMeRefine.remainingToday)
        }

    @Test
    fun `should not call the repository when RegenerateRefineClicked is dispatched after remainingToday reaches 0`() =
        runTest {
            // Arrange
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult =
                Result.success(AiPromptResult(text = "Last refinement.", remainingToday = 0))
            val viewModel = viewModel(aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )
            viewModel.onIntent(EditJournalEntryIntent.RefineClicked)
            runCurrent()
            val callCountAtZero = aiAssistRepository.refineCallCount

            // Act
            viewModel.onIntent(EditJournalEntryIntent.RegenerateRefineClicked)
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
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )
            viewModel.onIntent(EditJournalEntryIntent.RefineClicked)
            runCurrent()

            // Act
            viewModel.onIntent(EditJournalEntryIntent.UseRefinedTextClicked)

            // Assert
            assertEquals("Refined text.", viewModel.uiState.value.text)
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
        }

    @Test
    fun `should discard the result and emit Discarded when the edit window closes during a refine`() =
        runTest {
            // Arrange
            val clock = MutableClock(createdAt.plus(Duration.ofHours(1)))
            val aiAssistRepository = FakeAiAssistRepository()
            aiAssistRepository.refineResult =
                Result.success(AiPromptResult(text = "Refined text.", remainingToday = 9))
            val viewModel = viewModel(clock = clock, aiAssistRepository = aiAssistRepository)
            runCurrent()
            viewModel.onIntent(EditJournalEntryIntent.HelpMeRefineClicked)
            viewModel.onIntent(
                EditJournalEntryIntent.HelpMeRefineToneSelected(JournalPromptToneUiState.GOOD),
            )
            val events = mutableListOf<EditJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }
            clock.advanceTo(createdAt.plus(Duration.ofHours(25)))

            // Act
            viewModel.onIntent(EditJournalEntryIntent.RefineClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(EditJournalEntryUiEvent.Discarded), events)
            assertFalse(viewModel.uiState.value.helpMeRefine.isVisible)
            assertNull(viewModel.uiState.value.helpMeRefine.refinedText)
            collectJob.cancel()
        }
}
