package com.mobile.trackerapp.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Exposes the singleton [AdsManager] and [AdsConfigStore] to the Compose tree so
 * screens can render banners/native ads and trigger full-screen ads without
 * Hilt-injecting every composable. Provided once at the MainActivity root.
 */
val LocalAdsManager: ProvidableCompositionLocal<AdsManager?> = staticCompositionLocalOf { null }
val LocalAdsConfigStore: ProvidableCompositionLocal<AdsConfigStore?> = staticCompositionLocalOf { null }

@Composable
fun AdsProvider(
    adsManager: AdsManager,
    adsConfigStore: AdsConfigStore,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAdsManager provides adsManager,
        LocalAdsConfigStore provides adsConfigStore,
        content = content,
    )
}

/** Walk the Context chain to find the hosting Activity (needed for full-screen ads). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Convenience for composables to get the current Activity, or null. */
@Composable
fun rememberCurrentActivity(): Activity? = LocalContext.current.findActivity()
