package pl.luczka.todaywas.data.util

import kotlinx.coroutines.CancellationException

// Retries once before giving up, per the plan's write-resilience contract.
suspend fun safeDbCall(block: suspend () -> Unit): Result<Unit> = try {
    block()
    Result.success(Unit)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    try {
        block()
        Result.success(Unit)
    } catch (retryException: CancellationException) {
        throw retryException
    } catch (retryException: Exception) {
        Result.failure(retryException)
    }
}
