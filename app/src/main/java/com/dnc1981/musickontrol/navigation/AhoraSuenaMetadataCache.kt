package com.dnc1981.musickontrol.navigation

import android.graphics.Bitmap

data class CachedTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: Bitmap?
)

object AhoraSuenaMetadataCache {
    private val cache = mutableMapOf<String, CachedTrackMetadata>()
    private var forceRefresh = false

    fun get(key: String, reloadKey: Int): CachedTrackMetadata? {
        return if (!forceRefresh) cache[key] else null
    }

    fun put(key: String, reloadKey: Int, metadata: CachedTrackMetadata) {
        cache[key] = metadata
    }

    fun clearForced() {
        forceRefresh = true
        cache.clear()
    }

    fun shouldRefresh(): Boolean = forceRefresh.also { forceRefresh = false }
}
