package link.mczihan.androidResourceDownload.service

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class UploadConcurrencyGate(maxParallel: Int = 3) {
    private val semaphore = Semaphore(maxParallel.also { require(it > 0) })

    suspend fun <T> withFileSlot(block: suspend () -> T): T = semaphore.withPermit { block() }
}
