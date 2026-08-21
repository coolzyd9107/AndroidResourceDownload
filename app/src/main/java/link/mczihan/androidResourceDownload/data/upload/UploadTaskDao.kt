package link.mczihan.androidResourceDownload.data.upload

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import link.mczihan.androidResourceDownload.domain.model.UploadStatus

@Dao
abstract class UploadTaskDao {
    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE owner_id = :ownerId
        ORDER BY
            CASE status
                WHEN 'RUNNING' THEN 0
                WHEN 'PENDING' THEN 1
                WHEN 'FAILED' THEN 2
                WHEN 'SUCCESS' THEN 3
                ELSE 4
            END,
            created_at DESC,
            queue_order ASC
        """,
    )
    abstract fun observeForOwner(ownerId: String): Flow<List<UploadTaskEntity>>

    @Insert
    abstract suspend fun insertAll(tasks: List<UploadTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun reservePermission(permission: UploadPermissionEntity)

    @Query("SELECT * FROM upload_permissions")
    abstract suspend fun allPermissionReservations(): List<UploadPermissionEntity>

    @Query("DELETE FROM upload_permissions WHERE uri = :uri")
    abstract suspend fun deletePermissionReservation(uri: String): Int

    @Query("SELECT * FROM upload_tasks WHERE id = :taskId AND owner_id = :ownerId LIMIT 1")
    abstract suspend fun findById(ownerId: String, taskId: String): UploadTaskEntity?

    @Query("SELECT status FROM upload_tasks WHERE id = :taskId LIMIT 1")
    abstract suspend fun status(taskId: String): UploadStatus?

    @Query(
        """
        SELECT remote_path FROM upload_tasks
        WHERE owner_id = :ownerId AND status IN ('PENDING', 'RUNNING')
        """,
    )
    abstract suspend fun activeRemotePaths(ownerId: String): List<String>

    @Query(
        """
        SELECT * FROM upload_tasks AS candidate
        WHERE owner_id = :ownerId AND status = 'PENDING' AND is_directory = 1
          AND NOT EXISTS(
              SELECT 1 FROM upload_tasks AS parent
              WHERE parent.owner_id = candidate.owner_id
                AND parent.batch_id = candidate.batch_id
                AND parent.is_directory = 1
                AND parent.path_depth < candidate.path_depth
                AND parent.status != 'SUCCESS'
          )
        ORDER BY created_at ASC, path_depth ASC, queue_order ASC
        LIMIT 1
        """,
    )
    protected abstract suspend fun nextPendingDirectory(ownerId: String): UploadTaskEntity?

    @Query(
        """
        SELECT * FROM upload_tasks
        WHERE owner_id = :ownerId AND status = 'PENDING' AND is_directory = 0
          AND (
            is_tree_upload = 0 OR NOT EXISTS(
                SELECT 1 FROM upload_tasks AS directories
                WHERE directories.owner_id = upload_tasks.owner_id
                  AND directories.batch_id = upload_tasks.batch_id
                  AND directories.is_directory = 1
                  AND directories.status != 'SUCCESS'
            )
          )
        ORDER BY created_at ASC, queue_order ASC
        LIMIT 1
        """,
    )
    protected abstract suspend fun nextPendingFile(ownerId: String): UploadTaskEntity?

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'RUNNING', uploaded_bytes = 0, committing = 0,
            error_message = NULL, updated_at = :now
        WHERE id = :taskId AND owner_id = :ownerId AND status = 'PENDING'
        """,
    )
    protected abstract suspend fun claim(taskId: String, ownerId: String, now: Long): Int

    @Transaction
    open suspend fun claimNextDirectory(ownerId: String, now: Long): UploadTaskEntity? =
        claimNext(ownerId, now, ::nextPendingDirectory)

    @Transaction
    open suspend fun claimNextFile(ownerId: String, now: Long): UploadTaskEntity? =
        claimNext(ownerId, now, ::nextPendingFile)

    private suspend fun claimNext(
        ownerId: String,
        now: Long,
        candidate: suspend (String) -> UploadTaskEntity?,
    ): UploadTaskEntity? {
        while (true) {
            val task = candidate(ownerId) ?: return null
            if (claim(task.id, ownerId, now) == 1) {
                return task.copy(
                    status = UploadStatus.RUNNING,
                    uploadedBytes = 0L,
                    committing = false,
                    errorMessage = null,
                    updatedAt = now,
                )
            }
        }
    }

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'PENDING', uploaded_bytes = 0, committing = 0,
            error_message = NULL, updated_at = :now
        WHERE id = :taskId AND owner_id = :ownerId
          AND status IN ('FAILED', 'CANCELLED')
          AND (is_directory = 1 OR source_uri IS NOT NULL)
        """,
    )
    abstract suspend fun retry(ownerId: String, taskId: String, now: Long): Int

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'PENDING', uploaded_bytes = 0, committing = 0,
            error_message = NULL, updated_at = :now
        WHERE owner_id = :ownerId AND batch_id = :batchId AND status = 'FAILED'
          AND (is_directory = 1 OR source_uri IS NOT NULL)
        """,
    )
    abstract suspend fun retryFailedBatch(ownerId: String, batchId: String, now: Long): Int

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'CANCELLED', committing = 0, error_message = NULL, updated_at = :now
        WHERE id = :taskId AND owner_id = :ownerId AND is_directory = 0
          AND committing = 0 AND status IN ('PENDING', 'RUNNING', 'FAILED')
        """,
    )
    abstract suspend fun cancelFile(ownerId: String, taskId: String, now: Long): Int

    @Query(
        """
        DELETE FROM upload_tasks
        WHERE id = :taskId AND owner_id = :ownerId
          AND status IN ('SUCCESS', 'FAILED', 'CANCELLED')
        """,
    )
    abstract suspend fun deleteTerminal(ownerId: String, taskId: String): Int

    @Query("SELECT * FROM upload_tasks WHERE owner_id = :ownerId AND batch_id = :batchId")
    abstract suspend fun tasksForBatch(ownerId: String, batchId: String): List<UploadTaskEntity>

    @Query(
        """
        DELETE FROM upload_tasks
        WHERE owner_id = :ownerId AND batch_id = :batchId
          AND status IN ('SUCCESS', 'FAILED', 'CANCELLED')
        """,
    )
    abstract suspend fun deleteTerminalBatch(ownerId: String, batchId: String): Int

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'PENDING', uploaded_bytes = 0, committing = 0, updated_at = :now
        WHERE owner_id = :ownerId AND status = 'RUNNING' AND committing = 0
        """,
    )
    abstract suspend fun recoverRunning(ownerId: String, now: Long): Int

    @Query("SELECT * FROM upload_tasks WHERE owner_id = :ownerId AND status = 'RUNNING'")
    abstract suspend fun runningForOwner(ownerId: String): List<UploadTaskEntity>

    @Query(
        """
        UPDATE upload_tasks
        SET total_bytes = :totalBytes, uploaded_bytes = 0, updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING' AND committing = 0
        """,
    )
    abstract suspend fun updatePreparation(taskId: String, totalBytes: Long?, now: Long): Int

    @Query(
        """
        UPDATE upload_tasks
        SET uploaded_bytes = MAX(uploaded_bytes, :uploadedBytes), updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING' AND committing = 0
        """,
    )
    abstract suspend fun updateProgress(taskId: String, uploadedBytes: Long, now: Long): Int

    @Query(
        """
        UPDATE upload_tasks
        SET committing = 1,
            uploaded_bytes = MAX(uploaded_bytes, COALESCE(total_bytes, uploaded_bytes)),
            updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING' AND committing = 0
        """,
    )
    abstract suspend fun markCommitting(taskId: String, now: Long): Int

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'SUCCESS',
            total_bytes = CASE
                WHEN is_directory = 1 THEN total_bytes
                ELSE COALESCE(total_bytes, :uploadedBytes)
            END,
            uploaded_bytes = :uploadedBytes,
            source_uri = NULL,
            permission_uri = NULL,
            committing = 0,
            error_message = NULL,
            updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun complete(taskId: String, uploadedBytes: Long, now: Long): Int

    @Query("DELETE FROM upload_tasks WHERE id = :taskId AND status = 'SUCCESS'")
    protected abstract suspend fun deleteCompleted(taskId: String): Int

    @Transaction
    open suspend fun completeAndDelete(
        taskId: String,
        uploadedBytes: Long,
        now: Long,
    ): Boolean {
        if (complete(taskId, uploadedBytes, now) != 1) return false
        return deleteCompleted(taskId) == 1
    }

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'FAILED', committing = 0, error_message = :message, updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun fail(taskId: String, message: String, now: Long): Int

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'FAILED', committing = 0, error_message = :message, updated_at = :now
        WHERE owner_id = :ownerId AND batch_id = :batchId AND status = 'PENDING'
        """,
    )
    abstract suspend fun failPendingBatch(
        ownerId: String,
        batchId: String,
        message: String,
        now: Long,
    ): Int

    @Transaction
    open suspend fun failDirectoryBatch(
        ownerId: String,
        batchId: String,
        taskId: String,
        message: String,
        now: Long,
    ) {
        fail(taskId, message, now)
        failPendingBatch(ownerId, batchId, message, now)
    }

    @Query(
        """
        UPDATE upload_tasks
        SET status = 'PENDING', uploaded_bytes = 0, committing = 0, updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun requeueIfRunning(taskId: String, now: Long): Int

    @Query(
        """
        UPDATE upload_tasks
        SET error_message = :message, updated_at = :now
        WHERE id = :taskId AND status = 'RUNNING'
        """,
    )
    abstract suspend fun markReconciliationBlocked(taskId: String, message: String, now: Long): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM upload_tasks
            WHERE owner_id = :ownerId AND status IN ('PENDING', 'RUNNING')
        )
        """,
    )
    abstract suspend fun hasRunnable(ownerId: String): Boolean

    @Query("SELECT COUNT(*) FROM upload_tasks WHERE permission_uri = :permissionUri")
    abstract suspend fun countPermissionReferences(permissionUri: String): Int
}
