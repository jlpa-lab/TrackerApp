package com.location.and.phone.tracker.locationnphonetracker.ui.tools

import android.location.Location
import android.os.SystemClock
import java.util.LinkedHashMap
import kotlin.math.floor

/** Centralized network knobs so timeout changes do not require editing the activity. */
internal object NearbyNetworkConfig {
    const val CONNECT_TIMEOUT_SECONDS = 4L
    const val READ_TIMEOUT_SECONDS = 8L
    const val CALL_TIMEOUT_SECONDS = 12L
    const val CACHE_TTL_MILLIS = 3 * 60 * 1_000L
    const val STALE_CACHE_WINDOW_MILLIS = 10 * 60 * 1_000L
    const val MAX_CACHE_ENTRIES = 24
    const val MAX_CACHED_LOCATION_AGE_MILLIS = 60 * 1_000L
    const val MAX_CACHED_LOCATION_ACCURACY_METERS = 100f
    const val ENABLE_OVERPASS_FALLBACK = false
}

internal data class NearbyLocationBucket(
    val latitude: Int,
    val longitude: Int
) {
    companion object {
        // Approximately 500m buckets at the equator. The location is never logged.
        fun from(location: Location): NearbyLocationBucket = NearbyLocationBucket(
            floor(location.latitude / 0.005).toInt(),
            floor(location.longitude / 0.005).toInt()
        )
    }
}

internal data class NearbyCacheKey(
    val googleType: String?,
    val textQuery: String?,
    val radiusKm: Int,
    val location: NearbyLocationBucket
)

internal data class NearbyPlaceCacheEntry(
    val places: List<NearbyPlace>,
    val fetchedAtElapsedMillis: Long
)

internal data class NearbyCacheLookup(
    val places: List<NearbyPlace>,
    val ageMillis: Long,
    val isFresh: Boolean
)

/** Small access-ordered LRU. Expired entries are never returned as fresh results. */
internal class NearbyPlaceCache(
    private val maxEntries: Int = NearbyNetworkConfig.MAX_CACHE_ENTRIES,
    private val ttlMillis: Long = NearbyNetworkConfig.CACHE_TTL_MILLIS,
    private val staleWindowMillis: Long = NearbyNetworkConfig.STALE_CACHE_WINDOW_MILLIS,
    private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val entries = object : LinkedHashMap<NearbyCacheKey, NearbyPlaceCacheEntry>(
        maxEntries, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<NearbyCacheKey, NearbyPlaceCacheEntry>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: NearbyCacheKey): NearbyCacheLookup? {
        val entry = entries[key] ?: return null
        val age = (nowMillis() - entry.fetchedAtElapsedMillis).coerceAtLeast(0L)
        if (age > staleWindowMillis) {
            entries.remove(key)
            return null
        }
        return NearbyCacheLookup(entry.places, age, age <= ttlMillis)
    }

    @Synchronized
    fun put(key: NearbyCacheKey, places: List<NearbyPlace>) {
        entries[key] = NearbyPlaceCacheEntry(places.toList(), nowMillis())
    }

    @Synchronized
    fun clear() = entries.clear()
}

internal object NearbyLocationPolicy {
    fun isUsable(
        location: Location?,
        nowElapsedMillis: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        if (location == null || !location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (!location.hasAccuracy()) return false
        val timestampNanos = location.elapsedRealtimeNanos
        if (timestampNanos <= 0L) return false
        val age = nowElapsedMillis - timestampNanos / 1_000_000L
        return isUsable(location.latitude, location.longitude, location.accuracy, age)
    }

    internal fun isUsable(latitude: Double, longitude: Double, accuracyMeters: Float, ageMillis: Long): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            accuracyMeters <= NearbyNetworkConfig.MAX_CACHED_LOCATION_ACCURACY_METERS &&
            ageMillis in 0..NearbyNetworkConfig.MAX_CACHED_LOCATION_AGE_MILLIS
}

/** Generation gate used to prevent a late response from an older selection updating the UI. */
internal class NearbySearchGeneration {
    private var current = 0

    @Synchronized
    fun begin(): Int = ++current

    @Synchronized
    fun isCurrent(generation: Int): Boolean = generation == current
}
