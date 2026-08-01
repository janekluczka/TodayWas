package pl.luczka.todaywas.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.luczka.todaywas.data.repository.HabitRepository
import pl.luczka.todaywas.domain.model.HabitCheckInBoard
import javax.inject.Inject

class ObserveHabitCheckInBoardUseCase @Inject constructor(
    private val repository: HabitRepository,
) {

    operator fun invoke(): Flow<HabitCheckInBoard> =
        combine(
            repository.observeHabits(),
            repository.observeCheckIns(),
        ) { habits, checkIns ->
            HabitCheckInBoard(
                habits = habits,
                checkIns = checkIns,
            )
        }
}
