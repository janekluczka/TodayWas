package pl.luczka.todaywas.feature.journal.impl.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsFilledTonalButtonWithLoading
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
import pl.luczka.todaywas.core.ui.R as CoreUiR
import pl.luczka.todaywas.core.ui.model.AuthStateUi
import pl.luczka.todaywas.core.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.feature.journal.impl.R
import pl.luczka.todaywas.feature.journal.impl.edit.HelpMeRefineBottomSheet
import pl.luczka.todaywas.feature.journal.impl.edit.MAX_REFINE_TEXT_LENGTH
import pl.luczka.todaywas.feature.journal.impl.model.AiAssistErrorUiState
import pl.luczka.todaywas.feature.journal.impl.model.JournalPromptToneUiState

@Composable
internal fun AddJournalEntryScreen(
    onSaveComplete: () -> Unit,
    onCancel: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    viewModel: AddJournalEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentOnSaveComplete by rememberUpdatedState(onSaveComplete)
    val currentOnNavigateToSignIn by rememberUpdatedState(onNavigateToSignIn)
    LaunchedEffect(uiState.pendingNavigation) {
        when (uiState.pendingNavigation ?: return@LaunchedEffect) {
            AddJournalEntryNavigation.Saved -> currentOnSaveComplete()
            AddJournalEntryNavigation.SignIn -> currentOnNavigateToSignIn()
        }
        viewModel.onIntent(AddJournalEntryIntent.NavigationHandled)
    }

    AddJournalEntryScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onCancel = onCancel,
    )
}

@Composable
private fun AddJournalEntryScreenContent(
    uiState: AddJournalEntryUiState,
    onIntent: (AddJournalEntryIntent) -> Unit,
    onCancel: () -> Unit,
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
                    DsIconButton(onClick = onCancel) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.content_description_back),
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
                    .padding(
                        horizontal = DsSpacing.space600,
                        vertical = DsSpacing.space400,
                    ),
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
                        onPromptClick = { onIntent(AddJournalEntryIntent.TextChanged(it)) },
                        onHelpMeStartClick = { onIntent(AddJournalEntryIntent.HelpMeStartClicked) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                    )
                } else if (uiState.authState is AuthStateUi.SignedIn && uiState.text.length <= MAX_REFINE_TEXT_LENGTH) {
                    DsAssistChip(
                        text = stringResource(R.string.journal_help_me_refine_cta),
                        leadingIcon = Icons.Filled.AutoAwesome,
                        onClick = { onIntent(AddJournalEntryIntent.HelpMeRefineClicked) },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }

    if (uiState.helpMeStart.isVisible) {
        HelpMeStartBottomSheet(
            uiState = uiState.helpMeStart,
            onDismissRequest = { onIntent(AddJournalEntryIntent.HelpMeStartDismissed) },
            onSignInClick = { onIntent(AddJournalEntryIntent.SignInClicked) },
            onToneSelect = { onIntent(AddJournalEntryIntent.HelpMeStartToneSelected(it)) },
            onThoughtsChange = { onIntent(AddJournalEntryIntent.HelpMeStartThoughtsChanged(it)) },
            onGenerateClick = { onIntent(AddJournalEntryIntent.GenerateClicked) },
            onRegenerateClick = { onIntent(AddJournalEntryIntent.RegenerateClicked) },
            onUseGeneratedTextClick = { onIntent(AddJournalEntryIntent.UseGeneratedTextClicked) },
        )
    }

    if (uiState.helpMeRefine.isVisible) {
        HelpMeRefineBottomSheet(
            uiState = uiState.helpMeRefine,
            onDismissRequest = { onIntent(AddJournalEntryIntent.HelpMeRefineDismissed) },
            onToneSelect = { onIntent(AddJournalEntryIntent.HelpMeRefineToneSelected(it)) },
            onThoughtsChange = { onIntent(AddJournalEntryIntent.HelpMeRefineThoughtsChanged(it)) },
            onRefineClick = { onIntent(AddJournalEntryIntent.RefineClicked) },
            onRegenerateClick = { onIntent(AddJournalEntryIntent.RegenerateRefineClicked) },
            onUseRefinedTextClick = { onIntent(AddJournalEntryIntent.UseRefinedTextClicked) },
        )
    }
}

