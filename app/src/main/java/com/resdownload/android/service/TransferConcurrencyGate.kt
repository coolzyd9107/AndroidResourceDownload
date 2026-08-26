package com.resdownload.android.service

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val MAX_PARALLEL_TRANSFERS = 3

internal class TransferConcurrencyGate(maxParallel: Int = MAX_PARALLEL_TRANSFERS) {
    private val semaphore = Semaphore(maxParallel.also { require(it > 0) })

    suspend fun <T> withSlot(block: suspend () -> T): T = semaphore.withPermit { block() }
}
