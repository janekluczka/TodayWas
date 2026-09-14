package pl.luczka.todaywas.ui.habit.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.chips.DsChip
import pl.luczka.todaywas.core.designsystem.components.contribution.DsContributionLevel
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.habit.HabitRow
import pl.luczka.todaywas.ui.model.HabitCheckInStatusUiState
import pl.luczka.todaywas.ui.model.HabitSortUiState
import pl.luczka.todaywas.ui.model.HabitTypeUiState
import pl.luczka.todaywas.ui.model.HabitUiState

@Composable
fun HabitListScreen(
    onBack: () -> Unit,
    onHabitClicked: (String) -> Unit,
    viewModel: HabitListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HabitListUiEvent.NavigateToDetail -> onHabitClicked(event.habitId)
            }
        }
    }

    HabitListScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@Composable
private fun HabitListScreenContent(
    uiState: HabitListUiState,
    onIntent: (HabitListIntent) -> Unit,
    onBack: () -> Unit,
) {
    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.habit_list_title),
                navigationIcon = {
                    DsIconButton(onClick = onBack) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SortChipRow(
                selected = uiState.selectedSort,
                onSortSelected = { onIntent(HabitListIntent.SortSelected(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!uiState.isLoading && uiState.habits.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.space400),
                ) {
                    DsText(
                        text = stringResource(R.string.main_habit_empty_state),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(uiState.habits) { habit ->
                        HabitRow(
                            habit = habit,
                            onClick = { onIntent(HabitListIntent.HabitClicked(habit)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChipRow(
    selected: HabitSortUiState,
    onSortSelected: (HabitSortUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.space200),
        contentPadding = PaddingValues(
            horizontal = DsSpacing.space400,
            vertical = DsSpacing.space200,
        ),
        modifier = modifier,
    ) {
        items(HabitSortUiState.entries) { sort ->
            DsChip(
                text = stringResource(sort.labelRes()),
                selected = sort == selected,
                onClick = { onSortSelected(sort) },
            )
        }
    }
}

private fun HabitSortUiState.labelRes(): Int = when (this) {
    HabitSortUiState.RECENTLY_CHECKED_IN -> R.string.habit_sort_recently_checked_in
    HabitSortUiState.ALPHABETICAL -> R.string.habit_sort_alphabetical
    HabitSortUiState.DATE_CREATED -> R.string.habit_sort_date_created
}

private class HabitListUiStatePreviewProvider : PreviewParameterProvider<HabitListUiState> {
    override val values = sequenceOf(
        HabitListUiState(isLoading = true),
        HabitListUiState(isLoading = false, habits = emptyList()),
        HabitListUiState(
            isLoading = false,
            habits = listOf(
                HabitUiState(
                    id = "1",
                    name = "Drink water",
                    type = HabitTypeUiState.BINARY,
                    todayStatus = HabitCheckInStatusUiState.NotLogged,
                    todayLevel = DsContributionLevel.NONE,
                ),
                HabitUiState(
                    id = "2",
                    name = "Run",
                    type = HabitTypeUiState.BINARY,
                    todayStatus = HabitCheckInStatusUiState.LoggedBinary(done = true),
                    todayLevel = DsContributionLevel.LEVEL_5,
                ),
                HabitUiState(
                    id = "3",
                    name = "Mood",
                    type = HabitTypeUiState.SCALE,
                    todayStatus = HabitCheckInStatusUiState.LoggedScale(value = 3),
                    todayLevel = DsContributionLevel.LEVEL_3,
                ),
            ),
        ),
    )
}

@PreviewLightDark
@Composable
private fun HabitListScreenPreview(
    @PreviewParameter(HabitListUiStatePreviewProvider::class) state: HabitListUiState,
) {
    DsTheme {
        HabitListScreenContent(
            uiState = state,
            onIntent = {},
            onBack = {},
        )
    }
}
