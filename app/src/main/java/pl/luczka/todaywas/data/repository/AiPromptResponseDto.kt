package pl.luczka.todaywas.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptResponseDto(
    val text: String,
)
