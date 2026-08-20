package pl.luczka.todaywas.data.util

import androidx.room.withTransaction
import pl.luczka.todaywas.data.local.database.TodayWasDatabase
import javax.inject.Inject

class RoomTransactionRunner @Inject constructor(
    private val database: TodayWasDatabase,
) : TransactionRunner {

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = database.withTransaction(
        block,
    )
}
