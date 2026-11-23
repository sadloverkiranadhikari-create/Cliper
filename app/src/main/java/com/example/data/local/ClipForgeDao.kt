package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipForgeDao {
    @Transaction
    @Query("SELECT * FROM clip_projects ORDER BY createdAt DESC")
    fun getAllProjectsWithClips(): Flow<List<ProjectWithClips>>

    @Transaction
    @Query("SELECT * FROM clip_projects WHERE id = :projectId")
    fun getProjectWithClips(projectId: Long): Flow<ProjectWithClips?>

    @Transaction
    @Query("SELECT * FROM clip_projects WHERE id = :projectId")
    suspend fun getProjectWithClipsOnce(projectId: Long): ProjectWithClips?

    @Transaction
    @Query("SELECT * FROM video_clips WHERE exportedMediaUri IS NOT NULL ORDER BY exportedAt DESC")
    fun getExportedClips(): Flow<List<ExportedClipWithProject>>

    @Query("SELECT * FROM video_clips WHERE id = :clipId")
    suspend fun getClipById(clipId: Long): VideoClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ClipProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<VideoClipEntity>)

    @Query(
        "UPDATE clip_projects SET attachedSourceUri = :uri, attachedSourceLabel = :label WHERE id = :projectId"
    )
    suspend fun updateAttachedSource(projectId: Long, uri: String?, label: String?)

    @Query("UPDATE video_clips SET startSeconds = :startSeconds, endSeconds = :endSeconds WHERE id = :clipId")
    suspend fun updateClipRange(clipId: Long, startSeconds: Int, endSeconds: Int)

    @Query(
        "UPDATE video_clips SET exportedMediaUri = :uri, exportedFileName = :fileName, exportedFileSizeBytes = :sizeBytes, exportedAt = :exportedAt WHERE id = :clipId"
    )
    suspend fun markClipExported(
        clipId: Long,
        uri: String,
        fileName: String,
        sizeBytes: Long,
        exportedAt: Long
    )

    @Query("DELETE FROM clip_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)
}
