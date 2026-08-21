package pl.luczka.todaywas.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import pl.luczka.todaywas.core.designsystem.components.chips.DsChip
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionGrid
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsModalBottomSheet
import pl.luczka.todaywas.core.designsystem.components.fab.DsExtendedFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.fab.DsFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.lists.DsSectionedList
import pl.luczka.todaywas.core.designsystem.components.progress.DsLoadingIndicator
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.auth.util.message
import pl.luczka.todaywas.ui.habit.HabitRow
import pl.luczka.todaywas.ui.journal.JournalEntryRow
import pl.luczka.todaywas.ui.model.AuthErrorUiState
import pl.luczka.todaywas.ui.model.AuthStateUi
import pl.luczka.todaywas.ui.model.ContributionGridUiState
import pl.luczka.todaywas.ui.model.ContributionWindowUiState
import pl.luczka.todaywas.ui.model.FabActionUiState
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.model.HabitUiState
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import java.time.Instant
import java.time.LocalDate

// Each section shows at most this many items on Main before falling back to a "View all" row.
private const val MAIN_SECTION_CAP = 5

@Composable
fun MainScreen(
    onAddEntryClicked: () -> Unit,
    onJournalEntryClicked: (JournalEntryUiState) -> Unit,
    onJournalListClicked: () -> Unit,
    onCreateHabitClicked: () -> Unit,
    onLogCheckInsClicked: () -> Unit,
    onHabitClicked: (String) -> Unit,
    onHabitListClicked: () -> Unit,
    onAccountClicked: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessages = AuthErrorUiState.entries.associateWith { it.message() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                MainUiEvent.NavigateToAddEntry -> onAddEntryClicked()
                is MainUiEvent.NavigateToJournalDetail -> onJournalEntryClicked(event.entry)
                MainUiEvent.NavigateToJournalList -> onJournalListClicked()
                MainUiEvent.NavigateToCreateHabit -> onCreateHabitClicked()
                MainUiEvent.NavigateToLogHabitCheckIns -> onLogCheckInsClicked()
                is MainUiEvent.NavigateToHabitDetail -> onHabitClicked(event.habitId)
                MainUiEvent.NavigateToHabitList -> onHabitListClicked()
                MainUiEvent.NavigateToAccount -> onAccountClicked()
                is MainUiEvent.ShowError -> snackbarHostState.showSnackbar(
                    errorMessages.getValue(event.error),
                )
            }
        }
    }

    MainScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun MainScreenContent(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.main_top_bar_title),
                actions = {
                    DsIconButton(onClick = { onIntent(MainIntent.AccountIconClicked) }) {
                        DsIcon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(
                                R.string.content_description_account,
                            ),
                        )
                    }
                },
            )
        },
        floatingActionButton = { MainFab(uiState, onIntent) },
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        val bothEmpty = !uiState.isLoading &&
            uiState.journalEntries.isEmpty() &&
            uiState.habits.isEmpty()
        if (bothEmpty) {
            MainEmptyState(
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            // Each section is capped (DsSectionedList never shows more than MAIN_SECTION_CAP
            // rows) and doesn't stretch to fill extra space, so sections are stacked at their
            // natural content height rather than weighted — a weighted split left dead space
            // between a short section and the next one instead of making it look "bigger".
            // Scrollable as a safety net in case combined content ever exceeds screen height.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                JournalSection(uiState = uiState, onIntent = onIntent)
                HabitSection(uiState = uiState, onIntent = onIntent)
            }
        }
    }

    if (uiState.isAccountSheetVisible) {
        AccountBottomSheet(uiState, onIntent)
    }

    if (uiState.isSignOutConfirmVisible) {
        SignOutConfirmDialog(onIntent)
    }
}

