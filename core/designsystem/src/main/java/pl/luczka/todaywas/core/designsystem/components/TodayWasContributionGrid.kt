package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
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
// Months own their own contiguous block of weeks: a month's first/last week is truncated to only
// the weekdays that actually fall within that month (e.g. a month starting on Saturday gets a week
// with only Saturday+Sunday populated) — the rest of that week's 7 slots are `Blank` (same
// footprint, renders nothing). `hasGapBefore` marks every cell in the first week of a new month;
// callers check it once per week (all 7 cells in a week share the same value) and apply a margin
// to the whole week container — a horizontal margin before the column in the horizontal grid, a
// vertical margin above the row in the vertical timeline. `date` on `Level` is identifying
// metadata for callers/tests — neither renderer reads it.
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
// Internal (not private) so TodayWasContributionTimeline.kt can share the same rendering.
internal val LightLevelColors = mapOf(
    TodayWasContributionLevel.NONE to Color(0xFFEBEDF0),
    TodayWasContributionLevel.LEVEL_1 to Color(0xFF9BE9A8),
    TodayWasContributionLevel.LEVEL_2 to Color(0xFF40C463),
    TodayWasContributionLevel.LEVEL_3 to Color(0xFF30A14E),
    TodayWasContributionLevel.LEVEL_4 to Color(0xFF216E39),
    TodayWasContributionLevel.LEVEL_5 to Color(0xFF0E4429),
)

internal val DarkLevelColors = mapOf(
    TodayWasContributionLevel.NONE to Color(0xFF161B22),
    TodayWasContributionLevel.LEVEL_1 to Color(0xFF0E4429),
    TodayWasContributionLevel.LEVEL_2 to Color(0xFF006D32),
    TodayWasContributionLevel.LEVEL_3 to Color(0xFF26A641),
    TodayWasContributionLevel.LEVEL_4 to Color(0xFF39D353),
    TodayWasContributionLevel.LEVEL_5 to Color(0xFF56D364),
)

internal val CELL_SIZE = 12.dp
internal val CELL_SPACING = 2.dp
internal val MONTH_GAP = 6.dp

@Composable
internal fun contributionLevelColors(): Map<TodayWasContributionLevel, Color> =
    if (isSystemInDarkTheme()) DarkLevelColors else LightLevelColors

// All 7 cells in one week share the same hasGapBefore value (set uniformly by the mapper), so
// callers only need to check the first cell to decide whether the whole week needs a margin.
internal fun List<TodayWasContributionCellUiState>.weekHasGapBefore(): Boolean = when (val cell = first()) {
    is TodayWasContributionCellUiState.Level -> cell.hasGapBefore
    is TodayWasContributionCellUiState.Blank -> cell.hasGapBefore
}

// Shared by both TodayWasContributionGrid (horizontal) and TodayWasContributionTimeline
// (vertical) so the two stay visually consistent without duplicating the palette/size logic. Pure
// rendering only — month-gap spacing is the week container's job (see weekHasGapBefore), not a
// per-cell concern, since "before" means a different axis in each component.
@Composable
internal fun ContributionCell(
    cell: TodayWasContributionCellUiState,
    levelColors: Map<TodayWasContributionLevel, Color>,
    modifier: Modifier = Modifier,
    cellSize: Dp = CELL_SIZE,
) {
    val cellModifier = modifier
        .padding(CELL_SPACING / 2)
        .size(cellSize)
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

@Composable
fun TodayWasContributionGrid(
    cells: List<TodayWasContributionCellUiState>,
    modifier: Modifier = Modifier,
) {
    val levelColors = contributionLevelColors()

    // Each week is one lazy item (not one item per cell) — a week is always exactly 7 cells, so
    // there's no need for LazyHorizontalGrid's per-item grid-slot math; a plain Column stacking
    // 7 cells inside a LazyRow item is simpler and cheaper.
    LazyRow(
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = modifier,
    ) {
        items(cells.chunked(7)) { week ->
            Column(
                modifier = Modifier.padding(start = if (week.weekHasGapBefore()) MONTH_GAP else 0.dp),
            ) {
                week.forEach { cell -> ContributionCell(cell = cell, levelColors = levelColors) }
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
