package pl.luczka.todaywas.feature.account.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.luczka.todaywas.core.navigation.Navigator
import pl.luczka.todaywas.feature.account.api.navigation.AccountKey
import pl.luczka.todaywas.feature.account.impl.AccountScreen

fun EntryProviderScope<NavKey>.accountEntries(navigator: Navigator) {
    entry<AccountKey> { AccountScreen(onBack = navigator::goBack) }
}
