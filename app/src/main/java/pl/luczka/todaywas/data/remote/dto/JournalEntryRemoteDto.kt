package pl.luczka.todaywas.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JournalEntryRemoteDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("date") val date: String,
    @SerialName("text") val text: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)
