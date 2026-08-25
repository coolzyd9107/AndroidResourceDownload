package com.resdownload.android.data.mock

import java.util.Base64
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask
import com.resdownload.android.domain.model.FileNode
import com.resdownload.android.domain.model.FilePreviewContent

private const val HOUR_MILLIS = 60L * 60L * 1_000L
private const val DAY_MILLIS = 24L * HOUR_MILLIS

fun mockFilesForPath(path: String, now: Long = System.currentTimeMillis()): List<FileNode>? = when (path) {
    "/" -> listOf(
        FileNode("应用发布", "/应用发布", true, lastModified = now - HOUR_MILLIS),
        FileNode("设计资料", "/设计资料", true, lastModified = now - DAY_MILLIS),
        FileNode("归档", "/归档", true, lastModified = now - 8L * DAY_MILLIS),
        FileNode("暂不可用", "/暂不可用", true, lastModified = now - 2L * DAY_MILLIS),
        FileNode("使用说明.pdf", "/使用说明.pdf", false, 2_840_576L, now - 3L * HOUR_MILLIS, "application/pdf", "mock-guide-v2"),
        FileNode("预览示例.png", "/预览示例.png", false, 68L, now - 4L * HOUR_MILLIS, "image/png", "mock-image-v1"),
    )
    "/应用发布" -> listOf(
        FileNode("android-client-1.1.0.apk", "/应用发布/android-client-1.1.0.apk", false, 38_624_256L, now - 2L * HOUR_MILLIS, "application/vnd.android.package-archive", "mock-apk-110"),
        FileNode("release-notes.txt", "/应用发布/release-notes.txt", false, 8_192L, now - 2L * HOUR_MILLIS, "text/plain", "mock-notes-110"),
    )
    "/设计资料" -> listOf(
        FileNode("界面规范.pdf", "/设计资料/界面规范.pdf", false, 12_582_912L, now - DAY_MILLIS, "application/pdf", "mock-design-v4"),
    )
    "/归档" -> emptyList()
    "/暂不可用" -> null
    else -> emptyList()
}

fun mockPreviewForFile(file: FileNode): FilePreviewContent? = when (file.path) {
    "/应用发布/release-notes.txt" -> FilePreviewContent.Text(
        text = """
            Android Resource Download 2.3.0

            - 新增云端文件管理能力
            - 优化下载任务恢复
            - 完善深色模式显示
        """.trimIndent(),
        contentType = "text/plain; charset=utf-8",
        entityTag = "\"mock-preview-v1\"",
    )
    "/预览示例.png" -> FilePreviewContent.Image(
        bytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        ),
        mimeType = "image/png",
    )
    else -> null
}

fun initialMockDownloads(now: Long = System.currentTimeMillis()): List<DownloadTask> = listOf(
    DownloadTask(
        id = "mock-running",
        fileName = "android-client-1.1.0.apk",
        remotePath = "/应用发布/android-client-1.1.0.apk",
        mimeType = "application/vnd.android.package-archive",
        totalBytes = 38_624_256L,
        downloadedBytes = 24_117_248L,
        status = DownloadStatus.RUNNING,
        supportRange = true,
        createdAt = now - 720_000L,
        updatedAt = now,
    ),
    DownloadTask(
        id = "mock-success",
        fileName = "使用说明.pdf",
        remotePath = "/使用说明.pdf",
        mimeType = "application/pdf",
        totalBytes = 2_840_576L,
        downloadedBytes = 2_840_576L,
        status = DownloadStatus.SUCCESS,
        supportRange = true,
        createdAt = now - DAY_MILLIS,
        updatedAt = now - DAY_MILLIS,
    ),
    DownloadTask(
        id = "mock-failed",
        fileName = "界面规范.pdf",
        remotePath = "/设计资料/界面规范.pdf",
        mimeType = "application/pdf",
        totalBytes = 12_582_912L,
        downloadedBytes = 3_145_728L,
        status = DownloadStatus.FAILED,
        supportRange = true,
        createdAt = now - 2L * DAY_MILLIS,
        updatedAt = now - 2L * DAY_MILLIS,
    ),
)

fun mockTaskForFile(file: FileNode, now: Long = System.currentTimeMillis()) = DownloadTask(
    id = "mock-${file.path.hashCode()}-$now",
    fileName = file.name,
    remotePath = file.path,
    mimeType = file.mimeType,
    totalBytes = file.size,
    status = DownloadStatus.PENDING,
    supportRange = true,
    etag = file.etag,
    createdAt = now,
    updatedAt = now,
)
