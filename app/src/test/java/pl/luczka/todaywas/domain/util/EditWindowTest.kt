package pl.luczka.todaywas.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class EditWindowTest {

    private val createdAt = Instant.parse("2026-08-01T00:00:00Z")

    @Test
    fun `should be editable when checked at the moment of creation`() {
        // Arrange
        val now = createdAt

        // Act
        val isEditable = EditWindow.isEditable(createdAt, now)

        // Assert
        assertTrue(isEditable)
    }

    @Test
    fun `should be editable when checked 23h59m59s after creation`() {
        // Arrange
        val now = createdAt.plus(Duration.ofHours(23).plusMinutes(59).plusSeconds(59))

        // Act
        val isEditable = EditWindow.isEditable(createdAt, now)

        // Assert
        assertTrue(isEditable)
    }

    @Test
    fun `should be locked when checked exactly 24h after creation`() {
        // Arrange
        val now = createdAt.plus(Duration.ofHours(24))

        // Act
        val isEditable = EditWindow.isEditable(createdAt, now)

        // Assert
        assertFalse(isEditable)
    }

    @Test
    fun `should be locked when checked 24h00m01s after creation`() {
        // Arrange
        val now = createdAt.plus(Duration.ofHours(24).plusSeconds(1))

        // Act
        val isEditable = EditWindow.isEditable(createdAt, now)

        // Assert
        assertFalse(isEditable)
    }
}