@Composable
private fun MainEmptyState(
    onIntent: (MainIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(DsSpacing.space600),
    ) {
        DsText(
            text = stringResource(R.string.main_empty_state_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        DsText(
            text = stringResource(R.string.main_empty_state_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = DsSpacing.space400),
        )
        DsButton(
            text = stringResource(R.string.main_fab_add_journal),
            onClick = {
                onIntent(MainIntent.FabActionClicked(FabActionUiState.ADD_JOURNAL_ENTRY))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DsSpacing.space600),
        )
        DsButton(
            text = stringResource(R.string.main_fab_create_habit),
            onClick = { onIntent(MainIntent.FabActionClicked(FabActionUiState.CREATE_HABIT)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DsSpacing.space200),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountBottomSheet(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
) {
    DsModalBottomSheet(onDismissRequest = { onIntent(MainIntent.AccountSheetDismissed) }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
            modifier = Modifier
                .fillMaxWidth()
                .padding(DsSpacing.space600),
        ) {
            when (val authState = uiState.authState) {
                AuthStateUi.Loading -> DsLoadingIndicator()
                is AuthStateUi.SignedIn -> {
                    DsText(
                        text =
                            authState.email
                                ?: stringResource(R.string.preferences_signed_in_no_email),
                    )
                    DsButtonWithLoading(
                        text = stringResource(R.string.preferences_sign_out_cta),
                        onClick = { onIntent(MainIntent.SignOutClicked) },
                        loading = uiState.isSigningOut,
                    )
                }
                AuthStateUi.SignedOut -> {
                    DsText(text = stringResource(R.string.account_sheet_signed_out_description))
                    DsButton(
                        text = stringResource(R.string.onboarding_account_signin_signup_cta),
                        onClick = { onIntent(MainIntent.SignInSignUpPromptClicked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignOutConfirmDialog(onIntent: (MainIntent) -> Unit) {
    DsAlertDialog(
        onDismissRequest = { onIntent(MainIntent.SignOutCancelled) },
        confirmButton = {
            DsTextButton(
                text = stringResource(R.string.account_sign_out_confirm_cta),
                onClick = { onIntent(MainIntent.SignOutConfirmed) },
            )
        },
        dismissButton = {
            DsTextButton(
                text = stringResource(R.string.account_sign_out_cancel_cta),
                onClick = { onIntent(MainIntent.SignOutCancelled) },
            )
        },
        title = { DsText(text = stringResource(R.string.account_sign_out_confirm_title)) },
        text = { DsText(text = stringResource(R.string.account_sign_out_confirm_message)) },
    )
}

@Composable
private fun MainFab(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
) {
    val actions = uiState.fabActions
    if (actions.isEmpty()) return

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space200),
    ) {
        if (uiState.fabExpanded) {
            for (action in actions) {
                DsExtendedFloatingActionButton(
                    text = stringResource(action.labelRes),
                    onClick = { onIntent(MainIntent.FabActionClicked(action)) },
                )
            }
        }
        DsFloatingActionButton(
            onClick = {
                if (actions.size == 1) {
                    onIntent(MainIntent.FabActionClicked(actions.single()))
                } else {
                    onIntent(MainIntent.FabToggled)
                }
            },
        ) {
            DsIcon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.content_description_add),
            )
        }
    }
}

@Composable
private fun JournalSection(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = DsSpacing.space600)) {
        DsText(
            text = stringResource(R.string.main_journal_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = DsSpacing.space600),
        )
        // Full-bleed (no horizontal inset), same as Habit Detail's grid: it needs all available
        // width so more weeks are visible at once.
        DsContributionGrid(
            cells = uiState.journalContributionGrid.cells,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DsSpacing.space200),
        )
        ContributionWindowChipRow(
            availableWindows = uiState.journalAvailableWindows,
            selectedWindow = uiState.journalSelectedWindow,
            onWindowSelected = { onIntent(MainIntent.JournalWindowSelected(it)) },
            modifier = Modifier.padding(
                horizontal = DsSpacing.space600,
                vertical = DsSpacing.space200,
            ),
        )
        val hasMore = uiState.journalEntries.size > MAIN_SECTION_CAP
        when {
            uiState.isLoading -> DsSectionedList(
                items = emptyList<JournalEntryUiState>(),
                isLoading = true,
                itemContent = {},
                modifier = Modifier.padding(horizontal = DsSpacing.space600),
            )
            uiState.journalEntries.isEmpty() -> JournalEmptyContent(onIntent)
            else -> DsSectionedList(
                items = uiState.journalEntries.take(MAIN_SECTION_CAP),
                isLoading = false,
                itemContent = { entry ->
                    JournalEntryRow(
                        entry = entry,
                        onClick = { onIntent(MainIntent.JournalEntryClicked(entry)) },
                    )
                },
                onViewAllClicked = if (hasMore) {
                    { onIntent(MainIntent.JournalViewAllClicked) }
                } else {
                    null
                },
                viewAllLabel = if (hasMore) {
                    stringResource(R.string.main_journal_view_all_cta)
                } else {
                    null
                },
                modifier = Modifier.padding(horizontal = DsSpacing.space600),
            )
        }
    }
}

@Composable
private fun JournalEmptyContent(onIntent: (MainIntent) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = DsSpacing.space600)) {
        DsText(text = stringResource(R.string.main_journal_empty_state))
        DsButton(
            text = stringResource(R.string.main_fab_add_journal),
            onClick = {
                onIntent(MainIntent.FabActionClicked(FabActionUiState.ADD_JOURNAL_ENTRY))
            },
            modifier = Modifier.padding(top = DsSpacing.space200),
        )
    }
}

@Composable
private fun ContributionWindowChipRow(
    availableWindows: List<ContributionWindowUiState>,
    selectedWindow: ContributionWindowUiState,
    onWindowSelected: (ContributionWindowUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier) {
        items(availableWindows) { window ->
            DsChip(
                text = window.label(),
                selected = window == selectedWindow,
                onClick = { onWindowSelected(window) },
                modifier = Modifier.padding(end = DsSpacing.space200),
            )
        }
    }
}

@Composable
private fun ContributionWindowUiState.label(): String = when (this) {
    ContributionWindowUiState.RollingTwelveMonths -> stringResource(
        R.string.contribution_window_last_12_months_label,
    )
    is ContributionWindowUiState.CalendarYear -> year.toString()
}

@Composable
private fun HabitSection(
    uiState: MainUiState,
    onIntent: (MainIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = DsSpacing.space600)) {
        DsText(
            text = stringResource(R.string.main_habit_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = DsSpacing.space600),
        )
        val hasMore = uiState.habits.size > MAIN_SECTION_CAP
        when {
            uiState.isLoading -> DsSectionedList(
                items = emptyList<HabitUiState>(),
                isLoading = true,
                itemContent = {},
                modifier = Modifier.padding(
                    horizontal = DsSpacing.space600,
                    vertical = DsSpacing.space200,
                ),
            )
            uiState.habits.isEmpty() -> HabitEmptyContent(onIntent)
            else -> DsSectionedList(
                items = uiState.habits.take(MAIN_SECTION_CAP),
                isLoading = false,
                itemContent = { habit ->
                    HabitRow(
                        habit = habit,
                        onClick = { onIntent(MainIntent.HabitClicked(habit)) },
                    )
                },
                onViewAllClicked = if (hasMore) {
                    { onIntent(MainIntent.HabitViewAllClicked) }
                } else {
                    null
                },
                viewAllLabel = if (hasMore) {
                    stringResource(R.string.main_habit_view_all_cta)
                } else {
                    null
                },
                modifier = Modifier.padding(
                    horizontal = DsSpacing.space600,
                    vertical = DsSpacing.space200,
                ),
            )
        }
    }
}

@Composable
private fun HabitEmptyContent(onIntent: (MainIntent) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = DsSpacing.space600)) {
        DsText(text = stringResource(R.string.main_habit_empty_state))
        DsButton(
            text = stringResource(R.string.main_fab_create_habit),
            onClick = { onIntent(MainIntent.FabActionClicked(FabActionUiState.CREATE_HABIT)) },
            modifier = Modifier.padding(top = DsSpacing.space200),
        )
    }
}

private class MainScreenPreviewStateProvider : PreviewParameterProvider<MainUiState> {
    override val values = sequenceOf(
        previewMainUiState(isLoading = true),
        previewMainUiState(
            fabActions = listOf(FabActionUiState.ADD_JOURNAL_ENTRY, FabActionUiState.CREATE_HABIT),
        ),
        previewMainUiState(
            journalEntries = listOf(
                JournalEntryUiState(
                    id = "1",
                    date = LocalDate.now(),
                    formattedDate = "Jul 27, 2026",
                    text = "Today was a good day.",
                    createdAt = Instant.now(),
                ),
                JournalEntryUiState(
                    id = "2",
                    date = LocalDate.now().minusDays(1),
                    formattedDate = "Jul 26, 2026",
                    text =
                        "A long entry that should get truncated in the list preview once it " +
                            "wraps past two lines of text.",
                    createdAt = Instant.now(),
                ),
            ),
            habits = listOf(
                HabitUiState(
                    id = "1",
                    name = "Drink water",
                    type = HabitTypeUiState.BINARY,
                    todayStatus = HabitCheckInStatusUiState.NotLogged,
                ),
                HabitUiState(
                    id = "2",
                    name = "Mood",
                    type = HabitTypeUiState.SCALE,
                    todayStatus = HabitCheckInStatusUiState.LoggedScale(value = 4),
                ),
            ),
            fabActions = listOf(
                FabActionUiState.CREATE_HABIT,
                FabActionUiState.LOG_HABIT_CHECK_INS,
            ),
        ),
        previewMainUiState(
            journalEntries = (1..7).map {
                JournalEntryUiState(
                    id = it.toString(),
                    date = LocalDate.now().minusDays(it.toLong()),
                    formattedDate = "Entry $it",
                    text = "Entry number $it",
                    createdAt = Instant.now(),
                )
            },
            habits = (1..7).map {
                HabitUiState(
                    id = it.toString(),
                    name = "Habit $it",
                    type = HabitTypeUiState.BINARY,
                    todayStatus = HabitCheckInStatusUiState.NotLogged,
                )
            },
        ),
        previewMainUiState(
            authState = AuthStateUi.Loading,
            isAccountSheetVisible = true,
        ),
        previewMainUiState(
            authState = AuthStateUi.SignedOut,
            isAccountSheetVisible = true,
        ),
        previewMainUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            isAccountSheetVisible = true,
        ),
        previewMainUiState(
            authState = AuthStateUi.SignedIn(email = "person@example.com"),
            isAccountSheetVisible = true,
            isSignOutConfirmVisible = true,
        ),
    )
}

