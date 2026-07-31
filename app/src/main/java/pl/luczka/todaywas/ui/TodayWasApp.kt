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
import pl.luczka.todaywas.ui.journal.AddJournalEntryScreen
import pl.luczka.todaywas.ui.journal.JournalEntryDetailScreen
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
                    onJournalEntryClicked = { entry ->
                        backStack.add(
                            JournalEntryDetailKey(
                                date = entry.date.toString(),
                                text = entry.text,
                                createdAt = entry.createdAt.toEpochMilli(),
                            ),
                        )
                    },
                    // TODO(S-03 Phase 4): wire to CreateHabitKey/LogHabitCheckInsKey nav entries.
                    onCreateHabitClicked = {},
                    onLogCheckInsClicked = {},
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
                    date = key.date,
                    text = key.text,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
