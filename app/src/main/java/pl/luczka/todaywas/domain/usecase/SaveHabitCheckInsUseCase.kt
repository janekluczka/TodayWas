package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import pl.luczka.todaywas.domain.model.HabitCheckIn
import pl.luczka.todaywas.domain.repository.HabitRepository
import pl.luczka.todaywas.domain.util.EditWindow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class SaveHabitCheckInsUseCase @Inject constructor(
    private val repository: HabitRepository,
    private val clock: Clock,
) {

    // Routes each pending value to an add or an update depending on whether that date already has
    // a check-in, mirroring what LogHabitCheckInsUseCase/UpdateHabitCheckInUseCase each do on their
    // own - this use case depends on the repository directly rather than on either of them, since a
    // use case must never depend on another use case. Every pending value is written independently
    // (partial success is preserved, matching prior behavior): the aggregate result reports failure
    // if any write failed, surfacing EditWindowExpiredException specifically when that was the
    // cause, while writes that did succeed are kept.
    suspend operator fun invoke(
        habitId: String,
        existing: List<HabitCheckIn>,
        pending: Map<LocalDate, Int>,
    ): Result<Unit> {
        val existingByDate = existing.associateBy { it.date }
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
            results.any { it.exceptionOrNull() is EditWindowExpiredException } -> Result.failure(EditWindowExpiredException())
            else -> results.first { it.isFailure }
        }
    }
}
