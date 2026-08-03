package pl.luczka.todaywas.ui.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.segmentedbuttons.DsSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.HabitTypeUiState
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

    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.habit_detail_title),
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(HabitDetailIntent.BackClicked) }) {
                        DsIcon(
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
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DsSpacing.space600),
        ) {
            DsText(
                text = uiState.habitName,
                modifier = Modifier.padding(top = DsSpacing.space600),
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.rows) { row ->
                    HabitDetailRow(
                        row = row,
                        type = uiState.type,
                        range = uiState.range,
                        enabled = uiState.isEditMode && row.eligibleForEdit,
                        onIntent = onIntent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DsSpacing.space400),
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitDetailActions(
    uiState: HabitDetailUiState,
    onIntent: (HabitDetailIntent) -> Unit,
) {
    if (uiState.isEditMode) {
        Row {
            DsIconButton(onClick = { onIntent(HabitDetailIntent.CancelEditClicked) }) {
                DsIcon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.habit_detail_cancel_edit_action),
                )
            }
            DsButtonWithLoading(
                text = stringResource(R.string.habit_detail_save_cta),
                onClick = { onIntent(HabitDetailIntent.SaveClicked) },
                enabled = !uiState.isSaving,
                loading = uiState.isSaving,
            )
        }
    } else if (uiState.rows.any { it.eligibleForEdit }) {
        DsIconButton(onClick = { onIntent(HabitDetailIntent.EditClicked) }) {
            DsIcon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.habit_detail_edit_action),
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
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val dateLabel = when (row.date) {
        today -> stringResource(R.string.habit_detail_today_label)
        yesterday -> stringResource(R.string.habit_detail_yesterday_label)
        else -> row.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    val doneLabel = stringResource(R.string.habit_checkin_done_label)
    val notDoneLabel = stringResource(R.string.habit_checkin_not_done_label)
    val label: (Int) -> String = if (type == HabitTypeUiState.BINARY) {
        { value -> if (value == 1) doneLabel else notDoneLabel }
    } else {
        { value -> value.toString() }
    }

    Column(modifier = modifier) {
        DsText(text = dateLabel)
        DsSegmentedRow(
            items = range.toList(),
            selectedItem = row.value,
            onItemSelected = { onIntent(HabitDetailIntent.ValueChanged(row.date, it)) },
            enabled = enabled,
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DsSpacing.space200),
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
            isEditMode = false,
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
            isEditMode = true,
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
    DsTheme {
        HabitDetailScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
