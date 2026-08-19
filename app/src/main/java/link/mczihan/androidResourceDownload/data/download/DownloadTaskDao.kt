package link.mczihan.androidResourceDownload.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus

@Dao
abstract class DownloadTaskDao {
    @Query(
        """
        SELECT * FROM download_tasks
        WHERE owner_id = :ownerId
        ORDER BY
            CASE status
                WHEN 'RUNNING' THEN 0
                WHEN 'PENDING' THEN 1
                WHEN 'PAUSED' THEN 2
                WHEN 'FAILED' THEN 3
                WHEN 'SUCCESS' THEN 4
                ELSE 5
            END,
            created_at DESC
        """,
    )
    abstract fun observeForOwner(ownerId: String): Flow<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT * FROM download_tasks
        WHERE owner_id = :ownerId AND remote_path = :remotePath
        ORDER BY created_at DESC
        LIMIT 1
        """,
    )
    abstract suspend fun findLatest(ownerId: String, remotePath: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE id = :taskId AND owner_id = :ownerId LIMIT 1")
    abstract suspend fun findById(ownerId: String, taskId: String): DownloadTaskEntity?

    @Insert
    abstract suspend fun insert(task: DownloadTaskEntity)

    @Query(
        """
        SELECT * FROM download_tasks
        WHERE owner_id = :ownerId AND status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT 1
        """,
    )
    protected abstract suspend fun nextPending(ownerId: String): DownloadTaskEntity?

    @Query(
        """
        UPDATE download_tasks
        SET status = 'RUNNING', error_message = NULL, updated_at = :now
        WHERE id = :taskId AND owner_id = :ownerId AND status = 'PENDING'
        """,
    )
    protected abstract suspend fun claim(taskId: String, ownerId: String, now: Long): Int

    @Transaction
    open suspend fun claimNext(ownerId: String, now: Long): DownloadTaskEntity? {
        while (true) {
            val candidate = nextPending(ownerId) ?: return null
            if (claim(candidate.id, ownerId, now) == 1) {
                return candidate.copy(status = DownloadStatus.RUNNING, errorMessage = null, updatedAt = now)
            }
        }
    }

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PENDING', error_message = NULL, updated_at = :now
        WHERE id = :taskId AND owner_id = :ownerId
          AND status IN ('PAUSED', 'FAILED', 'CANCELLED')
        """,
    )
    abstract suspend fun retry(ownerId: String, taskId: String, now: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PAUSED', updated_at = :now
        WHERE id = :taskId AND owner_id = :ownerId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun pause(ownerId: String, taskId: String, now: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PAUSED', updated_at = :now
        WHERE owner_id = :ownerId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun pauseRunning(ownerId: String, now: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'CANCELLED', error_message = NULL, updated_at = :now
        WHERE id = :taskId AND owner_id = :ownerId
          AND status IN ('PENDING', 'RUNNING', 'PAUSED', 'FAILED')
        """,
    )
    abstract suspend fun cancel(ownerId: String, taskId: String, now: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PENDING', updated_at = :now
        WHERE owner_id = :ownerId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun recoverRunning(ownerId: String, now: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET total_bytes = :totalBytes,
            downloaded_bytes = :downloadedBytes,
            support_range = :supportRange,
            etag = :etag,
            last_modified = :lastModified,
            mime_type = COALESCE(:mimeType, mime_type),
            updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun updatePreparation(
        taskId: String,
        totalBytes: Long?,
        downloadedBytes: Long,
        supportRange: Boolean,
        etag: String?,
        lastModified: String?,
        mimeType: String?,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET downloaded_bytes = :downloadedBytes, updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun updateProgress(taskId: String, downloadedBytes: Long, now: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'SUCCESS',
            total_bytes = :totalBytes,
            downloaded_bytes = :downloadedBytes,
            support_range = :supportRange,
            etag = :etag,
            last_modified = :lastModified,
            mime_type = COALESCE(:mimeType, mime_type),
            error_message = NULL,
            updated_at = :now
        WHERE id = :taskId AND status IN ('RUNNING', 'PAUSED')
        """,
    )
    abstract suspend fun complete(
        taskId: String,
        totalBytes: Long?,
        downloadedBytes: Long,
        supportRange: Boolean,
        etag: String?,
        lastModified: String?,
        mimeType: String?,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'FAILED', error_message = :message, updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun fail(taskId: String, message: String, now: Long): Int

    @Query(
        """
        UPDATE download_tasks
        SET status = 'PENDING', updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun requeueIfRunning(taskId: String, now: Long): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM download_tasks
            WHERE owner_id = :ownerId AND status = 'PENDING'
        )
        """,
    )
    abstract suspend fun hasPending(ownerId: String): Boolean
}
