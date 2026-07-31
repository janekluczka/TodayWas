package pl.luczka.todaywas.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasExtendedFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasIcon
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.FocusUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.Instant
import java.time.LocalDate

@Composable
fun MainScreen(
    onAddEntryClicked: () -> Unit,
    onJournalEntryClicked: (JournalEntryUiState) -> Unit,
    onCreateHabitClicked: () -> Unit,
    onLogCheckInsClicked: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                MainUiEvent.NavigateToAddEntry -> onAddEntryClicked()
                is MainUiEvent.NavigateToJournalDetail -> onJournalEntryClicked(event.entry)
                MainUiEvent.NavigateToCreateHabit -> onCreateHabitClicked()
                MainUiEvent.NavigateToLogHabitCheckIns -> onLogCheckInsClicked()
            }
        }
    }

    MainScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun MainScreenContent(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
) {
    TodayWasScaffold(
        topBar = { TodayWasTopBar(title = stringResource(R.string.main_top_bar_title)) },
        floatingActionButton = { MainFab(uiState, onIntent) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (uiState.focus) {
                null -> TodayWasText(text = stringResource(R.string.main_empty_state))
                FocusUiState.JOURNAL, FocusUiState.BOTH -> JournalSection(uiState, onIntent)
                FocusUiState.HABIT -> TodayWasText(text = stringResource(R.string.main_habit_placeholder))
            }
        }
    }
}

@Composable
private fun MainFab(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
) {
    val actions = uiState.fabActions
    if (actions.isEmpty()) return

    // actions.size is always 1 today (only journal has a destination), so this FAB behaves as a
    // plain single-tap button and the expand branch below never triggers. It becomes a real
    // speed-dial automatically once a second action exists (e.g. habit tracking in S-03) — no
    // further changes needed here when that happens.
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.fabExpanded) {
            for (action in actions) {
                TodayWasExtendedFloatingActionButton(
                    text = stringResource(action.labelRes),
                    onClick = { onIntent(MainIntent.FabActionClicked(action)) },
                )
            }
        }
        TodayWasFloatingActionButton(
            onClick = {
                if (actions.size == 1) {
                    onIntent(MainIntent.FabActionClicked(actions.single()))
                } else {
                    onIntent(MainIntent.FabToggled)
                }
            },
        ) {
            TodayWasIcon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.content_description_add),
            )
        }
    }
}

@Composable
private fun JournalSection(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        TodayWasText(text = stringResource(R.string.main_journal_section_title))
        if (uiState.journalEntries.isEmpty()) {
            TodayWasText(text = stringResource(R.string.main_journal_empty_state))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.journalEntries) { entry ->
                    JournalEntryListItem(
                        entry = entry,
                        onClick = { onIntent(MainIntent.JournalEntryClicked(entry)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalEntryListItem(
    entry: JournalEntryUiState,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        TodayWasText(text = entry.formattedDate)
        TodayWasText(
            text = entry.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class MainScreenPreviewStateProvider : PreviewParameterProvider<MainUiState> {
    override val values = sequenceOf(
        MainUiState(
            focus = null,
            journalEntries = emptyList(),
            habits = emptyList(),
            fabActions = emptyList(),
            fabExpanded = false,
        ),
        MainUiState(
            focus = FocusUiState.JOURNAL,
            journalEntries = emptyList(),
            habits = emptyList(),
            fabActions = listOf(FabActionUiState.ADD_JOURNAL_ENTRY),
            fabExpanded = false,
        ),
        MainUiState(
            focus = FocusUiState.BOTH,
            journalEntries = listOf(
                JournalEntryUiState(
                    id = 1,
                    date = LocalDate.now(),
                    formattedDate = "Jul 27, 2026",
                    text = "Today was a good day.",
                    createdAt = Instant.now(),
                ),
                JournalEntryUiState(
                    id = 2,
                    date = LocalDate.now().minusDays(1),
                    formattedDate = "Jul 26, 2026",
                    text = "A long entry that should get truncated in the list preview once it wraps past two lines of text.",
                    createdAt = Instant.now(),
                ),
            ),
            habits = emptyList(),
            fabActions = emptyList(),
            fabExpanded = false,
        ),
        MainUiState(
            focus = FocusUiState.HABIT,
            journalEntries = emptyList(),
            habits = emptyList(),
            fabActions = emptyList(),
            fabExpanded = false,
        ),
    )
}

@PreviewLightDark
@Composable
private fun MainScreenPreview(
    @PreviewParameter(MainScreenPreviewStateProvider::class) state: MainUiState,
) {
    TodayWasTheme {
        MainScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
