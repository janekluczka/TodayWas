package pl.luczka.todaywas.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import pl.luczka.todaywas.domain.model.JournalDateSlot

@Serializable
sealed interface TodayWasKey : NavKey

@Serializable
data object OnboardingKey : TodayWasKey

@Serializable
data object MainKey : TodayWasKey

@Serializable
data class AddJournalEntryKey(
    val availableSlots: List<JournalDateSlot>,
) : TodayWasKey

@Serializable
data class JournalEntryDetailKey(
    val date: String,
    val text: String,
    val createdAt: Long,
) : TodayWasKey
