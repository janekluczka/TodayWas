package pl.luczka.todaywas.ui.habit.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsFilledTonalIconButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionGrid
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionGridSize
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsBottomSheet
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.segmentedbuttons.DsSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
            // Full-bleed (no horizontal inset from this Column): the grid owns its own 16.dp
            // content padding, so it scrolls edge-to-edge while resting at the same inset as the
            // day panel below it.
            DsContributionGrid(
                cells = uiState.contributionGrid.cells,
                cellSize = DsContributionGridSize.LARGE,
                contentPadding = PaddingValues(horizontal = DsSpacing.space400),
                showMonthLabels = true,
                selectedDate = uiState.selectedDate,
                onCellClick = { date -> onIntent(HabitDetailIntent.DaySelected(date)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.space200, bottom = DsSpacing.space600),
            )
            DayDetailPanel(
                day = uiState.selectedDay,
                type = uiState.type,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.space400),
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
            checkInCount = uiState.checkInCount,
            onIntent = onIntent,
        )
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

// Replaces the old full-history rows list: shows only the day currently selected in the grid
// above, centered under it with plenty of whitespace to spare. A logged value gets a very large
// display style since it's the one thing worth emphasizing here; "Not logged" is deliberately
// plain (titleMedium) rather than matching that size — it's an absence, not a value to celebrate.
// The status text sits over an invisible displayLarge copy of the "Not logged" label, so the box
// it occupies is always exactly the same size regardless of which day is selected — without it,
// switching between a big logged value and the small "Not logged" label would visibly resize the
// panel and shift the buttons below it. Edit-or-Add/Delete are filled icon buttons, not text
// buttons, so the pair reads as a compact action cluster under the centered date/value rather than
// a left-aligned row. Neither action shows for an unlogged day outside the addable window
// (today/yesterday) — see HabitDetailMapper.toSelectedDayUiState for the eligibility rule.
@Composable
private fun DayDetailPanel(
    day: HabitDetailDayUiState,
    type: HabitTypeUiState,
    onIntent: (HabitDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDate = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val dateLabel = if (day.date == LocalDate.now()) {
        stringResource(R.string.habit_detail_today_suffix_format, formattedDate)
    } else {
        formattedDate
    }
    val statusLabel = if (day.alreadyLogged) {
        valueLabel(day.value, type)
    } else {
        stringResource(R.string.habit_checkin_not_logged_label)
    }
    val statusStyle = if (day.alreadyLogged) {
        MaterialTheme.typography.displayLarge
    } else {
        MaterialTheme.typography.titleMedium
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        DsText(text = dateLabel, style = MaterialTheme.typography.titleMedium)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                top = DsSpacing.space100,
                bottom = DsSpacing.space400,
            ),
        ) {
            DsText(
                text = stringResource(R.string.habit_checkin_not_logged_label),
                style = MaterialTheme.typography.displayLarge,
                color = Color.Transparent,
            )
            DsText(text = statusLabel, style = statusStyle)
        }
        if (day.eligibleForEdit || day.alreadyLogged) {
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.space200)) {
                if (day.eligibleForEdit) {
                    DsFilledTonalIconButton(
                        onClick = { onIntent(HabitDetailIntent.EditRowClicked(day.date)) },
                    ) {
                        DsIcon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(
                                if (day.alreadyLogged) {
                                    R.string.habit_detail_edit_action
                                } else {
                                    R.string.habit_detail_add_action
                                },
                            ),
                        )
                    }
                }
                if (day.alreadyLogged) {
                    DsFilledTonalIconButton(
                        onClick = { onIntent(HabitDetailIntent.DeleteCheckInClicked(day.date)) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
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

private class HabitDetailScreenPreviewStateProvider : PreviewParameterProvider<HabitDetailUiState> {
    override val values = sequenceOf(
        // Today selected, not yet logged but addable.
        HabitDetailUiState(
            isLoading = false,
            habitName = "Drink water",
            type = HabitTypeUiState.BINARY,
            range = 0..1,
            selectedDate = LocalDate.now(),
            selectedDay = HabitDetailDayUiState(
                date = LocalDate.now(),
                value = null,
                eligibleForEdit = true,
                alreadyLogged = false,
            ),
            checkInCount = 2,
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            isSaving = false,
            saveError = false,
            saveErrorIsWindowExpired = false,
        ),
        // A past day, logged and still within the edit window, edit sheet open.
        HabitDetailUiState(
            isLoading = false,
            habitName = "Mood",
            type = HabitTypeUiState.SCALE,
            range = 1..5,
            selectedDate = LocalDate.now(),
            selectedDay = HabitDetailDayUiState(
                date = LocalDate.now(),
                value = 4,
                eligibleForEdit = true,
                alreadyLogged = true,
            ),
            checkInCount = 2,
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            editingDate = LocalDate.now(),
            editingValue = 4,
            isSaving = false,
            saveError = true,
            saveErrorIsWindowExpired = false,
        ),
        // A past day, logged but past the edit window: delete-only, no edit action.
        HabitDetailUiState(
            isLoading = false,
            habitName = "Drink water",
            type = HabitTypeUiState.BINARY,
            range = 0..1,
            selectedDate = LocalDate.now().minusDays(1),
            selectedDay = HabitDetailDayUiState(
                date = LocalDate.now().minusDays(1),
                value = 0,
                eligibleForEdit = false,
                alreadyLogged = true,
            ),
            checkInCount = 2,
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            isSaving = false,
            saveError = false,
            saveErrorIsWindowExpired = false,
            isDeleteHabitDialogVisible = true,
        ),
        // An older, unlogged, non-addable day: view-only, no actions.
        HabitDetailUiState(
            isLoading = false,
            habitName = "Drink water",
            type = HabitTypeUiState.BINARY,
            range = 0..1,
            selectedDate = LocalDate.now().minusDays(5),
            selectedDay = HabitDetailDayUiState(
                date = LocalDate.now().minusDays(5),
                value = null,
                eligibleForEdit = false,
                alreadyLogged = false,
            ),
            checkInCount = 2,
            contributionGrid = ContributionGridUiState(cells = emptyList()),
            isSaving = false,
            saveError = false,
            saveErrorIsWindowExpired = false,
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
