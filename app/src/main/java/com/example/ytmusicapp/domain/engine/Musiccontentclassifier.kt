package com.example.ytmusicapp.domain.engine

import javax.inject.Inject
import javax.inject.Singleton

// Word-boundary matching so e.g. "mv" doesn't match inside "movement", "live" doesn't match
// inside "delivery", etc.
private fun keywordRegex(words: List<String>): Regex =
    Regex("""\b(${words.joinToString("|") { Regex.escape(it) }})\b""", RegexOption.IGNORE_CASE)

private val POSITIVE_KEYWORDS = keywordRegex(
    listOf(
        "official music video", "official audio", "official video", "lyric video", "lyrics",
        "cover", "sings", "singing", "karaoke", "live", "concert", "performance", "acoustic",
        "mv", "ost", "remix", "instrumental"
    )
)

private val NEGATIVE_KEYWORDS = keywordRegex(
    listOf(
        "gameplay", "reaction", "let's play", "commentary", "podcast", "interview", "review",
        "tutorial", "walkthrough", "clip", "funny", "meme", "stream archive", "vod",
        "unboxing", "vlog"
    )
)

private const val POSITIVE_CAP = 4.0f
private const val NEGATIVE_CAP = -6.0f
private const val POSITIVE_PER_HIT = 1.5f
private const val NEGATIVE_PER_HIT = -2.0f
private const val LOCAL_MATCH_BONUS = 3.0f
private const val NON_MUSIC_FLOOR = -4.0f

/**
 * Scores how likely a YouTube search result is to be legitimate music content, using only the
 * metadata actually available from a search-tab result (title, uploader/channel name, duration —
 * NewPipeExtractor's `StreamInfoItem` from `SearchExtractor.fetchPage()`, not the richer
 * `StreamInfo.getInfo()` used elsewhere for an already-selected video). No ML, no external calls —
 * a keyword/duration scoring function, replacing the previous hard `duration in 60..600` cutoff
 * that excluded legitimate long-form content (concerts, extended performances) while doing nothing
 * to catch obviously non-music results.
 *
 * This produces a continuous score for classification-assisted filtering/demotion layered on top
 * of the caller's existing result order — it is NOT a replacement ranking algorithm. Callers
 * should drop only [isLikelyNonMusic] results and otherwise preserve their own relevance ordering
 * (e.g. via a stable partition), not sort purely by this score: a 0.0 doesn't mean "more musically
 * relevant than -0.1", it's just above the same soft midpoint.
 */
@Singleton
class MusicContentClassifier @Inject constructor() {

    fun score(
        title: String,
        uploaderName: String,
        durationSec: Long,
        isKnownLocalMatch: Boolean = false
    ): Float {
        val positiveHits = POSITIVE_KEYWORDS.findAll(title).count()
        val negativeHits = NEGATIVE_KEYWORDS.findAll(title).count()

        var score = 0f
        score += (positiveHits * POSITIVE_PER_HIT).coerceAtMost(POSITIVE_CAP)
        score += (negativeHits * NEGATIVE_PER_HIT).coerceAtLeast(NEGATIVE_CAP)
        score += durationScore(durationSec)
        if (isKnownLocalMatch) score += LOCAL_MATCH_BONUS

        return score
    }

    fun isLikelyNonMusic(score: Float): Boolean = score <= NON_MUSIC_FLOOR

    // Graduated soft signal, not a hard cutoff — a long, neutrally-titled video (a full concert)
    // is still recoverable by strong positive title signal, unlike the old hard 60-600s filter.
    private fun durationScore(durationSec: Long): Float = when {
        durationSec < 15L -> -2.5f
        durationSec < 60L -> -1.0f
        durationSec <= 600L -> 0f
        durationSec <= 1200L -> -0.5f
        else -> -1.5f
    }
}
