package pl.luczka.todaywas.ui.habit

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.chips.DsChip
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.pickers.DsDateStrip
import pl.luczka.todaywas.core.designsystem.components.segmentedbuttons.DsSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.theme.SuccessContainerDark
import pl.luczka.todaywas.ui.theme.SuccessContainerLight
import pl.luczka.todaywas.ui.theme.SuccessLabelDark
import pl.luczka.todaywas.ui.theme.SuccessLabelLight
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.LocalDate

@Composable
fun LogHabitCheckInsScreen(
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: LogHabitCheckInsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                LogHabitCheckInsUiEvent.Saved -> onSaved()
                LogHabitCheckInsUiEvent.Cancelled -> onCancelled()
            }
        }
    }

    LogHabitCheckInsScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

private fun LogHabitCheckInsUiState.hasPendingInput(): Boolean =
    rows.any { it is HabitCheckInRowUiState.Editable && it.value != null }

@Composable
private fun LogHabitCheckInsScreenContent(
    uiState: LogHabitCheckInsUiState,
    onIntent: (LogHabitCheckInsIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.habit_checkin_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.habit_checkin_title),
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(LogHabitCheckInsIntent.CancelClicked) }) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    DsButtonWithLoading(
                        text = stringResource(R.string.habit_checkin_save_cta),
                        onClick = { onIntent(LogHabitCheckInsIntent.SaveClicked) },
                        enabled = !uiState.isSaving && uiState.hasPendingInput(),
                        loading = uiState.isSaving,
                    )
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
            DsDateStrip(
                selectedDate = uiState.selectedDate,
                isSelectable = { it in uiState.selectableDates },
                onDateSelected = { onIntent(LogHabitCheckInsIntent.DateSelected(it)) },
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.rows) { row ->
                    HabitCheckInRow(
                        row = row,
                        onIntent = onIntent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitCheckInRow(
    row: HabitCheckInRowUiState,
    onIntent: (LogHabitCheckInsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val doneLabel = stringResource(R.string.habit_checkin_done_label)
    val notDoneLabel = stringResource(R.string.habit_checkin_not_done_label)
    val label: (Int) -> String = if (row.type == HabitTypeUiState.BINARY) {
        { value -> if (value == 1) doneLabel else notDoneLabel }
    } else {
        { value -> value.toString() }
    }
    val isDarkTheme = isSystemInDarkTheme()
    val doneChipContainerColor = if (isDarkTheme) SuccessContainerDark else SuccessContainerLight
    val doneChipLabelColor = if (isDarkTheme) SuccessLabelDark else SuccessLabelLight
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DsText(text = row.name)
            if (row is HabitCheckInRowUiState.AlreadyLogged) {
                DsChip(
                    text = doneLabel,
                    containerColor = doneChipContainerColor,
                    labelColor = doneChipLabelColor,
                )
            }
        }
        when (row) {
            is HabitCheckInRowUiState.Editable -> {
                DsSegmentedRow(
                    items = row.range.toList(),
                    selectedItem = row.value,
                    onItemSelected = { onIntent(LogHabitCheckInsIntent.ValueChanged(row.habitId, it)) },
                    label = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            is HabitCheckInRowUiState.AlreadyLogged -> {
                DsSegmentedRow(
                    items = row.range.toList(),
                    selectedItem = row.value,
                    onItemSelected = {},
                    enabled = false,
                    label = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LogHabitCheckInsScreenPreview() {
    TodayWasTheme {
        val today = LocalDate.now()
        LogHabitCheckInsScreenContent(
            uiState = LogHabitCheckInsUiState(
                selectableDates = (0..6).map { today.minusDays((6 - it).toLong()) },
                selectedDate = today,
                rows = listOf(
                    HabitCheckInRowUiState.Editable(
                        habitId = 1L,
                        name = "Drink water",
                        value = null,
                        range = 0..1,
                        type = HabitTypeUiState.BINARY,
                    ),
                    HabitCheckInRowUiState.Editable(
                        habitId = 2L,
                        name = "Mood",
                        value = 3,
                        range = 1..5,
                        type = HabitTypeUiState.SCALE,
                    ),
                    HabitCheckInRowUiState.AlreadyLogged(
                        habitId = 3L,
                        name = "Read",
                        range = 0..1,
                        type = HabitTypeUiState.BINARY,
                        value = 1,
                    ),
                ),
                isSaving = false,
                saveError = false,
            ),
            onIntent = {},
        )
    }
}
