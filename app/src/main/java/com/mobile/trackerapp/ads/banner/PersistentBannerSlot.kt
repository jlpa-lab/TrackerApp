package com.mobile.trackerapp.ads.banner

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobile.trackerapp.ads.shimmer.AdShimmerBox

/**
 * The single [PersistentBannerAd] instance, provided at the navigation root so
 * every primary screen renders the SAME banner without recreating it.
 */
val LocalPersistentBanner: ProvidableCompositionLocal<PersistentBannerAd?> =
    staticCompositionLocalOf { null }

/**
 * Renders the shared persistent banner directly below the bottom navigation bar.
 * Re-parents the single AdView's host into this slot on compose and detaches it
 * on dispose, so switching tabs moves (never rebuilds) the one instance.
 *
 * Shows a shimmer while loading and collapses to zero height on failure/no-fill,
 * so it never leaves an empty gap or infinite shimmer.
 */
@Composable
fun PersistentBannerSlot(modifier: Modifier = Modifier) {
    val banner = LocalPersistentBanner.current ?: return

    var loadState by remember { mutableStateOf(banner.loadState) }
    LaunchedEffect(banner) {
        banner.ensureLoaded()
    }
    DisposableEffect(banner) {
        banner.observe { loadState = it }
        onDispose { banner.clearObserver() }
    }

    val density = LocalDensity.current
    val heightDp = with(density) { banner.heightPx().toDp() }.coerceAtLeast(0.dp)

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Loading shimmer sized to the banner height.
        AnimatedVisibility(visible = loadState == null, enter = fadeIn(), exit = fadeOut()) {
            AdShimmerBox(modifier = Modifier.fillMaxWidth().height(heightDp))
        }
        // Loaded banner: host the single re-parentable AdView container.
        AnimatedVisibility(visible = loadState == true, enter = fadeIn(), exit = fadeOut()) {
            AndroidView(
                factory = { FrameLayout(it) },
                modifier = Modifier.fillMaxWidth().height(heightDp),
                update = { container ->
                    // Detach the shared host from any previous parent, then attach
                    // here. This moves the one AdView instead of recreating it.
                    (banner.host.parent as? ViewGroup)?.removeView(banner.host)
                    if (container.childCount == 0 || container.getChildAt(0) !== banner.host) {
                        container.removeAllViews()
                        container.addView(
                            banner.host,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    }
                },
            )
        }
        // loadState == false -> render nothing (collapse), no gap.
    }

    DisposableEffect(Unit) {
        onDispose {
            // Detach so the host can be re-attached by the next screen's slot.
            (banner.host.parent as? ViewGroup)?.removeView(banner.host)
        }
    }
}
