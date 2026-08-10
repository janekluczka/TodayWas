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
}
