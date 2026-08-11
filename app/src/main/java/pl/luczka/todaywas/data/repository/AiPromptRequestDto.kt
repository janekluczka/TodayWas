package pl.luczka.todaywas.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptRequestDto(
    val tone: Int,
    val thoughts: String? = null,
)
