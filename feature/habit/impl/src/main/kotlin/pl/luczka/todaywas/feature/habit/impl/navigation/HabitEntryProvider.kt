package pl.luczka.todaywas.feature.habit.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import pl.luczka.todaywas.core.navigation.Navigator
import pl.luczka.todaywas.feature.habit.api.navigation.CreateHabitKey
import pl.luczka.todaywas.feature.habit.api.navigation.HabitDetailKey
import pl.luczka.todaywas.feature.habit.api.navigation.HabitListKey
import pl.luczka.todaywas.feature.habit.api.navigation.LogHabitCheckInsKey
import pl.luczka.todaywas.feature.habit.api.navigation.navigateToHabitDetail
import pl.luczka.todaywas.feature.habit.impl.create.CreateHabitScreen
import pl.luczka.todaywas.feature.habit.impl.detail.HabitDetailScreen
import pl.luczka.todaywas.feature.habit.impl.list.HabitListScreen
import pl.luczka.todaywas.feature.habit.impl.logcheckin.LogHabitCheckInsScreen

fun EntryProviderScope<NavKey>.habitEntries(navigator: Navigator) {
    entry<HabitListKey> {
        HabitListScreen(
            onBack = navigator::goBack,
            onHabitClick = navigator::navigateToHabitDetail,
        )
    }
    entry<CreateHabitKey> {
        CreateHabitScreen(
            onSaveComplete = navigator::goBack,
            onCancel = navigator::goBack,
        )
    }
    entry<LogHabitCheckInsKey> {
        LogHabitCheckInsScreen(
            onSaveComplete = navigator::goBack,
            onCancel = navigator::goBack,
        )
    }
    entry<HabitDetailKey> { key ->
        HabitDetailScreen(
            habitId = key.habitId,
            onBack = navigator::goBack,
        )
    }
}
