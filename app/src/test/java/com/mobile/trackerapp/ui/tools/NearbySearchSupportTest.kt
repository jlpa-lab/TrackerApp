package com.mobile.trackerapp.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySearchSupportTest {
    private val place = NearbyPlace(
        id = "google-1",
        name = "Test cafe",
        kind = "Coffee",
        address = "Test street",
        latitude = 19.15,
        longitude = 72.99,
        distanceMeters = 120f
    )

    @Test
    fun rapidCategorySwitches_onlyLatestGenerationIsCurrent() {
        val generations = NearbySearchGeneration()
        val first = generations.begin()
        val second = generations.begin()

        assertFalse(generations.isCurrent(first))
        assertTrue(generations.isCurrent(second))
    }

    @Test
    fun noGpsFix_orPoorAccuracy_isRejected() {
        assertFalse(NearbyLocationPolicy.isUsable(19.15, 72.99, 101f, 0))
        assertFalse(NearbyLocationPolicy.isUsable(19.15, 72.99, 20f, NearbyNetworkConfig.MAX_CACHED_LOCATION_AGE_MILLIS + 1))
        assertTrue(NearbyLocationPolicy.isUsable(19.15, 72.99, 20f, 1_000))
    }

    @Test
    fun slowNetwork_hasExplicitBoundedTimeouts() {
        assertEquals(4L, NearbyNetworkConfig.CONNECT_TIMEOUT_SECONDS)
        assertEquals(8L, NearbyNetworkConfig.READ_TIMEOUT_SECONDS)
        assertEquals(12L, NearbyNetworkConfig.CALL_TIMEOUT_SECONDS)
    }

    @Test
    fun offlineOrStaleCache_isClearlyMarkedStale() {
        var now = 1_000L
        val cache = NearbyPlaceCache(ttlMillis = 100L, staleWindowMillis = 500L, nowMillis = { now })
        val key = key(19.15, 72.99)
        cache.put(key, listOf(place))
        now += 200L

        val lookup = cache.get(key)
        assertNotNull(lookup)
        assertFalse(lookup!!.isFresh)
    }

    @Test
    fun emptyResults_areStoredWithoutTurningIntoAnError() {
        var now = 5_000L
        val cache = NearbyPlaceCache(nowMillis = { now })
        val key = key(19.15, 72.99)
        cache.put(key, emptyList())

        assertNotNull(cache.get(key))
        assertTrue(cache.get(key)!!.places.isEmpty())
    }

    @Test
    fun apiErrors_keepHttpStatusAndMessage() {
        val error = GooglePlacesHttpException(403, "Requests are not authorized")

        assertEquals(403, error.statusCode)
        assertEquals("Requests are not authorized", error.apiMessage)
    }

    @Test
    fun cacheExpires_afterConfiguredTtlAndStaleWindow() {
        var now = 10_000L
        val cache = NearbyPlaceCache(ttlMillis = 100L, staleWindowMillis = 200L, nowMillis = { now })
        val key = key(19.15, 72.99)
        cache.put(key, listOf(place))
        now += 201L

        assertEquals(null, cache.get(key))
    }

    @Test
    fun movement_changesLocationBucket_andDoesNotReuseSameKey() {
        val first = key(19.150, 72.990)
        val moved = key(19.160, 73.000)

        assertFalse(first.location == moved.location)
    }

    private fun key(latitude: Double, longitude: Double) = NearbyCacheKey(
        googleType = "cafe",
        textQuery = null,
        radiusKm = 5,
        location = NearbyLocationBucket(
            kotlin.math.floor(latitude / 0.005).toInt(),
            kotlin.math.floor(longitude / 0.005).toInt()
        )
    )
}
