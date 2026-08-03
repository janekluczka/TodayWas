package pl.luczka.todaywas.core.designsystem.components.pickers

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.luczka.todaywas.core.designsystem.components.cards.DsCardVariant
import pl.luczka.todaywas.core.designsystem.components.cards.DsSelectableCard
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Cards are a fixed width, purely for calendar-strip context — many more days than are ever
// selectable get rendered (however many fit the available width), all disabled except the ones
// `isSelectable` accepts. This is what keeps the selected date visually centered even when it
// sits at the edge of the "real" selectable window (e.g. YESTERDAY with nothing before it, or
// day 7 of a 7-day habit backfill window) — flanking context days fill the space either side.
// Page index is the date's epoch-day, so the pager never needs true infinite pages: today's
// epoch-day is a small positive Int for any realistic date, and Int.MAX_VALUE pages comfortably
// covers it.
private val DATE_STRIP_CARD_WIDTH = 56.dp
private val DATE_STRIP_CARD_SPACING = DsSpacing.space200

@Composable
fun DsDateStrip(
    selectedDate: LocalDate,
    isSelectable: (LocalDate) -> Boolean,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedPage = selectedDate.toEpochDay().toInt()
    val pagerState = rememberPagerState(initialPage = selectedPage) { Int.MAX_VALUE }
    LaunchedEffect(selectedPage) {
        pagerState.animateScrollToPage(selectedPage)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = DATE_STRIP_CARD_WIDTH
        val sideInset = (maxWidth - cardWidth) / 2

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            pageSize = PageSize.Fixed(cardWidth),
            pageSpacing = DATE_STRIP_CARD_SPACING,
            contentPadding = PaddingValues(horizontal = sideInset),
        ) { page ->
            val date = LocalDate.ofEpochDay(page.toLong())
            DateStripCard(
                date = date,
                selected = date == selectedDate,
                available = isSelectable(date),
                onClick = { onDateSelected(date) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DateStripCard(
    date: LocalDate,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DsSelectableCard(
        onClick = onClick,
        enabled = available,
        variant = if (selected) DsCardVariant.PRIMARY else DsCardVariant.SECONDARY,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DsText(
                text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                fontSize = 10.sp,
            )
            DsText(text = date.dayOfMonth.toString())
        }
    }
}

@PreviewLightDark
@Composable
private fun DsDateStripPreview() {
    DsTheme {
        val today = LocalDate.now()
        DsDateStrip(
            selectedDate = today,
            isSelectable = { it == today || it == today.minusDays(1) },
            onDateSelected = {},
        )
    }
}
