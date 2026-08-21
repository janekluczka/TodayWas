package pl.luczka.todaywas.core.designsystem.components.contribution

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import java.time.LocalDate

// A compact, single-line counterpart to the full DsContributionGrid — same scrollable, full-history
// cell list (most recent on the right, reverseLayout = true, matching the grid's own convention),
// just flattened into one row instead of stacked into weekly columns, and rendered at double the
// grid's cell size by default for a quick-glance strip in tighter contexts (like Main).
@Composable
fun DsContributionRow(
    cells: List<DsContributionCellUiState>,
    modifier: Modifier = Modifier,
    cellSize: Dp = CELL_SIZE * 2,
) {
    val levelColors = contributionLevelColors()
    LazyRow(
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier,
    ) {
        items(cells) { cell ->
            ContributionCell(cell = cell, levelColors = levelColors, cellSize = cellSize)
        }
    }
}

private class DsContributionRowPreviewProvider :
    PreviewParameterProvider<List<DsContributionCellUiState>> {
    private val today = LocalDate.now()
    private val levels = DsContributionLevel.entries

    // Index 0 is the most recent day, matching ContributionGridUiState.cells' own convention
    // (already reversed upstream) — this is what MainViewModel actually feeds the real component.
    override val values = sequenceOf(
        (0..27).map { offset ->
            DsContributionCellUiState.Level(
                today.minusDays(offset.toLong()),
                levels[offset % levels.size],
            )
        },
        List(28) {
            DsContributionCellUiState.Level(today.minusDays(it.toLong()), DsContributionLevel.NONE)
        },
    )
}

@PreviewLightDark
@Composable
private fun DsContributionRowPreview(
    @PreviewParameter(DsContributionRowPreviewProvider::class)
    cells: List<DsContributionCellUiState>,
) {
    DsTheme {
        DsContributionRow(cells = cells)
    }
}
