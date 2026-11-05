package pl.luczka.todaywas.core.ui.mapper

import pl.luczka.todaywas.core.domain.model.LocalDataSummary
import pl.luczka.todaywas.core.ui.model.LocalDataSummaryUi

fun LocalDataSummary.toUiState(): LocalDataSummaryUi = LocalDataSummaryUi(
    journalEntryCount = journalEntryCount,
    habitCount = habitCount,
    checkInCount = checkInCount,
)
