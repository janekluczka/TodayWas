package pl.luczka.todaywas.ui.journal.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import pl.luczka.todaywas.core.designsystem.components.chips.DsAssistChip
import pl.luczka.todaywas.core.designsystem.components.chips.DsChoiceFlowRow
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsModalBottomSheet
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.pickers.DsDateStrip
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.components.textfields.DsPlainTextField
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
    onNavigateToSignIn: () -> Unit,
    viewModel: AddJournalEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AddJournalEntryUiEvent.Saved -> onSaved()
                AddJournalEntryUiEvent.Cancelled -> onCancelled()
                AddJournalEntryUiEvent.NavigateToSignIn -> onNavigateToSignIn()
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
                title = uiState.selectedSlot.title(),
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.space600, vertical = DsSpacing.space400),
            ) {
                DsPlainTextField(
                    value = uiState.text,
                    onValueChange = { onIntent(AddJournalEntryIntent.TextChanged(it)) },
                    minLines = 6,
                    fillAvailableSpace = true,
                    modifier = Modifier.fillMaxSize(),
                )
                if (uiState.text.isEmpty()) {
                    StarterPromptList(
                        starterPrompts = uiState.starterPrompts,
                        onPromptClicked = { onIntent(AddJournalEntryIntent.TextChanged(it)) },
                        onHelpMeStartClicked = {
                            onIntent(AddJournalEntryIntent.HelpMeStartClicked)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (uiState.helpMeStart.isVisible) {
        HelpMeStartBottomSheet(
            uiState = uiState.helpMeStart,
            onDismissRequest = { onIntent(AddJournalEntryIntent.HelpMeStartDismissed) },
            onSignInClicked = { onIntent(AddJournalEntryIntent.SignInClicked) },
            onToneSelected = { onIntent(AddJournalEntryIntent.ToneSelected(it)) },
            onThoughtsChanged = { onIntent(AddJournalEntryIntent.ThoughtsChanged(it)) },
            onGenerateClicked = { onIntent(AddJournalEntryIntent.GenerateClicked) },
            onRegenerateClicked = { onIntent(AddJournalEntryIntent.RegenerateClicked) },
            onUseGeneratedTextClicked = {
                onIntent(AddJournalEntryIntent.UseGeneratedTextClicked)
            },
        )
    }
}

@Composable
private fun JournalDateSlotUiState.title(): String = when (this) {
    JournalDateSlotUiState.TODAY -> stringResource(R.string.journal_add_entry_title_today)
    JournalDateSlotUiState.YESTERDAY -> stringResource(R.string.journal_add_entry_title_yesterday)
}

// The entry point for "help me start" plus three hardcoded, randomly-varied starter lines (one
// per tone — see AddJournalEntryViewModel for how the variant is picked), overlaid at the bottom
// of the (full-screen) writing area only while it's empty, disappearing the moment the user types
// anything. Chips show the actual line that gets inserted, not a generic tone label, so tapping
// one is never a surprise. "Help me refine" (Journal Entry Detail) is the equivalent entry point
// once there's already text to work with. The AI chip is shown regardless of sign-in state —
// tapping it while signed out opens the sheet with a sign-in explainer instead of the tone
// picker, so the feature stays discoverable either way.
@Composable
fun StarterPromptList(
    starterPrompts: List<JournalStarterPromptUiState>,
    onPromptClicked: (String) -> Unit,
    onHelpMeStartClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        for (prompt in starterPrompts) {
            val text = prompt.resolvedText()
            DsAssistChip(
                text = text,
                leadingIcon = prompt.tone.icon(),
                onClick = { onPromptClicked(text) },
            )
        }
        DsAssistChip(
            text = stringResource(R.string.journal_help_me_start_cta),
            leadingIcon = Icons.Filled.AutoAwesome,
            onClick = onHelpMeStartClicked,
        )
    }
}

private fun JournalStarterPromptTone.icon(): ImageVector = when (this) {
    JournalStarterPromptTone.POSITIVE -> Icons.Filled.SentimentSatisfied
    JournalStarterPromptTone.NEUTRAL -> Icons.Filled.SentimentNeutral
    JournalStarterPromptTone.NEGATIVE -> Icons.Filled.SentimentDissatisfied
}

@Composable
private fun JournalStarterPromptUiState.resolvedText(): String {
    val variants = when (tone) {
        JournalStarterPromptTone.POSITIVE -> POSITIVE_PROMPTS
        JournalStarterPromptTone.NEUTRAL -> NEUTRAL_PROMPTS
        JournalStarterPromptTone.NEGATIVE -> NEGATIVE_PROMPTS
    }
    return stringResource(variants[variant])
}

private val POSITIVE_PROMPTS = listOf(
    R.string.journal_starter_prompt_positive_1,
    R.string.journal_starter_prompt_positive_2,
    R.string.journal_starter_prompt_positive_3,
    R.string.journal_starter_prompt_positive_4,
    R.string.journal_starter_prompt_positive_5,
)

private val NEUTRAL_PROMPTS = listOf(
    R.string.journal_starter_prompt_neutral_1,
    R.string.journal_starter_prompt_neutral_2,
    R.string.journal_starter_prompt_neutral_3,
    R.string.journal_starter_prompt_neutral_4,
    R.string.journal_starter_prompt_neutral_5,
)

private val NEGATIVE_PROMPTS = listOf(
    R.string.journal_starter_prompt_negative_1,
    R.string.journal_starter_prompt_negative_2,
    R.string.journal_starter_prompt_negative_3,
    R.string.journal_starter_prompt_negative_4,
    R.string.journal_starter_prompt_negative_5,
)

@Composable
private fun JournalDateStrip(
    uiState: AddJournalEntryUiState,
    onIntent: (AddJournalEntryIntent) -> Unit,
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val selectedDate = if (uiState.selectedSlot ==
        JournalDateSlotUiState.TODAY
    ) {
        today
    } else {
        yesterday
    }

    DsDateStrip(
        selectedDate = selectedDate,
        isSelectable = { date ->
            date.toSlot(today, yesterday)?.let { it in uiState.availableSlots }
                ?: false
        },
        onDateSelected = { date ->
            date.toSlot(today, yesterday)?.let { onIntent(AddJournalEntryIntent.SlotSelected(it)) }
        },
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

// No top bar (close icon + fixed action) — swipe-down/tap-outside is the dismiss affordance, and
// each step's own action button lives inline at the end of its content instead, right below the
// input it acts on rather than floating in a header disconnected from it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpMeStartBottomSheet(
    uiState: HelpMeStartUiState,
    onDismissRequest: () -> Unit,
    onSignInClicked: () -> Unit,
    onToneSelected: (JournalPromptToneUiState) -> Unit,
    onThoughtsChanged: (String) -> Unit,
    onGenerateClicked: () -> Unit,
    onRegenerateClicked: () -> Unit,
    onUseGeneratedTextClicked: () -> Unit,
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
                when (uiState.step) {
                    HelpMeStartStep.SIGNED_OUT -> {
                        DsText(
                            text = stringResource(R.string.journal_help_me_start_signed_out_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        DsText(
                            text = stringResource(
                                R.string.journal_help_me_start_signed_out_message,
                            ),
                        )
                        DsButton(
                            text = stringResource(R.string.onboarding_account_signin_signup_cta),
                            onClick = onSignInClicked,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HelpMeStartStep.INPUT, HelpMeStartStep.PREVIEW -> {
                        DsText(
                            text = stringResource(R.string.journal_help_me_start_dialog_title),
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
                        // Same borderless, containerless field as the entry's own body — the tone
                        // picker aside, this sheet should feel like an extension of that writing
                        // surface, not a separate form. minLines reserves space up front so typing
                        // a second line doesn't reflow the sheet around it.
                        DsPlainTextField(
                            value = uiState.thoughts,
                            onValueChange = onThoughtsChanged,
                            placeholder = stringResource(
                                R.string.journal_help_me_start_thoughts_label,
                            ),
                            enabled = !uiState.isGenerating,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (uiState.step == HelpMeStartStep.PREVIEW &&
                            uiState.generatedText != null
                        ) {
                            DsText(text = uiState.generatedText)
                            DsAssistChip(
                                text = stringResource(
                                    R.string.journal_help_me_start_regenerate_cta,
                                ),
                                onClick = onRegenerateClicked,
                                enabled = !uiState.isGenerating &&
                                    (uiState.remainingToday ?: 1) > 0,
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
                        if (uiState.step == HelpMeStartStep.INPUT) {
                            DsButtonWithLoading(
                                text = stringResource(R.string.journal_help_me_start_generate_cta),
                                onClick = onGenerateClicked,
                                enabled = uiState.selectedTone != null && !uiState.isGenerating,
                                loading = uiState.isGenerating,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            DsButton(
                                text = stringResource(R.string.journal_help_me_start_use_this_cta),
                                onClick = onUseGeneratedTextClicked,
                                enabled = !uiState.isGenerating,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            DsSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun aiAssistToneLabel(tone: JournalPromptToneUiState): String = when (tone) {
    JournalPromptToneUiState.VERY_BAD -> stringResource(R.string.journal_ai_assist_tone_very_bad)
    JournalPromptToneUiState.BAD -> stringResource(R.string.journal_ai_assist_tone_bad)
    JournalPromptToneUiState.NEUTRAL -> stringResource(R.string.journal_ai_assist_tone_neutral)
    JournalPromptToneUiState.GOOD -> stringResource(R.string.journal_ai_assist_tone_good)
    JournalPromptToneUiState.VERY_GOOD -> stringResource(R.string.journal_ai_assist_tone_very_good)
}

@Composable
fun aiAssistErrorMessage(error: AiAssistErrorUiState): String = when (error) {
    AiAssistErrorUiState.INVALID_REQUEST -> stringResource(
        R.string.journal_ai_assist_error_invalid_request,
    )
    AiAssistErrorUiState.UPSTREAM_FAILED -> stringResource(
        R.string.journal_ai_assist_error_upstream_failed,
    )
    AiAssistErrorUiState.NOT_SIGNED_IN -> stringResource(
        R.string.journal_ai_assist_error_not_signed_in,
    )
    AiAssistErrorUiState.DAILY_LIMIT_REACHED -> stringResource(
        R.string.journal_ai_assist_error_daily_limit_reached,
    )
    AiAssistErrorUiState.NETWORK_UNAVAILABLE -> stringResource(
        R.string.journal_ai_assist_error_network_unavailable,
    )
    AiAssistErrorUiState.UNKNOWN -> stringResource(R.string.journal_ai_assist_error_unknown)
}

private val previewStarterPrompts = JournalStarterPromptTone.entries.map { tone ->
    JournalStarterPromptUiState(tone = tone, variant = 0)
}

private class AddJournalEntryScreenPreviewStateProvider :
    PreviewParameterProvider<AddJournalEntryUiState> {
    override val values = sequenceOf(
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "",
            isSaving = false,
            saveError = false,
            authState = AuthStateUi.SignedOut,
            starterPrompts = previewStarterPrompts,
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
    @PreviewParameter(AddJournalEntryScreenPreviewStateProvider::class) state:
        AddJournalEntryUiState,
) {
    DsTheme {
        AddJournalEntryScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}

private class HelpMeStartBottomSheetPreviewStateProvider :
    PreviewParameterProvider<HelpMeStartUiState> {
    override val values = sequenceOf(
        HelpMeStartUiState(isVisible = true, step = HelpMeStartStep.SIGNED_OUT),
        HelpMeStartUiState(isVisible = true),
        HelpMeStartUiState(
            isVisible = true,
            selectedTone = JournalPromptToneUiState.GOOD,
            isGenerating = true,
        ),
        HelpMeStartUiState(
            isVisible = true,
            step = HelpMeStartStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            generatedText =
                "I made real progress today, and it feels good to see it come together...",
            remainingToday = 7,
        ),
        HelpMeStartUiState(
            isVisible = true,
            step = HelpMeStartStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            generatedText =
                "I made real progress today, and it feels good to see it come together...",
            remainingToday = 0,
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
private fun HelpMeStartBottomSheetPreview(
    @PreviewParameter(HelpMeStartBottomSheetPreviewStateProvider::class) state: HelpMeStartUiState,
) {
    DsTheme {
        HelpMeStartBottomSheet(
            uiState = state,
            onDismissRequest = {},
            onSignInClicked = {},
            onToneSelected = {},
            onThoughtsChanged = {},
            onGenerateClicked = {},
            onRegenerateClicked = {},
            onUseGeneratedTextClicked = {},
        )
    }
}
