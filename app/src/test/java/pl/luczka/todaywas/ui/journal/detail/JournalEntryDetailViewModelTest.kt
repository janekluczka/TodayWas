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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.usecase.DeleteJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.IsEditableUseCase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
        id: String = "1",
    ) = JournalEntryDetailViewModel(
        id = id,
        getJournalEntry = GetJournalEntryUseCase(repository),
        deleteJournalEntry = DeleteJournalEntryUseCase(repository),
        isEditable = IsEditableUseCase(clock),
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
    fun `should load the entry and populate isEditable when ScreenEntered is dispatched within the window`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
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
    fun `should derive isEditable false when ScreenEntered is dispatched past the 24h window`() =
        runTest {
            // Arrange
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneOffset.UTC)
            val viewModel = viewModel(clock = expiredClock)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `should refetch the entry when ScreenEntered is dispatched again, fixing the stale-entry-after-edit bug`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
            runCurrent()
            assertEquals(
                "Original text.",
                viewModel.uiState.value.entry
                    ?.text,
            )
            // Simulate Edit having saved a change through the repository directly, the same way
            // the real Edit screen and this Detail ViewModel are now two separate ViewModel
            // instances sharing the same underlying repository.
            repository.entriesFlow.value = listOf(entry.copy(text = "Edited elsewhere."))

            // Act -- this is what the screen dispatches every time it (re)enters composition,
            // including every return from Edit.
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
            runCurrent()

            // Assert
            assertEquals(
                "Edited elsewhere.",
                viewModel.uiState.value.entry
                    ?.text,
            )
        }

    @Test
    fun `should emit NavigateToEdit when EditClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
            runCurrent()
            val events = mutableListOf<JournalEntryDetailUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(JournalEntryDetailUiEvent.NavigateToEdit), events)
            collectJob.cancel()
        }

    @Test
    fun `should emit NavigatedBack when BackClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            val events = mutableListOf<JournalEntryDetailUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.BackClicked)
            runCurrent()

            // Assert
            assertEquals(listOf(JournalEntryDetailUiEvent.NavigatedBack), events)
            collectJob.cancel()
        }

    @Test
    fun `should show isDeleteDialogVisible when DeleteClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

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
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
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
    fun `should succeed regardless of the entry's age, unlike editing`() =
        runTest {
            // Arrange - entry is well past its 24h edit window, unlike editing's expiry tests
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneOffset.UTC)
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository, clock = expiredClock)
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
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
            viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
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
}
