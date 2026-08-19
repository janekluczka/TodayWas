package pl.luczka.todaywas.data.local.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.dao.HabitCheckInDao
import pl.luczka.todaywas.data.local.dao.HabitDao
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.util.FakeTransactionRunner
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LocalHabitDataSourceImplTest {

    private fun dataSource(
        habitDao: HabitDao,
        habitCheckInDao: HabitCheckInDao,
    ) = LocalHabitDataSourceImpl(
        habitDao = habitDao,
        habitCheckInDao = habitCheckInDao,
        transactionRunner = FakeTransactionRunner(),
    )

    private class FakeHabitDao(
        private val failuresBeforeSuccess: Int,
        private val entities: MutableMap<String, HabitEntity> = mutableMapOf(),
    ) : HabitDao {

        var insertCallCount = 0
            private set
        var softDeleteByIdCallCount = 0
            private set
        var upsertCallCount = 0
            private set

        override fun observeAll(): Flow<List<HabitEntity>> = flowOf(entities.values.filter { it.deletedAt == null }.toList())

        override suspend fun getAll(): List<HabitEntity> = entities.values.filter { it.deletedAt == null }.toList()

        override suspend fun getAllIncludingDeleted(): List<HabitEntity> = entities.values.toList()

        override suspend fun getById(id: String): HabitEntity? = entities[id]?.takeIf { it.deletedAt == null }

        override suspend fun insert(entity: HabitEntity) {
            insertCallCount++
            if (insertCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.id] = entity
        }

        override suspend fun upsert(entity: HabitEntity) {
            upsertCallCount++
            entities[entity.id] = entity
        }

        override suspend fun softDeleteById(
            id: String,
            deletedAt: Long,
        ) {
            softDeleteByIdCallCount++
            entities[id]?.let { entities[id] = it.copy(deletedAt = deletedAt) }
        }

        override suspend fun purgeDeletedBefore(cutoff: Long) {
            entities.values.filter { it.deletedAt != null && it.deletedAt < cutoff }.forEach { entities.remove(it.id) }
        }

        override suspend fun clearAll() {
            entities.clear()
        }
    }

    private class FakeHabitCheckInDao(
        private val failuresBeforeSuccess: Int = 0,
        private val entities: MutableMap<Pair<String, String>, HabitCheckInEntity> = mutableMapOf(),
    ) : HabitCheckInDao {

        var insertAllCallCount = 0
            private set
        var updateCallCount = 0
            private set
        var softDeleteByIdCallCount = 0
            private set
        var softDeleteByHabitIdCallCount = 0
            private set
        var upsertAllCallCount = 0
            private set

        override fun observeAll(): Flow<List<HabitCheckInEntity>> =
            flowOf(entities.values.filter { it.deletedAt == null }.toList())

        override suspend fun getAll(): List<HabitCheckInEntity> = entities.values.filter { it.deletedAt == null }.toList()

        override suspend fun getAllIncludingDeleted(): List<HabitCheckInEntity> = entities.values.toList()

        override suspend fun getByHabitId(habitId: String): List<HabitCheckInEntity> =
            entities.values.filter { it.habitId == habitId && it.deletedAt == null }

        override suspend fun getByHabitAndDate(
            habitId: String,
            date: String,
        ): HabitCheckInEntity? = entities[habitId to date]?.takeIf { it.deletedAt == null }

        override suspend fun insertOne(entity: HabitCheckInEntity): Unit = throw UnsupportedOperationException(
            "not used by the data source",
        )

        override suspend fun insertAll(entities: List<HabitCheckInEntity>) {
            insertAllCallCount++
            if (insertAllCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities.forEach { this.entities[it.habitId to it.date] = it }
        }

        override suspend fun upsertOne(entity: HabitCheckInEntity): Unit = throw UnsupportedOperationException(
            "not used by the data source",
        )

        override suspend fun upsertAll(entities: List<HabitCheckInEntity>) {
            upsertAllCallCount++
            entities.forEach { this.entities[it.habitId to it.date] = it }
        }

        override suspend fun update(entity: HabitCheckInEntity) {
            updateCallCount++
            if (updateCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.habitId to entity.date] = entity
        }

        override suspend fun softDeleteById(
            id: String,
            deletedAt: Long,
        ) {
            softDeleteByIdCallCount++
            entities.entries.find { it.value.id == id }?.let { entities[it.key] = it.value.copy(deletedAt = deletedAt) }
        }

        override suspend fun softDeleteByHabitId(
            habitId: String,
            deletedAt: Long,
        ) {
            softDeleteByHabitIdCallCount++
            entities.entries
                .filter { it.key.first == habitId && it.value.deletedAt == null }
                .forEach { entities[it.key] = it.value.copy(deletedAt = deletedAt) }
        }

        override suspend fun purgeDeletedBefore(cutoff: Long) {
            entities.entries
                .filter { it.value.deletedAt != null && it.value.deletedAt!! < cutoff }
                .forEach { entities.remove(it.key) }
        }

        override suspend fun clearAll() {
            entities.clear()
        }
    }

    @Test
    fun `should succeed after one retry when insertHabit's first write fails`() =
        runTest {
            // Arrange
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 1)
            val dataSource = dataSource(habitDao, FakeHabitCheckInDao())
            val habit = HabitEntity(
                id = "1",
                name = "Drink water",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )

            // Act
            val result = dataSource.insertHabit(habit)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, habitDao.insertCallCount)
        }

    @Test
    fun `should return failure when insertHabit's retry also fails`() =
        runTest {
            // Arrange
            val habitDao = FakeHabitDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val dataSource = dataSource(habitDao, FakeHabitCheckInDao())
            val habit = HabitEntity(
                id = "1",
                name = "Drink water",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )

            // Act
            val result = dataSource.insertHabit(habit)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, habitDao.insertCallCount)
        }

    @Test
    fun `should succeed after one retry when insertCheckIns's first write fails`() =
        runTest {
            // Arrange
            val checkInDao = FakeHabitCheckInDao(failuresBeforeSuccess = 1)
            val dataSource = dataSource(FakeHabitDao(failuresBeforeSuccess = 0), checkInDao)
            val checkIn =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)

            // Act
            val result = dataSource.insertCheckIns(listOf(checkIn))

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, checkInDao.insertAllCallCount)
        }

    @Test
    fun `should return failure when insertCheckIns's retry also fails`() =
        runTest {
            // Arrange
            val checkInDao = FakeHabitCheckInDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val dataSource = dataSource(FakeHabitDao(failuresBeforeSuccess = 0), checkInDao)
            val checkIn =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)

            // Act
            val result = dataSource.insertCheckIns(listOf(checkIn))

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, checkInDao.insertAllCallCount)
        }

    @Test
    fun `should succeed after one retry when updateCheckIn's first write fails`() =
        runTest {
            // Arrange
            val existing =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(
                failuresBeforeSuccess = 1,
                entities = mutableMapOf(("1" to "2026-07-27") to existing),
            )
            val dataSource = dataSource(FakeHabitDao(failuresBeforeSuccess = 0), checkInDao)

            // Act
            val result = dataSource.updateCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), value = 0, updatedAt = 2_000L)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull()?.value)
            assertEquals(2, checkInDao.updateCallCount)
        }

    @Test
    fun `should return failure when updateCheckIn's retry also fails`() =
        runTest {
            // Arrange
            val existing =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(
                failuresBeforeSuccess = Int.MAX_VALUE,
                entities = mutableMapOf(("1" to "2026-07-27") to existing),
            )
            val dataSource = dataSource(FakeHabitDao(failuresBeforeSuccess = 0), checkInDao)

            // Act
            val result = dataSource.updateCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), value = 0, updatedAt = 2_000L)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, checkInDao.updateCallCount)
        }

    @Test
    fun `should return failure when updateCheckIn targets a non-existent habitId and date`() =
        runTest {
            // Arrange
            val checkInDao = FakeHabitCheckInDao()
            val dataSource = dataSource(FakeHabitDao(failuresBeforeSuccess = 0), checkInDao)

            // Act
            val result = dataSource.updateCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), value = 0, updatedAt = 2_000L)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, checkInDao.updateCallCount)
        }

    @Test
    fun `should soft-delete the habit and its check-ins in both DAOs when deleteHabitAndCheckIns is called`() =
        runTest {
            // Arrange
            val habit = HabitEntity(
                id = "1",
                name = "Drink water",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 0, entities = mutableMapOf("1" to habit))
            val checkIn =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(entities = mutableMapOf(("1" to "2026-07-27") to checkIn))
            val dataSource = dataSource(habitDao, checkInDao)

            // Act
            val result = dataSource.deleteHabitAndCheckIns(habitId = "1", deletedAt = 2_000L)

            // Assert
            assertTrue(result.isSuccess)
            val (deletedHabit, deletedCheckIns) = result.getOrThrow()
            assertEquals("1", deletedHabit?.id)
            assertEquals(1, deletedCheckIns.size)
            assertTrue(habitDao.getAll().isEmpty())
            assertTrue(checkInDao.getAll().isEmpty())
        }

    @Test
    fun `should remove only the matching check-in when deleteCheckIn is called`() =
        runTest {
            // Arrange
            val target =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)
            val other =
                HabitCheckInEntity(id = "check-in-2", habitId = "1", date = "2026-07-26", value = 0, createdAt = 1_000L, updatedAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(
                entities = mutableMapOf(("1" to "2026-07-27") to target, ("1" to "2026-07-26") to other),
            )
            val dataSource = dataSource(FakeHabitDao(failuresBeforeSuccess = 0), checkInDao)

            // Act
            val result = dataSource.deleteCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), deletedAt = 2_000L)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(null, checkInDao.getByHabitAndDate("1", "2026-07-27"))
            assertEquals(other, checkInDao.getByHabitAndDate("1", "2026-07-26"))
        }

    @Test
    fun `should return failure when deleteCheckIn targets a non-existent habitId and date`() =
        runTest {
            // Arrange
            val checkInDao = FakeHabitCheckInDao()
            val dataSource = dataSource(FakeHabitDao(failuresBeforeSuccess = 0), checkInDao)

            // Act
            val result = dataSource.deleteCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), deletedAt = 2_000L)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, checkInDao.softDeleteByIdCallCount)
        }

    @Test
    fun `should upsert both habits and check-ins when applyRemoteSnapshot is called`() =
        runTest {
            // Arrange
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 0)
            val checkInDao = FakeHabitCheckInDao()
            val dataSource = dataSource(habitDao, checkInDao)
            val habit = HabitEntity(
                id = "1",
                name = "Drink water",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )
            val checkIn =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)

            // Act
            dataSource.applyRemoteSnapshot(habitsToApply = listOf(habit), checkInsToApply = listOf(checkIn))

            // Assert
            assertEquals(1, habitDao.upsertCallCount)
            assertEquals(1, checkInDao.upsertAllCallCount)
            assertTrue(habitDao.getAll().any { it.id == "1" })
            assertTrue(checkInDao.getAll().any { it.id == "check-in-1" })
        }

    @Test
    fun `should purge tombstoned rows from both DAOs when purgeDeletedBefore is called`() =
        runTest {
            // Arrange
            val agedOutHabit = HabitEntity(
                id = "1",
                name = "Long gone",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = 500L,
            )
            val agedOutCheckIn = HabitCheckInEntity(
                id = "check-in-1",
                habitId = "1",
                date = "2026-07-27",
                value = 1,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = 500L,
            )
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 0, entities = mutableMapOf("1" to agedOutHabit))
            val checkInDao = FakeHabitCheckInDao(entities = mutableMapOf(("1" to "2026-07-27") to agedOutCheckIn))
            val dataSource = dataSource(habitDao, checkInDao)

            // Act
            dataSource.purgeDeletedBefore(cutoff = 1_000L)

            // Assert
            assertTrue(habitDao.getAllIncludingDeleted().isEmpty())
            assertTrue(checkInDao.getAllIncludingDeleted().isEmpty())
        }

    @Test
    fun `should empty both DAOs when clearAll is called`() =
        runTest {
            // Arrange
            val habitDao = FakeHabitDao(
                failuresBeforeSuccess = 0,
                entities = mutableMapOf(
                    "1" to HabitEntity(
                        id = "1",
                        name = "Drink water",
                        description = null,
                        type = "BINARY",
                        scaleMin = null,
                        scaleMax = null,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                    ),
                ),
            )
            val checkInDao = FakeHabitCheckInDao(
                entities = mutableMapOf(
                    ("1" to "2026-07-27") to
                        HabitCheckInEntity(
                            id = "check-in-1",
                            habitId = "1",
                            date = "2026-07-27",
                            value = 1,
                            createdAt = 1_000L,
                            updatedAt = 1_000L,
                        ),
                ),
            )
            val dataSource = dataSource(habitDao, checkInDao)

            // Act
            val result = dataSource.clearAll()

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(habitDao.getAll().isEmpty())
            assertTrue(checkInDao.getAll().isEmpty())
        }
}
