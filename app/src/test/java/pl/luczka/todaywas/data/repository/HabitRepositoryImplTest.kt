package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.dao.HabitCheckInDao
import pl.luczka.todaywas.data.local.dao.HabitDao
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.remote.api.FakeRemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.FakeRemoteHabitDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSource
import pl.luczka.todaywas.data.remote.dto.HabitRemoteDto
import pl.luczka.todaywas.domain.model.HabitType
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HabitRepositoryImplTest {

    private fun repository(
        habitDao: HabitDao,
        habitCheckInDao: HabitCheckInDao,
        scope: CoroutineScope,
        remoteHabitDataSource: RemoteHabitDataSource = FakeRemoteHabitDataSource(),
        remoteHabitCheckInDataSource: RemoteHabitCheckInDataSource = FakeRemoteHabitCheckInDataSource(),
        authRepository: AuthRepository = FakeAuthRepository(),
    ) = HabitRepositoryImpl(
        habitDao = habitDao,
        habitCheckInDao = habitCheckInDao,
        remoteHabitDataSource = remoteHabitDataSource,
        remoteHabitCheckInDataSource = remoteHabitCheckInDataSource,
        authRepository = authRepository,
        syncScope = scope,
    )

    private class FakeHabitDao(
        private val failuresBeforeSuccess: Int,
        private val entities: MutableMap<String, HabitEntity> = mutableMapOf(),
    ) : HabitDao {

        var insertCallCount = 0
            private set

        override fun observeAll(): Flow<List<HabitEntity>> = flowOf(entities.values.toList())

        override suspend fun getAll(): List<HabitEntity> = entities.values.toList()

        override suspend fun insert(entity: HabitEntity) {
            insertCallCount++
            if (insertCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.id] = entity
        }

        override suspend fun upsert(entity: HabitEntity) {
            entities[entity.id] = entity
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

        override fun observeAll(): Flow<List<HabitCheckInEntity>> = flowOf(entities.values.toList())

        override suspend fun getAll(): List<HabitCheckInEntity> = entities.values.toList()

        override suspend fun getByHabitAndDate(
            habitId: String,
            date: String,
        ): HabitCheckInEntity? = entities[habitId to date]

        override suspend fun insertOne(entity: HabitCheckInEntity): Unit = throw UnsupportedOperationException("not used by the repository")

        override suspend fun insertAll(entities: List<HabitCheckInEntity>) {
            insertAllCallCount++
            if (insertAllCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities.forEach { this.entities[it.habitId to it.date] = it }
        }

        override suspend fun upsertOne(entity: HabitCheckInEntity): Unit = throw UnsupportedOperationException("not used by the repository")

        override suspend fun upsertAll(entities: List<HabitCheckInEntity>) {
            entities.forEach { this.entities[it.habitId to it.date] = it }
        }

        override suspend fun update(entity: HabitCheckInEntity) {
            updateCallCount++
            if (updateCallCount <= failuresBeforeSuccess) {
                throw RuntimeException("simulated write failure")
            }
            entities[entity.habitId to entity.date] = entity
        }

        override suspend fun clearAll() {
            entities.clear()
        }
    }

    @Test
    fun `should succeed after one retry when createHabit's first write fails`() =
        runTest {
            // Arrange
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 1)
            val repository = repository(
                habitDao = habitDao,
                habitCheckInDao = FakeHabitCheckInDao(failuresBeforeSuccess = 0),
                scope = backgroundScope,
            )

            // Act
            val result = repository.createHabit(
                name = "Drink water",
                description = null,
                type = HabitType.SCALE,
                scaleMin = 1,
                scaleMax = 5,
            )

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, habitDao.insertCallCount)
        }

    @Test
    fun `should return failure when createHabit's retry also fails`() =
        runTest {
            // Arrange
            val habitDao = FakeHabitDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = repository(
                habitDao = habitDao,
                habitCheckInDao = FakeHabitCheckInDao(failuresBeforeSuccess = 0),
                scope = backgroundScope,
            )

            // Act
            val result = repository.createHabit(
                name = "Drink water",
                description = null,
                type = HabitType.BINARY,
                scaleMin = null,
                scaleMax = null,
            )

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, habitDao.insertCallCount)
        }

    @Test
    fun `should succeed after one retry when addCheckIns's first write fails`() =
        runTest {
            // Arrange
            val checkInDao = FakeHabitCheckInDao(failuresBeforeSuccess = 1)
            val repository = repository(
                habitDao = FakeHabitDao(failuresBeforeSuccess = 0),
                habitCheckInDao = checkInDao,
                scope = backgroundScope,
            )

            // Act
            val result = repository.addCheckIns(
                date = LocalDate.of(2026, 7, 27),
                values = mapOf("1" to 1, "2" to 3),
            )

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, checkInDao.insertAllCallCount)
        }

    @Test
    fun `should return failure when addCheckIns's retry also fails`() =
        runTest {
            // Arrange
            val checkInDao = FakeHabitCheckInDao(failuresBeforeSuccess = Int.MAX_VALUE)
            val repository = repository(
                habitDao = FakeHabitDao(failuresBeforeSuccess = 0),
                habitCheckInDao = checkInDao,
                scope = backgroundScope,
            )

            // Act
            val result = repository.addCheckIns(
                date = LocalDate.of(2026, 7, 27),
                values = mapOf("1" to 1),
            )

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, checkInDao.insertAllCallCount)
        }

    @Test
    fun `should succeed after one retry when updateCheckIn's first write fails`() =
        runTest {
            // Arrange
            val existing = HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(
                failuresBeforeSuccess = 1,
                entities = mutableMapOf(("1" to "2026-07-27") to existing),
            )
            val repository =
                repository(habitDao = FakeHabitDao(failuresBeforeSuccess = 0), habitCheckInDao = checkInDao, scope = backgroundScope)

            // Act
            val result = repository.updateCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), value = 0)

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(2, checkInDao.updateCallCount)
            assertEquals(0, checkInDao.getByHabitAndDate("1", "2026-07-27")?.value)
        }

    @Test
    fun `should return failure when updateCheckIn's retry also fails`() =
        runTest {
            // Arrange
            val existing = HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L)
            val checkInDao = FakeHabitCheckInDao(
                failuresBeforeSuccess = Int.MAX_VALUE,
                entities = mutableMapOf(("1" to "2026-07-27") to existing),
            )
            val repository =
                repository(habitDao = FakeHabitDao(failuresBeforeSuccess = 0), habitCheckInDao = checkInDao, scope = backgroundScope)

            // Act
            val result = repository.updateCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), value = 0)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(2, checkInDao.updateCallCount)
        }

    @Test
    fun `should return failure when updateCheckIn targets a non-existent habitId and date`() =
        runTest {
            // Arrange
            val checkInDao = FakeHabitCheckInDao()
            val repository =
                repository(habitDao = FakeHabitDao(failuresBeforeSuccess = 0), habitCheckInDao = checkInDao, scope = backgroundScope)

            // Act
            val result = repository.updateCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27), value = 0)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, checkInDao.updateCallCount)
        }

    @Test
    fun `should still succeed when the background remote push fails`() =
        runTest {
            // Arrange
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 0)
            val remoteHabits = FakeRemoteHabitDataSource(shouldFail = true)
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(
                habitDao = habitDao,
                habitCheckInDao = FakeHabitCheckInDao(),
                scope = backgroundScope,
                remoteHabitDataSource = remoteHabits,
                authRepository = auth,
            )

            // Act
            val result = repository.createHabit(
                name = "Drink water",
                description = null,
                type = HabitType.BINARY,
                scaleMin = null,
                scaleMax = null,
            )
            runCurrent()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, remoteHabits.upsertCallCount)
        }

    @Test
    fun `should push all local habits and check-ins then pull remote-only rows when syncWithRemote is called`() =
        runTest {
            // Arrange
            val localHabit = HabitEntity(
                id = "local-habit",
                name = "Drink water",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
            )
            val habitDao = FakeHabitDao(failuresBeforeSuccess = 0, entities = mutableMapOf("local-habit" to localHabit))
            val checkInDao = FakeHabitCheckInDao()
            val remoteHabit = HabitRemoteDto(
                id = "remote-habit",
                userId = "user-1",
                name = "Read",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = "2026-07-20T00:00:00Z",
            )
            val remoteHabits = FakeRemoteHabitDataSource(habits = mutableMapOf("remote-habit" to remoteHabit))
            val remoteCheckIns = FakeRemoteHabitCheckInDataSource()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(
                habitDao = habitDao,
                habitCheckInDao = checkInDao,
                scope = backgroundScope,
                remoteHabitDataSource = remoteHabits,
                remoteHabitCheckInDataSource = remoteCheckIns,
                authRepository = auth,
            )

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, remoteHabits.upsertCallCount)
            assertTrue(habitDao.getAll().any { it.id == "remote-habit" })
        }

    @Test
    fun `should empty both DAOs when clearLocal is called`() =
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
                    ),
                ),
            )
            val checkInDao = FakeHabitCheckInDao(
                entities = mutableMapOf(
                    ("1" to "2026-07-27") to
                        HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L),
                ),
            )
            val repository = repository(habitDao = habitDao, habitCheckInDao = checkInDao, scope = backgroundScope)

            // Act
            val result = repository.clearLocal()

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(habitDao.getAll().isEmpty())
            assertTrue(checkInDao.getAll().isEmpty())
        }
}
