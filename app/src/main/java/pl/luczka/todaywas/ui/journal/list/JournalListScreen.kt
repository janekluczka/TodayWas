package pl.luczka.todaywas.ui.journal.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.chips.DsChip
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionGrid
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionGridSize
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.journal.JournalEntryRow
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.JournalSortUiState
import java.time.Instant
import java.time.LocalDate

@Composable
fun JournalListScreen(
    onBack: () -> Unit,
    onEntryClicked: (String) -> Unit,
    viewModel: JournalListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JournalListUiEvent.NavigateToDetail -> onEntryClicked(event.id)
            }
        }
    }

    JournalListScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@Composable
private fun JournalListScreenContent(
    uiState: JournalListUiState,
    onIntent: (JournalListIntent) -> Unit,
    onBack: () -> Unit,
) {
    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.journal_list_title),
                navigationIcon = {
                    DsIconButton(onClick = onBack) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Full-bleed (no horizontal inset from this Column): the grid owns its own 16.dp
            // content padding via contentPadding, so it scrolls edge-to-edge while still resting
            // at the same inset as the rest of the screen's content.
            DsContributionGrid(
                cells = uiState.contributionGrid.cells,
                cellSize = DsContributionGridSize.MEDIUM,
                contentPadding = PaddingValues(horizontal = DsSpacing.space400),
                showMonthLabels = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.space200),
            )
            SortChipRow(
                selected = uiState.selectedSort,
                onSortSelected = { onIntent(JournalListIntent.SortSelected(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!uiState.isLoading && uiState.entries.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.space400),
                ) {
                    DsText(
                        text = stringResource(R.string.main_journal_empty_state),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(uiState.entries) { entry ->
                        JournalEntryRow(
                            entry = entry,
                            onClick = { onIntent(JournalListIntent.EntryClicked(entry)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChipRow(
    selected: JournalSortUiState,
    onSortSelected: (JournalSortUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.space200),
        contentPadding = PaddingValues(
            horizontal = DsSpacing.space400,
            vertical = DsSpacing.space200,
        ),
        modifier = modifier,
    ) {
        items(JournalSortUiState.entries) { sort ->
            DsChip(
                text = stringResource(sort.labelRes()),
                selected = sort == selected,
                onClick = { onSortSelected(sort) },
            )
        }
    }
}

private fun JournalSortUiState.labelRes(): Int = when (this) {
    JournalSortUiState.NEWEST_FIRST -> R.string.journal_sort_newest_first
    JournalSortUiState.OLDEST_FIRST -> R.string.journal_sort_oldest_first
}

private class JournalListUiStatePreviewProvider : PreviewParameterProvider<JournalListUiState> {
    private val previewGrid = ContributionGridUiState(
        cells = List(7) {
            DsContributionCellUiState.Level(
                date = LocalDate.now().minusDays(it.toLong()),
                level = DsContributionLevel.entries[it % DsContributionLevel.entries.size],
            )
        },
    )
    override val values = sequenceOf(
        JournalListUiState(isLoading = true),
        JournalListUiState(
            isLoading = false,
            entries = emptyList(),
            contributionGrid = previewGrid,
        ),
        JournalListUiState(
            isLoading = false,
            entries = (1..8).map {
                JournalEntryUiState(
                    id = it.toString(),
                    date = LocalDate.now().minusDays(it.toLong()),
                    formattedDate = "Entry $it",
                    text = "Entry number $it",
                    createdAt = Instant.now(),
                )
            },
            contributionGrid = previewGrid,
        ),
    )
}

@PreviewLightDark
@Composable
private fun JournalListScreenPreview(
    @PreviewParameter(JournalListUiStatePreviewProvider::class) state: JournalListUiState,
) {
    DsTheme {
        JournalListScreenContent(
            uiState = state,
            onIntent = {},
            onBack = {},
        )
    }
}
