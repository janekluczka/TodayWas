package pl.luczka.todaywas.feature.onboarding.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.luczka.todaywas.core.navigation.Navigator
import pl.luczka.todaywas.feature.main.api.navigation.replaceAllWithMain
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingAccountSetupKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingAllSetKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingChoiceKey
import pl.luczka.todaywas.feature.onboarding.api.navigation.OnboardingWelcomeKey
import pl.luczka.todaywas.feature.onboarding.impl.accountsetup.OnboardingAccountSetupScreen
import pl.luczka.todaywas.feature.onboarding.impl.allset.OnboardingAllSetScreen
import pl.luczka.todaywas.feature.onboarding.impl.choice.OnboardingChoiceScreen
import pl.luczka.todaywas.feature.onboarding.impl.welcome.OnboardingWelcomeScreen

fun EntryProviderScope<NavKey>.onboardingEntries(navigator: Navigator) {
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
}
