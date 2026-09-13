package pl.luczka.todaywas.core.designsystem.components.contribution

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.theme.DsColor
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class DsContributionLevel {
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
sealed interface DsContributionCellUiState {

    data class Level(
        val date: LocalDate,
        val level: DsContributionLevel,
        val hasGapBefore: Boolean = false,
    ) : DsContributionCellUiState

    data class Blank(
        val hasGapBefore: Boolean = false,
    ) : DsContributionCellUiState
}

// A tonal ramp of the app's own teal hue (DsColor.teal*), not GitHub's green — this is a
// journaling/habit app, not a code host. Fixed (not read from MaterialTheme.colorScheme) so
// relative intensity comparisons stay meaningful regardless of the active theme — reaches straight
// into the raw DsColor palette rather than through DsTheme's semantic roles.
// One palette for every habit today; a per-habit color choice (e.g. picking a different hue per
// scale habit) is a deliberately deferred future step, not built into this API yet.
// Internal (not private) so DsContributionTimeline.kt can share the same rendering.
internal val LightLevelColors = mapOf(
    DsContributionLevel.NONE to DsColor.neutralVariant90,
    DsContributionLevel.LEVEL_1 to DsColor.teal90,
    DsContributionLevel.LEVEL_2 to DsColor.teal70,
    DsContributionLevel.LEVEL_3 to DsColor.teal60,
    DsContributionLevel.LEVEL_4 to DsColor.teal40,
    DsContributionLevel.LEVEL_5 to DsColor.teal10,
)

internal val DarkLevelColors = mapOf(
    DsContributionLevel.NONE to DsColor.neutralVariant30,
    DsContributionLevel.LEVEL_1 to DsColor.teal30,
    DsContributionLevel.LEVEL_2 to DsColor.teal60,
    DsContributionLevel.LEVEL_3 to DsColor.teal70,
    DsContributionLevel.LEVEL_4 to DsColor.teal80,
    DsContributionLevel.LEVEL_5 to DsColor.teal90,
)

internal val CELL_SIZE = 12.dp
internal val CELL_SPACING = 2.dp
internal val MONTH_GAP = 6.dp

@Composable
internal fun contributionLevelColors(): Map<DsContributionLevel, Color> =
    if (isSystemInDarkTheme()) DarkLevelColors else LightLevelColors

// All 7 cells in one week share the same hasGapBefore value (set uniformly by the mapper), so
// callers only need to check the first cell to decide whether the whole week needs a margin.
internal fun List<DsContributionCellUiState>.weekHasGapBefore(): Boolean =
    when (val cell = first()) {
        is DsContributionCellUiState.Level -> cell.hasGapBefore
        is DsContributionCellUiState.Blank -> cell.hasGapBefore
    }

// Pure rendering only — month-gap spacing is the week container's job (see weekHasGapBefore), not
// a per-cell concern, since "before" means a different axis in each caller. Selection/click only
// ever apply to a Level cell — a Blank cell carries no date, so it silently ignores both.
@Composable
internal fun ContributionCell(
    cell: DsContributionCellUiState,
    levelColors: Map<DsContributionLevel, Color>,
    modifier: Modifier = Modifier,
    cellSize: Dp = CELL_SIZE,
    cellSpacing: Dp = CELL_SPACING,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val cellModifier = modifier
        .padding(cellSpacing / 2)
        .size(cellSize)
    when (cell) {
        is DsContributionCellUiState.Level -> {
            val shape = RoundedCornerShape(2.dp)
            val clickModifier = if (onClick != null) {
                val label = cell.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                Modifier.clickable(onClickLabel = label, onClick = onClick)
            } else {
                Modifier
            }
            val selectionModifier = if (selected) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
            } else {
                Modifier
            }
            Box(
                modifier = cellModifier
                    .background(color = levelColors.getValue(cell.level), shape = shape)
                    .then(clickModifier)
                    .then(selectionModifier),
            )
        }
        is DsContributionCellUiState.Blank -> Box(modifier = cellModifier)
    }
}

// cellSize/cellSpacing/monthGap default to the compact sizing this ships with today (Main's
// overview-scale usage) — a caller that needs a bigger read (e.g. a single-habit drill-down) can
// override them without affecting every other caller. selectedDate/onCellClick default to
// null so every existing non-interactive caller compiles and renders unchanged.
@Composable
fun DsContributionGrid(
    cells: List<DsContributionCellUiState>,
    modifier: Modifier = Modifier,
    cellSize: Dp = CELL_SIZE,
    cellSpacing: Dp = CELL_SPACING,
    monthGap: Dp = MONTH_GAP,
    selectedDate: LocalDate? = null,
    onCellClick: ((LocalDate) -> Unit)? = null,
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
                modifier = Modifier.padding(
                    start = if (week.weekHasGapBefore()) monthGap else 0.dp,
                ),
            ) {
                week.forEach { cell ->
                    val date = (cell as? DsContributionCellUiState.Level)?.date
                    ContributionCell(
                        cell = cell,
                        levelColors = levelColors,
                        cellSize = cellSize,
                        cellSpacing = cellSpacing,
                        selected = date != null && date == selectedDate,
                        onClick = if (date != null && onCellClick != null) {
                            { onCellClick(date) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

private class DsContributionGridPreviewProvider :
    PreviewParameterProvider<List<DsContributionCellUiState>> {
    private val today = LocalDate.now()

    override val values = sequenceOf(
        // A month boundary mid-week: the ending month's tail column has only its first 5 rows
        // filled (Mon-Fri), the new month's first column (gap before it) has only the last 2
        // rows filled (Sat-Sun) — matching a month that starts on a Saturday.
        buildList<DsContributionCellUiState> {
            repeat(
                5,
            ) {
                add(
                    DsContributionCellUiState.Level(
                        today.minusDays(10),
                        DsContributionLevel.LEVEL_3,
                    ),
                )
            }
            add(DsContributionCellUiState.Blank())
            add(DsContributionCellUiState.Blank())
            add(DsContributionCellUiState.Blank(hasGapBefore = true))
            add(DsContributionCellUiState.Blank(hasGapBefore = true))
            add(DsContributionCellUiState.Blank(hasGapBefore = true))
            add(DsContributionCellUiState.Blank(hasGapBefore = true))
            add(DsContributionCellUiState.Blank(hasGapBefore = true))
            add(
                DsContributionCellUiState.Level(
                    today.minusDays(2),
                    DsContributionLevel.LEVEL_1,
                    hasGapBefore = true,
                ),
            )
            add(
                DsContributionCellUiState.Level(
                    today.minusDays(1),
                    DsContributionLevel.LEVEL_5,
                    hasGapBefore = true,
                ),
            )
            repeat(7) { add(DsContributionCellUiState.Level(today, DsContributionLevel.LEVEL_2)) }
        },
        // A single full column, no gaps.
        List(7) {
            DsContributionCellUiState.Level(
                today.minusDays(it.toLong()),
                DsContributionLevel.LEVEL_3,
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun DsContributionGridPreview(
    @PreviewParameter(DsContributionGridPreviewProvider::class) cells:
        List<DsContributionCellUiState>,
) {
    DsTheme {
        DsContributionGrid(cells = cells)
    }
}

@PreviewLightDark
@Composable
private fun DsContributionGridSelectedPreview() {
    val today = LocalDate.now()
    val cells = List(7) {
        DsContributionCellUiState.Level(today.minusDays(it.toLong()), DsContributionLevel.LEVEL_3)
    }
    DsTheme {
        DsContributionGrid(
            cells = cells,
            selectedDate = today.minusDays(2),
            onCellClick = {},
        )
    }
}
