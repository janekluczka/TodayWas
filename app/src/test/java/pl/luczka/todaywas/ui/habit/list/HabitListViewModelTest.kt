package pl.luczka.todaywas.ui.habit.list

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.luczka.todaywas.domain.model.Habit
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.repository.FakeHabitRepository
import pl.luczka.todaywas.domain.usecase.ObserveHabitCheckInBoardUseCase
import pl.luczka.todaywas.ui.model.HabitSortUiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HabitListViewModelTest {

    private val today = LocalDate.of(2026, 6, 15)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private fun habit(
        id: String,
        name: String = "habit-$id",
        createdAt: Instant = Instant.EPOCH,
    ) = Habit(
        id = id,
        name = name,
        description = null,
        type = HabitType.BINARY,
        scaleMin = null,
        scaleMax = null,
        createdAt = createdAt,
        updatedAt = Instant.EPOCH,
    )

    private fun checkIn(
        habitId: String,
        date: LocalDate,
    ) = HabitCheckIn(
        id = habitId,
        habitId = habitId,
        date = date,
        value = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun viewModel(
        habits: List<Habit> = emptyList(),
        checkIns: List<HabitCheckIn> = emptyList(),
    ) = HabitListViewModel(
        observeHabitCheckInBoard = ObserveHabitCheckInBoardUseCase(
            FakeHabitRepository(habits, checkIns),
        ),
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
    fun `should default to RECENTLY_CHECKED_IN sort`() =
        runTest {
            // Arrange & Act
            val viewModel = viewModel()

            // Assert
            assertEquals(HabitSortUiState.RECENTLY_CHECKED_IN, viewModel.uiState.value.selectedSort)
            assertTrue(!viewModel.uiState.value.isLoading)
        }

    @Test
    fun `should sort RECENTLY_CHECKED_IN by most recent check-in date descending`() =
        runTest {
            // Arrange
            val habits = listOf(habit("1"), habit("2"), habit("3"))
            val checkIns = listOf(
                checkIn(habitId = "1", date = today.minusDays(5)),
                checkIn(habitId = "2", date = today),
            )

            // Act
            val viewModel = viewModel(habits, checkIns)

            // Assert
            assertEquals(
                listOf("2", "1", "3"),
                viewModel.uiState.value.habits
                    .map { it.id },
            )
        }

    @Test
    fun `should sort ALPHABETICAL by name when SortSelected is dispatched`() =
        runTest {
            // Arrange
            val habits = listOf(
                habit("1", name = "Run"),
                habit("2", name = "Drink water"),
                habit("3", name = "Meditate"),
            )
            val viewModel = viewModel(habits)

            // Act
            viewModel.onIntent(HabitListIntent.SortSelected(HabitSortUiState.ALPHABETICAL))
            runCurrent()

            // Assert
            assertEquals(HabitSortUiState.ALPHABETICAL, viewModel.uiState.value.selectedSort)
            assertEquals(
                listOf("Drink water", "Meditate", "Run"),
                viewModel.uiState.value.habits
                    .map { it.name },
            )
        }

    @Test
    fun `should sort DATE_CREATED ascending when SortSelected is dispatched`() =
        runTest {
            // Arrange
            val habits = listOf(
                habit("1", createdAt = Instant.ofEpochSecond(300)),
                habit("2", createdAt = Instant.ofEpochSecond(100)),
                habit("3", createdAt = Instant.ofEpochSecond(200)),
            )
            val viewModel = viewModel(habits)

            // Act
            viewModel.onIntent(HabitListIntent.SortSelected(HabitSortUiState.DATE_CREATED))
            runCurrent()

            // Assert
            assertEquals(
                listOf("2", "3", "1"),
                viewModel.uiState.value.habits
                    .map { it.id },
            )
        }

    @Test
    fun `should emit NavigateToDetail with the clicked habit's id when HabitClicked is dispatched`() =
        runTest {
            // Arrange
            val viewModel = viewModel(listOf(habit("1")))
            val events = mutableListOf<HabitListUiEvent>()
            val collectJob = launch { viewModel.events.collect { events.add(it) } }
            val clickedHabit = viewModel.uiState.value.habits
                .single()

            // Act
            viewModel.onIntent(HabitListIntent.HabitClicked(clickedHabit))
            runCurrent()

            // Assert
            assertEquals(listOf(HabitListUiEvent.NavigateToDetail("1")), events)
            collectJob.cancel()
        }
}
