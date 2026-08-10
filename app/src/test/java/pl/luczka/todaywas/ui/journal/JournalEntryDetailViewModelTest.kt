package pl.luczka.todaywas.ui.journal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.usecase.GetJournalEntryUseCase
import pl.luczka.todaywas.domain.usecase.UpdateJournalEntryUseCase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class JournalEntryDetailViewModelTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")
    private val entry = JournalEntry(id = 1L, date = LocalDate.of(2026, 8, 1), text = "Original text.", createdAt = createdAt)

    private fun viewModel(
        repository: FakeJournalRepository = FakeJournalRepository(initialEntries = listOf(entry)),
        clock: Clock = Clock.fixed(createdAt.plus(Duration.ofHours(1)), ZoneOffset.UTC),
        id: Long = 1L,
    ) = JournalEntryDetailViewModel(
        id = id,
        getJournalEntry = GetJournalEntryUseCase(repository),
        updateJournalEntry = UpdateJournalEntryUseCase(repository, clock),
        clock = clock,
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
}
