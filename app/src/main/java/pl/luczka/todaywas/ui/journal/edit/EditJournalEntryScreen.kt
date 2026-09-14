package pl.luczka.todaywas.ui.journal.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import pl.luczka.todaywas.core.designsystem.components.chips.DsChoiceFlowRow
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsModalBottomSheet
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.components.textfields.DsPlainTextField
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.journal.create.HelpMeStartBottomSheet
import pl.luczka.todaywas.ui.journal.create.JournalStarterPromptTone
import pl.luczka.todaywas.ui.journal.create.JournalStarterPromptUiState
import pl.luczka.todaywas.ui.journal.create.MAX_THOUGHTS_LENGTH
import pl.luczka.todaywas.ui.journal.create.StarterPromptList
import pl.luczka.todaywas.ui.journal.create.aiAssistErrorMessage
import pl.luczka.todaywas.ui.journal.create.aiAssistToneLabel
import pl.luczka.todaywas.ui.model.AiAssistErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.model.JournalPromptToneUiState
import java.time.Instant
import java.time.LocalDate

@Composable
fun EditJournalEntryScreen(
    id: String,
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    viewModel: EditJournalEntryViewModel =
        hiltViewModel<EditJournalEntryViewModel, EditJournalEntryViewModel.Factory> { factory ->
            factory.create(id)
        },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EditJournalEntryUiEvent.Saved -> onSaved()
                EditJournalEntryUiEvent.Discarded -> onDiscarded()
                EditJournalEntryUiEvent.NavigateToSignIn -> onNavigateToSignIn()
            }
        }
    }

    BackHandler(enabled = true) { viewModel.onIntent(EditJournalEntryIntent.BackClicked) }

    EditJournalEntryScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun EditJournalEntryScreenContent(
    uiState: EditJournalEntryUiState,
    onIntent: (EditJournalEntryIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.journal_detail_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    DsScaffold(
        topBar = {
            DsTopBar(
                title = uiState.entry?.formattedDate
                    ?: stringResource(R.string.journal_edit_entry_title),
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(EditJournalEntryIntent.BackClicked) }) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    DsButtonWithLoading(
                        text = stringResource(R.string.journal_detail_save_cta),
                        onClick = { onIntent(EditJournalEntryIntent.SaveClicked) },
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.space600, vertical = DsSpacing.space400),
            ) {
                DsPlainTextField(
                    value = uiState.text,
                    onValueChange = { onIntent(EditJournalEntryIntent.TextChanged(it)) },
                    minLines = 6,
                    fillAvailableSpace = true,
                    modifier = Modifier.fillMaxSize(),
                )
                if (uiState.text.isEmpty()) {
                    StarterPromptList(
                        starterPrompts = uiState.starterPrompts,
                        onPromptClicked = { onIntent(EditJournalEntryIntent.TextChanged(it)) },
                        onHelpMeStartClicked = {
                            onIntent(EditJournalEntryIntent.HelpMeStartClicked)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                    )
                } else if (uiState.authState is AuthStateUi.SignedIn &&
                    uiState.text.length <= MAX_REFINE_TEXT_LENGTH
                ) {
                    DsAssistChip(
                        text = stringResource(R.string.journal_help_me_refine_cta),
                        leadingIcon = Icons.Filled.AutoAwesome,
                        onClick = { onIntent(EditJournalEntryIntent.HelpMeRefineClicked) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }

    if (uiState.helpMeStart.isVisible) {
        HelpMeStartBottomSheet(
            uiState = uiState.helpMeStart,
            onDismissRequest = { onIntent(EditJournalEntryIntent.HelpMeStartDismissed) },
            onSignInClicked = { onIntent(EditJournalEntryIntent.SignInClicked) },
            onToneSelected = { onIntent(EditJournalEntryIntent.HelpMeStartToneSelected(it)) },
            onThoughtsChanged = {
                onIntent(EditJournalEntryIntent.HelpMeStartThoughtsChanged(it))
            },
            onGenerateClicked = { onIntent(EditJournalEntryIntent.GenerateClicked) },
            onRegenerateClicked = { onIntent(EditJournalEntryIntent.RegenerateClicked) },
            onUseGeneratedTextClicked = {
                onIntent(EditJournalEntryIntent.UseGeneratedTextClicked)
            },
        )
    }

    if (uiState.helpMeRefine.isVisible) {
        HelpMeRefineBottomSheet(
            uiState = uiState.helpMeRefine,
            onDismissRequest = { onIntent(EditJournalEntryIntent.HelpMeRefineDismissed) },
            onToneSelected = { onIntent(EditJournalEntryIntent.HelpMeRefineToneSelected(it)) },
            onThoughtsChanged = {
                onIntent(EditJournalEntryIntent.HelpMeRefineThoughtsChanged(it))
            },
            onRefineClicked = { onIntent(EditJournalEntryIntent.RefineClicked) },
            onRegenerateClicked = { onIntent(EditJournalEntryIntent.RegenerateRefineClicked) },
            onUseRefinedTextClicked = { onIntent(EditJournalEntryIntent.UseRefinedTextClicked) },
        )
    }

    if (uiState.isDiscardConfirmVisible) {
        DiscardChangesDialog(onIntent = onIntent)
    }
}

@Composable
private fun DiscardChangesDialog(
    onIntent: (EditJournalEntryIntent) -> Unit,
) {
    DsAlertDialog(
        onDismissRequest = { onIntent(EditJournalEntryIntent.DiscardDismissed) },
        title = { DsText(text = stringResource(R.string.journal_edit_discard_dialog_title)) },
        text = { DsText(text = stringResource(R.string.journal_edit_discard_dialog_text)) },
        confirmButton = {
            DsTextButton(
                text = stringResource(R.string.journal_edit_discard_confirm_cta),
                onClick = { onIntent(EditJournalEntryIntent.DiscardConfirmed) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            )
        },
        dismissButton = {
            DsTextButton(
                text = stringResource(R.string.journal_edit_discard_cancel_cta),
                onClick = { onIntent(EditJournalEntryIntent.DiscardDismissed) },
            )
        },
    )
}

// Same bottom-sheet shape as HelpMeStartBottomSheet (no top bar, tone picker + optional thoughts +
// preview/regenerate/use inline), minus the signed-out step -- the entry point that opens this
// sheet is only ever shown while already signed in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpMeRefineBottomSheet(
    uiState: HelpMeRefineUiState,
    onDismissRequest: () -> Unit,
    onToneSelected: (JournalPromptToneUiState) -> Unit,
    onThoughtsChanged: (String) -> Unit,
    onRefineClicked: () -> Unit,
    onRegenerateClicked: () -> Unit,
    onUseRefinedTextClicked: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.let { aiAssistErrorMessage(it) }
    LaunchedEffect(uiState.error) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    DsModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DsSpacing.space600),
            ) {
                DsText(
                    text = stringResource(R.string.journal_help_me_refine_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                val toneLabels =
                    JournalPromptToneUiState.entries.associateWith { aiAssistToneLabel(it) }
                DsChoiceFlowRow(
                    items = JournalPromptToneUiState.entries.toList(),
                    selectedItem = uiState.selectedTone,
                    onItemSelected = { tone ->
                        tone?.let(onToneSelected)
                    },
                    enabled = !uiState.isGenerating,
                    allowDeselect = false,
                    label = { toneLabels.getValue(it) },
                )
                DsPlainTextField(
                    value = uiState.thoughts,
                    onValueChange = onThoughtsChanged,
                    placeholder = stringResource(R.string.journal_help_me_refine_thoughts_label),
                    enabled = !uiState.isGenerating,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.step == HelpMeRefineStep.PREVIEW && uiState.refinedText != null) {
                    DsText(text = uiState.refinedText)
                    DsAssistChip(
                        text = stringResource(R.string.journal_help_me_start_regenerate_cta),
                        onClick = onRegenerateClicked,
                        enabled = !uiState.isGenerating && (uiState.remainingToday ?: 1) > 0,
                    )
                }
                uiState.remainingToday?.let { remaining ->
                    DsText(
                        text = pluralStringResource(
                            R.plurals.journal_ai_assist_remaining_today,
                            remaining,
                            remaining,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.step == HelpMeRefineStep.INPUT) {
                    DsButtonWithLoading(
                        text = stringResource(R.string.journal_help_me_refine_generate_cta),
                        onClick = onRefineClicked,
                        enabled = uiState.selectedTone != null &&
                            !uiState.isGenerating &&
                            uiState.thoughts.length <= MAX_THOUGHTS_LENGTH,
                        loading = uiState.isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DsButton(
                        text = stringResource(R.string.journal_help_me_start_use_this_cta),
                        onClick = onUseRefinedTextClicked,
                        enabled = !uiState.isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            DsSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private val previewStarterPrompts = JournalStarterPromptTone.entries.map { tone ->
    JournalStarterPromptUiState(tone = tone, variant = 0)
}

private val previewEntry = JournalEntryUiState(
    id = "1",
    date = LocalDate.of(2026, 9, 12),
    formattedDate = "Sep 12, 2026",
    text = "Today was a good day.",
    createdAt = Instant.now(),
)

private class EditJournalEntryScreenPreviewStateProvider :
    PreviewParameterProvider<EditJournalEntryUiState> {
    override val values = sequenceOf(
        EditJournalEntryUiState(
            isLoading = false,
            entry = previewEntry,
            text = "",
            isSaving = false,
            saveError = false,
            authState = AuthStateUi.SignedOut,
            starterPrompts = previewStarterPrompts,
        ),
        EditJournalEntryUiState(
            isLoading = false,
            entry = previewEntry,
            text = "Today was a good day.",
            isSaving = false,
            saveError = false,
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
        ),
        EditJournalEntryUiState(
            isLoading = false,
            entry = previewEntry,
            text = "Today was a good day.",
            isSaving = true,
            saveError = false,
        ),
        EditJournalEntryUiState(
            isLoading = false,
            entry = previewEntry,
            text = "Today was a good day.",
            isSaving = false,
            saveError = true,
        ),
        EditJournalEntryUiState(
            isLoading = false,
            entry = previewEntry,
            text = "Today was a great day after all.",
            isSaving = false,
            saveError = false,
            isDiscardConfirmVisible = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun EditJournalEntryScreenPreview(
    @PreviewParameter(EditJournalEntryScreenPreviewStateProvider::class) state:
        EditJournalEntryUiState,
) {
    DsTheme {
        EditJournalEntryScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}

private class HelpMeRefineBottomSheetPreviewStateProvider :
    PreviewParameterProvider<HelpMeRefineUiState> {
    override val values = sequenceOf(
        HelpMeRefineUiState(isVisible = true),
        HelpMeRefineUiState(
            isVisible = true,
            selectedTone = JournalPromptToneUiState.GOOD,
            isGenerating = true,
        ),
        HelpMeRefineUiState(
            isVisible = true,
            step = HelpMeRefineStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            refinedText =
                "Today went well overall, and I got a few things done that I'm proud of...",
            remainingToday = 7,
        ),
        HelpMeRefineUiState(
            isVisible = true,
            step = HelpMeRefineStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            refinedText =
                "Today went well overall, and I got a few things done that I'm proud of...",
            remainingToday = 0,
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
private fun HelpMeRefineBottomSheetPreview(
    @PreviewParameter(HelpMeRefineBottomSheetPreviewStateProvider::class) state:
        HelpMeRefineUiState,
) {
    DsTheme {
        HelpMeRefineBottomSheet(
            uiState = state,
            onDismissRequest = {},
            onToneSelected = {},
            onThoughtsChanged = {},
            onRefineClicked = {},
            onRegenerateClicked = {},
            onUseRefinedTextClicked = {},
        )
    }
}
