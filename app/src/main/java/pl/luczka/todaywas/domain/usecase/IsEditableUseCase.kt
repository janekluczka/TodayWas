package pl.luczka.todaywas.domain.usecase

import pl.luczka.todaywas.domain.util.EditWindow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class IsEditableUseCase @Inject constructor(
    private val clock: Clock,
) {
    operator fun invoke(
        createdAt: Instant,
    ): Boolean = EditWindow.isEditable(createdAt, clock.instant())
}
