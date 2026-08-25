package com.resdownload.android.feature.downloads

import com.resdownload.android.domain.model.FileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPermissionQueueTest {
    @Test
    fun queuesEveryDeferredActionAndRequestsPermissionOnlyOnce() {
        val queue = DownloadPermissionQueue()
        val first = FileNode("first.txt", "/first.txt", isDirectory = false)
        val second = FileNode("second.txt", "/nested/second.txt", isDirectory = false)

        assertTrue(queue.deferDownload(first, ""))
        assertFalse(queue.deferDownload(second, "nested"))
        assertFalse(queue.deferDownload(first, ""))
        assertFalse(queue.deferRetry("retry-one"))
        assertFalse(queue.deferRetry("retry-one"))
        assertFalse(queue.deferStartPending())

        val work = queue.take()

        assertEquals(listOf(first to "", second to "nested"), work.downloads)
        assertEquals(listOf("retry-one"), work.retryIds)
        assertTrue(work.startPending)
        assertTrue(queue.deferRetry("new-request"))
    }
}
