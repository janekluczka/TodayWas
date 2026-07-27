package pl.luczka.todaywas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
        entryProvider =
            entryProvider {
                entry<OnboardingKey> {
                    OnboardingScreen(
                        onFinished = {
                            backStack.clear()
                            backStack.add(MainKey)
                        },
                    )
                }
                entry<MainKey> {
                    MainScreen()
                }
            },
    )
}
