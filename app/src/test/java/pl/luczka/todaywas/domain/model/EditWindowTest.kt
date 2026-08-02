package pl.luczka.todaywas.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class EditWindowTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")

    @Test
    fun `just created is editable`() {
        assertTrue(EditWindow.isEditable(createdAt, createdAt))
    }

    @Test
    fun `23h59m59s after creation is editable`() {
        val now = createdAt.plus(Duration.ofHours(23).plusMinutes(59).plusSeconds(59))
        assertTrue(EditWindow.isEditable(createdAt, now))
    }

    @Test
    fun `exactly 24h after creation is locked`() {
        val now = createdAt.plus(Duration.ofHours(24))
        assertFalse(EditWindow.isEditable(createdAt, now))
    }

    @Test
    fun `24h00m01s after creation is locked`() {
        val now = createdAt.plus(Duration.ofHours(24).plusSeconds(1))
        assertFalse(EditWindow.isEditable(createdAt, now))
    }
}
