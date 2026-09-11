package pl.luczka.todaywas.ui.habit.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.cards.DsCard
import pl.luczka.todaywas.core.designsystem.components.chips.DsChip
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionGrid
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsBottomSheet
import pl.luczka.todaywas.core.designsystem.components.dividers.DsHorizontalDivider
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.segmentedbuttons.DsSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Bigger than the compact 12dp/2dp/6dp used elsewhere (Main, journal view-all) — this is the one
// place a single habit's grid is the main content rather than one of several overview items, so
// there's room to make it easier to read.
private val DETAIL_GRID_CELL_SIZE = 28.dp
private val DETAIL_GRID_CELL_SPACING = 4.dp
private val DETAIL_GRID_MONTH_GAP = 8.dp

@Composable
fun HabitDetailScreen(
    habitId: String,
    onBack: () -> Unit,
    viewModel: HabitDetailViewModel =
        hiltViewModel<HabitDetailViewModel, HabitDetailViewModel.Factory> { factory ->
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
            val message =
                if (uiState.saveErrorIsWindowExpired) expiredErrorMessage else genericErrorMessage
            snackbarHostState.showSnackbar(message)
        }
    }
    val deleteErrorMessage = stringResource(R.string.habit_detail_delete_error)
    LaunchedEffect(uiState.deleteHabitError) {
        if (uiState.deleteHabitError) {
            snackbarHostState.showSnackbar(deleteErrorMessage)
        }
    }
    LaunchedEffect(uiState.deleteCheckInError) {
        if (uiState.deleteCheckInError) {
            snackbarHostState.showSnackbar(deleteErrorMessage)
        }
    }

    DsScaffold(
        topBar = {
            DsTopBar(
                title = uiState.habitName,
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(HabitDetailIntent.BackClicked) }) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    DsIconButton(onClick = { onIntent(HabitDetailIntent.DeleteHabitClicked) }) {
                        DsIcon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(
                                R.string.habit_detail_delete_action,
                            ),
                        )
                    }
                },
            )
        },
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ContributionWindowChipRow(
                availableWindows = uiState.availableWindows,
                selectedWindow = uiState.selectedWindow,
                onWindowSelected = { onIntent(HabitDetailIntent.WindowSelected(it)) },
                modifier = Modifier.padding(
                    horizontal = DsSpacing.space600,
                    vertical = DsSpacing.space200,
                ),
            )
            // Full-bleed (no horizontal inset), same reasoning as the journal view-all screen's
            // grid: it needs all available width so more weeks are visible at once.
            DsContributionGrid(
                cells = uiState.contributionGrid.cells,
                cellSize = DETAIL_GRID_CELL_SIZE,
                cellSpacing = DETAIL_GRID_CELL_SPACING,
                monthGap = DETAIL_GRID_MONTH_GAP,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DsSpacing.space400),
            )
            HabitDetailRowsList(
                rows = uiState.rows,
                type = uiState.type,
                onIntent = onIntent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.space600),
            )
        }
    }

    if (uiState.isEditSheetOpen) {
        DsBottomSheet(
            onDismissRequest = { onIntent(HabitDetailIntent.CancelEditClicked) },
            onCloseClicked = { onIntent(HabitDetailIntent.CancelEditClicked) },
            closeContentDescription = stringResource(R.string.habit_detail_cancel_edit_action),
            saveText = stringResource(R.string.habit_detail_save_cta),
            onSaveClicked = { onIntent(HabitDetailIntent.SaveClicked) },
            isSaving = uiState.isSaving,
            saveEnabled = uiState.editingValue != null,
        ) {
            EditRowSheetContent(uiState, onIntent)
        }
    }

    if (uiState.isDeleteHabitDialogVisible) {
        DeleteHabitDialog(
            checkInCount = uiState.rows.count { it.alreadyLogged },
            onIntent = onIntent,
        )
    }
}

// The rows list is the habit's full check-in history (unbounded, grows over the habit's
// lifetime) — a LazyColumn keeps it virtualized. The DsCard + DsHorizontalDivider pairing gives it
// the same bordered/divided look as DsSectionedList without reusing that component directly:
// DsSectionedList is explicitly built for small, non-lazy lists (e.g. Main's capped 5-item
// sections) and would eagerly compose every row here as a habit's history grows.
@Composable
private fun HabitDetailRowsList(
    rows: List<HabitDetailRowUiState>,
    type: HabitTypeUiState,
    onIntent: (HabitDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DsCard(modifier = modifier) {
        LazyColumn {
            itemsIndexed(rows, key = { _, row -> row.date.toEpochDay() }) { index, row ->
                HabitDetailRow(
                    row = row,
                    type = type,
                    onIntent = onIntent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DsSpacing.space400),
                )
                if (index < rows.lastIndex) DsHorizontalDivider()
            }
        }
    }
}

@Composable
private fun valueLabel(
    value: Int?,
    type: HabitTypeUiState,
): String = when {
    value == null -> ""
    type == HabitTypeUiState.BINARY && value == 1 -> stringResource(
        R.string.habit_checkin_done_label,
    )
    type == HabitTypeUiState.BINARY -> stringResource(R.string.habit_checkin_not_done_label)
    else -> value.toString()
}

