package pl.luczka.todaywas.ui.journal.list

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import pl.luczka.todaywas.domain.model.JournalEntry
import pl.luczka.todaywas.domain.repository.FakeJournalRepository
import pl.luczka.todaywas.domain.usecase.ObserveJournalContributionUseCase
import pl.luczka.todaywas.domain.usecase.ObserveJournalEntriesUseCase
import pl.luczka.todaywas.ui.model.JournalSortUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class JournalListViewModelTest {

    private val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

    private fun entry(
        id: String,
        date: LocalDate,
    ) = JournalEntry(
        id = id,
        date = date,
        text = "entry-$id",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    // uiState uses stateIn(WhileSubscribed), so it only updates while something collects it.
    private fun TestScope.viewModel(entries: List<JournalEntry> = emptyList()): JournalListViewModel {
        val repository = FakeJournalRepository(entries)
        return JournalListViewModel(
            observeJournalEntries = ObserveJournalEntriesUseCase(repository),
            observeJournalContribution = ObserveJournalContributionUseCase(repository, clock),
            clock = clock,
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
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
    fun `should default to NEWEST_FIRST sort`() =
        runTest {
            // Arrange & Act
            val viewModel = viewModel()

            // Assert
            assertEquals(JournalSortUiState.NEWEST_FIRST, viewModel.uiState.value.selectedSort)
            assertTrue(!viewModel.uiState.value.isLoading)
        }

    @Test
    fun `should sort NEWEST_FIRST by date descending`() =
        runTest {
            // Arrange
            val today = LocalDate.of(2026, 6, 15)
            val entries = listOf(
                entry("1", today.minusDays(5)),
                entry("2", today),
                entry("3", today.minusDays(1)),
            )

            // Act
            val viewModel = viewModel(entries)

            // Assert
            assertEquals(
                listOf("2", "3", "1"),
                viewModel.uiState.value.entries
                    .map { it.id },
            )
        }

    @Test
    fun `should sort OLDEST_FIRST by date ascending when SortSelected is dispatched`() =
        runTest {
            // Arrange
            val today = LocalDate.of(2026, 6, 15)
            val entries = listOf(
                entry("1", today.minusDays(5)),
                entry("2", today),
                entry("3", today.minusDays(1)),
            )
            val viewModel = viewModel(entries)

            // Act
            viewModel.onIntent(JournalListIntent.SortSelected(JournalSortUiState.OLDEST_FIRST))
            runCurrent()

            // Assert
            assertEquals(JournalSortUiState.OLDEST_FIRST, viewModel.uiState.value.selectedSort)
            assertEquals(
                listOf("1", "3", "2"),
                viewModel.uiState.value.entries
                    .map { it.id },
            )
        }

    @Test
    fun `should populate a non-empty contributionGrid`() =
        runTest {
            // Arrange & Act
            val viewModel = viewModel(listOf(entry("1", LocalDate.now())))

            // Assert
            assertTrue(
                viewModel.uiState.value.contributionGrid.cells
                    .isNotEmpty(),
            )
        }
}
