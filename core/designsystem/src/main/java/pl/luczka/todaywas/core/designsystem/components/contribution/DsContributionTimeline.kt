package pl.luczka.todaywas.core.designsystem.components.contribution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import java.time.LocalDate

// A bit bigger than the horizontal grid's compact 12dp cells — this view only ever shows one
// week per row, so there's room to make each cell easier to read.
private val TIMELINE_CELL_SIZE = 20.dp

// A vertical alternative to DsContributionGrid: one row per week, most recent at the top,
// scrolling down through history. Better suited to a single-item drill-down (e.g. Habit Detail)
// than the horizontal grid, which reads best as a compact multi-item overview. Reuses the exact
// same precomputed cell list the horizontal grid consumes — no new mapper logic.
@Composable
fun DsContributionTimeline(
    cells: List<DsContributionCellUiState>,
    modifier: Modifier = Modifier,
) {
    val levelColors = contributionLevelColors()

    LazyColumn(modifier = modifier) {
        // `cells` is already newest-first whole-weeks (per the mapper's contract with the
        // horizontal grid), so item 0 here is already the most recent week — no reverseLayout
        // trick needed, unlike the horizontal grid.
        items(cells.chunked(7)) { week ->
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (week.weekHasGapBefore()) MONTH_GAP else 0.dp),
            ) {
                // Reversed so a row reads Monday -> Sunday left-to-right: within-week order is
                // cosmetically irrelevant in the horizontal grid (never directly scanned), but
                // here each row IS directly scanned, so ascending order reads naturally.
                week.reversed().forEach { cell ->
                    ContributionCell(
                        cell = cell,
                        levelColors = levelColors,
                        cellSize = TIMELINE_CELL_SIZE,
                    )
                }
            }
        }
    }
}

private class DsContributionTimelinePreviewProvider :
    PreviewParameterProvider<List<DsContributionCellUiState>> {
    private val today = LocalDate.now()

    override val values = sequenceOf(
        // Several weeks of history, most recent first (offset 0 = today).
        (0..27).map { offset ->
            DsContributionCellUiState.Level(
                today.minusDays(offset.toLong()),
                DsContributionLevel.entries[offset % DsContributionLevel.entries.size],
            )
        },
        // A single week.
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
private fun DsContributionTimelinePreview(
    @PreviewParameter(DsContributionTimelinePreviewProvider::class) cells:
        List<DsContributionCellUiState>,
) {
    DsTheme {
        DsContributionTimeline(cells = cells)
    }
}
