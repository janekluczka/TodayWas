package pl.luczka.todaywas.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptRequestDto(
    val tone: Int,
    val thoughts: String? = null,
    val text: String? = null,
)
