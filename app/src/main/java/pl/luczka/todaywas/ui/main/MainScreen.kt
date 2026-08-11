package pl.luczka.todaywas.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.chips.DsChip
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionGrid
import pl.luczka.todaywas.core.designsystem.components.fab.DsExtendedFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.fab.DsFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import java.time.Instant
import java.time.LocalDate

@Composable
fun MainScreen(
    onAddEntryClicked: () -> Unit,
    onJournalEntryClicked: (JournalEntryUiState) -> Unit,
    onCreateHabitClicked: () -> Unit,
    onLogCheckInsClicked: () -> Unit,
    onHabitClicked: (String) -> Unit,
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
                is MainUiEvent.NavigateToHabitDetail -> onHabitClicked(event.habitId)
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
    DsScaffold(
        topBar = { DsTopBar(title = stringResource(R.string.main_top_bar_title)) },
        floatingActionButton = { MainFab(uiState, onIntent) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            JournalSection(uiState, onIntent, modifier = Modifier.weight(1f))
            HabitSection(uiState, onIntent, modifier = Modifier.weight(1f))
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

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space200),
    ) {
        if (uiState.fabExpanded) {
            for (action in actions) {
                DsExtendedFloatingActionButton(
                    text = stringResource(action.labelRes),
                    onClick = { onIntent(MainIntent.FabActionClicked(action)) },
                )
            }
        }
        DsFloatingActionButton(
            onClick = {
                if (actions.size == 1) {
                    onIntent(MainIntent.FabActionClicked(actions.single()))
                } else {
                    onIntent(MainIntent.FabToggled)
                }
            },
        ) {
            DsIcon(
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        DsText(
            text = stringResource(R.string.main_journal_section_title),
            modifier = Modifier.padding(horizontal = DsSpacing.space600),
        )
        // Full-bleed (no horizontal inset), same as Habit Detail's grid: it needs all available
        // width so more weeks are visible at once.
        DsContributionGrid(
            cells = uiState.journalContributionGrid.cells,
            modifier = Modifier.fillMaxWidth(),
        )
        ContributionWindowChipRow(
            availableWindows = uiState.journalAvailableWindows,
            selectedWindow = uiState.journalSelectedWindow,
            onWindowSelected = { onIntent(MainIntent.JournalWindowSelected(it)) },
            modifier = Modifier.padding(horizontal = DsSpacing.space600, vertical = DsSpacing.space200),
        )
        if (uiState.journalEntries.isEmpty()) {
            DsText(
                text = stringResource(R.string.main_journal_empty_state),
                modifier = Modifier.padding(horizontal = DsSpacing.space600),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = DsSpacing.space600),
            ) {
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
private fun ContributionWindowChipRow(
    availableWindows: List<ContributionWindowUiState>,
    selectedWindow: ContributionWindowUiState,
    onWindowSelected: (ContributionWindowUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier) {
        items(availableWindows) { window ->
            DsChip(
                text = window.label(),
                selected = window == selectedWindow,
                onClick = { onWindowSelected(window) },
                modifier = Modifier.padding(end = DsSpacing.space200),
            )
        }
    }
}

@Composable
private fun ContributionWindowUiState.label(): String = when (this) {
    ContributionWindowUiState.RollingTwelveMonths -> stringResource(R.string.contribution_window_last_12_months_label)
    is ContributionWindowUiState.CalendarYear -> year.toString()
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
            .padding(vertical = DsSpacing.space200),
    ) {
        DsText(text = entry.formattedDate)
        DsText(
            text = entry.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HabitSection(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(DsSpacing.space600),
    ) {
        DsText(text = stringResource(R.string.main_habit_section_title))
        if (uiState.habits.isEmpty()) {
            DsText(text = stringResource(R.string.main_habit_empty_state))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.habits) { habit ->
                    HabitListItem(
                        habit = habit,
                        onClick = { onIntent(MainIntent.HabitClicked(habit)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitListItem(
    habit: HabitUiState,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DsSpacing.space200),
    ) {
        DsText(text = habit.name)
        DsText(text = habit.todayStatus.displayText())
    }
}

@Composable
private fun HabitCheckInStatusUiState.displayText(): String = when (this) {
    HabitCheckInStatusUiState.NotLogged -> ""
    is HabitCheckInStatusUiState.LoggedBinary ->
        stringResource(if (done) R.string.habit_checkin_done_label else R.string.habit_checkin_not_done_label)
    is HabitCheckInStatusUiState.LoggedScale -> value.toString()
}

private class MainScreenPreviewStateProvider : PreviewParameterProvider<MainUiState> {
    override val values = sequenceOf(
        MainUiState(
            journalEntries = emptyList(),
            habits = emptyList(),
            journalContributionGrid = previewJournalContributionGrid,
            journalAvailableWindows = previewJournalAvailableWindows,
            journalSelectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            fabActions = listOf(FabActionUiState.ADD_JOURNAL_ENTRY, FabActionUiState.CREATE_HABIT),
            fabExpanded = false,
        ),
        MainUiState(
            journalEntries = listOf(
                JournalEntryUiState(
                    id = "1",
                    date = LocalDate.now(),
                    formattedDate = "Jul 27, 2026",
                    text = "Today was a good day.",
                    createdAt = Instant.now(),
                ),
                JournalEntryUiState(
                    id = "2",
                    date = LocalDate.now().minusDays(1),
                    formattedDate = "Jul 26, 2026",
                    text = "A long entry that should get truncated in the list preview once it wraps past two lines of text.",
                    createdAt = Instant.now(),
                ),
            ),
            habits = listOf(
                HabitUiState(
                    id = "1",
                    name = "Drink water",
                    type = HabitTypeUiState.BINARY,
                    todayStatus = HabitCheckInStatusUiState.NotLogged,
                ),
                HabitUiState(
                    id = "2",
                    name = "Mood",
                    type = HabitTypeUiState.SCALE,
                    todayStatus = HabitCheckInStatusUiState.LoggedScale(value = 4),
                ),
            ),
            journalContributionGrid = previewJournalContributionGrid,
            journalAvailableWindows = previewJournalAvailableWindows,
            journalSelectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            fabActions = listOf(FabActionUiState.CREATE_HABIT, FabActionUiState.LOG_HABIT_CHECK_INS),
            fabExpanded = false,
        ),
    )
}

private val previewJournalContributionGrid = ContributionGridUiState(cells = emptyList())

private val previewJournalAvailableWindows = listOf(
    ContributionWindowUiState.RollingTwelveMonths,
    ContributionWindowUiState.CalendarYear(LocalDate.now().year),
)

@PreviewLightDark
@Composable
private fun MainScreenPreview(
    @PreviewParameter(MainScreenPreviewStateProvider::class) state: MainUiState,
) {
    DsTheme {
        MainScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
