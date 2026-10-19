package com.mobile.trackerapp.ui.tools

internal enum class NearbySource(val label: String) {
    GOOGLE("Google"),
    OPENSTREETMAP("OpenStreetMap fallback")
}

internal data class NearbyPlace(
    val id: String,
    val name: String,
    val kind: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val imageUrl: String? = null,
    val rating: String? = null,
    val reviewCount: String? = null,
    val openingHours: String? = null,
    val source: NearbySource = NearbySource.GOOGLE
)
