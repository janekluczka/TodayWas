package pl.luczka.todaywas.ui.journal.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsBottomSheet
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.segmentedbuttons.DsSegmentedRow
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.components.textfields.DsTextField
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.journal.create.MAX_REGENERATIONS
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import java.time.Instant
import java.time.LocalDate

@Composable
fun JournalEntryDetailScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: JournalEntryDetailViewModel = hiltViewModel<JournalEntryDetailViewModel, JournalEntryDetailViewModel.Factory> { factory ->
        factory.create(id)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                JournalEntryDetailUiEvent.NavigatedBack -> onBack()
            }
        }
    }

    JournalEntryDetailScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun JournalEntryDetailScreenContent(
    uiState: JournalEntryDetailUiState,
    onIntent: (JournalEntryDetailIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val genericErrorMessage = stringResource(R.string.journal_detail_error)
    val expiredErrorMessage = stringResource(R.string.journal_detail_edit_window_expired_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            val message = if (uiState.isEditable) genericErrorMessage else expiredErrorMessage
            snackbarHostState.showSnackbar(message)
        }
    }

    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.journal_detail_title),
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(JournalEntryDetailIntent.BackClicked) }) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    JournalEntryDetailActions(uiState, onIntent)
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
                .padding(DsSpacing.space600),
        ) {
            if (uiState.entry != null) {
                DsText(text = uiState.entry.formattedDate)
                DsText(text = uiState.entry.text)
            }
        }
    }

    if (uiState.isEditing) {
        DsBottomSheet(
            onDismissRequest = { onIntent(JournalEntryDetailIntent.CancelEditClicked) },
            onCloseClicked = { onIntent(JournalEntryDetailIntent.CancelEditClicked) },
            closeContentDescription = stringResource(R.string.journal_detail_cancel_edit_action),
            saveText = stringResource(R.string.journal_detail_save_cta),
            onSaveClicked = { onIntent(JournalEntryDetailIntent.SaveClicked) },
            isSaving = uiState.isSaving,
        ) {
            Column {
                val canRefine = uiState.editedText.isNotBlank() && uiState.editedText.length <= MAX_REFINE_TEXT_LENGTH
                if (uiState.authState is AuthStateUi.SignedIn && canRefine) {
                    DsAssistChip(
                        text = stringResource(R.string.journal_help_me_refine_cta),
                        onClick = { onIntent(JournalEntryDetailIntent.HelpMeRefineClicked) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = DsSpacing.space400)
                            .padding(horizontal = DsSpacing.space600),
                    )
                }
                DsTextField(
                    value = uiState.editedText,
                    onValueChange = { onIntent(JournalEntryDetailIntent.TextChanged(it)) },
                    minLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DsSpacing.space600),
                )
            }
        }
    }

    if (uiState.helpMeRefine.isVisible) {
        HelpMeRefineDialog(
            uiState = uiState.helpMeRefine,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun JournalEntryDetailActions(
    uiState: JournalEntryDetailUiState,
    onIntent: (JournalEntryDetailIntent) -> Unit,
) {
    if (uiState.isEditable) {
        DsIconButton(onClick = { onIntent(JournalEntryDetailIntent.EditClicked) }) {
            DsIcon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.journal_detail_edit_action),
            )
        }
    }
}

@Composable
private fun HelpMeRefineDialog(
    uiState: HelpMeRefineUiState,
    onIntent: (JournalEntryDetailIntent) -> Unit,
) {
    DsAlertDialog(
        onDismissRequest = { onIntent(JournalEntryDetailIntent.HelpMeRefineDismissed) },
        title = { DsText(text = stringResource(R.string.journal_help_me_refine_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.space400)) {
                val toneLabels = JournalPromptToneUiState.entries.associateWith { refineToneLabel(it) }
                DsSegmentedRow(
                    items = JournalPromptToneUiState.entries.toList(),
                    selectedItem = uiState.selectedTone,
                    onItemSelected = { tone -> tone?.let { onIntent(JournalEntryDetailIntent.ToneSelected(it)) } },
                    enabled = !uiState.isGenerating,
                    allowDeselect = false,
                    label = { toneLabels.getValue(it) },
                )
                if (uiState.step == HelpMeRefineStep.PREVIEW && uiState.refinedText != null) {
                    DsText(text = uiState.refinedText)
                    DsAssistChip(
                        text = stringResource(R.string.journal_help_me_start_regenerate_cta),
                        onClick = { onIntent(JournalEntryDetailIntent.RegenerateRefineClicked) },
                        enabled = !uiState.isGenerating && uiState.regenerationsUsed < MAX_REGENERATIONS,
                    )
                }
                uiState.error?.let { error ->
                    DsText(text = helpMeRefineErrorMessage(error))
                }
            }
        },
        confirmButton = {
            when (uiState.step) {
                HelpMeRefineStep.INPUT -> DsButtonWithLoading(
                    text = stringResource(R.string.journal_help_me_refine_generate_cta),
                    onClick = { onIntent(JournalEntryDetailIntent.RefineClicked) },
                    enabled = uiState.selectedTone != null && !uiState.isGenerating,
                    loading = uiState.isGenerating,
                )
                HelpMeRefineStep.PREVIEW -> DsButton(
                    text = stringResource(R.string.journal_help_me_start_use_this_cta),
                    onClick = { onIntent(JournalEntryDetailIntent.UseRefinedTextClicked) },
                    enabled = !uiState.isGenerating,
                )
            }
        },
        dismissButton = {
            DsTextButton(
                text = stringResource(R.string.journal_help_me_start_cancel_cta),
                onClick = { onIntent(JournalEntryDetailIntent.HelpMeRefineDismissed) },
            )
        },
    )
}

