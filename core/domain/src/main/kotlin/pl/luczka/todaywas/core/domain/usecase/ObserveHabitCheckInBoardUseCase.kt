package pl.luczka.todaywas.core.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pl.luczka.todaywas.core.domain.model.HabitCheckInBoard
import pl.luczka.todaywas.core.domain.repository.HabitRepository

class ObserveHabitCheckInBoardUseCase @Inject constructor(
    private val repository: HabitRepository,
) {

    operator fun invoke(): Flow<HabitCheckInBoard> = combine(
        repository.observeHabits(),
        repository.observeCheckIns(),
    ) { habits, checkIns ->
        HabitCheckInBoard(
            habits = habits,
            checkIns = checkIns,
        )
    }
}
