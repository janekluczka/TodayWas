package pl.luczka.todaywas.core.designsystem.components.contribution

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsColor
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

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

// The selected-cell ring is drawn as three concentric layers, outside in: a 2.dp ring in the
// selection color, a 2.dp gap that lets the surface behind the grid show through (so the ring
// visibly floats off the cell rather than sitting flush against it), then the cell's own level
// color filling the remainder. The ring's own outer corner radius matches the preset's own
// cellCornerRadius so a selected cell reads as the same rounded shape, just outlined.
internal val SELECTION_RING_WIDTH = 2.dp
internal val SELECTION_RING_GAP = 1.dp

// Named presets a screen picks from instead of wiring cell/spacing/gap/corner dp values by hand.
// cellCornerRadius is per-preset (not a single shared constant) since a bigger cell reads better
// with a proportionally bigger corner radius than a smaller one does.
enum class DsContributionGridSize(
    val cellSize: Dp,
    val cellSpacing: Dp,
    val monthGap: Dp,
    val cellCornerRadius: Dp,
) {
    MEDIUM(cellSize = 24.dp, cellSpacing = 4.dp, monthGap = 24.dp, cellCornerRadius = 4.dp),
    LARGE(cellSize = 32.dp, cellSpacing = 4.dp, monthGap = 24.dp, cellCornerRadius = 8.dp),
}

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

// Pure rendering only — spacing between cells is the week container's job (a verticalArrangement),
// and spacing between weeks is the outer LazyRow's job (horizontalArrangement) — a cell never
// carries its own margin, so there's no edge-bleed at the very first/last cell in either axis.
// Selection/click only ever apply to a Level cell — a Blank cell carries no date, so it silently
// ignores both.
@Composable
internal fun ContributionCell(
    cell: DsContributionCellUiState,
    levelColors: Map<DsContributionLevel, Color>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 12.dp,
    cellCornerRadius: Dp = 4.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(cellCornerRadius)
    when (cell) {
        is DsContributionCellUiState.Level -> {
            val clickModifier = if (onClick != null) {
                val label = cell.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                Modifier.clickable(onClickLabel = label, onClick = onClick)
            } else {
                Modifier
            }
            val fillSize = if (selected) {
                cellSize - (SELECTION_RING_WIDTH + SELECTION_RING_GAP) * 2
            } else {
                cellSize
            }
            val fillShape = if (selected) {
                val fillCornerRadius =
                    (cellCornerRadius - SELECTION_RING_WIDTH - SELECTION_RING_GAP)
                        .coerceAtLeast(0.dp)
                RoundedCornerShape(fillCornerRadius)
            } else {
                shape
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(cellSize)
                    .then(clickModifier),
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(SELECTION_RING_WIDTH, MaterialTheme.colorScheme.primary, shape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(fillSize)
                        .background(color = levelColors.getValue(cell.level), shape = fillShape),
                )
            }
        }
        is DsContributionCellUiState.Blank -> Box(modifier = modifier.size(cellSize))
    }
}

// cellSize picks one of the named DsContributionGridSize presets — the screen just passes the
// preset it wants rather than wiring cell/spacing/gap dp values by hand. selectedDate/onCellClick
// default to null so every existing non-interactive caller compiles and renders unchanged.
// contentPadding defaults to none — the screen embedding this grid owns whatever horizontal inset
// it needs (e.g. 16.dp to match the rest of that screen's content) rather than this component
// baking one in, since a hardcoded inset here would either double up with or fight the screen's
// own. showMonthLabels is opt-in (defaults off) since Main's compact overview scale has no room
// for it; screens with a full-size drill-down grid can turn it on.
@Composable
fun DsContributionGrid(
    cells: List<DsContributionCellUiState>,
    modifier: Modifier = Modifier,
    cellSize: DsContributionGridSize = DsContributionGridSize.MEDIUM,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showMonthLabels: Boolean = false,
    selectedDate: LocalDate? = null,
    onCellClick: ((LocalDate) -> Unit)? = null,
) {
    val levelColors = contributionLevelColors()
    val weeks = cells.chunked(7)

    // Each week is one lazy item (not one item per cell) — a week is always exactly 7 cells, so
    // there's no need for LazyHorizontalGrid's per-item grid-slot math; a plain Column stacking
    // 7 cells inside a LazyRow item is simpler and cheaper. horizontalArrangement/
    // verticalArrangement supply the normal cellSpacing gap on both axes with zero bleed at the
    // outer edges (Arrangement.spacedBy never adds space before the first or after the last
    // item) — a month boundary just tops that base gap up to the full monthGap via one extra
    // leading padding, skipped entirely for the very first week since there's nothing before it
    // to gap from.
    LazyRow(
        reverseLayout = true,
        horizontalArrangement = Arrangement.spacedBy(cellSize.cellSpacing),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        itemsIndexed(weeks) { index, week ->
            val extraGapBeforeMonth = if (index > 0 && week.weekHasGapBefore()) {
                cellSize.monthGap - cellSize.cellSpacing
            } else {
                0.dp
            }
            Column(
                modifier = Modifier.padding(start = extraGapBeforeMonth),
            ) {
                if (showMonthLabels) {
                    // A month's true first calendar week (weekHasGapBefore) always gets a real
                    // label; the oldest week in view (the last item, wherever the window happens
                    // to be cut off) gets one too even when it isn't a real month-start, so the
                    // leftmost visible column is never unlabeled. Every other week still reserves
                    // the exact same two-line height via a transparent copy of the label, so the
                    // 7-cell blocks stay aligned in a row across the whole grid regardless of
                    // which columns actually show text.
                    MonthYearLabel(
                        week = week,
                        visible = index == weeks.lastIndex || week.weekHasGapBefore(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(cellSize.cellSpacing)) {
                    week.forEach { cell ->
                        val date = (cell as? DsContributionCellUiState.Level)?.date
                        ContributionCell(
                            cell = cell,
                            levelColors = levelColors,
                            cellSize = cellSize.cellSize,
                            cellCornerRadius = cellSize.cellCornerRadius,
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
}

@Composable
private fun MonthYearLabel(
    week: List<DsContributionCellUiState>,
    visible: Boolean,
) {
    val date = week.firstNotNullOfOrNull { (it as? DsContributionCellUiState.Level)?.date }
    val color = if (visible) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.Transparent
    }
    val yearText = date?.year?.toString().orEmpty()
    val monthText = date?.month?.getDisplayName(TextStyle.SHORT, Locale.getDefault()).orEmpty()
    Column(modifier = Modifier.padding(bottom = DsSpacing.space100)) {
        DsText(text = monthText, style = MaterialTheme.typography.labelSmall, color = color)
        DsText(text = yearText, style = MaterialTheme.typography.labelSmall, color = color)
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
