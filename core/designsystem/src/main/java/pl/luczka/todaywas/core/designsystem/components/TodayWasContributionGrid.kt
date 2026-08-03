package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme
import java.time.LocalDate

enum class TodayWasContributionLevel {
    NONE,
    LEVEL_1,
    LEVEL_2,
    LEVEL_3,
    LEVEL_4,
    LEVEL_5,
}

// A raw Map parameter is unstable to the Compose compiler (Map is an interface with mutable
// subtypes), so passing `cells` directly would defeat recomposition-skipping and force this whole
// ~370-cell grid to redraw on every unrelated recomposition of the caller (e.g. every keystroke
// elsewhere on the same screen). Wrapping it in an @Immutable data class restores structural
// equals()-based skipping.
@Immutable
data class TodayWasContributionCells(
    val values: Map<LocalDate, TodayWasContributionLevel>,
)

// Fixed, non-MaterialTheme.colorScheme palette: TodayWasTheme has dynamicColor = true, so
// colorScheme roles vary per device/wallpaper (Material You) and would make relative intensity
// comparisons meaningless. These are the same values GitHub's own contribution graph uses.
private val LightLevelColors = mapOf(
    TodayWasContributionLevel.NONE to Color(0xFFEBEDF0),
    TodayWasContributionLevel.LEVEL_1 to Color(0xFF9BE9A8),
    TodayWasContributionLevel.LEVEL_2 to Color(0xFF40C463),
    TodayWasContributionLevel.LEVEL_3 to Color(0xFF30A14E),
    TodayWasContributionLevel.LEVEL_4 to Color(0xFF216E39),
    TodayWasContributionLevel.LEVEL_5 to Color(0xFF0E4429),
)

private val DarkLevelColors = mapOf(
    TodayWasContributionLevel.NONE to Color(0xFF161B22),
    TodayWasContributionLevel.LEVEL_1 to Color(0xFF0E4429),
    TodayWasContributionLevel.LEVEL_2 to Color(0xFF006D32),
    TodayWasContributionLevel.LEVEL_3 to Color(0xFF26A641),
    TodayWasContributionLevel.LEVEL_4 to Color(0xFF39D353),
    TodayWasContributionLevel.LEVEL_5 to Color(0xFF56D364),
)

private val CELL_SIZE = 12.dp
private val CELL_SPACING = 2.dp

@Composable
fun TodayWasContributionGrid(
    startDate: LocalDate,
    endDate: LocalDate,
    cells: TodayWasContributionCells,
    modifier: Modifier = Modifier,
) {
    // Bounds come from the caller's selected window (not derived from `cells`, which only
    // contains non-NONE days) — otherwise a mostly-empty window would render as a tiny grid
    // spanning just its few logged days instead of the full selected range.
    // Padded to whole weeks (preceding Monday .. following Sunday) so every column is a full
    // week, then listed newest-first: combined with reverseLayout below, this anchors the most
    // recent week at the visual end of the row (like GitHub's own graph) with no blank trailing
    // space, regardless of viewport width — an explicit initial-scroll-index can't achieve that
    // without knowing how many columns fit on screen. Memoized since this is a ~370-entry list.
    val dates = remember(startDate, endDate) {
        val paddedStart = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
        val paddedEnd = endDate.plusDays(((7 - endDate.dayOfWeek.value) % 7).toLong())
        generateSequence(paddedEnd) { it.minusDays(1) }
            .takeWhile { it >= paddedStart }
            .toList()
    }
    val levelColors = if (isSystemInDarkTheme()) DarkLevelColors else LightLevelColors

    LazyHorizontalGrid(
        rows = GridCells.Fixed(7),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier.height((CELL_SIZE + CELL_SPACING) * 7),
    ) {
        items(dates) { date ->
            val level = cells.values[date] ?: TodayWasContributionLevel.NONE
            Box(
                modifier = Modifier
                    .padding(CELL_SPACING / 2)
                    .size(CELL_SIZE)
                    .background(
                        color = levelColors.getValue(level),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

private data class ContributionGridPreviewState(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val cells: TodayWasContributionCells,
)

private class TodayWasContributionGridPreviewProvider : PreviewParameterProvider<ContributionGridPreviewState> {
    private val today = LocalDate.now()

    override val values = sequenceOf(
        // Empty window: no data at all, still renders the full (all-NONE) range.
        ContributionGridPreviewState(
            startDate = today.minusDays(34),
            endDate = today,
            cells = TodayWasContributionCells(emptyMap()),
        ),
        // Sparse window: a few logged days inside an otherwise-empty range.
        ContributionGridPreviewState(
            startDate = today.minusDays(34),
            endDate = today,
            cells = TodayWasContributionCells(
                mapOf(
                    today to TodayWasContributionLevel.LEVEL_3,
                    today.minusDays(2) to TodayWasContributionLevel.LEVEL_1,
                    today.minusDays(10) to TodayWasContributionLevel.LEVEL_5,
                ),
            ),
        ),
        // Full 5-level gradient across the whole window.
        ContributionGridPreviewState(
            startDate = today.minusDays(34),
            endDate = today,
            cells = TodayWasContributionCells(
                (0..34).associate { offset ->
                    val date = today.minusDays(offset.toLong())
                    val level = TodayWasContributionLevel.entries[offset % TodayWasContributionLevel.entries.size]
                    date to level
                },
            ),
        ),
    )
}

@PreviewLightDark
@Composable
private fun TodayWasContributionGridPreview(
    @PreviewParameter(TodayWasContributionGridPreviewProvider::class) state: ContributionGridPreviewState,
) {
    DesignSystemPreviewTheme {
        TodayWasContributionGrid(
            startDate = state.startDate,
            endDate = state.endDate,
            cells = state.cells,
        )
    }
}
