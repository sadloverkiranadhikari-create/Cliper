package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "clip_projects")
data class ClipProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val youtubeUrl: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val videoSummary: String,
    val clipFocus: String,
    val targetDurationRange: String,
    val attachedSourceUri: String? = null,
    val attachedSourceLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_clips",
    foreignKeys = [
        ForeignKey(
            entity = ClipProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class VideoClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val title: String,
    val startSeconds: Int,
    val endSeconds: Int,
    val viralScore: Int,
    val hookLine: String,
    val summary: String,
    val suggestedCaption: String,
    val hashtagsCsv: String,
    val exportedMediaUri: String? = null,
    val exportedFileName: String? = null,
    val exportedFileSizeBytes: Long = 0L,
    val exportedAt: Long? = null
)

data class ProjectWithClips(
    @Embedded val project: ClipProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val clips: List<VideoClipEntity>
)

data class ExportedClipWithProject(
    @Embedded val clip: VideoClipEntity,
    @Relation(
        parentColumn = "projectId",
        entityColumn = "id"
    )
    val project: ClipProjectEntity?
)
