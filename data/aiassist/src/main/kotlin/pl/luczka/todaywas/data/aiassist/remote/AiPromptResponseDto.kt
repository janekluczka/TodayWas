package pl.luczka.todaywas.data.aiassist.remote

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptResponseDto(
    val text: String,
    val remaining: Int,
)
