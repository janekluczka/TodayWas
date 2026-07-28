package pl.luczka.todaywas.ui.model

import androidx.annotation.StringRes
import pl.luczka.todaywas.R

enum class FabActionUiState(
    @StringRes val labelRes: Int,
) {
    ADD_JOURNAL_ENTRY(R.string.main_fab_add_journal),
}
