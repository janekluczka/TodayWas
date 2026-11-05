package pl.luczka.todaywas.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The app's back stack as feature entry builders see it: they push keys and go back, while `:app` owns
 * the stack itself and renders it in its `NavDisplay`.
 */
class Navigator(
    private val backStack: NavBackStack<NavKey>,
) {
    fun navigate(key: NavKey) {
        backStack.add(key)
    }

    /** Pops the top entry. The root entry stays, since `NavDisplay` throws on an empty back stack. */
    fun goBack() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    /** Replaces the whole back stack with [key], so back from it leaves the app instead of returning. */
    fun replaceAll(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }
}
