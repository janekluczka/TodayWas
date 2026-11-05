package pl.luczka.todaywas.feature.account.impl

internal sealed interface AccountNavigation {

    data object Back : AccountNavigation
}
