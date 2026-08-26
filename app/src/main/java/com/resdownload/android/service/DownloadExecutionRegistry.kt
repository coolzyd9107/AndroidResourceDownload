package com.resdownload.android.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.yield

@Singleton
class DownloadExecutionRegistry @Inject constructor() {
    private data class ActiveTransfer(
        val ownerId: String,
        val job: Job,
    )

    private val lock = Any()
    private val activeTransfers = mutableMapOf<String, ActiveTransfer>()
    private val closedOwners = mutableSetOf<String>()
    private var closedAll = false

    fun activate(ownerId: String) {
        synchronized(lock) { closedOwners.remove(ownerId) }
    }

    suspend fun awaitQuiescenceAndOpenServiceGeneration() {
        while (true) {
            val jobs = synchronized(lock) { activeTransfers.values.map(ActiveTransfer::job) }
            jobs.joinAll()
            val opened = synchronized(lock) {
                if (activeTransfers.isEmpty()) {
                    closedAll = false
                    true
                } else {
                    false
                }
            }
            if (opened) return
            yield()
        }
    }

    fun closeOwner(ownerId: String) {
        synchronized(lock) { closedOwners += ownerId }
    }

    fun blockOwner(ownerId: String) {
        val jobs = synchronized(lock) {
            closedOwners += ownerId
            activeTransfers.values.filter { it.ownerId == ownerId }.map(ActiveTransfer::job)
        }
        jobs.forEach(Job::cancel)
    }

    fun register(ownerId: String, taskId: String, job: Job): Boolean {
        val accepted = synchronized(lock) {
            if (closedAll || ownerId in closedOwners) false else {
                activeTransfers[taskId] = ActiveTransfer(ownerId, job)
                true
            }
        }
        if (accepted) {
            job.invokeOnCompletion { clear(taskId, job) }
        } else {
            job.cancel()
        }
        return accepted
    }

    fun clear(taskId: String, job: Job) {
        synchronized(lock) {
            activeTransfers[taskId]?.takeIf { it.job === job }?.let {
                activeTransfers.remove(taskId)
            }
        }
    }

    fun cancelTask(taskId: String) {
        synchronized(lock) { activeTransfers[taskId]?.job }?.cancel()
    }

    suspend fun cancelTaskAndJoin(taskId: String) {
        synchronized(lock) { activeTransfers[taskId]?.job }?.cancelAndJoin()
    }

    fun cancelOwner(ownerId: String) {
        synchronized(lock) {
            activeTransfers.values.filter { it.ownerId == ownerId }.map(ActiveTransfer::job)
        }.forEach(Job::cancel)
    }

    suspend fun cancelOwnerAndJoin(ownerId: String) {
        val jobs = synchronized(lock) {
            activeTransfers.values.filter { it.ownerId == ownerId }.map(ActiveTransfer::job)
        }
        jobs.forEach(Job::cancel)
        jobs.joinAll()
    }

    fun blockAll() {
        val jobs = synchronized(lock) {
            closedAll = true
            activeTransfers.values.map(ActiveTransfer::job)
        }
        jobs.forEach(Job::cancel)
    }
}
