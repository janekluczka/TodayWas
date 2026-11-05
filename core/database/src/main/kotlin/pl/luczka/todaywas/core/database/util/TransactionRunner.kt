package pl.luczka.todaywas.core.database.util

interface TransactionRunner {

    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
