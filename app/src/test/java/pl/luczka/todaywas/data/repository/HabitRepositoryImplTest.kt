package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.HabitCheckInDao
import pl.luczka.todaywas.data.local.HabitCheckInEntity
import pl.luczka.todaywas.data.local.HabitDao
import pl.luczka.todaywas.data.local.HabitEntity
import pl.luczka.todaywas.domain.model.HabitType
import java.time.LocalDate

class HabitRepositoryImplTest {

    private class FakeHabitDao(
        private val failuresBeforeSuccess: Int,
    ) : HabitDao {

        var insertCallCount = 0
            private set

        override fun observeAll(): Flow<List<HabitEntity>> = flowOf(emptyList())

        override suspend fun insert(entity: HabitEntity) {
            insertCallCount++
            if (insertCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
        }
    }

    private class FakeHabitCheckInDao(
        private val failuresBeforeSuccess: Int = 0,
        private val entities: MutableMap<Pair<Long, String>, HabitCheckInEntity> = mutableMapOf(),
    ) : HabitCheckInDao {

        var insertAllCallCount = 0
            private set
        var updateCallCount = 0
            private set

        override fun observeAll(): Flow<List<HabitCheckInEntity>> = flowOf(entities.values.toList())

        override suspend fun getByHabitAndDate(
            habitId: Long,
            date: String,
        ): HabitCheckInEntity? = entities[habitId to date]

        override suspend fun insertOne(entity: HabitCheckInEntity): Unit = throw UnsupportedOperationException("not used by the repository")

        override suspend fun insertAll(entities: List<HabitCheckInEntity>) {
            insertAllCallCount++
            if (insertAllCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
        }

        override suspend fun update(entity: HabitCheckInEntity) {
            updateCallCount++
            if (updateCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.habitId to entity.date] = entity
        }
    }

    @Test
    fun `createHabit retries once then succeeds if the retry works`() =
        runTest {
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 1)
            val repository = HabitRepositoryImpl(
                habitDao = habitDao,
                habitCheckInDao = FakeHabitCheckInDao(failuresBeforeSuccess = 0),
            )

            val result = repository.createHabit(
                name = "Drink water",
                description = null,
                type = HabitType.SCALE,
                scaleMin = 1,
                scaleMax = 5,
            )

            assertTrue(result.isSuccess)
            assertEquals(2, habitDao.insertCallCount)
        }

    @Test
    fun `createHabit returns failure after the retry also fails`() =
        runTest {
            val habitDao = FakeHabitDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = HabitRepositoryImpl(
                habitDao = habitDao,
                habitCheckInDao = FakeHabitCheckInDao(failuresBeforeSuccess = 0),
            )

            val result = repository.createHabit(
                name = "Drink water",
                description = null,
                type = HabitType.BINARY,
                scaleMin = null,
                scaleMax = null,
            )

            assertTrue(result.isFailure)
            assertEquals(2, habitDao.insertCallCount)
        }

    @Test
    fun `addCheckIns retries once then succeeds if the retry works`() =
        runTest {
            val checkInDao = FakeHabitCheckInDao(failuresBeforeSuccess = 1)
            val repository = HabitRepositoryImpl(
                habitDao = FakeHabitDao(failuresBeforeSuccess = 0),
                habitCheckInDao = checkInDao,
            )

            val result = repository.addCheckIns(
                date = LocalDate.of(2026, 7, 27),
                values = mapOf(1L to 1, 2L to 3),
            )

            assertTrue(result.isSuccess)
            assertEquals(2, checkInDao.insertAllCallCount)
        }

    @Test
    fun `addCheckIns returns failure after the retry also fails`() =
        runTest {
            val checkInDao = FakeHabitCheckInDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = HabitRepositoryImpl(
                habitDao = FakeHabitDao(failuresBeforeSuccess = 0),
                habitCheckInDao = checkInDao,
            )

            val result = repository.addCheckIns(
                date = LocalDate.of(2026, 7, 27),
                values = mapOf(1L to 1),
            )

            assertTrue(result.isFailure)
            assertEquals(2, checkInDao.insertAllCallCount)
        }

    @Test
    fun `updateCheckIn retries once then succeeds if the retry works`() =
        runTest {
            val existing = HabitCheckInEntity(habitId = 1L, date = "2026-07-27", value = 1, createdAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(
                failuresBeforeSuccess = 1,
                entities = mutableMapOf((1L to "2026-07-27") to existing),
            )
            val repository = HabitRepositoryImpl(habitDao = FakeHabitDao(failuresBeforeSuccess = 0), habitCheckInDao = checkInDao)

            val result = repository.updateCheckIn(habitId = 1L, date = LocalDate.of(2026, 7, 27), value = 0)

            assertTrue(result.isSuccess)
            assertEquals(2, checkInDao.updateCallCount)
            assertEquals(0, checkInDao.getByHabitAndDate(1L, "2026-07-27")?.value)
        }

    @Test
    fun `updateCheckIn returns failure after the retry also fails`() =
        runTest {
            val existing = HabitCheckInEntity(habitId = 1L, date = "2026-07-27", value = 1, createdAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(
                failuresBeforeSuccess = Int.MAX_VALUE,
                entities = mutableMapOf((1L to "2026-07-27") to existing),
            )
            val repository = HabitRepositoryImpl(habitDao = FakeHabitDao(failuresBeforeSuccess = 0), habitCheckInDao = checkInDao)

            val result = repository.updateCheckIn(habitId = 1L, date = LocalDate.of(2026, 7, 27), value = 0)

            assertTrue(result.isFailure)
            assertEquals(2, checkInDao.updateCallCount)
        }

    @Test
    fun `updateCheckIn returns failure for a non-existent habitId and date`() =
        runTest {
            val checkInDao = FakeHabitCheckInDao()
            val repository = HabitRepositoryImpl(habitDao = FakeHabitDao(failuresBeforeSuccess = 0), habitCheckInDao = checkInDao)

            val result = repository.updateCheckIn(habitId = 1L, date = LocalDate.of(2026, 7, 27), value = 0)

            assertTrue(result.isFailure)
            assertEquals(0, checkInDao.updateCallCount)
        }
}
