package pl.luczka.todaywas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import pl.luczka.todaywas.ui.account.AccountScreen
import pl.luczka.todaywas.ui.habit.create.CreateHabitScreen
import pl.luczka.todaywas.ui.habit.detail.HabitDetailScreen
import pl.luczka.todaywas.ui.habit.list.HabitListScreen
import pl.luczka.todaywas.ui.habit.logcheckin.LogHabitCheckInsScreen
import pl.luczka.todaywas.ui.journal.create.AddJournalEntryScreen
import pl.luczka.todaywas.ui.journal.detail.JournalEntryDetailScreen
import pl.luczka.todaywas.ui.journal.list.JournalListScreen
import pl.luczka.todaywas.ui.main.MainScreen
import pl.luczka.todaywas.ui.onboarding.accountsetup.OnboardingAccountSetupScreen
import pl.luczka.todaywas.ui.onboarding.allset.OnboardingAllSetScreen
import pl.luczka.todaywas.ui.onboarding.choice.OnboardingChoiceScreen
import pl.luczka.todaywas.ui.onboarding.welcome.OnboardingWelcomeScreen

@Composable
fun TodayWasApp(viewModel: RootViewModel = hiltViewModel()) {
    val initialDestination by viewModel.initialDestination.collectAsStateWithLifecycle()
    val destination = initialDestination
    if (destination != null) {
        TodayWasNavDisplay(initialDestination = destination)
    }
}

@Composable
private fun TodayWasNavDisplay(initialDestination: TodayWasKey) {
    val backStack = rememberNavBackStack(initialDestination)

    NavDisplay(
        backStack = backStack,
        // NavDisplay throws if the backstack is emptied, so only pop when more than the root
        // entry remains; back from the root falls through to the platform's default behavior.
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<OnboardingWelcomeKey> {
                OnboardingWelcomeScreen(
                    onGetStartedClicked = {
                        // Welcome is a one-way intro — replace it so back from Choice exits the
                        // app instead of returning here.
                        backStack.clear()
                        backStack.add(OnboardingChoiceKey)
                    },
                )
            }
            entry<OnboardingChoiceKey> {
                OnboardingChoiceScreen(
                    onNavigateToAccountSetup = { backStack.add(OnboardingAccountSetupKey) },
                    onFinished = { reason ->
                        backStack.clear()
                        backStack.add(OnboardingAllSetKey(reason))
                    },
                )
            }
            entry<OnboardingAccountSetupKey> {
                OnboardingAccountSetupScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onFinished = { reason ->
                        backStack.clear()
                        backStack.add(OnboardingAllSetKey(reason))
                    },
                )
            }
            entry<OnboardingAllSetKey> { key ->
                OnboardingAllSetScreen(
                    reason = key.reason,
                    onFinished = {
                        backStack.clear()
                        backStack.add(MainKey)
                    },
                )
            }
            entry<MainKey> {
                MainScreen(
                    onAddEntryClicked = { backStack.add(AddJournalEntryKey) },
                    onJournalEntryClicked = { entry ->
                        backStack.add(JournalEntryDetailKey(id = entry.id))
                    },
                    onJournalListClicked = { backStack.add(JournalListKey) },
                    onCreateHabitClicked = { backStack.add(CreateHabitKey) },
                    onLogCheckInsClicked = { backStack.add(LogHabitCheckInsKey) },
                    onHabitClicked = { habitId -> backStack.add(HabitDetailKey(habitId)) },
                    onHabitListClicked = { backStack.add(HabitListKey) },
                    onAccountClicked = { backStack.add(AccountKey) },
                )
            }
            entry<AccountKey> {
                AccountScreen(
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<JournalListKey> {
                JournalListScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onEntryClicked = { id -> backStack.add(JournalEntryDetailKey(id = id)) },
                )
            }
            entry<HabitListKey> {
                HabitListScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onHabitClicked = { habitId -> backStack.add(HabitDetailKey(habitId)) },
                )
            }
            entry<AddJournalEntryKey> {
                AddJournalEntryScreen(
                    onSaved = { backStack.removeLastOrNull() },
                    onCancelled = { backStack.removeLastOrNull() },
                )
            }
            entry<JournalEntryDetailKey> { key ->
                JournalEntryDetailScreen(
                    id = key.id,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<CreateHabitKey> {
                CreateHabitScreen(
                    onSaved = { backStack.removeLastOrNull() },
                    onCancelled = { backStack.removeLastOrNull() },
                )
            }
            entry<LogHabitCheckInsKey> {
                LogHabitCheckInsScreen(
                    onSaved = { backStack.removeLastOrNull() },
                    onCancelled = { backStack.removeLastOrNull() },
                )
            }
            entry<HabitDetailKey> { key ->
                HabitDetailScreen(
                    habitId = key.habitId,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
