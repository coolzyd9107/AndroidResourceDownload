package com.resdownload.android.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadExecutionRegistryTest {
    @Test
    fun tracksAndCancelsConcurrentDownloads() = runTest {
        val registry = DownloadExecutionRegistry()
        val ownerId = "owner"
        val jobs = List(3) { Job() }
        registry.activate(ownerId)

        jobs.forEachIndexed { index, job ->
            assertTrue(registry.register(ownerId, "task-$index", job))
        }

        registry.cancelTaskAndJoin("task-0")
        assertTrue(jobs[0].isCancelled)
        assertTrue(jobs[1].isActive)
        assertTrue(jobs[2].isActive)

        registry.cancelOwnerAndJoin(ownerId)
        assertTrue(jobs.all(Job::isCancelled))
    }

    @Test
    fun blockedOwnerRejectsNewDownloadsUntilActivated() = runTest {
        val registry = DownloadExecutionRegistry()
        val ownerId = "owner"
        registry.blockOwner(ownerId)

        val rejected = Job()
        assertFalse(registry.register(ownerId, "rejected", rejected))
        assertTrue(rejected.isCancelled)

        registry.activate(ownerId)
        val accepted = Job()
        assertTrue(registry.register(ownerId, "accepted", accepted))
        registry.cancelOwnerAndJoin(ownerId)
        assertTrue(accepted.isCancelled)
    }

    @Test
    fun newServiceGenerationWaitsForOldDownloadsAndReopensRegistration() = runTest {
        val registry = DownloadExecutionRegistry()
        val ownerId = "owner"
        val oldJob = Job()
        registry.activate(ownerId)
        assertTrue(registry.register(ownerId, "old", oldJob))

        registry.blockAll()
        registry.awaitQuiescenceAndOpenServiceGeneration()
        assertTrue(oldJob.isCancelled)

        val newJob = Job()
        assertTrue(registry.register(ownerId, "new", newJob))
        registry.cancelOwnerAndJoin(ownerId)
        assertTrue(newJob.isCancelled)
    }
}
