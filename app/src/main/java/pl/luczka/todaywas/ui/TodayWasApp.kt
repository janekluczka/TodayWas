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
import pl.luczka.todaywas.feature.account.impl.navigation.accountEntries
import pl.luczka.todaywas.feature.habit.impl.navigation.habitEntries
import pl.luczka.todaywas.feature.journal.impl.navigation.journalEntries
import pl.luczka.todaywas.feature.main.impl.navigation.mainEntries
import pl.luczka.todaywas.feature.onboarding.impl.navigation.onboardingEntries

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
            onboardingEntries(navigator)
            mainEntries(navigator)
            accountEntries(navigator)
            habitEntries(navigator)
            journalEntries(navigator)
        },
    )
}
