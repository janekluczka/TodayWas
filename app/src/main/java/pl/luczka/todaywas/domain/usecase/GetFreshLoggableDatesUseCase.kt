package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.util.EditWindow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class GetFreshLoggableDatesUseCase @Inject constructor(
    private val clock: Clock,
) {
    operator fun invoke(): List<LocalDate> = EditWindow.freshLoggableDates(clock.instant())
}