private fun previewMainUiState(
    journalEntries: List<JournalEntryUiState> = emptyList(),
    habits: List<HabitUiState> = emptyList(),
    fabActions: List<FabActionUiState> = emptyList(),
    authState: AuthStateUi = AuthStateUi.SignedOut,
    isAccountSheetVisible: Boolean = false,
    isSignOutConfirmVisible: Boolean = false,
    isLoading: Boolean = false,
) = MainUiState(
    isLoading = isLoading,
    journalEntries = journalEntries,
    habits = habits,
    journalContributionGrid = previewJournalContributionGrid,
    journalAvailableWindows = previewJournalAvailableWindows,
    journalSelectedWindow = ContributionWindowUiState.RollingTwelveMonths,
    fabActions = fabActions,
    fabExpanded = false,
    authState = authState,
    isAccountSheetVisible = isAccountSheetVisible,
    isSignOutConfirmVisible = isSignOutConfirmVisible,
    isSigningOut = false,
)

private val previewJournalContributionGrid = ContributionGridUiState(cells = emptyList())

private val previewJournalAvailableWindows = listOf(
    ContributionWindowUiState.RollingTwelveMonths,
    ContributionWindowUiState.CalendarYear(LocalDate.now().year),
)

@PreviewLightDark
@Composable
private fun MainScreenPreview(
    @PreviewParameter(MainScreenPreviewStateProvider::class) state: MainUiState,
) {
    DsTheme {
        MainScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
