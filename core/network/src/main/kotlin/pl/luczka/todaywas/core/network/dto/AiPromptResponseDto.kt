package pl.luczka.todaywas.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptResponseDto(
    val text: String,
    val remaining: Int,
)
