package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.repository.JournalRepository
import pl.luczka.todaywas.domain.util.EditWindow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class UpdateJournalEntryUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        id: String,
        text: String,
        createdAt: Instant,
    ): Result<Unit> {
        if (!EditWindow.isEditable(createdAt, clock.instant())) {
            return Result.failure(EditWindowExpiredException())
        }
        return repository.updateEntry(id, text)
    }
}
