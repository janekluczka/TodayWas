package pl.luczka.todaywas.feature.account.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import pl.luczka.todaywas.core.navigation.Navigator

@Serializable
data object AccountKey : NavKey

fun Navigator.navigateToAccount() = navigate(AccountKey)
