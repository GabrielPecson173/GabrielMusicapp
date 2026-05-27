package com.example.ytmusicapp.domain.model

data class StreamInfo(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int,
    val durationMs: Long
)