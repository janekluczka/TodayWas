package pl.luczka.todaywas.feature.main.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import pl.luczka.todaywas.core.navigation.Navigator

@Serializable
data object MainKey : NavKey

/** Main is the app's home: it replaces whatever led to it (onboarding), so back from it exits. */
fun Navigator.replaceAllWithMain() = replaceAll(MainKey)
