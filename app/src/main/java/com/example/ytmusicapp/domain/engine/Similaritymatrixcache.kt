package com.example.ytmusicapp.domain.engine

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class SimilarityMatrixCache @Inject constructor() {

    // key: "videoIdA::videoIdB" (always sorted so A < B for dedup)
    private val cache = ConcurrentHashMap<String, Float>()

    fun get(videoIdA: String, videoIdB: String): Float? =
        cache[cacheKey(videoIdA, videoIdB)]

    fun put(videoIdA: String, videoIdB: String, score: Float) {
        cache[cacheKey(videoIdA, videoIdB)] = score
    }

    fun getOrCompute(
        videoIdA: String,
        embeddingA: FloatArray,
        videoIdB: String,
        embeddingB: FloatArray
    ): Float {
        val key = cacheKey(videoIdA, videoIdB)
        return cache.getOrPut(key) { cosineSimilarity(embeddingA, embeddingB) }
    }

    fun invalidate(videoId: String) {
        val keysToRemove = cache.keys.filter { it.contains(videoId) }
        keysToRemove.forEach { cache.remove(it) }
    }

    fun clear() = cache.clear()

    fun size(): Int = cache.size

    private fun cacheKey(a: String, b: String): String =
        if (a <= b) "$a::$b" else "$b::$a"

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}