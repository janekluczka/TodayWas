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
import pl.luczka.todaywas.domain.model.JournalDateSlot
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.model.OnboardingState
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.domain.usecase.ObserveOnboardingStateUseCase
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

            assertEquals(Focus.JOURNAL, state.focus)
            assertEquals(listOf(today), state.journalEntries)
        }

    @Test
    fun `addableSlots includes both TODAY and YESTERDAY when neither is logged`() =
        runTest {
            val viewModel = viewModel(entries = emptyList())

            assertEquals(listOf(JournalDateSlot.TODAY, JournalDateSlot.YESTERDAY), viewModel.uiState.value.addableSlots)
        }

    @Test
    fun `addableSlots excludes TODAY when an entry exists for today`() =
        runTest {
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now())))

            assertEquals(listOf(JournalDateSlot.YESTERDAY), viewModel.uiState.value.addableSlots)
        }

    @Test
    fun `addableSlots excludes YESTERDAY when an entry exists for yesterday`() =
        runTest {
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now().minusDays(1))))

            assertEquals(listOf(JournalDateSlot.TODAY), viewModel.uiState.value.addableSlots)
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
    fun `AddEntryClicked emits NavigateToAddEntry with the current addableSlots`() =
        runTest {
            val viewModel = viewModel(entries = listOf(entry(LocalDate.now())))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.AddEntryClicked)
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToAddEntry(listOf(JournalDateSlot.YESTERDAY))), events)
            collectJob.cancel()
        }

    @Test
    fun `JournalEntryClicked emits NavigateToJournalDetail with the clicked entry`() =
        runTest {
            val today = entry(LocalDate.now())
            val viewModel = viewModel(entries = listOf(today))
            val events = mutableListOf<MainUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(MainIntent.JournalEntryClicked(today))
            runCurrent()

            assertEquals(listOf(MainUiEvent.NavigateToJournalDetail(today)), events)
            collectJob.cancel()
        }
}
