package pl.luczka.todaywas.feature.onboarding.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingWelcomeKey : NavKey

@Serializable
data object OnboardingChoiceKey : NavKey

// Hosts sign-in, sign-up, and the data-sync review internally (own AccountSubStep state) rather than three separate nav
// destinations — kept as one screen since splitting those further adds more complexity than it buys right now.
@Serializable
data object OnboardingAccountSetupKey : NavKey

@Serializable
data class OnboardingAllSetKey(
    val reason: AllSetReason,
) : NavKey
