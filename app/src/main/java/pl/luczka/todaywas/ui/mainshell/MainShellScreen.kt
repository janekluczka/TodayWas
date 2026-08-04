package pl.luczka.todaywas.ui.mainshell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.navigation.DsNavigationBar
import pl.luczka.todaywas.core.designsystem.components.navigation.DsNavigationBarItem
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.ui.main.MainScreen
import pl.luczka.todaywas.ui.model.JournalEntryUiState

@Composable
fun MainShellScreen(
    onAddEntryClicked: () -> Unit,
    onJournalEntryClicked: (JournalEntryUiState) -> Unit,
    onCreateHabitClicked: () -> Unit,
    onLogCheckInsClicked: () -> Unit,
    onHabitClicked: (Long) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(BottomNavTab.HOME) }

    MainShellScaffold(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
    ) {
        when (selectedTab) {
            BottomNavTab.HOME -> MainScreen(
                onAddEntryClicked = onAddEntryClicked,
                onJournalEntryClicked = onJournalEntryClicked,
                onCreateHabitClicked = onCreateHabitClicked,
                onLogCheckInsClicked = onLogCheckInsClicked,
                onHabitClicked = onHabitClicked,
            )
            BottomNavTab.JOURNAL -> TabPlaceholder(stringResource(R.string.main_journal_tab_placeholder))
            BottomNavTab.HABITS -> TabPlaceholder(stringResource(R.string.main_habit_tab_placeholder))
            BottomNavTab.PREFERENCES -> Unit
        }
    }
}

@Composable
private fun MainShellScaffold(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    content: @Composable () -> Unit,
) {
    DsScaffold(
        bottomBar = {
            DsNavigationBar {
                for (tab in BottomNavTab.entries) {
                    DsNavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { DsIcon(imageVector = tab.icon(), contentDescription = tab.label()) },
                        label = { DsText(text = tab.label()) },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            content()
        }
    }
}

@Composable
private fun TabPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        DsText(text = text)
    }
}

private enum class BottomNavTab {
    HOME,
    JOURNAL,
    HABITS,
    PREFERENCES,
}

@Composable
private fun BottomNavTab.label(): String = when (this) {
    BottomNavTab.HOME -> stringResource(R.string.bottom_nav_home_label)
    BottomNavTab.JOURNAL -> stringResource(R.string.bottom_nav_journal_label)
    BottomNavTab.HABITS -> stringResource(R.string.bottom_nav_habits_label)
    BottomNavTab.PREFERENCES -> stringResource(R.string.bottom_nav_preferences_label)
}

private fun BottomNavTab.icon(): ImageVector = when (this) {
    BottomNavTab.HOME -> Icons.Default.Home
    BottomNavTab.JOURNAL -> Icons.Default.Edit
    BottomNavTab.HABITS -> Icons.Default.CheckCircle
    BottomNavTab.PREFERENCES -> Icons.Default.Person
}

private class BottomNavTabPreviewProvider : PreviewParameterProvider<BottomNavTab> {
    override val values = sequenceOf(BottomNavTab.HOME, BottomNavTab.PREFERENCES)
}

@PreviewLightDark
@Composable
private fun MainShellScaffoldPreview(
    @PreviewParameter(BottomNavTabPreviewProvider::class) selectedTab: BottomNavTab,
) {
    DsTheme {
        MainShellScaffold(
            selectedTab = selectedTab,
            onTabSelected = {},
        ) {
            TabPlaceholder(stringResource(R.string.main_journal_tab_placeholder))
        }
    }
}
