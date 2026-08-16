package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

class LogHabitCheckInsUseCase @Inject constructor(
    private val repository: HabitRepository,
) {

    suspend operator fun invoke(
        date: LocalDate,
        values: Map<String, Int>,
    ): Result<Unit> = repository.addCheckIns(date, values)
}
