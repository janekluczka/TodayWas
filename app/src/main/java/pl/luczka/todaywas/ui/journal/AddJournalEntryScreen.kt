package pl.luczka.todaywas.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.TodayWasButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.TodayWasIcon
import pl.luczka.todaywas.core.designsystem.components.TodayWasIconButton
import pl.luczka.todaywas.core.designsystem.components.TodayWasScaffold
import pl.luczka.todaywas.core.designsystem.components.TodayWasSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.TodayWasText
import pl.luczka.todaywas.core.designsystem.components.TodayWasTextField
import pl.luczka.todaywas.core.designsystem.components.TodayWasTopBar
import pl.luczka.todaywas.ui.model.JournalDateSlotUiState
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AddJournalEntryScreen(
    availableSlots: List<JournalDateSlotUiState>,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: AddJournalEntryViewModel = hiltViewModel<AddJournalEntryViewModel, AddJournalEntryViewModel.Factory> { factory ->
        factory.create(availableSlots)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AddJournalEntryUiEvent.Saved -> onSaved()
                AddJournalEntryUiEvent.Cancelled -> onCancelled()
            }
        }
    }

    AddJournalEntryScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun AddJournalEntryScreenContent(
    uiState: AddJournalEntryUiState,
    onIntent: (AddJournalEntryIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.journal_add_entry_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    TodayWasScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TodayWasTopBar(
                title = stringResource(R.string.journal_add_entry_title),
                navigationIcon = {
                    TodayWasIconButton(onClick = { onIntent(AddJournalEntryIntent.CancelClicked) }) {
                        TodayWasIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    TodayWasButtonWithLoading(
                        text = stringResource(R.string.journal_add_entry_save_cta),
                        onClick = { onIntent(AddJournalEntryIntent.SaveClicked) },
                        enabled = !uiState.isSaving && uiState.text.isNotBlank(),
                        loading = uiState.isSaving,
                    )
                },
            )
        },
        snackbarHost = { TodayWasSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DayStrip(
                uiState = uiState,
                onIntent = onIntent,
            )
            TodayWasTextField(
                value = uiState.text,
                onValueChange = { onIntent(AddJournalEntryIntent.TextChanged(it)) },
                label = stringResource(R.string.journal_add_entry_text_label),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp),
                minLines = 6,
            )
        }
    }
}

// Cards are a fixed width, purely for calendar-strip context — only the TODAY/YESTERDAY
// cards are ever actually selectable. However many fit the available width is fine;
// partial/hidden cards at the edges are expected, not a bug. Page index is the date's
// epoch-day, so the pager never needs true infinite pages: today's epoch-day is a small
// positive Int for any realistic date, and Int.MAX_VALUE pages comfortably covers it.
private val DAY_STRIP_CARD_WIDTH = 56.dp

@Composable
private fun DayStrip(
    uiState: AddJournalEntryUiState,
    onIntent: (AddJournalEntryIntent) -> Unit,
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val selectedDate = if (uiState.selectedSlot == JournalDateSlotUiState.TODAY) today else yesterday
    val selectedPage = selectedDate.toEpochDay().toInt()

    val pagerState = rememberPagerState(initialPage = selectedPage) { Int.MAX_VALUE }
    LaunchedEffect(selectedPage) {
        pagerState.animateScrollToPage(selectedPage)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = DAY_STRIP_CARD_WIDTH
        val sideInset = (maxWidth - cardWidth) / 2

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            pageSize = PageSize.Fixed(cardWidth),
            contentPadding = PaddingValues(horizontal = sideInset),
        ) { page ->
            val date = LocalDate.ofEpochDay(page.toLong())
            val slot =
                when (date) {
                    today -> JournalDateSlotUiState.TODAY
                    yesterday -> JournalDateSlotUiState.YESTERDAY
                    else -> null
                }
            val available = slot != null && slot in uiState.availableSlots
            DayCard(
                date = date,
                selected = date == selectedDate,
                available = available,
                onClick = { slot?.let { onIntent(AddJournalEntryIntent.SlotSelected(it)) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            available -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when {
            selected -> MaterialTheme.colorScheme.onPrimary
            available -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .let { if (available) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TodayWasText(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            color = contentColor,
            fontSize = 10.sp,
        )
        TodayWasText(text = date.dayOfMonth.toString(), color = contentColor)
    }
}

private class AddJournalEntryScreenPreviewStateProvider : PreviewParameterProvider<AddJournalEntryUiState> {
    override val values = sequenceOf(
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "",
            isSaving = false,
            saveError = false,
        ),
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "Today was a good day.",
            isSaving = false,
            saveError = false,
        ),
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY, JournalDateSlotUiState.YESTERDAY),
            selectedSlot = JournalDateSlotUiState.YESTERDAY,
            text = "Today was a good day.",
            isSaving = true,
            saveError = false,
        ),
        AddJournalEntryUiState(
            availableSlots = listOf(JournalDateSlotUiState.TODAY),
            selectedSlot = JournalDateSlotUiState.TODAY,
            text = "Today was a good day.",
            isSaving = false,
            saveError = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun AddJournalEntryScreenPreview(
    @PreviewParameter(AddJournalEntryScreenPreviewStateProvider::class) state: AddJournalEntryUiState,
) {
    TodayWasTheme {
        AddJournalEntryScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
