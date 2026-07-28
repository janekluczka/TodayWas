package pl.luczka.todaywas.ui.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.data.repository.OnboardingRepository
import pl.luczka.todaywas.domain.model.Focus
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.toUiState
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private class FakeOnboardingRepository(
        initialState: OnboardingState,
    ) : OnboardingRepository {

        private val stateFlow = MutableStateFlow(initialState)

        override fun observeState(): Flow<OnboardingState> = stateFlow

        override suspend fun saveFocus(focus: Focus): Result<Unit> = Result.success(Unit)
    }

    private class FakeJournalRepository(
        initialEntries: List<JournalEntry> = emptyList(),
    ) : JournalRepository {

        private val entriesFlow = MutableStateFlow(initialEntries)

        override fun observeEntries(): Flow<List<JournalEntry>> = entriesFlow

        override suspend fun addEntry(
            date: LocalDate,
            text: String,
        ): Result<Unit> = Result.success(Unit)
    }

    private fun entry(
        date: LocalDate,
        text: String = "entry",
    ) = JournalEntry(id = date.hashCode().toLong(), date = date, text = text, createdAt = Instant.now())

    private fun viewModel(
        focus: Focus? = Focus.JOURNAL,
        entries: List<JournalEntry> = emptyList(),
    ) = MainViewModel(
        observeOnboardingState = ObserveOnboardingStateUseCase(FakeOnboardingRepository(OnboardingState(completed = true, focus = focus))),
        observeJournalEntries = ObserveJournalEntriesUseCase(FakeJournalRepository(entries)),
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
    fun `uiState reflects focus and entries from both sources`() =
        runTest {
            val today = entry(LocalDate.now())
            val viewModel = viewModel(focus = Focus.JOURNAL, entries = listOf(today))

            val state = viewModel.uiState.value

            assertEquals(FocusUiState.JOURNAL, state.focus)
            assertEquals(listOf(today.toUiState()), state.journalEntries)
        }

    @Test
    fun `addableSlots includes both TODAY and YESTERDAY when neither is logged`() =
        runTest {
            val viewModel = viewModel(entries = emptyList())

            assertEquals(listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY), viewModel.uiState.value.addableSlots)
        }

    @Test
    fun `addableSlots excludes TODAY when an entry exists for today`() =
        runTest {
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now())))

            assertEquals(listOf(JournalDateSlotUiState.YESTERDAY), viewModel.uiState.value.addableSlots)
        }

    @Test
    fun `addableSlots excludes YESTERDAY when an entry exists for yesterday`() =
        runTest {
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now().minusDays(1))))

            assertEquals(listOf(JournalDateSlotUiState.TODAY), viewModel.uiState.value.addableSlots)
        }

    @Test
    fun `addableSlots is empty when both today and yesterday are logged`() =
        runTest {
            val entries = listOf(entry(LocalDate.now()), entry(LocalDate.now().minusDays(1)))
            val viewModel = viewModel(entries = entries)

            assertTrue(
                viewModel.uiState.value.addableSlots
                    .isEmpty(),
            )
        }

    @Test
    fun `fabActions includes ADD_JOURNAL_ENTRY when focus is JOURNAL and a slot is addable`() =
        runTest {
            val viewModel = viewModel(focus = Focus.JOURNAL, entries = emptyList())

            assertEquals(listOf(FabActionUiState.ADD_JOURNAL_ENTRY), viewModel.uiState.value.fabActions)
        }

    @Test
    fun `fabActions is empty when no slots are addable`() =
        runTest {
            val entries = listOf(entry(LocalDate.now()), entry(LocalDate.now().minusDays(1)))
            val viewModel = viewModel(focus = Focus.JOURNAL, entries = entries)

            assertTrue(
                viewModel.uiState.value.fabActions
                    .isEmpty(),
            )
        }

    @Test
    fun `fabActions is empty when focus is HABIT`() =
        runTest {
            val viewModel = viewModel(focus = Focus.HABIT, entries = emptyList())

            assertTrue(
                viewModel.uiState.value.fabActions
                    .isEmpty(),
            )
        }

    @Test
    fun `FabActionClicked emits NavigateToAddEntry with the current addableSlots and collapses the fab`() =
        runTest {
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now())))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.FabToggled)
            viewModel.onIntent(MainIntent.FabActionClicked(FabActionUiState.ADD_JOURNAL_ENTRY))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToAddEntry(listOf(JournalDateSlotUiState.YESTERDAY))), events)
            assertEquals(false, viewModel.uiState.value.fabExpanded)
            collectJob.cancel()
        }

    @Test
    fun `FabToggled flips fabExpanded`() =
        runTest {
            val viewModel = viewModel(entries = emptyList())

            viewModel.onIntent(MainIntent.FabToggled)

            assertTrue(viewModel.uiState.value.fabExpanded)
        }

    @Test
    fun `JournalEntryClicked emits NavigateToJournalDetail with the clicked entry`() =
        runTest {
            val today = entry(LocalDate.now())
            val viewModel = viewModel(entries = listOf(today))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.JournalEntryClicked(today.toUiState()))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToJournalDetail(today.toUiState())), events)
            collectJob.cancel()
        }
}
