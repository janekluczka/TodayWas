package pl.luczka.todaywas.domain.usecase

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import pl.luczka.todaywas.domain.util.EditWindow

class IsEditableUseCase @Inject constructor(
    private val clock: Clock,
) {
    operator fun invoke(createdAt: Instant): Boolean =
        EditWindow.isEditable(createdAt, clock.instant())
}
