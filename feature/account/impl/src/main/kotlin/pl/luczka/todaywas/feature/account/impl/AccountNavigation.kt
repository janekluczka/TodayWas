package pl.luczka.todaywas.feature.account.impl

sealed interface AccountNavigation {

    data object Back : AccountNavigation
}
