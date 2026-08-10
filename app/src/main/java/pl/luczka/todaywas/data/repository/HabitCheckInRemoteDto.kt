package pl.luczka.todaywas.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HabitCheckInRemoteDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("habit_id") val habitId: String,
    @SerialName("date") val date: String,
    @SerialName("value") val value: Int,
    @SerialName("created_at") val createdAt: String,
)
