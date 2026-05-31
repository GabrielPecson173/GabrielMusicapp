package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_embeddings",
    indices = [Index(value = ["videoId"], unique = true)]
)
data class TrackEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackEmbeddingEntity) return false
        return id == other.id &&
                videoId == other.videoId &&
                embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + videoId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}