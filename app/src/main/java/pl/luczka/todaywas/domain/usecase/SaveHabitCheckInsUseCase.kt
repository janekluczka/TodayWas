package pl.luczka.todaywas.domain.usecase

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.repository.HabitRepository
import pl.luczka.todaywas.domain.util.EditWindow

class SaveHabitCheckInsUseCase @Inject constructor(
    private val repository: HabitRepository,
    private val clock: Clock,
) {

    // Routes each pending value to an add or an update depending on whether that date already has
    // a check-in. Existing check-ins (and their createdAt, for the edit window) are read from the
    // repository, never taken from the caller, so stale UI state can't reopen an expired check-in.
    // Every pending value is written independently (partial success is preserved): the aggregate
    // result reports failure if any write failed, surfacing EditWindowExpiredException specifically
    // when that was the cause, while writes that did succeed are kept.
    suspend operator fun invoke(
        habitId: String,
        pending: Map<LocalDate, Int>,
    ): Result<Unit> {
        val existingByDate = repository.observeCheckIns(habitId).first().associateBy { it.date }
        val results = pending.map { (date, value) ->
            val existingCheckIn = existingByDate[date]
            if (existingCheckIn != null) {
                if (EditWindow.isEditable(existingCheckIn.createdAt, clock.instant())) {
                    repository.updateCheckIn(habitId, date, value)
                } else {
                    Result.failure(EditWindowExpiredException())
                }
            } else {
                repository.addCheckIns(date, mapOf(habitId to value))
            }
        }

        return when {
            results.all { it.isSuccess } -> Result.success(Unit)
            results.any {
                it.exceptionOrNull() is EditWindowExpiredException
            } -> Result.failure(EditWindowExpiredException())
            else -> results.first { it.isFailure }
        }
    }
}
