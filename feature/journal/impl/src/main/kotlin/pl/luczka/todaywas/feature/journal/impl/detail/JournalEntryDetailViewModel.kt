package pl.luczka.todaywas.feature.journal.impl.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.luczka.todaywas.core.domain.usecase.DeleteJournalEntryUseCase
import pl.luczka.todaywas.core.domain.usecase.ObserveJournalEntryUseCase
import pl.luczka.todaywas.core.ui.mapper.toUiState
import pl.luczka.todaywas.domain.entry.usecase.IsEditableUseCase

@HiltViewModel(assistedFactory = JournalEntryDetailViewModel.Factory::class)
internal class JournalEntryDetailViewModel @AssistedInject constructor(
    @Assisted private val id: String,
    observeJournalEntry: ObserveJournalEntryUseCase,
    private val deleteJournalEntry: DeleteJournalEntryUseCase,
    private val isEditable: IsEditableUseCase,
) : ViewModel() {

    // State owned by this screen itself, as opposed to the entry it observes.
    private data class ScreenState(
        val isDeleteDialogVisible: Boolean = false,
        val isDeleting: Boolean = false,
        val deleteError: Boolean = false,
        val pendingNavigation: JournalEntryDetailNavigation? = null,
    )

    private val screenState = MutableStateFlow(ScreenState())

    // filterNotNull: a missing row (unknown id, or the moment after a delete) keeps the last
    // shown entry instead of blanking the screen while it navigates back.
    val uiState: StateFlow<JournalEntryDetailUiState> = combine(
        observeJournalEntry(id).filterNotNull(),
        screenState,
    ) { entry, screen ->
        JournalEntryDetailUiState(
            isLoading = false,
            entry = entry.toUiState(),
            isEditable = isEditable(entry.createdAt),
            isDeleteDialogVisible = screen.isDeleteDialogVisible,
            isDeleting = screen.isDeleting,
            deleteError = screen.deleteError,
            pendingNavigation = screen.pendingNavigation,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalEntryDetailUiState(
            isLoading = true,
            entry = null,
            isEditable = false,
        ),
    )

    fun onIntent(intent: JournalEntryDetailIntent) {
        when (intent) {
            JournalEntryDetailIntent.NavigationHandled -> onNavigationHandled()
            JournalEntryDetailIntent.DeleteClicked -> onDeleteClicked()
            JournalEntryDetailIntent.DeleteConfirmed -> onDeleteConfirmed()
            JournalEntryDetailIntent.DeleteDismissed -> onDeleteDismissed()
        }
    }

    private fun onNavigationHandled() {
        screenState.update { it.copy(pendingNavigation = null) }
    }

    private fun onDeleteClicked() {
        screenState.update { it.copy(isDeleteDialogVisible = true) }
    }

    private fun onDeleteDismissed() {
        screenState.update { it.copy(isDeleteDialogVisible = false) }
    }

    private fun onDeleteConfirmed() {
        if (screenState.value.isDeleting) return
        viewModelScope.launch {
            screenState.update {
                it.copy(
                    isDeleting = true,
                    deleteError = false,
                )
            }
            val result = deleteJournalEntry(id)
            if (result.isSuccess) {
                screenState.update { it.copy(pendingNavigation = JournalEntryDetailNavigation.EntryDeleted) }
            } else {
                screenState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = true,
                        isDeleteDialogVisible = false,
                    )
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted id: String): JournalEntryDetailViewModel
    }
}
