package pl.luczka.todaywas.domain.model

enum class JournalPromptTone(
    val level: Int,
) {
    VERY_BAD(1),
    BAD(2),
    NEUTRAL(3),
    GOOD(4),
    VERY_GOOD(5),
}
