package pl.luczka.todaywas.domain.model

data class LocalDataSummary(
    val journalEntryCount: Int,
    val habitCount: Int,
    val checkInCount: Int,
) {
    val isEmpty: Boolean
        get() = journalEntryCount == 0 && habitCount == 0 && checkInCount == 0
}
