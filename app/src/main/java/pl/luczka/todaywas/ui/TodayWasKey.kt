package pl.luczka.todaywas.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface TodayWasKey : NavKey

@Serializable
data object OnboardingKey : TodayWasKey

@Serializable
data object MainKey : TodayWasKey
