package com.resdownload.android.service

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadConcurrencyGateTest {
    @Test
    fun fourthFileWaitsAndStartsWhenOneOfThreeSlotsCompletes() = runTest {
        val gate = UploadConcurrencyGate(maxParallel = 3)
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val started = Channel<Int>(Channel.UNLIMITED)
        val releases = List(4) { CompletableDeferred<Unit>() }

        val jobs = List(4) { index ->
            launch {
                gate.withFileSlot {
                    val count = active.incrementAndGet()
                    maxActive.updateAndGet { current -> maxOf(current, count) }
                    started.send(index)
                    try {
                        releases[index].await()
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
        }

        val initial = List(3) { started.receive() }.toSet()
        assertEquals(3, initial.size)
        assertTrue(started.tryReceive().isFailure)
        assertEquals(3, maxActive.get())

        releases[initial.first()].complete(Unit)
        val waitingTask = started.receive()

        assertTrue(waitingTask !in initial)
        assertEquals(3, maxActive.get())
        releases.forEach { it.complete(Unit) }
        jobs.joinAll()
        assertEquals(0, active.get())
    }
}
