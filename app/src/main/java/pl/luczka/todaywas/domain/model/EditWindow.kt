package pl.luczka.todaywas.domain.model

import java.time.Duration
import java.time.Instant

object EditWindow {

    val HOURS: Duration = Duration.ofHours(24)

    fun isEditable(
        createdAt: Instant,
        now: Instant,
    ): Boolean = Duration.between(createdAt, now) < HOURS
}
