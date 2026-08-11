package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.domain.model.LocalDataSummary

fun LocalDataSummary.toUiState(): LocalDataSummaryUi = LocalDataSummaryUi(
    journalEntryCount = journalEntryCount,
    habitCount = habitCount,
    checkInCount = checkInCount,
)
