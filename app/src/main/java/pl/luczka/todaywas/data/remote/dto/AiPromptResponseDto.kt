package pl.luczka.todaywas.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptResponseDto(
    val text: String,
)
