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
    fun `construction loads the entry and populates isEditable when within the window`() =
        runTest {
            val viewModel = viewModel()
            runCurrent()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(
                "Original text.",
                viewModel.uiState.value.entry
                    ?.text,
            )
            assertTrue(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `construction derives isEditable false past the 24h window`() =
        runTest {
            val expiredClock = Clock.fixed(createdAt.plus(Duration.ofHours(25)), ZoneOffset.UTC)
            val viewModel = viewModel(clock = expiredClock)
            runCurrent()

            assertFalse(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `edit then save success updates entry text and clears isEditing`() =
        runTest {
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            runCurrent()

            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.TextChanged("Edited text."))
            viewModel.onIntent(JournalEntryDetailIntent.SaveClicked)
            runCurrent()

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
    fun `save failure generic keeps isEditing true and sets saveError`() =
        runTest {
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            repository.updateEntryResult = Result.failure(RuntimeException("write failed"))
            val viewModel = viewModel(repository)
            runCurrent()

            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.TextChanged("Edited text."))
            viewModel.onIntent(JournalEntryDetailIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertTrue(viewModel.uiState.value.isEditing)
            assertTrue(viewModel.uiState.value.saveError)
            assertTrue(viewModel.uiState.value.isEditable)
        }

    @Test
    fun `save failure expired window clears isEditing and isEditable and sets saveError`() =
        runTest {
            val repository = FakeJournalRepository(initialEntries = listOf(entry))
            val viewModel = viewModel(repository)
            runCurrent()
            viewModel.onIntent(JournalEntryDetailIntent.EditClicked)
            viewModel.onIntent(JournalEntryDetailIntent.TextChanged("Edited text."))
            // Stub the repository to return the same failure UpdateJournalEntryUseCase would
            // produce on a real mid-session expiry, isolating the ViewModel's own branching logic.
            repository.updateEntryResult = Result.failure(EditWindowExpiredException())

            viewModel.onIntent(JournalEntryDetailIntent.SaveClicked)
            runCurrent()

            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.isEditing)
            assertFalse(viewModel.uiState.value.isEditable)
            assertTrue(viewModel.uiState.value.saveError)
        }
}
