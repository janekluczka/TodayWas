package pl.luczka.todaywas.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HabitRemoteDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String?,
    @SerialName("type") val type: String,
    @SerialName("scale_min") val scaleMin: Int?,
    @SerialName("scale_max") val scaleMax: Int?,
    @SerialName("created_at") val createdAt: String,
)
