package pl.luczka.todaywas.ui.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasIcon
import pl.luczka.todaywas.core.designsystem.components.TodayWasIconButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun JournalEntryDetailScreen(
    date: String,
    text: String,
    onBack: () -> Unit,
) {
    TodayWasScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TodayWasTopBar(
                title = stringResource(R.string.journal_detail_title),
                navigationIcon = {
                    TodayWasIconButton(onClick = onBack) {
                        TodayWasIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            TodayWasText(text = LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            TodayWasText(text = text)
        }
    }
}

@PreviewLightDark
@Composable
private fun JournalEntryDetailScreenPreview() {
    TodayWasTheme {
        JournalEntryDetailScreen(
            date = "2026-07-27",
            text = "Today was a good day. I went for a walk and read a book.",
            onBack = {},
        )
    }
}