@Composable
private fun refineToneLabel(tone: JournalPromptToneUiState): String = when (tone) {
    JournalPromptToneUiState.VERY_BAD -> stringResource(R.string.journal_help_me_start_tone_very_bad)
    JournalPromptToneUiState.BAD -> stringResource(R.string.journal_help_me_start_tone_bad)
    JournalPromptToneUiState.NEUTRAL -> stringResource(R.string.journal_help_me_start_tone_neutral)
    JournalPromptToneUiState.GOOD -> stringResource(R.string.journal_help_me_start_tone_good)
    JournalPromptToneUiState.VERY_GOOD -> stringResource(R.string.journal_help_me_start_tone_very_good)
}

@Composable
private fun helpMeRefineErrorMessage(error: AiAssistErrorUiState): String = when (error) {
    AiAssistErrorUiState.INVALID_REQUEST -> stringResource(R.string.journal_help_me_start_error_invalid_request)
    AiAssistErrorUiState.UPSTREAM_FAILED -> stringResource(R.string.journal_help_me_start_error_upstream_failed)
    AiAssistErrorUiState.NOT_SIGNED_IN -> stringResource(R.string.journal_help_me_start_error_not_signed_in)
    AiAssistErrorUiState.NETWORK_UNAVAILABLE -> stringResource(R.string.journal_help_me_start_error_network_unavailable)
    AiAssistErrorUiState.UNKNOWN -> stringResource(R.string.journal_help_me_start_error_unknown)
}

private class JournalEntryDetailScreenPreviewStateProvider : PreviewParameterProvider<JournalEntryDetailUiState> {
    override val values = sequenceOf(
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = "1",
                date = LocalDate.of(2026, 7, 27),
                formattedDate = "Jul 27, 2026",
                text = "Today was a good day. I went for a walk and read a book.",
                createdAt = Instant.now(),
            ),
            editedText = "Today was a good day. I went for a walk and read a book.",
            isEditable = true,
            isEditing = false,
            isSaving = false,
            saveError = false,
        ),
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = "1",
                date = LocalDate.of(2026, 7, 27),
                formattedDate = "Jul 27, 2026",
                text = "Today was a good day.",
                createdAt = Instant.now(),
            ),
            editedText = "Today was a great day after all.",
            isEditable = true,
            isEditing = true,
            isSaving = false,
            saveError = false,
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
        ),
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = "1",
                date = LocalDate.of(2026, 7, 20),
                formattedDate = "Jul 20, 2026",
                text = "An older entry, no longer editable.",
                createdAt = Instant.now(),
            ),
            editedText = "An older entry, no longer editable.",
            isEditable = false,
            isEditing = false,
            isSaving = false,
            saveError = false,
        ),
    )
}

@PreviewLightDark
@Composable
private fun JournalEntryDetailScreenPreview(
    @PreviewParameter(JournalEntryDetailScreenPreviewStateProvider::class) state: JournalEntryDetailUiState,
) {
    DsTheme {
        JournalEntryDetailScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}

private class HelpMeRefineDialogPreviewStateProvider : PreviewParameterProvider<HelpMeRefineUiState> {
    override val values = sequenceOf(
        HelpMeRefineUiState(isVisible = true),
        HelpMeRefineUiState(isVisible = true, selectedTone = JournalPromptToneUiState.GOOD, isGenerating = true),
        HelpMeRefineUiState(
            isVisible = true,
            step = HelpMeRefineStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            refinedText = "Today went well overall, and I got a few things done that I'm proud of...",
            regenerationsUsed = 1,
        ),
        HelpMeRefineUiState(
            isVisible = true,
            step = HelpMeRefineStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            refinedText = "Today went well overall, and I got a few things done that I'm proud of...",
            regenerationsUsed = MAX_REGENERATIONS,
        ),
        HelpMeRefineUiState(
            isVisible = true,
            selectedTone = JournalPromptToneUiState.BAD,
            error = AiAssistErrorUiState.NETWORK_UNAVAILABLE,
        ),
    )
}

@PreviewLightDark
@Composable
private fun HelpMeRefineDialogPreview(
    @PreviewParameter(HelpMeRefineDialogPreviewStateProvider::class) state: HelpMeRefineUiState,
) {
    DsTheme {
        HelpMeRefineDialog(
            uiState = state,
            onIntent = {},
        )
    }
}
