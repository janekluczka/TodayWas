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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.data.repository.FakeJournalRepository
import pl.luczka.todaywas.data.repository.JournalRepository
import pl.luczka.todaywas.domain.usecase.AddJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.ObserveAddableJournalDateSlotsUseCase
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState

@OptIn(ExperimentalCoroutinesApi::class)
class AddJournalEntryViewModelTest {

    private fun viewModel(repository: JournalRepository = FakeJournalRepository()) = AddJournalEntryViewModel(
        observeAddableJournalDateSlots = ObserveAddableJournalDateSlotsUseCase(repository),
        addJournalEntry = AddJournalEntryUseCase(repository),
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
    fun `initial state derives both slots as available and defaults selectedSlot to TODAY`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(
                listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY),
                viewModel.uiState.value.availableSlots,
            )
            assertEquals(JournalDateSlotUiState.TODAY, viewModel.uiState.value.selectedSlot)
        }

    @Test
    fun `SlotSelected updates selectedSlot`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(AddJournalEntryIntent.SlotSelected(JournalDateSlotUiState.YESTERDAY))

            assertEquals(JournalDateSlotUiState.YESTERDAY, viewModel.uiState.value.selectedSlot)
        }

    @Test
    fun `TextChanged updates text`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))

            assertEquals("Today was good.", viewModel.uiState.value.text)
        }

    @Test
    fun `SaveClicked success clears isSaving and emits Saved`() =
        runTest {
            val repository = FakeJournalRepository()
            val viewModel = viewModel(repository)
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))
            val events = mutableListOf<AddJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(AddJournalEntryIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.saveError)
            assertEquals(1, repository.addEntryCallCount)
            assertEquals(listOf(AddJournalEntryUiEvent.Saved), events)
            collectJob.cancel()
        }

    @Test
    fun `SaveClicked failure sets saveError and does not emit Saved`() =
        runTest {
            val repository = FakeJournalRepository()
            repository.addEntryResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            viewModel.onIntent(AddJournalEntryIntent.TextChanged("Today was good."))
            val events = mutableListOf<AddJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(AddJournalEntryIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(events.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun `CancelClicked emits Cancelled`() =
        runTest {
            val viewModel = viewModel()
            val events = mutableListOf<AddJournalEntryUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            viewModel.onIntent(AddJournalEntryIntent.CancelClicked)
            runCurrent()

            assertEquals(listOf(AddJournalEntryUiEvent.Cancelled), events)
            collectJob.cancel()
        }
}