@Composable
private fun JournalDateSlotUiState.title(): String = when (this) {
    JournalDateSlotUiState.TODAY -> stringResource(R.string.journal_add_entry_title_today)
    JournalDateSlotUiState.YESTERDAY -> stringResource(R.string.journal_add_entry_title_yesterday)
}

// The entry point for "help me start" plus three hardcoded, randomly-varied starter lines (one per tone — see
// AddJournalEntryViewModel for how the variant is picked), overlaid at the bottom of the (full-screen) writing area
// only while it's empty, disappearing the moment the user types anything. Chips show the actual line that gets
// inserted, not a generic tone label, so tapping one is never a surprise. "Help me refine" (Journal Entry Detail) is
// the equivalent entry point once there's already text to work with. The AI chip is shown regardless of sign-in state —
// tapping it while signed out opens the sheet with a sign-in explainer instead of the tone picker, so the feature stays
// discoverable either way.
@Composable
internal fun StarterPromptList(
    starterPrompts: List<JournalStarterPromptUiState>,
    onPromptClick: (String) -> Unit,
    onHelpMeStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        for (prompt in starterPrompts) {
            val text = prompt.resolvedText()
            DsAssistChip(
                text = text,
                leadingIcon = prompt.tone.icon(),
                onClick = { onPromptClick(text) },
            )
        }
        DsAssistChip(
            text = stringResource(R.string.journal_help_me_start_cta),
            leadingIcon = Icons.Filled.AutoAwesome,
            onClick = onHelpMeStartClick,
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
    val selectedDate = if (uiState.selectedSlot == JournalDateSlotUiState.TODAY) {
        today
    } else {
        yesterday
    }

    DsDateStrip(
        selectedDate = selectedDate,
        isSelectable = { date -> date.toSlot(today, yesterday)?.let { it in uiState.availableSlots } ?: false },
        onDateSelect = { date ->
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
internal fun HelpMeStartBottomSheet(
    uiState: HelpMeStartUiState,
    onDismissRequest: () -> Unit,
    onSignInClick: () -> Unit,
    onToneSelect: (JournalPromptToneUiState) -> Unit,
    onThoughtsChange: (String) -> Unit,
    onGenerateClick: () -> Unit,
    onRegenerateClick: () -> Unit,
    onUseGeneratedTextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.let { aiAssistErrorMessage(it) }
    LaunchedEffect(uiState.error) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    DsModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
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
                        DsText(text = stringResource(R.string.journal_help_me_start_signed_out_message))
                        DsButton(
                            text = stringResource(CoreUiR.string.onboarding_account_signin_signup_cta),
                            onClick = onSignInClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HelpMeStartStep.INPUT -> {
                        DsText(
                            text = stringResource(R.string.journal_help_me_start_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val toneLabels = JournalPromptToneUiState.entries.associateWith { aiAssistToneLabel(it) }
                        DsChoiceFlowRow(
                            items = JournalPromptToneUiState.entries.toList(),
                            selectedItem = uiState.selectedTone,
                            onItemSelect = { tone -> tone?.let(onToneSelect) },
                            enabled = !uiState.isGenerating,
                            allowDeselect = false,
                            label = { toneLabels.getValue(it) },
                        )
                        // Same borderless, containerless field as the entry's own body — the tone picker aside, this
                        // sheet should feel like an extension of that writing surface, not a separate form. minLines
                        // reserves space up front so typing a second line doesn't reflow the sheet around it.
                        DsPlainTextField(
                            value = uiState.thoughts,
                            onValueChange = onThoughtsChange,
                            placeholder = stringResource(R.string.journal_help_me_start_thoughts_label),
                            enabled = !uiState.isGenerating,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DsButtonWithLoading(
                            text = stringResource(R.string.journal_help_me_start_generate_cta),
                            onClick = onGenerateClick,
                            enabled = uiState.selectedTone != null &&
                                !uiState.isGenerating &&
                                uiState.thoughts.length <= MAX_THOUGHTS_LENGTH,
                            loading = uiState.isGenerating,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // A dedicated screen once a result exists, rather than reusing INPUT's layout
                    // with the result appended below it — the tone picker/thoughts field step is
                    // done at this point, so this screen shows only the result and the two actions
                    // that apply to it. Regenerate silently reuses the last-selected tone/thoughts
                    // (no way back to INPUT to change them without dismissing and reopening).
                    HelpMeStartStep.PREVIEW -> {
                        DsText(
                            text = stringResource(R.string.journal_help_me_start_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        uiState.generatedText?.let { DsText(text = it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.space200)) {
                            DsFilledTonalButtonWithLoading(
                                text = stringResource(R.string.journal_help_me_start_regenerate_cta),
                                onClick = onRegenerateClick,
                                enabled = (uiState.remainingToday ?: 1) > 0,
                                loading = uiState.isGenerating,
                                modifier = Modifier.weight(1f),
                            )
                            DsButton(
                                text = stringResource(R.string.journal_ai_assist_accept_cta),
                                onClick = onUseGeneratedTextClick,
                                enabled = !uiState.isGenerating,
                                modifier = Modifier.weight(1f),
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
internal fun aiAssistToneLabel(tone: JournalPromptToneUiState): String = when (tone) {
    JournalPromptToneUiState.VERY_BAD -> stringResource(R.string.journal_ai_assist_tone_very_bad)
    JournalPromptToneUiState.BAD -> stringResource(R.string.journal_ai_assist_tone_bad)
    JournalPromptToneUiState.NEUTRAL -> stringResource(R.string.journal_ai_assist_tone_neutral)
    JournalPromptToneUiState.GOOD -> stringResource(R.string.journal_ai_assist_tone_good)
    JournalPromptToneUiState.VERY_GOOD -> stringResource(R.string.journal_ai_assist_tone_very_good)
}

@Composable
internal fun aiAssistErrorMessage(error: AiAssistErrorUiState): String = when (error) {
    AiAssistErrorUiState.INVALID_REQUEST -> stringResource(R.string.journal_ai_assist_error_invalid_request)
    AiAssistErrorUiState.UPSTREAM_FAILED -> stringResource(R.string.journal_ai_assist_error_upstream_failed)
    AiAssistErrorUiState.NOT_SIGNED_IN -> stringResource(R.string.journal_ai_assist_error_not_signed_in)
    AiAssistErrorUiState.DAILY_LIMIT_REACHED -> stringResource(R.string.journal_ai_assist_error_daily_limit_reached)
    AiAssistErrorUiState.NETWORK_UNAVAILABLE -> stringResource(R.string.journal_ai_assist_error_network_unavailable)
    AiAssistErrorUiState.UNKNOWN -> stringResource(R.string.journal_ai_assist_error_unknown)
}

private val previewStarterPrompts = JournalStarterPromptTone.entries.map { tone ->
    JournalStarterPromptUiState(
        tone = tone,
        variant = 0,
    )
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
    @PreviewParameter(AddJournalEntryScreenPreviewStateProvider::class)
    state: AddJournalEntryUiState,
) {
    DsTheme {
        AddJournalEntryScreenContent(
            uiState = state,
            onIntent = {},
            onCancel = {},
        )
    }
}

private class HelpMeStartBottomSheetPreviewStateProvider : PreviewParameterProvider<HelpMeStartUiState> {
    override val values = sequenceOf(
        HelpMeStartUiState(
            isVisible = true,
            step = HelpMeStartStep.SIGNED_OUT,
        ),
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
            generatedText = "I made real progress today, and it feels good to see it come together...",
            remainingToday = 7,
        ),
        HelpMeStartUiState(
            isVisible = true,
            step = HelpMeStartStep.PREVIEW,
            selectedTone = JournalPromptToneUiState.GOOD,
            generatedText = "I made real progress today, and it feels good to see it come together...",
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
    @PreviewParameter(HelpMeStartBottomSheetPreviewStateProvider::class)
    state: HelpMeStartUiState,
) {
    DsTheme {
        HelpMeStartBottomSheet(
            uiState = state,
            onDismissRequest = {},
            onSignInClick = {},
            onToneSelect = {},
            onThoughtsChange = {},
            onGenerateClick = {},
            onRegenerateClick = {},
            onUseGeneratedTextClick = {},
        )
    }
}
