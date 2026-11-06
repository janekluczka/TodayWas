package pl.luczka.todaywas.domain.entry.usecase

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import pl.luczka.todaywas.domain.entry.util.EditWindow

class GetFreshLoggableDatesUseCase @Inject constructor(
    private val clock: Clock,
) {
    operator fun invoke(): List<LocalDate> = EditWindow.freshLoggableDates(clock.instant())
}
