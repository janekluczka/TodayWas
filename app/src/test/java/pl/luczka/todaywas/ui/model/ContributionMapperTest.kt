package pl.luczka.todaywas.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
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
    ): List<List<DsContributionCellUiState>> =
        grid
            .toUiState(now, type)
            .cells
            .reversed()
            .chunked(7)

    @Test
    fun `should default to CONTINUOUS when type is not specified`() {
        // Arrange
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())

        // Act
        val default = grid.toUiState(now)
        val explicitContinuous = grid.toUiState(now, ContributionGridType.CONTINUOUS)

        // Assert
        assertEquals(explicitContinuous, default)
    }

    @Test
    fun `should never produce a Blank cell when mode is CONTINUOUS`() {
        // Arrange
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())

        // Act
        val cells = grid.toUiState(now, ContributionGridType.CONTINUOUS).cells

        // Assert
        assertTrue(cells.all { it is DsContributionCellUiState.Level })
    }

    @Test
    fun `should cover the whole window padded to full weeks when mode is CONTINUOUS`() {
        // Arrange
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())

        // Act
        val cells = grid.toUiState(now, ContributionGridType.CONTINUOUS).cells

        // Assert
        assertEquals(0, cells.size % 7)
        assertTrue(cells.filterIsInstance<DsContributionCellUiState.Level>().any { it.date == LocalDate.of(2026, 1, 1) })
        assertTrue(cells.filterIsInstance<DsContributionCellUiState.Level>().any { it.date == LocalDate.of(2026, 12, 31) })
    }

    @Test
    fun `should have no gap before the first column when mode is BY_MONTH`() {
        // Arrange
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())

        // Act
        val firstColumn = chronologicalChunks(grid, ContributionGridType.BY_MONTH).first()

        // Assert
        assertTrue(firstColumn.none { hasGapBefore(it) })
    }

    @Test
    fun `should produce columns of exactly 7 cells when mode is BY_MONTH`() {
        // Arrange
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())

        // Act
        val chunks = chronologicalChunks(grid, ContributionGridType.BY_MONTH)

        // Assert
        for (chunk in chunks) {
            assertEquals(7, chunk.size)
        }
    }

    @Test
    fun `should split a month starting mid-week into two truncated columns when mode is BY_MONTH`() {
        // Arrange
        val grid = ContributionGrid(window = ContributionWindow.CalendarYear(2026), days = emptyMap())

        // Act
        // August 1, 2026 is a Saturday: July's last column should show only Mon-Fri (Blank
        // Sat/Sun); August's first column should show only Sat-Sun (Blank Mon-Fri) and carry
        // hasGapBefore on all 7 of its cells.
        val chunks = chronologicalChunks(grid, ContributionGridType.BY_MONTH)
        val julyTail = chunks.first { chunk -> chunk.any { dateOf(it) == LocalDate.of(2026, 7, 31) } }
        val augustHead = chunks.first { chunk -> chunk.any { dateOf(it) == LocalDate.of(2026, 8, 1) } }

        // Assert
        assertTrue(julyTail != augustHead)
        // July's tail column: Mon-Fri (Jul 27-31) present, Sat-Sun (Aug 1-2) blank.
        assertTrue((0..4).all { dateOf(julyTail[it]) == LocalDate.of(2026, 7, 27).plusDays(it.toLong()) })
        assertTrue(julyTail[5] is DsContributionCellUiState.Blank)
        assertTrue(julyTail[6] is DsContributionCellUiState.Blank)
        assertFalse(hasGapBefore(julyTail[0]))

        // August's head column: Mon-Fri (Jul 27-31) blank, Sat-Sun (Aug 1-2) present.
        assertTrue(augustHead[0] is DsContributionCellUiState.Blank)
        assertTrue(augustHead[4] is DsContributionCellUiState.Blank)
        assertEquals(LocalDate.of(2026, 8, 1), dateOf(augustHead[5]))
        assertEquals(LocalDate.of(2026, 8, 2), dateOf(augustHead[6]))
        assertTrue(augustHead.all { hasGapBefore(it) })
    }

    @Test
    fun `should map a real check-in day to the correct level regardless of mode`() {
        // Arrange
        val date = LocalDate.of(2026, 3, 10)
        val grid = ContributionGrid(
            window = ContributionWindow.CalendarYear(2026),
            days = mapOf(date to ContributionLevel.LEVEL_4),
        )

        // Act
        val levelByType = ContributionGridType.entries.associateWith { type ->
            grid
                .toUiState(now, type)
                .cells
                .filterIsInstance<DsContributionCellUiState.Level>()
                .find { it.date == date }
                ?.level
        }

        // Assert
        for (level in levelByType.values) {
            assertEquals(DsContributionLevel.LEVEL_4, level)
        }
    }

    private fun hasGapBefore(cell: DsContributionCellUiState): Boolean = when (cell) {
        is DsContributionCellUiState.Level -> cell.hasGapBefore
        is DsContributionCellUiState.Blank -> cell.hasGapBefore
    }

    private fun dateOf(cell: DsContributionCellUiState): LocalDate? = when (cell) {
        is DsContributionCellUiState.Level -> cell.date
        is DsContributionCellUiState.Blank -> null
    }
}
