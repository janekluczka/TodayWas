package pl.luczka.todaywas.ui.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.luczka.todaywas.R
import pl.luczka.todaywas.core.designsystem.components.appbars.DsTopBar
import pl.luczka.todaywas.core.designsystem.components.buttons.DsButtonWithLoading
import pl.luczka.todaywas.core.designsystem.components.buttons.DsIconButton
import pl.luczka.todaywas.core.designsystem.components.icons.DsIcon
import pl.luczka.todaywas.core.designsystem.components.layout.DsScaffold
import pl.luczka.todaywas.core.designsystem.components.snackbar.DsSnackbarHost
import pl.luczka.todaywas.core.designsystem.components.text.DsText
import pl.luczka.todaywas.core.designsystem.components.textfields.DsTextField
import pl.luczka.todaywas.ui.model.JournalEntryUiState
import pl.luczka.todaywas.ui.theme.TodayWasTheme
import java.time.Instant
import java.time.LocalDate

@Composable
fun JournalEntryDetailScreen(
    id: Long,
    onBack: () -> Unit,
    viewModel: JournalEntryDetailViewModel = hiltViewModel<JournalEntryDetailViewModel, JournalEntryDetailViewModel.Factory> { factory ->
        factory.create(id)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                JournalEntryDetailUiEvent.NavigatedBack -> onBack()
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
    val genericErrorMessage = stringResource(R.string.journal_detail_error)
    val expiredErrorMessage = stringResource(R.string.journal_detail_edit_window_expired_error)
    LaunchedEffect(uiState.saveError) {
        if (uiState.saveError) {
            val message = if (uiState.isEditable) genericErrorMessage else expiredErrorMessage
            snackbarHostState.showSnackbar(message)
        }
    }

    DsScaffold(
        topBar = {
            DsTopBar(
                title = stringResource(R.string.journal_detail_title),
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
                .padding(24.dp),
        ) {
            if (uiState.entry != null) {
                DsText(text = uiState.entry.formattedDate)
                if (uiState.isEditing) {
                    DsTextField(
                        value = uiState.editedText,
                        onValueChange = { onIntent(JournalEntryDetailIntent.TextChanged(it)) },
                        minLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                } else {
                    DsText(text = uiState.entry.text)
                }
            }
        }
    }
}

@Composable
private fun JournalEntryDetailActions(
    uiState: JournalEntryDetailUiState,
    onIntent: (JournalEntryDetailIntent) -> Unit,
) {
    if (uiState.isEditing) {
        Row {
            DsIconButton(onClick = { onIntent(JournalEntryDetailIntent.CancelEditClicked) }) {
                DsIcon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.journal_detail_cancel_edit_action),
                )
            }
            DsButtonWithLoading(
                text = stringResource(R.string.journal_detail_save_cta),
                onClick = { onIntent(JournalEntryDetailIntent.SaveClicked) },
                enabled = !uiState.isSaving,
                loading = uiState.isSaving,
            )
        }
    } else if (uiState.isEditable) {
        DsIconButton(onClick = { onIntent(JournalEntryDetailIntent.EditClicked) }) {
            DsIcon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.journal_detail_edit_action),
            )
        }
    }
}

private class JournalEntryDetailScreenPreviewStateProvider : PreviewParameterProvider<JournalEntryDetailUiState> {
    override val values = sequenceOf(
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = 1L,
                date = LocalDate.of(2026, 7, 27),
                formattedDate = "Jul 27, 2026",
                text = "Today was a good day. I went for a walk and read a book.",
                createdAt = Instant.now(),
            ),
            editedText = "Today was a good day. I went for a walk and read a book.",
            isEditable = true,
            isEditing = false,
            isSaving = false,
            saveError = false,
        ),
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = 1L,
                date = LocalDate.of(2026, 7, 27),
                formattedDate = "Jul 27, 2026",
                text = "Today was a good day.",
                createdAt = Instant.now(),
            ),
            editedText = "Today was a great day after all.",
            isEditable = true,
            isEditing = true,
            isSaving = false,
            saveError = false,
        ),
        JournalEntryDetailUiState(
            isLoading = false,
            entry = JournalEntryUiState(
                id = 1L,
                date = LocalDate.of(2026, 7, 20),
                formattedDate = "Jul 20, 2026",
                text = "An older entry, no longer editable.",
                createdAt = Instant.now(),
            ),
            editedText = "An older entry, no longer editable.",
            isEditable = false,
            isEditing = false,
            isSaving = false,
            saveError = false,
        ),
    )
}

@PreviewLightDark
@Composable
private fun JournalEntryDetailScreenPreview(
    @PreviewParameter(JournalEntryDetailScreenPreviewStateProvider::class) state: JournalEntryDetailUiState,
) {
    TodayWasTheme {
        JournalEntryDetailScreenContent(
            uiState = state,
            onIntent = {},
        )
    }
}
