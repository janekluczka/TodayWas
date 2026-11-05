package pl.luczka.todaywas.feature.journal.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import pl.luczka.todaywas.core.navigation.Navigator

@Serializable
data object JournalListKey : NavKey

@Serializable
data object AddJournalEntryKey : NavKey

@Serializable
data class JournalEntryDetailKey(
    val id: String,
) : NavKey

@Serializable
data class EditJournalEntryKey(
    val id: String,
) : NavKey

fun Navigator.navigateToJournalList() = navigate(JournalListKey)

fun Navigator.navigateToAddJournalEntry() = navigate(AddJournalEntryKey)

fun Navigator.navigateToJournalEntryDetail(id: String) = navigate(JournalEntryDetailKey(id = id))

fun Navigator.navigateToEditJournalEntry(id: String) = navigate(EditJournalEntryKey(id = id))
