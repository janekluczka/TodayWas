package pl.luczka.todaywas.domain.usecase

import java.time.Clock
import javax.inject.Inject
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.model.JournalEntryNotFoundException
import pl.luczka.todaywas.domain.repository.JournalRepository
import pl.luczka.todaywas.domain.util.EditWindow

class UpdateJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val clock: Clock,
) {
    // Checks the edit window against the stored entry's createdAt, never a caller-supplied one, so
    // a stale or wrong timestamp held in UI state can't reopen an expired entry.
    suspend operator fun invoke(
        id: String,
        text: String,
    ): Result<Unit> {
        val stored = repository.getEntry(id)
            ?: return Result.failure(JournalEntryNotFoundException(id))
        if (!EditWindow.isEditable(stored.createdAt, clock.instant())) {
            return Result.failure(EditWindowExpiredException())
        }
        return repository.updateEntry(id, text)
    }
}
