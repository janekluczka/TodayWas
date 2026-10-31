package pl.luczka.todaywas.ui.account

sealed interface AccountNavigation {

    data object Back : AccountNavigation
}
