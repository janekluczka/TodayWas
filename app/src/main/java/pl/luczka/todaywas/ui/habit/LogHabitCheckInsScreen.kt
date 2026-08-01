package pl.luczka.todaywas.ui.habit

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.TodayWasChip
import pl.luczka.todaywas.core.designsystem.components.TodayWasDateStrip
import pl.luczka.todaywas.core.designsystem.components.TodayWasIcon
import pl.luczka.todaywas.core.designsystem.components.TodayWasIconButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.TodayWasSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.LocalDate

private val DoneChipContainerColor = Color(0xFFC8E6C9)
private val DoneChipLabelColor = Color(0xFF2E7D32)

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

    TodayWasScaffold(
        topBar = {
            TodayWasTopBar(
                title = stringResource(R.string.habit_checkin_title),
                navigationIcon = {
                    TodayWasIconButton(onClick = { onIntent(LogHabitCheckInsIntent.CancelClicked) }) {
                        TodayWasIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    TodayWasButtonWithLoading(
                        text = stringResource(R.string.habit_checkin_save_cta),
                        onClick = { onIntent(LogHabitCheckInsIntent.SaveClicked) },
                        enabled = !uiState.isSaving && uiState.hasPendingInput(),
                        loading = uiState.isSaving,
                    )
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
            TodayWasDateStrip(
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
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TodayWasText(text = row.name)
            if (row is HabitCheckInRowUiState.AlreadyLogged) {
                TodayWasChip(
                    text = doneLabel,
                    containerColor = DoneChipContainerColor,
                    labelColor = DoneChipLabelColor,
                )
            }
        }
        when (row) {
            is HabitCheckInRowUiState.Editable -> {
                TodayWasSegmentedRow(
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
                TodayWasSegmentedRow(
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
