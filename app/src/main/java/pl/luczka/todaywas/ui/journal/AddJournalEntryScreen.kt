package pl.luczka.todaywas.ui.journal

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.chips.DsAssistChip
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.pickers.DsDateStrip
import pl.luczka.todaywas.core.designsystem.components.segmentedbuttons.DsSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.components.textfields.DsTextField
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
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
            if (uiState.authState is AuthStateUi.SignedIn) {
                DsAssistChip(
                    text = stringResource(R.string.journal_help_me_start_cta),
                    onClick = { onIntent(AddJournalEntryIntent.HelpMeStartClicked) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = DsSpacing.space400)
                        .padding(horizontal = DsSpacing.space600),
                )
            }
            DsTextField(
                value = uiState.text,
                onValueChange = { onIntent(AddJournalEntryIntent.TextChanged(it)) },
                label = stringResource(R.string.journal_add_entry_text_label),
                minLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.space400)
                    .padding(horizontal = DsSpacing.space600),
            )
        }
    }

    if (uiState.helpMeStart.isVisible) {
        HelpMeStartDialog(
            uiState = uiState.helpMeStart,
            onIntent = onIntent,
        )
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

@Composable
private fun HelpMeStartDialog(
    uiState: HelpMeStartUiState,
    onIntent: (AddJournalEntryIntent) -> Unit,
) {
    DsAlertDialog(
        onDismissRequest = { onIntent(AddJournalEntryIntent.HelpMeStartDismissed) },
        title = { DsText(text = stringResource(R.string.journal_help_me_start_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.space400)) {
                val toneLabels = JournalPromptToneUiState.entries.associateWith { toneLabel(it) }
                DsSegmentedRow(
                    items = JournalPromptToneUiState.entries.toList(),
                    selectedItem = uiState.selectedTone,
                    onItemSelected = { tone -> tone?.let { onIntent(AddJournalEntryIntent.ToneSelected(it)) } },
                    enabled = !uiState.isGenerating,
                    allowDeselect = false,
                    label = { toneLabels.getValue(it) },
                )
                DsTextField(
                    value = uiState.thoughts,
                    onValueChange = { onIntent(AddJournalEntryIntent.ThoughtsChanged(it)) },
                    label = stringResource(R.string.journal_help_me_start_thoughts_label),
                    enabled = !uiState.isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.step == HelpMeStartStep.PREVIEW && uiState.generatedText != null) {
                    DsText(text = uiState.generatedText)
                    DsAssistChip(
                        text = stringResource(R.string.journal_help_me_start_regenerate_cta),
                        onClick = { onIntent(AddJournalEntryIntent.RegenerateClicked) },
                        enabled = !uiState.isGenerating && uiState.regenerationsUsed < MAX_REGENERATIONS,
                    )
                }
                uiState.error?.let { error ->
                    DsText(text = helpMeStartErrorMessage(error))
                }
            }
        },
        confirmButton = {
            when (uiState.step) {
                HelpMeStartStep.INPUT -> DsButtonWithLoading(
                    text = stringResource(R.string.journal_help_me_start_generate_cta),
                    onClick = { onIntent(AddJournalEntryIntent.GenerateClicked) },
                    enabled = uiState.selectedTone != null && !uiState.isGenerating,
                    loading = uiState.isGenerating,
                )
                HelpMeStartStep.PREVIEW -> DsButton(
                    text = stringResource(R.string.journal_help_me_start_use_this_cta),
                    onClick = { onIntent(AddJournalEntryIntent.UseGeneratedTextClicked) },
                    enabled = !uiState.isGenerating,
                )
            }
        },
        dismissButton = {
            DsTextButton(
                text = stringResource(R.string.journal_help_me_start_cancel_cta),
                onClick = { onIntent(AddJournalEntryIntent.HelpMeStartDismissed) },
            )
        },
    )
}

@Composable
private fun toneLabel(tone: JournalPromptToneUiState): String = when (tone) {
    JournalPromptToneUiState.VERY_BAD -> stringResource(R.string.journal_help_me_start_tone_very_bad)
    JournalPromptToneUiState.BAD -> stringResource(R.string.journal_help_me_start_tone_bad)
    JournalPromptToneUiState.NEUTRAL -> stringResource(R.string.journal_help_me_start_tone_neutral)
    JournalPromptToneUiState.GOOD -> stringResource(R.string.journal_help_me_start_tone_good)
    JournalPromptToneUiState.VERY_GOOD -> stringResource(R.string.journal_help_me_start_tone_very_good)
}

@Composable
private fun helpMeStartErrorMessage(error: AiAssistErrorUiState): String = when (error) {
    AiAssistErrorUiState.INVALID_REQUEST -> stringResource(R.string.journal_help_me_start_error_invalid_request)
    AiAssistErrorUiState.UPSTREAM_FAILED -> stringResource(R.string.journal_help_me_start_error_upstream_failed)
    AiAssistErrorUiState.NOT_SIGNED_IN -> stringResource(R.string.journal_help_me_start_error_not_signed_in)
    AiAssistErrorUiState.NETWORK_UNAVAILABLE -> stringResource(R.string.journal_help_me_start_error_network_unavailable)
    AiAssistErrorUiState.UNKNOWN -> stringResource(R.string.journal_help_me_start_error_unknown)
}

private class AddJournalEntryScreenPreviewStateProvider : PreviewParameterProvider<AddJournalEntryUiState> {
    override val values = sequenceOf(
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "",
            isSaving = false,
            saveError = false,
            authState = AuthStateUi.SignedOut,
        ),
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "Today was a good day.",
            isSaving = false,
            saveError = false,
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
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
    DsTheme {
        AddJournalEntryScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}

private class HelpMeStartDialogPreviewStateProvider : PreviewParameterProvider<HelpMeStartUiState> {
    override val values = sequenceOf(
        HelpMeStartUiState(isVisible = true),
        HelpMeStartUiState(isVisible = true, selectedTone = JournalPromptToneUiState.GOOD, isGenerating = true),
        HelpMeStartUiState(
            isVisible = true,
            step = HelpMeStartStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            generatedText = "I made real progress today, and it feels good to see it come together...",
            regenerationsUsed = 1,
        ),
        HelpMeStartUiState(
            isVisible = true,
            step = HelpMeStartStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            generatedText = "I made real progress today, and it feels good to see it come together...",
            regenerationsUsed = MAX_REGENERATIONS,
        ),
        HelpMeStartUiState(
            isVisible = true,
            selectedTone = JournalPromptToneUiState.BAD,
            error = AiAssistErrorUiState.NETWORK_UNAVAILABLE,
        ),
    )
}

@PreviewLightDark
@Composable
private fun HelpMeStartDialogPreview(
    @PreviewParameter(HelpMeStartDialogPreviewStateProvider::class) state: HelpMeStartUiState,
) {
    DsTheme {
        HelpMeStartDialog(
            uiState = state,
            onIntent = {},
        )
    }
}
