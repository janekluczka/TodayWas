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

// A precomputed, already-laid-out cell list — every date/week/month calculation happens once,
// upstream in ui/model/ContributionMapper.kt, not on every recomposition of this component.
// Months own their own contiguous block of columns: a month's first/last column is truncated to
// only the weekdays that actually fall within that month (e.g. a month starting on Saturday gets a
// column with only Saturday+Sunday populated) — the rest of that column's 7 slots are `Blank`
// (same footprint, renders nothing). `hasGapBefore` marks every cell in the first column of a new
// month so the renderer can add a small leading margin there, visually separating month blocks
// without a dedicated spacer column. `date` on `Level` is identifying metadata for callers/tests —
// this component never reads it.
sealed interface TodayWasContributionCellUiState {

    data class Level(
        val date: LocalDate,
        val level: TodayWasContributionLevel,
        val hasGapBefore: Boolean = false,
    ) : TodayWasContributionCellUiState

    data class Blank(
        val hasGapBefore: Boolean = false,
    ) : TodayWasContributionCellUiState
}

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
private val MONTH_GAP = 6.dp

@Composable
fun TodayWasContributionGrid(
    cells: List<TodayWasContributionCellUiState>,
    modifier: Modifier = Modifier,
) {
    val levelColors = if (isSystemInDarkTheme()) DarkLevelColors else LightLevelColors

    LazyHorizontalGrid(
        rows = GridCells.Fixed(7),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier.height((CELL_SIZE + CELL_SPACING) * 7),
    ) {
        items(cells) { cell ->
            val hasGapBefore = when (cell) {
                is TodayWasContributionCellUiState.Level -> cell.hasGapBefore
                is TodayWasContributionCellUiState.Blank -> cell.hasGapBefore
            }
            val cellModifier = Modifier
                .padding(start = if (hasGapBefore) MONTH_GAP else 0.dp)
                .padding(CELL_SPACING / 2)
                .size(CELL_SIZE)
            when (cell) {
                is TodayWasContributionCellUiState.Level -> Box(
                    modifier = cellModifier.background(
                        color = levelColors.getValue(cell.level),
                        shape = RoundedCornerShape(2.dp),
                    ),
                )
                is TodayWasContributionCellUiState.Blank -> Box(modifier = cellModifier)
            }
        }
    }
}

private class TodayWasContributionGridPreviewProvider : PreviewParameterProvider<List<TodayWasContributionCellUiState>> {
    private val today = LocalDate.now()

    override val values = sequenceOf(
        // A month boundary mid-week: the ending month's tail column has only its first 5 rows
        // filled (Mon-Fri), the new month's first column (gap before it) has only the last 2
        // rows filled (Sat-Sun) — matching a month that starts on a Saturday.
        buildList<TodayWasContributionCellUiState> {
            repeat(5) { add(TodayWasContributionCellUiState.Level(today.minusDays(10), TodayWasContributionLevel.LEVEL_3)) }
            add(TodayWasContributionCellUiState.Blank())
            add(TodayWasContributionCellUiState.Blank())
            add(TodayWasContributionCellUiState.Blank(hasGapBefore = true))
            add(TodayWasContributionCellUiState.Blank(hasGapBefore = true))
            add(TodayWasContributionCellUiState.Blank(hasGapBefore = true))
            add(TodayWasContributionCellUiState.Blank(hasGapBefore = true))
            add(TodayWasContributionCellUiState.Blank(hasGapBefore = true))
            add(TodayWasContributionCellUiState.Level(today.minusDays(2), TodayWasContributionLevel.LEVEL_1, hasGapBefore = true))
            add(TodayWasContributionCellUiState.Level(today.minusDays(1), TodayWasContributionLevel.LEVEL_5, hasGapBefore = true))
            repeat(7) { add(TodayWasContributionCellUiState.Level(today, TodayWasContributionLevel.LEVEL_2)) }
        },
        // A single full column, no gaps.
        List(7) { TodayWasContributionCellUiState.Level(today.minusDays(it.toLong()), TodayWasContributionLevel.LEVEL_3) },
    )
}

@PreviewLightDark
@Composable
private fun TodayWasContributionGridPreview(
    @PreviewParameter(TodayWasContributionGridPreviewProvider::class) cells: List<TodayWasContributionCellUiState>,
) {
    DesignSystemPreviewTheme {
        TodayWasContributionGrid(cells = cells)
    }
}
