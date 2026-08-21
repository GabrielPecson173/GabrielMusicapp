package com.example.ytmusicapp.data.util

/**
 * Matching/grouping helpers for turning a track's raw `artist`/`title` strings into the set of
 * artist pages it should appear on. Never mutates or replaces the original `Track.artist` value —
 * these are used only to decide membership, so existing metadata parsing elsewhere is untouched.
 */
object ArtistNameUtils {

    private val CHANNEL_NOISE = Regex(
        """\s*-\s*topic$|\bvevo\b|\bofficial\b|\(official.*?\)|\[official.*?\]""",
        RegexOption.IGNORE_CASE
    )

    private val SEPARATOR = Regex(
        """\s*(?:,|&|/|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bvs\.?\b|\bwith\b|\bx\b)\s*""",
        RegexOption.IGNORE_CASE
    )

    private val TITLE_FEATURE_CLAUSE = Regex(
        """[(\[]\s*(?:feat\.?|ft\.?|featuring)\s+([^)\]]+)[)\]]""",
        RegexOption.IGNORE_CASE
    )

    /** Lowercased, trimmed, channel-noise-stripped key used for matching — never for display. */
    fun normalize(name: String): String =
        name.replace(CHANNEL_NOISE, "").trim().lowercase()

    /**
     * All artist names (primary first, then any featured artists) a track should be attributed
     * to: the `artist` field taken whole, plus any "(feat. X)" clause embedded in the title.
     *
     * The `artist` field is a single YouTube uploader/channel name, not a delimited list — it is
     * deliberately NOT split on separators like "&"/"x"/"," here, because plenty of real act
     * names contain them verbatim (e.g. "Ben&Ben", "AC/DC", "Us the Duo"). Splitting it would
     * silently break matching for exactly those artists. Only the title's explicit feature
     * clause is split, since "(feat. A & B)" unambiguously lists separate people.
     */
    fun extractArtists(title: String, artist: String): List<String> {
        val primary = artist.trim()

        val featureClause = TITLE_FEATURE_CLAUSE.find(title)?.groupValues?.getOrNull(1)
        val fromTitle = featureClause
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        return (listOf(primary) + fromTitle)
            .filter { it.isNotEmpty() }
            .distinctBy { normalize(it) }
    }
}
