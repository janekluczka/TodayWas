package pl.luczka.todaywas.core.ui.mapper

import pl.luczka.todaywas.core.ui.model.LocalDataSummaryUi
import pl.luczka.todaywas.domain.sync.model.LocalDataSummary

fun LocalDataSummary.toUiState(): LocalDataSummaryUi = LocalDataSummaryUi(
    journalEntryCount = journalEntryCount,
    habitCount = habitCount,
    checkInCount = checkInCount,
)
