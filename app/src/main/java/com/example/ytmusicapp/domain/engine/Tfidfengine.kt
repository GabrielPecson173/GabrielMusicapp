package com.example.ytmusicapp.domain.engine

import com.example.ytmusicapp.data.db.entity.RecommendationCandidateEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.sqrt

@Singleton
class TfIdfEngine @Inject constructor() : EmbeddingProvider {

    // vocab: term → column index
    private var vocab: Map<String, Int> = emptyMap()
    // idf: term → idf weight
    private var idf: Map<String, Float> = emptyMap()
    // corpus size last indexed
    private var corpusSize: Int = 0

    // ── EmbeddingProvider impl ────────────────────────────────────────────────

    override suspend fun embedText(text: String): FloatArray {
        if (vocab.isEmpty()) return FloatArray(0)
        val tokens = tokenize(text)
        return tfidfVector(tokens)
    }

    override fun dimensions(): Int = vocab.size

    // ── Index building ────────────────────────────────────────────────────────

    fun buildIndex(candidates: List<RecommendationCandidateEntity>) {
        if (candidates.isEmpty()) return
        corpusSize = candidates.size

        val tokenizedDocs = candidates.map { tokenize(it.metadataTokens) }

        // document frequency per term
        val df = mutableMapOf<String, Int>()
        tokenizedDocs.forEach { tokens ->
            tokens.toSet().forEach { term -> df[term] = (df[term] ?: 0) + 1 }
        }

        // build vocab from all terms
        val allTerms = df.keys.sorted()
        vocab = allTerms.mapIndexed { i, term -> term to i }.toMap()

        // idf = ln((N + 1) / (df + 1)) + 1  (smoothed)
        idf = vocab.keys.associateWith { term ->
            val docFreq = df[term] ?: 0
            (ln((corpusSize + 1.0) / (docFreq + 1.0)) + 1.0).toFloat()
        }
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    fun scoreCandidate(
        seedTokens: List<String>,
        candidate: RecommendationCandidateEntity
    ): Float {
        if (vocab.isEmpty()) return 0f
        val seedVec = tfidfVector(seedTokens)
        val candVec = tfidfVector(tokenize(candidate.metadataTokens))
        return cosineSimilarity(seedVec, candVec)
    }

    fun rankCandidates(
        seedText: String,
        candidates: List<RecommendationCandidateEntity>
    ): List<Pair<RecommendationCandidateEntity, Float>> {
        val seedTokens = tokenize(seedText)
        return candidates
            .map { it to scoreCandidate(seedTokens, it) }
            .sortedByDescending { it.second }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun tfidfVector(tokens: List<String>): FloatArray {
        val vec = FloatArray(vocab.size)
        val tf = tokens.groupingBy { it }.eachCount()
        val maxTf = tf.values.maxOrNull()?.toFloat() ?: 1f

        tf.forEach { (term, count) ->
            val idx = vocab[term] ?: return@forEach
            val tfNorm = count.toFloat() / maxTf
            val idfWeight = idf[term] ?: 0f
            vec[idx] = tfNorm * idfWeight
        }
        return vec
    }

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

    fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
}