package pl.luczka.todaywas.ui.journal

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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.DsDateStrip
import pl.luczka.todaywas.core.designsystem.components.DsIcon
import pl.luczka.todaywas.core.designsystem.components.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.DsTextField
import pl.luczka.todaywas.core.designsystem.components.DsTopBar
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.LocalDate

@Composable
fun AddJournalEntryScreen(
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: AddJournalEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AddJournalEntryUiEvent.Saved -> onSaved()
                AddJournalEntryUiEvent.Cancelled -> onCancelled()
            }
        }
    }

    AddJournalEntryScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun AddJournalEntryScreenContent(
    uiState: AddJournalEntryUiState,
    onIntent: (AddJournalEntryIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.journal_add_entry_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.journal_add_entry_title),
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(AddJournalEntryIntent.CancelClicked) }) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    DsButtonWithLoading(
                        text = stringResource(R.string.journal_add_entry_save_cta),
                        onClick = { onIntent(AddJournalEntryIntent.SaveClicked) },
                        enabled = !uiState.isSaving && uiState.text.isNotBlank(),
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
            JournalDateStrip(
                uiState = uiState,
                onIntent = onIntent,
            )
            DsTextField(
                value = uiState.text,
                onValueChange = { onIntent(AddJournalEntryIntent.TextChanged(it)) },
                label = stringResource(R.string.journal_add_entry_text_label),
                minLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun JournalDateStrip(
    uiState: AddJournalEntryUiState,
    onIntent: (AddJournalEntryIntent) -> Unit,
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val selectedDate = if (uiState.selectedSlot == JournalDateSlotUiState.TODAY) today else yesterday

    DsDateStrip(
        selectedDate = selectedDate,
        isSelectable = { date -> date.toSlot(today, yesterday)?.let { it in uiState.availableSlots } ?: false },
        onDateSelected = { date -> date.toSlot(today, yesterday)?.let { onIntent(AddJournalEntryIntent.SlotSelected(it)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun LocalDate.toSlot(
    today: LocalDate,
    yesterday: LocalDate,
): JournalDateSlotUiState? = when (this) {
    today -> JournalDateSlotUiState.TODAY
    yesterday -> JournalDateSlotUiState.YESTERDAY
    else -> null
}

private class AddJournalEntryScreenPreviewStateProvider : PreviewParameterProvider<AddJournalEntryUiState> {
    override val values = sequenceOf(
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "",
            isSaving = false,
            saveError = false,
        ),
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "Today was a good day.",
            isSaving = false,
            saveError = false,
        ),
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY),
            selectedSlot = JournalDateSlotUiState.YESTERDAY,
            text = "Today was a good day.",
            isSaving = true,
            saveError = false,
        ),
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "Today was a good day.",
            isSaving = false,
            saveError = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun AddJournalEntryScreenPreview(
    @PreviewParameter(AddJournalEntryScreenPreviewStateProvider::class) state: AddJournalEntryUiState,
) {
    TodayWasTheme {
        AddJournalEntryScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
