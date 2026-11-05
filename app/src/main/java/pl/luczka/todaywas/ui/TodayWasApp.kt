package pl.luczka.todaywas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import pl.luczka.todaywas.core.navigation.Navigator
import pl.luczka.todaywas.feature.account.api.navigation.navigateToAccount
import pl.luczka.todaywas.feature.account.impl.navigation.accountEntries
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToCreateHabit
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToHabitDetail
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToHabitList
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToLogHabitCheckIns
import pl.luczka.todaywas.feature.habit.impl.navigation.habitEntries
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToAddJournalEntry
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToJournalEntryDetail
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToJournalList
import pl.luczka.todaywas.feature.journal.impl.navigation.journalEntries
import pl.luczka.todaywas.feature.main.api.navigation.MainKey
import pl.luczka.todaywas.feature.main.api.navigation.replaceAllWithMain
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingAccountSetupKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingAllSetKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingChoiceKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingWelcomeKey
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
private fun TodayWasNavDisplay(initialDestination: NavKey) {
    val backStack = rememberNavBackStack(initialDestination)
    val navigator = remember(backStack) { Navigator(backStack) }

    NavDisplay(
        backStack = backStack,
        // Back from the root falls through to the platform's default behavior.
        onBack = navigator::goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<OnboardingWelcomeKey> {
                OnboardingWelcomeScreen(
                    // Welcome is a one-way intro — replace it so back from Choice exits the
                    // app instead of returning here.
                    onGetStartedClick = { navigator.replaceAll(OnboardingChoiceKey) },
                )
            }
            entry<OnboardingChoiceKey> {
                OnboardingChoiceScreen(
                    onNavigateToAccountSetup = { navigator.navigate(OnboardingAccountSetupKey) },
                    onFinish = { reason -> navigator.replaceAll(OnboardingAllSetKey(reason)) },
                )
            }
            entry<OnboardingAccountSetupKey> {
                OnboardingAccountSetupScreen(
                    onBack = navigator::goBack,
                    onFinish = { reason -> navigator.replaceAll(OnboardingAllSetKey(reason)) },
                )
            }
            entry<OnboardingAllSetKey> { key ->
                OnboardingAllSetScreen(
                    reason = key.reason,
                    onFinish = navigator::replaceAllWithMain,
                )
            }
            entry<MainKey> {
                MainScreen(
                    onAddEntryClick = navigator::navigateToAddJournalEntry,
                    onJournalEntryClick = { entry -> navigator.navigateToJournalEntryDetail(entry.id) },
                    onJournalListClick = navigator::navigateToJournalList,
                    onCreateHabitClick = navigator::navigateToCreateHabit,
                    onLogCheckInsClick = navigator::navigateToLogHabitCheckIns,
                    onHabitClick = navigator::navigateToHabitDetail,
                    onHabitListClick = navigator::navigateToHabitList,
                    onAccountClick = navigator::navigateToAccount,
                )
            }
            accountEntries(navigator)
            habitEntries(navigator)
            journalEntries(navigator)
        },
    )
}
