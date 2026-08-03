package pl.luczka.todaywas.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun DsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { DsText(text = title) },
        navigationIcon = navigationIcon,
        actions = { actions() },
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DsTopBarPreview(
    @PreviewParameter(BooleanPreviewParameterProvider::class) withAction: Boolean,
) {
    DesignSystemPreviewTheme {
        DsTopBar(
            title = "TodayWas",
            navigationIcon = {
                if (withAction) {
                    DsIconButton(onClick = {}) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                }
            },
            actions = {
                if (withAction) {
                    DsIconButton(onClick = {}) {
                        DsIcon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change focus",
                        )
                    }
                }
            },
        )
    }
}
