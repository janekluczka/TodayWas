package pl.luczka.todaywas.data.util

class FakeTransactionRunner : TransactionRunner {

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}
