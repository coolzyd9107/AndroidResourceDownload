package com.resdownload.android.feature.uploads

import com.resdownload.android.domain.model.UploadStatus
import com.resdownload.android.domain.model.UploadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadTaskSearchTest {
    private val tasks = listOf(
        task(
            id = "photo",
            fileName = "Vacation.JPG",
            relativePath = "相册/Vacation.JPG",
            status = UploadStatus.SUCCESS,
        ),
        task(
            id = "draft",
            fileName = "draft.txt",
            relativePath = "文档/draft.txt",
            status = UploadStatus.RUNNING,
            committing = true,
        ),
    )

    @Test
    fun matchesNamePathAndDynamicLocalizedStatusIgnoringCase() {
        assertEquals(listOf("photo"), filterUploadTasks(tasks, "vacation").map { it.id })
        assertEquals(listOf("draft"), filterUploadTasks(tasks, "文档").map { it.id })
        assertEquals(listOf("draft"), filterUploadTasks(tasks, "提交中").map { it.id })
    }

    @Test
    fun blankQueryPreservesAllTasks() {
        assertEquals(tasks, filterUploadTasks(tasks, "\t"))
    }

    private fun task(
        id: String,
        fileName: String,
        relativePath: String,
        status: UploadStatus,
        committing: Boolean = false,
    ) = UploadTask(
        id = id,
        ownerId = "owner",
        batchId = "batch",
        fileName = fileName,
        relativePath = relativePath,
        destinationRoot = "/",
        remotePath = "/$relativePath",
        sourceUri = "content://source/$id",
        permissionUri = "content://source/$id",
        isDirectory = false,
        isTreeUpload = false,
        mimeType = null,
        totalBytes = null,
        status = status,
        committing = committing,
        queueOrder = 0,
        pathDepth = 1,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
