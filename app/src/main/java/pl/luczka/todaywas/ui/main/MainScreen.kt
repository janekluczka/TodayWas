package pl.luczka.todaywas.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionCellUiState
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionRow
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsModalBottomSheet
import pl.luczka.todaywas.core.designsystem.components.fab.DsExtendedFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.fab.DsFloatingActionButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.lists.DsListItem
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
    val bothEmpty = !uiState.isLoading &&
        uiState.journalEntries.isEmpty() &&
        uiState.habits.isEmpty()

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
        // The empty state already surfaces its own "add journal"/"create habit" CTAs inline, so
        // the FAB would be a redundant, floating duplicate of the same actions.
        floatingActionButton = { if (!bothEmpty) MainFab(uiState, onIntent) },
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
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
        when (val authState = uiState.authState) {
            AuthStateUi.Loading -> DsLoadingIndicator(
                modifier = Modifier.padding(DsSpacing.space600),
            )
            is AuthStateUi.SignedIn -> {
                AccountSectionList(title = stringResource(R.string.account_section_title)) {
                    DsListItem(
                        text = authState.email
                            ?: stringResource(R.string.preferences_signed_in_no_email),
                        leadingIcon = Icons.Default.Person,
                    )
                }
                AccountSectionList {
                    DsListItem(
                        text = stringResource(R.string.preferences_sign_out_cta),
                        leadingIcon = Icons.AutoMirrored.Filled.Logout,
                        contentColor = MaterialTheme.colorScheme.error,
                        trailingContent = if (uiState.isSigningOut) {
                            { DsLoadingIndicator(modifier = Modifier.size(20.dp)) }
                        } else {
                            null
                        },
                        onClick = if (uiState.isSigningOut) {
                            null
                        } else {
                            { onIntent(MainIntent.SignOutClicked) }
                        },
                    )
                }
            }
            AuthStateUi.SignedOut -> {
                AccountSectionList(
                    title = stringResource(R.string.account_section_title),
                    bottomPadding = DsSpacing.space600,
                ) {
                    DsListItem(
                        text = stringResource(R.string.onboarding_account_signin_signup_cta),
                        leadingIcon = Icons.Default.Person,
                        onClick = { onIntent(MainIntent.SignInSignUpPromptClicked) },
                    )
                }
            }
        }
    }
}

// A single-row DsSectionedList — every account-sheet section today (identity/CTA, sign out) is
// exactly one row, but wrapping in DsSectionedList keeps the card/divider treatment identical to
// a future section that grows past one row (e.g. Settings, once there's a real item for it).
@Composable
private fun AccountSectionList(
    title: String? = null,
    bottomPadding: Dp = DsSpacing.space200,
    row: @Composable () -> Unit,
) {
    DsSectionedList(
        items = listOf(Unit),
        isLoading = false,
        itemContent = { row() },
        title = title,
        modifier = Modifier.padding(
            start = DsSpacing.space600,
            top = DsSpacing.space200,
            end = DsSpacing.space600,
            bottom = bottomPadding,
        ),
    )
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
        // A quick-glance, single-row strip of the full history — same data and scroll behavior as
        // the grid on the Journal list screen (opened via "View all" below), just flattened into
        // one row of double-size cells instead of stacked weekly columns.
        DsContributionRow(
            cells = uiState.journalContributionCells,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DsSpacing.space200, bottom = DsSpacing.space400),
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
    journalContributionCells = previewJournalContributionCells,
    fabActions = fabActions,
    fabExpanded = false,
    authState = authState,
    isAccountSheetVisible = isAccountSheetVisible,
    isSignOutConfirmVisible = isSignOutConfirmVisible,
    isSigningOut = false,
)

private val previewJournalContributionCells = (0..29).map { offset ->
    DsContributionCellUiState.Level(
        LocalDate.now().minusDays(offset.toLong()),
        DsContributionLevel.entries[offset % DsContributionLevel.entries.size],
    )
}

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
