package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import pl.luczka.todaywas.core.designsystem.preview.BooleanPreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.preview.DesignSystemPreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayWasTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { TodayWasText(text = title) },
        modifier = modifier,
        actions = { actions() },
    )
}

@PreviewLightDark
@Composable
private fun TodayWasTopBarPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) withAction: Boolean,
) {
    DesignSystemPreviewTheme {
        TodayWasTopBar(
            title = "TodayWas",
            actions = {
                if (withAction) {
                    TodayWasIconButton(onClick = {}) {
                        TodayWasIcon(imageVector = Icons.Default.Edit, contentDescription = "Change focus")
                    }
                }
            },
        )
    }
}
