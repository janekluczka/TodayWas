package pl.luczka.todaywas.ui.habit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.TodayWasIcon
import pl.luczka.todaywas.core.designsystem.components.TodayWasIconButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.TodayWasSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.TodayWasStepper
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTextField
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.ui.theme.TodayWasTheme

@Composable
fun CreateHabitScreen(
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: CreateHabitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CreateHabitUiEvent.Saved -> onSaved()
                CreateHabitUiEvent.Cancelled -> onCancelled()
            }
        }
    }

    CreateHabitScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

private fun CreateHabitUiState.isSaveEnabled(): Boolean = !isSaving && name.isNotBlank()

@Composable
private fun CreateHabitScreenContent(
    uiState: CreateHabitUiState,
    onIntent: (CreateHabitIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.habit_create_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    TodayWasScaffold(
        topBar = {
            TodayWasTopBar(
                title = stringResource(R.string.habit_create_title),
                navigationIcon = {
                    TodayWasIconButton(onClick = { onIntent(CreateHabitIntent.CancelClicked) }) {
                        TodayWasIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    TodayWasButtonWithLoading(
                        text = stringResource(R.string.habit_create_save_cta),
                        onClick = { onIntent(CreateHabitIntent.SaveClicked) },
                        enabled = uiState.isSaveEnabled(),
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
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            TodayWasTextField(
                value = uiState.name,
                onValueChange = { onIntent(CreateHabitIntent.NameChanged(it)) },
                label = stringResource(R.string.habit_create_name_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
            TodayWasText(
                text = stringResource(R.string.habit_create_scale_steps_label),
                modifier = Modifier.padding(top = 16.dp),
            )
            TodayWasStepper(
                value = uiState.scaleSteps,
                range = HabitScaleStepsRange,
                onValueChange = { onIntent(CreateHabitIntent.ScaleStepsChanged(it)) },
                modifier = Modifier.padding(top = 4.dp),
            )
            TodayWasText(
                text = stringResource(R.string.habit_create_preview_title),
                modifier = Modifier.padding(top = 16.dp),
            )
            CheckInInputPreview(
                uiState = uiState,
                modifier = Modifier.padding(top = 8.dp),
            )
            TodayWasTextField(
                value = uiState.description,
                onValueChange = { onIntent(CreateHabitIntent.DescriptionChanged(it)) },
                label = stringResource(R.string.habit_create_description_label),
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun CheckInInputPreview(
    uiState: CreateHabitUiState,
    modifier: Modifier = Modifier,
) {
    if (uiState.isBinary) {
        val doneLabel = stringResource(R.string.habit_checkin_done_label)
        val notDoneLabel = stringResource(R.string.habit_checkin_not_done_label)
        TodayWasSegmentedRow(
            items = listOf(0, 1),
            selectedItem = null,
            onItemSelected = {},
            enabled = false,
            label = { if (it == 1) doneLabel else notDoneLabel },
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        TodayWasSegmentedRow(
            items = (1..uiState.scaleSteps).toList(),
            selectedItem = null,
            onItemSelected = {},
            enabled = false,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun CreateHabitScreenPreview() {
    TodayWasTheme {
        CreateHabitScreenContent(
            uiState = CreateHabitUiState(
                name = "Drink water",
                description = "",
                scaleSteps = 5,
                isSaving = false,
                saveError = false,
            ),
            onIntent = {},
        )
    }
}
