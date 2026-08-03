package pl.luczka.todaywas.ui.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasBottomSheet
import pl.luczka.todaywas.core.designsystem.components.TodayWasChip
import pl.luczka.todaywas.core.designsystem.components.TodayWasContributionGrid
import pl.luczka.todaywas.core.designsystem.components.TodayWasIcon
import pl.luczka.todaywas.core.designsystem.components.TodayWasIconButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.TodayWasSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    viewModel: HabitDetailViewModel = hiltViewModel<HabitDetailViewModel, HabitDetailViewModel.Factory> { factory ->
        factory.create(habitId)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                HabitDetailUiEvent.NavigatedBack -> onBack()
            }
        }
    }

    HabitDetailScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun HabitDetailScreenContent(
    uiState: HabitDetailUiState,
    onIntent: (HabitDetailIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val genericErrorMessage = stringResource(R.string.habit_detail_error)
    val expiredErrorMessage = stringResource(R.string.habit_detail_edit_window_expired_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            val message = if (uiState.saveErrorIsWindowExpired) expiredErrorMessage else genericErrorMessage
            snackbarHostState.showSnackbar(message)
        }
    }

    TodayWasScaffold(
        topBar = {
            TodayWasTopBar(
                title = stringResource(R.string.habit_detail_title),
                navigationIcon = {
                    TodayWasIconButton(onClick = { onIntent(HabitDetailIntent.BackClicked) }) {
                        TodayWasIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    HabitDetailActions(uiState, onIntent)
                },
            )
        },
        snackbarHost = { TodayWasSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TodayWasText(
                text = uiState.habitName,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
            // Full-bleed (no horizontal inset), unlike the name/chips above and below: the grid
            // needs all available width so more weeks are visible at once — the outer 24dp
            // content padding was visibly cutting into the grid's usable area.
            TodayWasContributionGrid(
                cells = uiState.contributionGrid.cells,
                modifier = Modifier.fillMaxWidth(),
            )
            ContributionWindowChipRow(
                availableWindows = uiState.availableWindows,
                selectedWindow = uiState.selectedWindow,
                onWindowSelected = { onIntent(HabitDetailIntent.WindowSelected(it)) },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }

    if (uiState.isEditSheetOpen) {
        TodayWasBottomSheet(
            onDismissRequest = { onIntent(HabitDetailIntent.CancelEditClicked) },
            onCloseClicked = { onIntent(HabitDetailIntent.CancelEditClicked) },
            closeContentDescription = stringResource(R.string.habit_detail_cancel_edit_action),
            saveText = stringResource(R.string.habit_detail_save_cta),
            onSaveClicked = { onIntent(HabitDetailIntent.SaveClicked) },
            isSaving = uiState.isSaving,
        ) {
            HabitDetailEditSheetContent(uiState, onIntent)
        }
    }
}

@Composable
private fun HabitDetailActions(
    uiState: HabitDetailUiState,
    onIntent: (HabitDetailIntent) -> Unit,
) {
    if (uiState.rows.any { it.eligibleForEdit }) {
        TodayWasIconButton(onClick = { onIntent(HabitDetailIntent.EditClicked) }) {
            TodayWasIcon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.habit_detail_edit_action),
            )
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
            TodayWasChip(
                text = window.label(),
                selected = window == selectedWindow,
                onClick = { onWindowSelected(window) },
                modifier = Modifier.padding(end = 8.dp),
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
private fun HabitDetailEditSheetContent(
    uiState: HabitDetailUiState,
    onIntent: (HabitDetailIntent) -> Unit,
) {
    val editableRows = uiState.rows.filter { it.eligibleForEdit }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .padding(horizontal = 24.dp),
    ) {
        items(editableRows) { row ->
            HabitDetailRow(
                row = row,
                type = uiState.type,
                range = uiState.range,
                enabled = true,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun HabitDetailRow(
    row: HabitDetailRowUiState,
    type: HabitTypeUiState,
    range: IntRange,
    enabled: Boolean,
    onIntent: (HabitDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDate = row.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val dateLabel = if (row.date == LocalDate.now()) {
        stringResource(R.string.habit_detail_today_suffix_format, formattedDate)
    } else {
        formattedDate
    }
    val doneLabel = stringResource(R.string.habit_checkin_done_label)
    val notDoneLabel = stringResource(R.string.habit_checkin_not_done_label)
    val label: (Int) -> String = if (type == HabitTypeUiState.BINARY) {
        { value -> if (value == 1) doneLabel else notDoneLabel }
    } else {
        { value -> value.toString() }
    }

    Column(modifier = modifier) {
        TodayWasText(text = dateLabel)
        TodayWasSegmentedRow(
            items = range.toList(),
            selectedItem = row.value,
            onItemSelected = { onIntent(HabitDetailIntent.ValueChanged(row.date, it)) },
            enabled = enabled,
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

private class HabitDetailScreenPreviewStateProvider : PreviewParameterProvider<HabitDetailUiState> {
    override val values = sequenceOf(
        HabitDetailUiState(
            isLoading = false,
            habitName = "Drink water",
            type = HabitTypeUiState.BINARY,
            range = 0..1,
            rows = listOf(
                HabitDetailRowUiState(date = LocalDate.now(), value = null, eligibleForEdit = true, alreadyLogged = false),
                HabitDetailRowUiState(date = LocalDate.now().minusDays(1), value = 1, eligibleForEdit = false, alreadyLogged = true),
                HabitDetailRowUiState(date = LocalDate.now().minusDays(5), value = 0, eligibleForEdit = false, alreadyLogged = true),
            ),
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            availableWindows = listOf(
                ContributionWindowUiState.RollingTwelveMonths,
                ContributionWindowUiState.CalendarYear(LocalDate.now().year),
            ),
            selectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            isEditSheetOpen = false,
            isSaving = false,
            saveError = false,
            saveErrorIsWindowExpired = false,
        ),
        HabitDetailUiState(
            isLoading = false,
            habitName = "Mood",
            type = HabitTypeUiState.SCALE,
            range = 1..5,
            rows = listOf(
                HabitDetailRowUiState(date = LocalDate.now(), value = 4, eligibleForEdit = true, alreadyLogged = true),
                HabitDetailRowUiState(date = LocalDate.now().minusDays(1), value = 3, eligibleForEdit = true, alreadyLogged = true),
            ),
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            availableWindows = listOf(
                ContributionWindowUiState.RollingTwelveMonths,
                ContributionWindowUiState.CalendarYear(LocalDate.now().year),
            ),
            selectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            isEditSheetOpen = true,
            isSaving = false,
            saveError = true,
            saveErrorIsWindowExpired = false,
        ),
    )
}

@PreviewLightDark
@Composable
private fun HabitDetailScreenPreview(
    @PreviewParameter(HabitDetailScreenPreviewStateProvider::class) state: HabitDetailUiState,
) {
    TodayWasTheme {
        HabitDetailScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
