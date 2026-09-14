package pl.luczka.todaywas.ui.journal.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import pl.luczka.todaywas.core.designsystem.components.buttons.DsTextButton
import pl.luczka.todaywas.core.designsystem.components.dialogs.DsAlertDialog
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.theme.DsTheme
import pl.luczka.todaywas.core.designsystem.tokens.DsSpacing
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import java.time.Instant
import java.time.LocalDate

@Composable
fun JournalEntryDetailScreen(
    id: String,
    onBack: () -> Unit,
    onEditClicked: () -> Unit,
    viewModel: JournalEntryDetailViewModel =
        hiltViewModel<JournalEntryDetailViewModel, JournalEntryDetailViewModel.Factory> { factory ->
            factory.create(id)
        },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onIntent(JournalEntryDetailIntent.ScreenEntered)
        viewModel.events.collect { event ->
            when (event) {
                JournalEntryDetailUiEvent.NavigatedBack -> onBack()
                JournalEntryDetailUiEvent.NavigateToEdit -> onEditClicked()
            }
        }
    }

    JournalEntryDetailScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun JournalEntryDetailScreenContent(
    uiState: JournalEntryDetailUiState,
    onIntent: (JournalEntryDetailIntent) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteErrorMessage = stringResource(R.string.journal_detail_delete_error)
    LaunchedEffect(uiState.deleteError) {
        if (uiState.deleteError) {
            snackbarHostState.showSnackbar(deleteErrorMessage)
        }
    }

    DsScaffold(
        topBar = {
            DsTopBar(
                title = uiState.entry?.formattedDate
                    ?: stringResource(R.string.journal_detail_title),
                navigationIcon = {
                    DsIconButton(onClick = { onIntent(JournalEntryDetailIntent.BackClicked) }) {
                        DsIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                        )
                    }
                },
                actions = {
                    JournalEntryDetailActions(uiState, onIntent)
                },
            )
        },
        snackbarHost = { DsSnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(DsSpacing.space600),
        ) {
            if (uiState.entry != null) {
                DsText(text = uiState.entry.text)
            }
        }
    }

    if (uiState.isDeleteDialogVisible) {
        DeleteEntryDialog(onIntent = onIntent)
    }
}

@Composable
private fun JournalEntryDetailActions(
    uiState: JournalEntryDetailUiState,
    onIntent: (JournalEntryDetailIntent) -> Unit,
) {
    if (uiState.isEditable) {
        DsIconButton(onClick = { onIntent(JournalEntryDetailIntent.EditClicked) }) {
            DsIcon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.journal_detail_edit_action),
            )
        }
    }
    DsIconButton(onClick = { onIntent(JournalEntryDetailIntent.DeleteClicked) }) {
        DsIcon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.journal_detail_delete_action),
        )
    }
}

@Composable
private fun DeleteEntryDialog(
    onIntent: (JournalEntryDetailIntent) -> Unit,
) {
    DsAlertDialog(
        onDismissRequest = { onIntent(JournalEntryDetailIntent.DeleteDismissed) },
        title = { DsText(text = stringResource(R.string.journal_detail_delete_dialog_title)) },
        text = { DsText(text = stringResource(R.string.journal_detail_delete_dialog_text)) },
        confirmButton = {
            DsTextButton(
                text = stringResource(R.string.journal_detail_delete_confirm_cta),
                onClick = { onIntent(JournalEntryDetailIntent.DeleteConfirmed) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            )
        },
        dismissButton = {
            DsTextButton(
                text = stringResource(R.string.journal_detail_delete_cancel_cta),
                onClick = { onIntent(JournalEntryDetailIntent.DeleteDismissed) },
            )
        },
    )
}

private class JournalEntryDetailScreenPreviewStateProvider :
    PreviewParameterProvider<JournalEntryDetailUiState> {
    override val values = sequenceOf(
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = "1",
                date = LocalDate.of(2026, 7, 27),
                formattedDate = "Jul 27, 2026",
                text = "Today was a good day. I went for a walk and read a book.",
                createdAt = Instant.now(),
            ),
            isEditable = true,
        ),
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = "1",
                date = LocalDate.of(2026, 7, 20),
                formattedDate = "Jul 20, 2026",
                text = "An older entry, no longer editable.",
                createdAt = Instant.now(),
            ),
            isEditable = false,
        ),
    )
}

@PreviewLightDark
@Composable
private fun JournalEntryDetailScreenPreview(
    @PreviewParameter(JournalEntryDetailScreenPreviewStateProvider::class) state:
        JournalEntryDetailUiState,
) {
    DsTheme {
        JournalEntryDetailScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DeleteEntryDialogPreview() {
    DsTheme {
        DeleteEntryDialog(onIntent = {})
    }
}
