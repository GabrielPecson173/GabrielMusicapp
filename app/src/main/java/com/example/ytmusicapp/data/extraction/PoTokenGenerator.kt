package com.example.ytmusicapp.data.extraction

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoTokenGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Stubbed — not used while ExtractionStrategy uses NewPipe directly
    suspend fun generatePoToken(videoId: String): String = ""
}