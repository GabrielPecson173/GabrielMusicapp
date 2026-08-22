package com.example.ytmusicapp.data.image

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import com.example.ytmusicapp.BuildConfig
import com.example.ytmusicapp.data.util.ThumbnailResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ThumbnailDebug"

// YouTube serves a small placeholder successfully (not an HTTP error) at maxresdefault.jpg for
// videos with no true HD thumbnail — a "successful response" alone doesn't mean a valid HD image,
// so any decoded result at or under this size is treated as that placeholder, not real content.
private const val PLACEHOLDER_MAX_WIDTH = 200
private const val PLACEHOLDER_MAX_HEIGHT = 150

// Coil decodes a request's bitmap sampled down to fit whatever size the requesting AsyncImage's
// layout resolves to (a 140dp album card, a 48dp list icon, ...) — checking drawable.intrinsicSize
// on THAT result means every small on-screen thumbnail looks "placeholder-sized" regardless of the
// source's real resolution, since Coil never upscales but happily downsamples. The verification
// probe below requests a larger fixed size instead (Coil still won't upscale a genuine placeholder
// past its native ~120x90, but a real source at any known YouTube tier — mqdefault's 320x180 at
// minimum — comfortably clears PLACEHOLDER_MAX_WIDTH within this box), so the size check reflects
// the source image, not the caller's layout. The winning tier is then re-requested at the chain's
// actual original size for the real, memory-efficient result that gets displayed.
private const val PROBE_SIZE = 500

/**
 * Centralizes YouTube thumbnail quality for every `AsyncImage`/Coil request in the app (installed
 * once on the app-wide ImageLoader in [com.example.ytmusicapp.App]) rather than requiring each
 * call site to know about resolution tiers: whenever the requested URL is a recognized
 * `i.ytimg.com/vi/<id>/<variant>.jpg` thumbnail, this always attempts the best tier the current
 * network conditions justify first, and — since a "successful" response can still be a
 * placeholder — verifies the decoded image's real size and walks down
 * [ThumbnailResolver.candidateUrls]'s fallback chain until it finds an actually-usable image.
 * This is what lets [com.example.ytmusicapp.domain.engine.Smartqueueengine] and
 * `HomeFeedRepository`'s stored `mqdefault.jpg`-tier fallback URLs still render at the best
 * available quality without those call sites needing to change.
 */
@Singleton
class YoutubeThumbnailInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {

    // Deliberately separate from the app's main ImageLoader (the one this interceptor is
    // installed on) — using coil.imageLoader/Coil.execute here would route probe requests back
    // through THIS SAME interceptor recursively. This loader has no custom interceptors, exists
    // only to verify a candidate URL's real size, and is never used for anything the user sees:
    // the actual displayed request always goes through chain.proceed() exactly once, below.
    // It does share Coil's default on-disk cache directory (unset = default), so a winning probe
    // still makes the real chain.proceed() call a disk-cache hit rather than a second fetch.
    private val probeLoader: ImageLoader by lazy { ImageLoader.Builder(context).build() }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val originalUrl = chain.request.data as? String
            ?: return chain.proceed(chain.request)
        val videoId = ThumbnailResolver.videoId(originalUrl)
            ?: return chain.proceed(chain.request)

        val candidates = ThumbnailResolver.candidateUrls(videoId)
            .let { if (isUnmeteredConnection()) it else it.drop(1) } // skip the maxresdefault attempt on metered connections

        var winningUrl: String? = null
        for ((attemptIndex, candidateUrl) in candidates.withIndex()) {
            // Executed on the isolated probeLoader, NOT chain.proceed() — proceeding through the
            // real chain fully executes the request including delivering it to the original
            // Target (the AsyncImage this load is for), so calling it multiple times per tier
            // attempt made every intermediate probe flash/settle on the caller's UI, and any
            // probe that errored could leave the UI showing nothing at all. Probing here is
            // completely invisible to the UI; only the one real request below is ever shown.
            val probeRequest = ImageRequest.Builder(context).data(candidateUrl).size(PROBE_SIZE, PROBE_SIZE).build()
            val probeResult = runCatching { probeLoader.execute(probeRequest) }.getOrNull()
            if (probeResult !is SuccessResult) continue
            val drawable = probeResult.drawable
            val isPlaceholder = drawable.intrinsicWidth <= PLACEHOLDER_MAX_WIDTH &&
                drawable.intrinsicHeight <= PLACEHOLDER_MAX_HEIGHT
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "requested=$originalUrl tried=$candidateUrl " +
                        "probeActual=${drawable.intrinsicWidth}x${drawable.intrinsicHeight} " +
                        "placeholder=$isPlaceholder attempt=${attemptIndex + 1}/${candidates.size} " +
                        "cache=${probeResult.dataSource}"
                )
            }
            if (!isPlaceholder) {
                winningUrl = candidateUrl
                break
            }
            // Placeholder-sized response: fall through and try the next lower tier.
        }

        // The one and only chain.proceed() call — goes to the real Target at the chain's own
        // originally-requested size (properly sampled for the real view, not PROBE_SIZE). Same
        // URL as whichever probe succeeded, so this is a disk-cache hit, not a second network
        // fetch. If every probe failed/errored (e.g. offline), fall back to the original request
        // unmodified rather than forcing a guaranteed-broken lowest-tier URL.
        val finalUrl = winningUrl ?: return chain.proceed(chain.request)
        return chain.proceed(chain.request.newBuilder().data(finalUrl).build())
    }

    private fun isUnmeteredConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
