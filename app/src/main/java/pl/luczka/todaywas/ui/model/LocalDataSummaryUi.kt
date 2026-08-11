package pl.luczka.todaywas.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class LocalDataSummaryUi(
    val journalEntryCount: Int,
    val habitCount: Int,
    val checkInCount: Int,
)
