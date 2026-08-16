package pl.luczka.todaywas.ui.mapper

import pl.luczka.todaywas.domain.model.LocalDataSummary
import pl.luczka.todaywas.ui.model.LocalDataSummaryUi

fun LocalDataSummary.toUiState(): LocalDataSummaryUi = LocalDataSummaryUi(
    journalEntryCount = journalEntryCount,
    habitCount = habitCount,
    checkInCount = checkInCount,
)
