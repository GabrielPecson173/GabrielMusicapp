package com.example.ytmusicapp.domain.engine

interface EmbeddingProvider {
    suspend fun embedText(text: String): FloatArray
    fun dimensions(): Int
}