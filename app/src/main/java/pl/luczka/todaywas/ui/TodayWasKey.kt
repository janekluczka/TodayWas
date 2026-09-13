package pl.luczka.todaywas.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import pl.luczka.todaywas.ui.onboarding.AllSetReason

@Serializable
sealed interface TodayWasKey : NavKey

@Serializable
data object OnboardingWelcomeKey : TodayWasKey

@Serializable
data object OnboardingChoiceKey : TodayWasKey

// Hosts sign-in, sign-up, and the data-sync review internally (own AccountSubStep state) rather
// than three separate nav destinations — kept as one screen since splitting those further adds
// more complexity than it buys right now.
@Serializable
data object OnboardingAccountSetupKey : TodayWasKey

@Serializable
data class OnboardingAllSetKey(
    val reason: AllSetReason,
) : TodayWasKey

@Serializable
data object MainKey : TodayWasKey

@Serializable
data object JournalListKey : TodayWasKey

@Serializable
data object HabitListKey : TodayWasKey

@Serializable
data object AddJournalEntryKey : TodayWasKey

@Serializable
data class JournalEntryDetailKey(
    val id: String,
) : TodayWasKey

@Serializable
data class EditJournalEntryKey(
    val id: String,
) : TodayWasKey

@Serializable
data object CreateHabitKey : TodayWasKey

@Serializable
data object LogHabitCheckInsKey : TodayWasKey

@Serializable
data class HabitDetailKey(
    val habitId: String,
) : TodayWasKey

@Serializable
data object AccountKey : TodayWasKey
