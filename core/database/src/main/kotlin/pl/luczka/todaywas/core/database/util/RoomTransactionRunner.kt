package pl.luczka.todaywas.core.database.util

import androidx.room.withTransaction
import javax.inject.Inject
import pl.luczka.todaywas.core.database.TodayWasDatabase

class RoomTransactionRunner @Inject constructor(
    private val database: TodayWasDatabase,
) : TransactionRunner {

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = database.withTransaction(block)
}
