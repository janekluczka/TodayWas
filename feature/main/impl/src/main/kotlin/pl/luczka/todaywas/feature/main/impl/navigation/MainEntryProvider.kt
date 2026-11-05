package pl.luczka.todaywas.feature.main.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.luczka.todaywas.core.navigation.Navigator
import pl.luczka.todaywas.feature.account.api.navigation.navigateToAccount
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToCreateHabit
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToHabitDetail
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToHabitList
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToLogHabitCheckIns
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToAddJournalEntry
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToJournalEntryDetail
import pl.luczka.todaywas.feature.journal.api.navigation.navigateToJournalList
import pl.luczka.todaywas.feature.main.api.navigation.MainKey
import pl.luczka.todaywas.feature.main.impl.MainScreen

fun EntryProviderScope<NavKey>.mainEntries(navigator: Navigator) {
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
}
