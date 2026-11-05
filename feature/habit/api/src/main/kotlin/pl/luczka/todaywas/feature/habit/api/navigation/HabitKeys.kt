package pl.luczka.todaywas.feature.habit.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import pl.luczka.todaywas.core.navigation.Navigator

@Serializable
data object HabitListKey : NavKey

@Serializable
data object CreateHabitKey : NavKey

@Serializable
data object LogHabitCheckInsKey : NavKey

@Serializable
data class HabitDetailKey(
    val habitId: String,
) : NavKey

fun Navigator.navigateToHabitList() = navigate(HabitListKey)

fun Navigator.navigateToCreateHabit() = navigate(CreateHabitKey)

fun Navigator.navigateToLogHabitCheckIns() = navigate(LogHabitCheckInsKey)

fun Navigator.navigateToHabitDetail(habitId: String) = navigate(HabitDetailKey(habitId))
