package pl.luczka.todaywas.core.designsystem.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class BooleanPreviewParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true, false)
}
