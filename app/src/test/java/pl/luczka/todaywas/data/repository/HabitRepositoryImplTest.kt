package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.luczka.todaywas.data.local.api.LocalHabitDataSource
import pl.luczka.todaywas.data.local.entity.HabitCheckInEntity
import pl.luczka.todaywas.data.local.entity.HabitEntity
import pl.luczka.todaywas.data.remote.api.FakeRemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.FakeRemoteHabitDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitCheckInDataSource
import pl.luczka.todaywas.data.remote.api.RemoteHabitDataSource
import pl.luczka.todaywas.data.remote.dto.HabitRemoteDto
import pl.luczka.todaywas.data.util.SyncScheduler
import pl.luczka.todaywas.data.util.TOMBSTONE_GC_WINDOW
import pl.luczka.todaywas.domain.model.HabitType
import pl.luczka.todaywas.domain.repository.AuthRepository
import pl.luczka.todaywas.domain.repository.FakeAuthRepository
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HabitRepositoryImplTest {

    private fun repository(
        local: LocalHabitDataSource,
        remoteHabitDataSource: RemoteHabitDataSource = FakeRemoteHabitDataSource(),
        remoteHabitCheckInDataSource: RemoteHabitCheckInDataSource = FakeRemoteHabitCheckInDataSource(),
        authRepository: AuthRepository = FakeAuthRepository(),
        syncScheduler: SyncScheduler = FakeSyncScheduler(),
    ) = HabitRepositoryImpl(
        local = local,
        remoteHabitDataSource = remoteHabitDataSource,
        remoteHabitCheckInDataSource = remoteHabitCheckInDataSource,
        authRepository = authRepository,
        syncScheduler = syncScheduler,
    )

    private class FakeSyncScheduler : SyncScheduler {
        var scheduleSyncCallCount = 0
            private set

        override fun scheduleSync() {
            scheduleSyncCallCount++
        }
    }

    private class FakeLocalHabitDataSource(
        private val habits: MutableMap<String, HabitEntity> = mutableMapOf(),
        private val checkIns: MutableMap<Pair<String, String>, HabitCheckInEntity> = mutableMapOf(),
        private val shouldFailInsertHabit: Boolean = false,
    ) : LocalHabitDataSource {

        var applyRemoteSnapshotCallCount = 0
            private set
        var lastAppliedHabits: List<HabitEntity> = emptyList()
            private set
        var lastAppliedCheckIns: List<HabitCheckInEntity> = emptyList()
            private set
        var purgeDeletedBeforeCallCount = 0
            private set

        override fun observeHabits(): Flow<List<HabitEntity>> = flowOf(habits.values.filter { it.deletedAt == null }.toList())

        override fun observeCheckIns(): Flow<List<HabitCheckInEntity>> = flowOf(checkIns.values.filter { it.deletedAt == null }.toList())

        override suspend fun insertHabit(entity: HabitEntity): Result<Unit> {
            if (shouldFailInsertHabit) return Result.failure(RuntimeException("simulated write failure"))
            habits[entity.id] = entity
            return Result.success(Unit)
        }

        override suspend fun insertCheckIns(entities: List<HabitCheckInEntity>): Result<Unit> {
            entities.forEach { checkIns[it.habitId to it.date] = it }
            return Result.success(Unit)
        }

        override suspend fun updateCheckIn(
            habitId: String,
            date: LocalDate,
            value: Int,
            updatedAt: Long,
        ): Result<HabitCheckInEntity> {
            val existing = checkIns[habitId to date.toString()]
                ?: return Result.failure(NoSuchElementException("Check-in for habit $habitId on $date not found"))
            val updated = existing.copy(value = value, updatedAt = updatedAt)
            checkIns[habitId to date.toString()] = updated
            return Result.success(updated)
        }

        override suspend fun deleteHabitAndCheckIns(
            habitId: String,
            deletedAt: Long,
        ): Result<Pair<HabitEntity?, List<HabitCheckInEntity>>> {
            val habit = habits[habitId]?.copy(deletedAt = deletedAt)
            habit?.let { habits[habitId] = it }
            val tombstonedCheckIns = checkIns.values
                .filter { it.habitId == habitId && it.deletedAt == null }
                .map { it.copy(deletedAt = deletedAt) }
            tombstonedCheckIns.forEach { checkIns[it.habitId to it.date] = it }
            return Result.success(habit to tombstonedCheckIns)
        }

        override suspend fun deleteCheckIn(
            habitId: String,
            date: LocalDate,
            deletedAt: Long,
        ): Result<HabitCheckInEntity> {
            val existing = checkIns[habitId to date.toString()]
                ?: return Result.failure(NoSuchElementException("Check-in for habit $habitId on $date not found"))
            val tombstoned = existing.copy(deletedAt = deletedAt)
            checkIns[habitId to date.toString()] = tombstoned
            return Result.success(tombstoned)
        }

        override suspend fun getAllHabitsIncludingDeleted(): List<HabitEntity> = habits.values.toList()

        override suspend fun getAllCheckInsIncludingDeleted(): List<HabitCheckInEntity> = checkIns.values.toList()

        override suspend fun applyRemoteSnapshot(
            habitsToApply: List<HabitEntity>,
            checkInsToApply: List<HabitCheckInEntity>,
        ) {
            applyRemoteSnapshotCallCount++
            lastAppliedHabits = habitsToApply
            lastAppliedCheckIns = checkInsToApply
            habitsToApply.forEach { habits[it.id] = it }
            checkInsToApply.forEach { checkIns[it.habitId to it.date] = it }
        }

        override suspend fun purgeDeletedBefore(cutoff: Long) {
            purgeDeletedBeforeCallCount++
            habits.values.filter { it.deletedAt != null && it.deletedAt < cutoff }.forEach { habits.remove(it.id) }
            checkIns.values.filter { it.deletedAt != null && it.deletedAt < cutoff }.forEach { checkIns.remove(it.habitId to it.date) }
        }

        override suspend fun clearAll(): Result<Unit> {
            habits.clear()
            checkIns.clear()
            return Result.success(Unit)
        }
    }

    @Test
    fun `should schedule a sync when signed in and createHabit succeeds`() =
        runTest {
            // Arrange
            val local = FakeLocalHabitDataSource()
            val syncScheduler = FakeSyncScheduler()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local = local, authRepository = auth, syncScheduler = syncScheduler)

            // Act
            repository.createHabit(name = "Drink water", description = null, type = HabitType.BINARY, scaleMin = null, scaleMax = null)

            // Assert
            assertEquals(1, syncScheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should not schedule a sync when signed out and createHabit succeeds`() =
        runTest {
            // Arrange
            val local = FakeLocalHabitDataSource()
            val syncScheduler = FakeSyncScheduler()
            val auth = FakeAuthRepository(currentUserId = null)
            val repository = repository(local = local, authRepository = auth, syncScheduler = syncScheduler)

            // Act
            repository.createHabit(name = "Drink water", description = null, type = HabitType.BINARY, scaleMin = null, scaleMax = null)

            // Assert
            assertEquals(0, syncScheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should not schedule a sync when createHabit's local write fails`() =
        runTest {
            // Arrange
            val local = FakeLocalHabitDataSource(shouldFailInsertHabit = true)
            val syncScheduler = FakeSyncScheduler()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local = local, authRepository = auth, syncScheduler = syncScheduler)

            // Act
            val result =
                repository.createHabit(name = "Drink water", description = null, type = HabitType.BINARY, scaleMin = null, scaleMax = null)

            // Assert
            assertTrue(result.isFailure)
            assertEquals(0, syncScheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should schedule a sync when signed in and deleteHabit succeeds`() =
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
            val local = FakeLocalHabitDataSource(habits = mutableMapOf("1" to habit))
            val syncScheduler = FakeSyncScheduler()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local = local, authRepository = auth, syncScheduler = syncScheduler)

            // Act
            repository.deleteHabit("1")

            // Assert
            assertEquals(1, syncScheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should not schedule a sync when signed out and deleteHabit succeeds`() =
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
            val local = FakeLocalHabitDataSource(habits = mutableMapOf("1" to habit))
            val syncScheduler = FakeSyncScheduler()
            val auth = FakeAuthRepository(currentUserId = null)
            val repository = repository(local = local, authRepository = auth, syncScheduler = syncScheduler)

            // Act
            val result = repository.deleteHabit("1")

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(local.getAllHabitsIncludingDeleted().single { it.id == "1" }.deletedAt != null)
            assertEquals(0, syncScheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should schedule a sync when signed in and deleteCheckIn succeeds`() =
        runTest {
            // Arrange
            val existing =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)
            val local = FakeLocalHabitDataSource(checkIns = mutableMapOf(("1" to "2026-07-27") to existing))
            val syncScheduler = FakeSyncScheduler()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local = local, authRepository = auth, syncScheduler = syncScheduler)

            // Act
            repository.deleteCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27))

            // Assert
            assertEquals(1, syncScheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should not schedule a sync when signed out and deleteCheckIn succeeds`() =
        runTest {
            // Arrange
            val existing =
                HabitCheckInEntity(id = "check-in-1", habitId = "1", date = "2026-07-27", value = 1, createdAt = 1_000L, updatedAt = 1_000L)
            val local = FakeLocalHabitDataSource(checkIns = mutableMapOf(("1" to "2026-07-27") to existing))
            val syncScheduler = FakeSyncScheduler()
            val auth = FakeAuthRepository(currentUserId = null)
            val repository = repository(local = local, authRepository = auth, syncScheduler = syncScheduler)

            // Act
            val result = repository.deleteCheckIn(habitId = "1", date = LocalDate.of(2026, 7, 27))

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(0, syncScheduler.scheduleSyncCallCount)
        }

    @Test
    fun `should push all local habits and check-ins then apply the remote-only snapshot when syncWithRemote is called`() =
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
                updatedAt = 1_000L,
            )
            val local = FakeLocalHabitDataSource(habits = mutableMapOf("local-habit" to localHabit))
            val remoteHabit = HabitRemoteDto(
                id = "remote-habit",
                userId = "user-1",
                name = "Read",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = "2026-07-20T00:00:00Z",
                updatedAt = "2026-07-20T00:00:00Z",
                deletedAt = null,
            )
            val remoteHabits = FakeRemoteHabitDataSource(habits = mutableMapOf("remote-habit" to remoteHabit))
            val remoteCheckIns = FakeRemoteHabitCheckInDataSource()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(
                local = local,
                remoteHabitDataSource = remoteHabits,
                remoteHabitCheckInDataSource = remoteCheckIns,
                authRepository = auth,
            )

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, remoteHabits.upsertCallCount)
            assertEquals(1, local.applyRemoteSnapshotCallCount)
            assertTrue(local.lastAppliedHabits.any { it.id == "remote-habit" })
        }

    @Test
    fun `should not resurrect a remotely-deleted habit during syncWithRemote`() =
        runTest {
            // Arrange - local has a stale, unsynced, non-deleted edit with a *newer* updatedAt than
            // the remote tombstone, exercising tombstone supremacy over plain timestamp comparison.
            val local = HabitEntity(
                id = "1",
                name = "Stale edit",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = 9_999_999L,
            )
            val localDataSource = FakeLocalHabitDataSource(habits = mutableMapOf("1" to local))
            val remoteTombstone = HabitRemoteDto(
                id = "1",
                userId = "user-1",
                name = "Deleted elsewhere",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = "2026-07-01T00:00:00Z",
                updatedAt = "2026-07-02T00:00:00Z",
                deletedAt = "2026-07-02T00:00:00Z",
            )
            val remoteHabits = FakeRemoteHabitDataSource(habits = mutableMapOf("1" to remoteTombstone))
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local = localDataSource, remoteHabitDataSource = remoteHabits, authRepository = auth)

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(localDataSource.getAllHabitsIncludingDeleted().none { it.id == "1" && it.deletedAt == null })
            assertEquals(0, remoteHabits.upsertCallCount)
        }

    @Test
    fun `should purge tombstoned habits older than the GC window during syncWithRemote`() =
        runTest {
            // Arrange
            val agedOut = Instant
                .now()
                .minus(TOMBSTONE_GC_WINDOW)
                .minusSeconds(3_600)
                .toEpochMilli()
            val tombstoned = HabitEntity(
                id = "1",
                name = "Long gone",
                description = null,
                type = "BINARY",
                scaleMin = null,
                scaleMax = null,
                createdAt = 1_000L,
                updatedAt = agedOut,
                deletedAt = agedOut,
            )
            val local = FakeLocalHabitDataSource(habits = mutableMapOf("1" to tombstoned))
            val remoteHabits = FakeRemoteHabitDataSource()
            val auth = FakeAuthRepository(currentUserId = "user-1")
            val repository = repository(local = local, remoteHabitDataSource = remoteHabits, authRepository = auth)

            // Act
            val result = repository.syncWithRemote()

            // Assert
            assertTrue(result.isSuccess)
            assertEquals(1, local.purgeDeletedBeforeCallCount)
            assertTrue(local.getAllHabitsIncludingDeleted().none { it.id == "1" })
            assertEquals(1, remoteHabits.purgeCallCount)
        }

    @Test
    fun `should delegate to the local data source when clearLocal is called`() =
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
            val local = FakeLocalHabitDataSource(habits = mutableMapOf("1" to habit))
            val repository = repository(local = local)

            // Act
            val result = repository.clearLocal()

            // Assert
            assertTrue(result.isSuccess)
            assertTrue(local.getAllHabitsIncludingDeleted().isEmpty())
        }
}
