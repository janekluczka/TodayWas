package pl.luczka.todaywas.core.ui.model

import androidx.annotation.StringRes
import pl.luczka.todaywas.core.ui.R

enum class FabActionUiState(
    @StringRes val labelRes: Int,
) {
    ADD_JOURNAL_ENTRY(R.string.main_fab_add_journal),
    CREATE_HABIT(R.string.main_fab_create_habit),
    LOG_HABIT_CHECK_INS(R.string.main_fab_log_check_ins),
}
