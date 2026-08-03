package pl.luczka.todaywas.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.core.designsystem.components.TodayWasContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.TodayWasContributionLevel
import pl.luczka.todaywas.domain.model.ContributionGrid
import pl.luczka.todaywas.domain.model.ContributionLevel
import pl.luczka.todaywas.domain.model.ContributionWindow
import java.time.Instant
import java.time.LocalDate

class ContributionMapperTest {

    private val now = Instant.parse("2026-06-15T12:00:00Z")

    // Chronological order (mapper output is newest-first — reverse it back for readability).
    private fun chronologicalChunks(
        grid: ContributionGrid,
        type: ContributionGridType,
    ): List<List<TodayWasContributionCellUiState>> =
        grid
            .toUiState(now, type)
            .cells
            .reversed()
            .chunked(7)

    @Test
    fun `toUiState defaults to CONTINUOUS`() {
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())
        assertEquals(grid.toUiState(now), grid.toUiState(now, ContributionGridType.CONTINUOUS))
    }

    @Test
    fun `CONTINUOUS mode never produces a Blank cell`() {
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())
        val cells = grid.toUiState(now, ContributionGridType.CONTINUOUS).cells
        assertTrue(cells.all { it is TodayWasContributionCellUiState.Level })
    }

    @Test
    fun `CONTINUOUS mode covers the whole window padded to full weeks`() {
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())
        val cells = grid.toUiState(now, ContributionGridType.CONTINUOUS).cells
        assertEquals(0, cells.size % 7)
        assertTrue(cells.filterIsInstance<TodayWasContributionCellUiState.Level>().any { it.date == LocalDate.of(2026, 1, 1) })
        assertTrue(cells.filterIsInstance<TodayWasContributionCellUiState.Level>().any { it.date == LocalDate.of(2026, 12, 31) })
    }

    @Test
    fun `BY_MONTH mode has no gap before the very first column of the window`() {
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())
        val firstColumn = chronologicalChunks(grid, ContributionGridType.BY_MONTH).first()
        assertTrue(firstColumn.none { hasGapBefore(it) })
    }

    @Test
    fun `BY_MONTH mode produces columns of exactly 7 cells`() {
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())
        for (chunk in chronologicalChunks(grid, ContributionGridType.BY_MONTH)) {
            assertEquals(7, chunk.size)
        }
    }

    @Test
    fun `BY_MONTH mode splits a month starting mid-week into two truncated columns`() {
        // August 1, 2026 is a Saturday: July's last column should show only Mon-Fri (Blank
        // Sat/Sun); August's first column should show only Sat-Sun (Blank Mon-Fri) and carry
        // hasGapBefore on all 7 of its cells.
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())
        val chunks = chronologicalChunks(grid, ContributionGridType.BY_MONTH)

        val julyTail = chunks.first { chunk -> chunk.any { dateOf(it) == LocalDate.of(2026, 7, 31) } }
        val augustHead = chunks.first { chunk -> chunk.any { dateOf(it) == LocalDate.of(2026, 8, 1) } }

        assertTrue(julyTail != augustHead)
        // July's tail column: Mon-Fri (Jul 27-31) present, Sat-Sun (Aug 1-2) blank.
        assertTrue((0..4).all { dateOf(julyTail[it]) == LocalDate.of(2026, 7, 27).plusDays(it.toLong()) })
        assertTrue(julyTail[5] is TodayWasContributionCellUiState.Blank)
        assertTrue(julyTail[6] is TodayWasContributionCellUiState.Blank)
        assertFalse(hasGapBefore(julyTail[0]))

        // August's head column: Mon-Fri (Jul 27-31) blank, Sat-Sun (Aug 1-2) present.
        assertTrue(augustHead[0] is TodayWasContributionCellUiState.Blank)
        assertTrue(augustHead[4] is TodayWasContributionCellUiState.Blank)
        assertEquals(LocalDate.of(2026, 8, 1), dateOf(augustHead[5]))
        assertEquals(LocalDate.of(2026, 8, 2), dateOf(augustHead[6]))
        assertTrue(augustHead.all { hasGapBefore(it) })
    }

    @Test
    fun `a real check-in day maps to the correct level in both modes`() {
        val date = LocalDate.of(2026, 3, 10)
        val grid = ContributionGrid(
            window = ContributionWindow.CalendarYear(2026),
            days = mapOf(date to ContributionLevel.LEVEL_4),
        )
        for (type in ContributionGridType.entries) {
            val level = grid
                .toUiState(now, type)
                .cells
                .filterIsInstance<TodayWasContributionCellUiState.Level>()
                .find { it.date == date }
                ?.level
            assertEquals(TodayWasContributionLevel.LEVEL_4, level)
        }
    }

    private fun hasGapBefore(cell: TodayWasContributionCellUiState): Boolean = when (cell) {
        is TodayWasContributionCellUiState.Level -> cell.hasGapBefore
        is TodayWasContributionCellUiState.Blank -> cell.hasGapBefore
    }

    private fun dateOf(cell: TodayWasContributionCellUiState): LocalDate? = when (cell) {
        is TodayWasContributionCellUiState.Level -> cell.date
        is TodayWasContributionCellUiState.Blank -> null
    }
}
