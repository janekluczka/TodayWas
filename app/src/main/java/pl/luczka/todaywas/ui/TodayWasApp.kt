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
import pl.luczka.todaywas.ui.habit.logcheckin.LogHabitCheckInsScreen
import pl.luczka.todaywas.ui.journal.create.AddJournalEntryScreen
import pl.luczka.todaywas.ui.journal.detail.JournalEntryDetailScreen
import pl.luczka.todaywas.ui.main.MainScreen
import pl.luczka.todaywas.ui.onboarding.OnboardingScreen

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
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<OnboardingKey> {
                OnboardingScreen(
                    onFinished = {
                        backStack.clear()
                        backStack.add(MainKey)
                    },
                )
            }
            entry<MainKey> {
                MainScreen(
                    onAddEntryClicked = { backStack.add(AddJournalEntryKey) },
                    onJournalEntryClicked = { entry -> backStack.add(JournalEntryDetailKey(id = entry.id)) },
                    onCreateHabitClicked = { backStack.add(CreateHabitKey) },
                    onLogCheckInsClicked = { backStack.add(LogHabitCheckInsKey) },
                    onHabitClicked = { habitId -> backStack.add(HabitDetailKey(habitId)) },
                    onAccountClicked = { backStack.add(AccountKey) },
                )
            }
            entry<AccountKey> {
                AccountScreen(
                    onBack = { backStack.removeLastOrNull() },
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
