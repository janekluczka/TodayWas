package pl.luczka.todaywas.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface TodayWasKey : NavKey

@Serializable
data object OnboardingKey : TodayWasKey

@Serializable
data object MainKey : TodayWasKey

@Serializable
data object AddJournalEntryKey : TodayWasKey

@Serializable
data class JournalEntryDetailKey(
    val date: String,
    val text: String,
    val createdAt: Long,
) : TodayWasKey

@Serializable
data object CreateHabitKey : TodayWasKey

@Serializable
data object LogHabitCheckInsKey : TodayWasKey
