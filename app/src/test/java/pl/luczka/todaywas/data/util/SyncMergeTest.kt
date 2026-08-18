package pl.luczka.todaywas.data.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SyncMergeTest {

    private val t1 = Instant.parse("2026-08-01T00:00:00Z")
    private val t2 = Instant.parse("2026-08-02T00:00:00Z")

    @Test
    fun `should push local-only row when remote has never seen it`() {
        // Arrange
        val local = listOf(SyncMeta(id = "1", updatedAt = t1, isDeleted = false))

        // Act
        val decision = mergeForSync(local, remote = emptyList())

        // Assert
        assertEquals(setOf("1"), decision.pushIds)
        assertEquals(emptySet<String>(), decision.applyIds)
    }

    @Test
    fun `should apply remote-only row when local has never seen it`() {
        // Arrange
        val remote = listOf(SyncMeta(id = "1", updatedAt = t1, isDeleted = false))

        // Act
        val decision = mergeForSync(local = emptyList(), remote)

        // Assert
        assertEquals(emptySet<String>(), decision.pushIds)
        assertEquals(setOf("1"), decision.applyIds)
    }

    @Test
    fun `should apply when both sides edited and remote is newer`() {
        // Arrange
        val local = listOf(SyncMeta(id = "1", updatedAt = t1, isDeleted = false))
        val remote = listOf(SyncMeta(id = "1", updatedAt = t2, isDeleted = false))

        // Act
        val decision = mergeForSync(local, remote)

        // Assert
        assertEquals(emptySet<String>(), decision.pushIds)
        assertEquals(setOf("1"), decision.applyIds)
    }

    @Test
    fun `should push when both sides edited and local is newer`() {
        // Arrange
        val local = listOf(SyncMeta(id = "1", updatedAt = t2, isDeleted = false))
        val remote = listOf(SyncMeta(id = "1", updatedAt = t1, isDeleted = false))

        // Act
        val decision = mergeForSync(local, remote)

        // Assert
        assertEquals(setOf("1"), decision.pushIds)
        assertEquals(emptySet<String>(), decision.applyIds)
    }

    @Test
    fun `should push the tombstone when local is deleted even if remote has a newer non-deleted edit`() {
        // Arrange
        val local = listOf(SyncMeta(id = "1", updatedAt = t1, isDeleted = true))
        val remote = listOf(SyncMeta(id = "1", updatedAt = t2, isDeleted = false))

        // Act
        val decision = mergeForSync(local, remote)

        // Assert
        assertEquals(setOf("1"), decision.pushIds)
        assertEquals(emptySet<String>(), decision.applyIds)
    }

    @Test
    fun `should apply the tombstone when remote is deleted even if local has a newer non-deleted edit`() {
        // Arrange
        val local = listOf(SyncMeta(id = "1", updatedAt = t2, isDeleted = false))
        val remote = listOf(SyncMeta(id = "1", updatedAt = t1, isDeleted = true))

        // Act
        val decision = mergeForSync(local, remote)

        // Assert
        assertEquals(emptySet<String>(), decision.pushIds)
        assertEquals(setOf("1"), decision.applyIds)
    }

    @Test
    fun `should push a local-only tombstone when remote never saw the row at all`() {
        // Arrange
        val local = listOf(SyncMeta(id = "1", updatedAt = t1, isDeleted = true))

        // Act
        val decision = mergeForSync(local, remote = emptyList())

        // Assert
        assertEquals(setOf("1"), decision.pushIds)
        assertEquals(emptySet<String>(), decision.applyIds)
    }
}
