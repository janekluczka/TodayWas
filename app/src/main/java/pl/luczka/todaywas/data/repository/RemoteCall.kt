package pl.luczka.todaywas.data.repository

import kotlinx.coroutines.CancellationException

suspend fun <T> remoteCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
