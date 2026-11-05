package pl.luczka.todaywas.feature.journal.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.luczka.todaywas.core.navigation.Navigator
import pl.luczka.todaywas.feature.account.api.navigation.navigateToAccount
import pl.luczka.todaywas.feature.journal.api.navigation.AddJournalEntryKey
import pl.luczka.todaywas.feature.journal.api.navigation.EditJournalEntryKey
import pl.luczka.todaywas.feature.journal.api.navigation.JournalEntryDetailKey
import pl.luczka.todaywas.feature.journal.api.navigation.JournalListKey
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToEditJournalEntry
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToJournalEntryDetail
import pl.luczka.todaywas.feature.journal.impl.create.AddJournalEntryScreen
import pl.luczka.todaywas.feature.journal.impl.detail.JournalEntryDetailScreen
import pl.luczka.todaywas.feature.journal.impl.edit.EditJournalEntryScreen
import pl.luczka.todaywas.feature.journal.impl.list.JournalListScreen

fun EntryProviderScope<NavKey>.journalEntries(navigator: Navigator) {
    entry<JournalListKey> {
        JournalListScreen(
            onBack = navigator::goBack,
            onEntryClick = navigator::navigateToJournalEntryDetail,
        )
    }
    entry<AddJournalEntryKey> {
        AddJournalEntryScreen(
            onSaveComplete = navigator::goBack,
            onCancel = navigator::goBack,
            onNavigateToSignIn = navigator::navigateToAccount,
        )
    }
    entry<JournalEntryDetailKey> { key ->
        JournalEntryDetailScreen(
            id = key.id,
            onBack = navigator::goBack,
            onEditClick = { navigator.navigateToEditJournalEntry(key.id) },
        )
    }
    entry<EditJournalEntryKey> { key ->
        EditJournalEntryScreen(
            id = key.id,
            onSaveComplete = navigator::goBack,
            onDiscardComplete = navigator::goBack,
            onNavigateToSignIn = navigator::navigateToAccount,
        )
    }
}
