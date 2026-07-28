package pl.luczka.todaywas.ui.model

import pl.luczka.todaywas.domain.model.Focus

fun Focus.toUiState(): FocusUiState =
    when (this) {
        Focus.JOURNAL -> FocusUiState.JOURNAL
        Focus.HABIT -> FocusUiState.HABIT
        Focus.BOTH -> FocusUiState.BOTH
    }

fun FocusUiState.toDomain(): Focus =
    when (this) {
        FocusUiState.JOURNAL -> Focus.JOURNAL
        FocusUiState.HABIT -> Focus.HABIT
        FocusUiState.BOTH -> Focus.BOTH
    }
