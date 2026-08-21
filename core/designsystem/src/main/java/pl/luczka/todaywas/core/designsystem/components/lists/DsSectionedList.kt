package pl.luczka.todaywas.core.designsystem.components.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.core.designsystem.components.cards.DsCard
import pl.luczka.todaywas.core.designsystem.components.dividers.DsHorizontalDivider
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing

private const val SKELETON_ROW_COUNT = 3

// A titled section: up to a handful of rows in one bordered card, divided, with an optional
// trailing "view all" row. Built for the small, caller-capped lists this ships with today (Main's
// 5-item sections) — not a LazyColumn, so it isn't meant for long/unbounded lists; use one
// directly for those instead.
//
// This component only ever renders a non-empty `items` list (or its loading skeleton) — it has no
// concept of "empty". Callers branch around it entirely and show their own empty-state content
// when their list is empty.
//
// `title` is optional: omit it when the caller already renders its own header above this
// component (e.g. a section that also shows other content, like a chart, under the same header).
@Composable
fun <T> DsSectionedList(
    items: List<T>,
    isLoading: Boolean,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onViewAllClicked: (() -> Unit)? = null,
    viewAllLabel: String? = null,
) {
    Column(modifier = modifier) {
        if (title != null) {
            DsText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = DsSpacing.space200),
            )
        }
        DsCard {
            when {
                isLoading -> repeat(SKELETON_ROW_COUNT) { index ->
                    SkeletonRow()
                    if (index < SKELETON_ROW_COUNT - 1) DsHorizontalDivider()
                }
                else -> items.forEachIndexed { index, item ->
                    itemContent(item)
                    if (index < items.lastIndex || onViewAllClicked != null) DsHorizontalDivider()
                }
            }
            if (!isLoading && onViewAllClicked != null && viewAllLabel != null) {
                ViewAllRow(label = viewAllLabel, onClick = onViewAllClicked)
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    Column(
        verticalArrangement = Arrangement.spacedBy(DsSpacing.space200),
        modifier = Modifier
            .fillMaxWidth()
            .padding(DsSpacing.space400),
    ) {
        SkeletonBar(widthFraction = 0.4f)
        SkeletonBar(widthFraction = 0.8f)
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(DsSpacing.space400)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(DsSpacing.space100),
            ),
    )
}

@Composable
private fun ViewAllRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(DsSpacing.space400),
    ) {
        DsText(text = label, color = MaterialTheme.colorScheme.primary)
        DsIcon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

@Composable
private fun PreviewRow(text: String) {
    DsText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(DsSpacing.space400),
    )
}

private enum class DsSectionedListPreviewState {
    POPULATED_WITH_VIEW_ALL,
    POPULATED_NO_VIEW_ALL,
    LOADING,
}

private class DsSectionedListPreviewProvider :
    PreviewParameterProvider<DsSectionedListPreviewState> {
    override val values = DsSectionedListPreviewState.entries.asSequence()
}

@PreviewLightDark
@Composable
private fun DsSectionedListPreview(
    @PreviewParameter(DsSectionedListPreviewProvider::class) state: DsSectionedListPreviewState,
) {
    DsTheme {
        when (state) {
            DsSectionedListPreviewState.POPULATED_WITH_VIEW_ALL -> DsSectionedList(
                title = "Journal",
                items = listOf("Today was a good day.", "Feeling productive."),
                isLoading = false,
                itemContent = { PreviewRow(it) },
                onViewAllClicked = {},
                viewAllLabel = "View all",
            )
            DsSectionedListPreviewState.POPULATED_NO_VIEW_ALL -> DsSectionedList(
                title = "Habits",
                items = listOf("Drink water", "Mood"),
                isLoading = false,
                itemContent = { PreviewRow(it) },
            )
            DsSectionedListPreviewState.LOADING -> DsSectionedList(
                title = "Journal",
                items = emptyList<String>(),
                isLoading = true,
                itemContent = { PreviewRow(it) },
            )
        }
    }
}
