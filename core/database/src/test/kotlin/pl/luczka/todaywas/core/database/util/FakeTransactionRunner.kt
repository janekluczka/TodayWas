package pl.luczka.todaywas.core.database.util

class FakeTransactionRunner : TransactionRunner {

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}
