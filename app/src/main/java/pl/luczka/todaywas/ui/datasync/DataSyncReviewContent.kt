package pl.luczka.todaywas.ui.datasync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.LocalDataSummaryUi

@Composable
fun DataSyncReviewContent(
    summary: LocalDataSummaryUi,
    isSyncing: Boolean,
    onConfirmClicked: () -> Unit,
    onSkipClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space400),
        modifier = modifier.fillMaxWidth(),
    ) {
        DsText(text = stringResource(R.string.data_sync_review_title))
        DsText(
            text = stringResource(
                R.string.data_sync_review_summary_format,
                summary.journalEntryCount,
                summary.habitCount,
                summary.checkInCount,
            ),
        )
        DsButtonWithLoading(
            text = stringResource(R.string.data_sync_review_confirm_cta),
            onClick = onConfirmClicked,
            loading = isSyncing,
            modifier = Modifier.fillMaxWidth(),
        )
        DsTextButton(
            text = stringResource(R.string.data_sync_review_skip_cta),
            onClick = onSkipClicked,
            enabled = !isSyncing,
        )
    }
}

private data class DataSyncReviewPreviewState(
    val summary: LocalDataSummaryUi,
    val isSyncing: Boolean,
)

private class DataSyncReviewContentPreviewProvider :
    PreviewParameterProvider<DataSyncReviewPreviewState> {
    override val values = sequenceOf(
        DataSyncReviewPreviewState(
            summary = LocalDataSummaryUi(journalEntryCount = 12, habitCount = 3, checkInCount = 40),
            isSyncing = false,
        ),
        DataSyncReviewPreviewState(
            summary = LocalDataSummaryUi(journalEntryCount = 12, habitCount = 3, checkInCount = 40),
            isSyncing = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun DataSyncReviewContentPreview(
    @PreviewParameter(DataSyncReviewContentPreviewProvider::class) state:
        DataSyncReviewPreviewState,
) {
    DsTheme {
        DataSyncReviewContent(
            summary = state.summary,
            isSyncing = state.isSyncing,
            onConfirmClicked = {},
            onSkipClicked = {},
        )
    }
}
