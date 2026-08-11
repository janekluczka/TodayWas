package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.data.repository.HabitRepository
import pl.luczka.todaywas.domain.model.EditWindow
import pl.luczka.todaywas.domain.model.EditWindowExpiredException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class UpdateHabitCheckInUseCase @Inject constructor(
    private val repository: HabitRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        habitId: String,
        date: LocalDate,
        value: Int,
        createdAt: Instant,
    ): Result<Unit> {
        if (!EditWindow.isEditable(createdAt, clock.instant())) {
            return Result.failure(EditWindowExpiredException())
        }
        return repository.updateCheckIn(habitId, date, value)
    }
}