@Composable
private fun HabitDetailRow(
    row: HabitDetailRowUiState,
    type: HabitTypeUiState,
    onIntent: (HabitDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDate = row.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val dateLabel = if (row.date == LocalDate.now()) {
        stringResource(R.string.habit_detail_today_suffix_format, formattedDate)
    } else {
        formattedDate
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.space200),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        DsText(text = dateLabel, modifier = Modifier.weight(1f))
        DsText(text = valueLabel(row.value, type))
        if (row.eligibleForEdit) {
            DsIconButton(onClick = { onIntent(HabitDetailIntent.EditRowClicked(row.date)) }) {
                DsIcon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.habit_detail_edit_action),
                )
            }
        }
        if (row.alreadyLogged) {
            DsIconButton(
                onClick = { onIntent(HabitDetailIntent.DeleteCheckInClicked(row.date)) },
            ) {
                DsIcon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(
                        R.string.habit_detail_delete_checkin_action,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EditRowSheetContent(
    uiState: HabitDetailUiState,
    onIntent: (HabitDetailIntent) -> Unit,
) {
    val date = uiState.editingDate ?: return
    val formattedDate = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val doneLabel = stringResource(R.string.habit_checkin_done_label)
    val notDoneLabel = stringResource(R.string.habit_checkin_not_done_label)
    val label: (Int) -> String = if (uiState.type == HabitTypeUiState.BINARY) {
        { value -> if (value == 1) doneLabel else notDoneLabel }
    } else {
        { value -> value.toString() }
    }
    Column(modifier = Modifier.padding(horizontal = DsSpacing.space600)) {
        DsText(
            text = formattedDate,
            modifier = Modifier.padding(bottom = DsSpacing.space400),
        )
        DsSegmentedRow(
            items = uiState.range.toList(),
            selectedItem = uiState.editingValue,
            onItemSelected = { onIntent(HabitDetailIntent.ValueChanged(it)) },
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = DsSpacing.space600),
        )
    }
}

@Composable
private fun DeleteHabitDialog(
    checkInCount: Int,
    onIntent: (HabitDetailIntent) -> Unit,
) {
    DsAlertDialog(
        onDismissRequest = { onIntent(HabitDetailIntent.DeleteHabitDismissed) },
        title = { DsText(text = stringResource(R.string.habit_detail_delete_dialog_title)) },
        text = {
            DsText(
                text = stringResource(
                    R.string.habit_detail_delete_dialog_text_format,
                    checkInCount,
                ),
            )
        },
        confirmButton = {
            DsTextButton(
                text = stringResource(R.string.habit_detail_delete_confirm_cta),
                onClick = { onIntent(HabitDetailIntent.DeleteHabitConfirmed) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            )
        },
        dismissButton = {
            DsTextButton(
                text = stringResource(R.string.habit_detail_delete_cancel_cta),
                onClick = { onIntent(HabitDetailIntent.DeleteHabitDismissed) },
            )
        },
    )
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
    ContributionWindowUiState.RollingTwelveMonths -> stringResource(
        R.string.contribution_window_last_12_months_label,
    )
    is ContributionWindowUiState.CalendarYear -> year.toString()
}

private class HabitDetailScreenPreviewStateProvider : PreviewParameterProvider<HabitDetailUiState> {
    override val values = sequenceOf(
        HabitDetailUiState(
            isLoading = false,
            habitName = "Drink water",
            type = HabitTypeUiState.BINARY,
            range = 0..1,
            rows = listOf(
                HabitDetailRowUiState(
                    date = LocalDate.now(),
                    value = null,
                    eligibleForEdit = true,
                    alreadyLogged = false,
                ),
                HabitDetailRowUiState(
                    date = LocalDate.now().minusDays(1),
                    value = 1,
                    eligibleForEdit = false,
                    alreadyLogged = true,
                ),
                HabitDetailRowUiState(
                    date = LocalDate.now().minusDays(5),
                    value = 0,
                    eligibleForEdit = false,
                    alreadyLogged = true,
                ),
            ),
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            availableWindows = listOf(
                ContributionWindowUiState.RollingTwelveMonths,
                ContributionWindowUiState.CalendarYear(LocalDate.now().year),
            ),
            selectedWindow = ContributionWindowUiState.RollingTwelveMonths,
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
                HabitDetailRowUiState(
                    date = LocalDate.now(),
                    value = 4,
                    eligibleForEdit = true,
                    alreadyLogged = true,
                ),
                HabitDetailRowUiState(
                    date = LocalDate.now().minusDays(1),
                    value = 3,
                    eligibleForEdit = true,
                    alreadyLogged = true,
                ),
            ),
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            availableWindows = listOf(
                ContributionWindowUiState.RollingTwelveMonths,
                ContributionWindowUiState.CalendarYear(LocalDate.now().year),
            ),
            selectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            editingDate = LocalDate.now(),
            editingValue = 4,
            isSaving = false,
            saveError = true,
            saveErrorIsWindowExpired = false,
        ),
        HabitDetailUiState(
            isLoading = false,
            habitName = "Drink water",
            type = HabitTypeUiState.BINARY,
            range = 0..1,
            rows = listOf(
                HabitDetailRowUiState(
                    date = LocalDate.now(),
                    value = 1,
                    eligibleForEdit = true,
                    alreadyLogged = true,
                ),
                HabitDetailRowUiState(
                    date = LocalDate.now().minusDays(1),
                    value = 0,
                    eligibleForEdit = false,
                    alreadyLogged = true,
                ),
            ),
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            availableWindows = listOf(
                ContributionWindowUiState.RollingTwelveMonths,
                ContributionWindowUiState.CalendarYear(LocalDate.now().year),
            ),
            selectedWindow = ContributionWindowUiState.RollingTwelveMonths,
            isSaving = false,
            saveError = false,
            saveErrorIsWindowExpired = false,
            isDeleteHabitDialogVisible = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun HabitDetailScreenPreview(
    @PreviewParameter(HabitDetailScreenPreviewStateProvider::class) state: HabitDetailUiState,
) {
    DsTheme {
        HabitDetailScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DeleteHabitDialogPreview() {
    DsTheme {
        DeleteHabitDialog(checkInCount = 12, onIntent = {})
    }
}
