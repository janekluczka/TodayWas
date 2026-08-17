package pl.luczka.todaywas.data.util

interface TransactionRunner {

    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
