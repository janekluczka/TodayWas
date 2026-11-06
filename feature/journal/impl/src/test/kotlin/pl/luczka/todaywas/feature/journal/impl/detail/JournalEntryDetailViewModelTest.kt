package pl.luczka.todaywas.feature.journal.impl.detail

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import pl.luczka.todaywas.core.testing.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.entry.usecase.IsEditableUseCase
import pl.luczka.todaywas.domain.journal.model.JournalEntry
import pl.luczka.todaywas.domain.journal.usecase.DeleteJournalEntryUseCase
import pl.luczka.todaywas.domain.journal.usecase.ObserveJournalEntryUseCase
import pl.luczka.todaywas.domain.journal.usecase.UpdateJournalEntryUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class JournalEntryDetailViewModelTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")
    private val withinWindowClock = Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneId.systemDefault())
    private val entry = JournalEntry(
        id = "1",
        date = LocalDate.of(2026, 8, 1),
        text = "Original text.",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun viewModel(
        repository: FakeJournalRepository = FakeJournalRepository(initialEntries = listOf(entry)),
        clock: Clock = withinWindowClock,
        id: String = "1",
    ) = JournalEntryDetailViewModel(
        id = id,
        observeJournalEntry = ObserveJournalEntryUseCase(repository),
        deleteJournalEntry = DeleteJournalEntryUseCase(repository),
        isEditable = IsEditableUseCase(clock),
    )

    // uiState uses stateIn(WhileSubscribed), so it only updates while something collects it.
    private fun TestScope.collectUiState(viewModel: JournalEntryDetailViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
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
    fun `should show the entry and isEditable when observed within the window`() =
        runTest {
            // Arrange
            val viewModel = viewModel()

            // Act
            collectUiState(viewModel)

            // Assert
            assertFalse(viewModel.uiState.value.isLoading)
            val shownEntry = viewModel.uiState.value.entry
            assertEquals("Original text.", shownEntry?.text)
            assertTrue(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `should derive isEditable false when observed past the 24h window`() =
        runTest {
            // Arrange
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneId.systemDefault())
            val viewModel = viewModel(clock = expiredClock)

            // Act
            collectUiState(viewModel)

            // Assert
            assertFalse(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `should stay loading when the entry does not exist`() =
        runTest {
            // Arrange
            val viewModel = viewModel(id = "missing")

            // Act
            collectUiState(viewModel)

            // Assert
            assertTrue(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `should show the saved text when returning from Edit after a save`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            val detailCollector = collectUiState(viewModel)
            val entryBeforeEdit = viewModel.uiState.value.entry
            assertEquals("Original text.", entryBeforeEdit?.text)
            // Navigating to Edit takes Detail out of composition: the ViewModel is retained, its
            // state stops being collected, and the upstream stops after the 5s grace period.
            detailCollector.cancel()
            UpdateJournalEntryUseCase(repository, withinWindowClock)("1", "Saved in Edit.")
            advanceTimeBy(10_000)

            // Act
            collectUiState(viewModel)

            // Assert
            val shownEntry = viewModel.uiState.value.entry
            assertEquals("Saved in Edit.", shownEntry?.text)
        }

    @Test
    fun `should show the saved text when Edit saves within the 5s grace period`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            val detailCollector = collectUiState(viewModel)
            detailCollector.cancel()

            // Act
            UpdateJournalEntryUseCase(repository, withinWindowClock)("1", "Saved in Edit.")
            collectUiState(viewModel)

            // Assert
            val shownEntry = viewModel.uiState.value.entry
            assertEquals("Saved in Edit.", shownEntry?.text)
        }

    @Test
    fun `should show isDeleteDialogVisible when DeleteClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel()
            collectUiState(viewModel)

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
            collectUiState(viewModel)
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteDismissed)

            // Assert
            assertFalse(viewModel.uiState.value.isDeleteDialogVisible)
        }

    @Test
    fun `should delete and set EntryDeleted when DeleteConfirmed succeeds`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            collectUiState(viewModel)
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteConfirmed)
            runCurrent()

            // Assert
            assertEquals(1, repository.deleteEntryCallCount)
            assertEquals("1", repository.lastDeletedId)
            assertEquals(JournalEntryDetailNavigation.EntryDeleted, viewModel.uiState.value.pendingNavigation)
        }

    @Test
    fun `should clear pendingNavigation when NavigationHandled`() =
        runTest {
            // Arrange
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            collectUiState(viewModel)
            viewModel.onIntent(JournalEntryDetailIntent.DeleteConfirmed)
            runCurrent()

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.NavigationHandled)

            // Assert
            assertEquals(null, viewModel.uiState.value.pendingNavigation)
        }

    @Test
    fun `should succeed when deleting an entry older than the edit window`() =
        runTest {
            // Arrange - entry is well past its 24h edit window, unlike editing's expiry tests
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneId.systemDefault())
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(
                repository,
                clock = expiredClock,
            )
            collectUiState(viewModel)
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
            collectUiState(viewModel)
            viewModel.onIntent(JournalEntryDetailIntent.DeleteClicked)

            // Act
            viewModel.onIntent(JournalEntryDetailIntent.DeleteConfirmed)
            runCurrent()

            // Assert
            assertFalse(viewModel.uiState.value.isDeleting)
            assertFalse(viewModel.uiState.value.isDeleteDialogVisible)
            assertTrue(viewModel.uiState.value.deleteError)
            assertEquals(null, viewModel.uiState.value.pendingNavigation)
        }
}
