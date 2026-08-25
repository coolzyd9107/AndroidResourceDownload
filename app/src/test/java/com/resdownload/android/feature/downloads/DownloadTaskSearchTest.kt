package com.resdownload.android.feature.downloads

import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadTaskSearchTest {
    private val tasks = listOf(
        task(
            id = "report",
            fileName = "Quarterly-Report.PDF",
            remotePath = "/资料/Quarterly-Report.PDF",
            status = DownloadStatus.SUCCESS,
        ),
        task(
            id = "archive",
            fileName = "archive.zip",
            remotePath = "/归档/archive.zip",
            status = DownloadStatus.RUNNING,
        ),
    )

    @Test
    fun matchesNamePathAndLocalizedStatusIgnoringCase() {
        assertEquals(listOf("report"), filterDownloadTasks(tasks, "REPORT").map { it.id })
        assertEquals(listOf("archive"), filterDownloadTasks(tasks, "归档").map { it.id })
        assertEquals(listOf("report"), filterDownloadTasks(tasks, "已完成").map { it.id })
    }

    @Test
    fun blankQueryPreservesAllTasks() {
        assertEquals(tasks, filterDownloadTasks(tasks, "   "))
    }

    private fun task(
        id: String,
        fileName: String,
        remotePath: String,
        status: DownloadStatus,
    ) = DownloadTask(
        id = id,
        fileName = fileName,
        remotePath = remotePath,
        status = status,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
