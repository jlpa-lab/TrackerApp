package com.mobile.trackerapp.splash

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberCoroutineScope
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.LocalAdsConfigStore
import com.mobile.trackerapp.ads.LocalAdsManager
import com.mobile.trackerapp.ads.PlacementBanner
import com.mobile.trackerapp.ads.rememberCurrentActivity
import kotlinx.coroutines.launch
import com.mobile.trackerapp.databinding.SplashScreenBinding



@Composable
fun SplashScreen(isReady: Boolean, onNext: () -> Unit, modifier: Modifier = Modifier, isUninstallFlow: Boolean = false) {
    val ads = LocalAdsManager.current
    val adConfig = LocalAdsConfigStore.current
    val context = LocalContext.current
    val activity = rememberCurrentActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }
    val messages = remember { listOf("INITIALIZING ENGINE...", "LOADING MODELS...", "OPTIMIZING GENERATION...") }
    val splashMaxWaitMs = adConfig?.current?.splashMaxWaitMs ?: 6_000L
    var bannerLoadState by remember { mutableStateOf<Boolean?>(null) }
    var advanced by remember { mutableStateOf(false) }

    fun advanceFromSplash() {
        if (advanced) return
        advanced = true
        scope.launch {
            if (isUninstallFlow && ads != null) {
                val placement = AdPlacement.INTER_POST_SPLASH
                val deadline = SystemClock.elapsedRealtime() + ads.config.fullScreenLoadTimeoutMs
                ads.preloadInterstitial(context, placement)
                while (SystemClock.elapsedRealtime() < deadline &&
                    (!ads.isInterstitialReady(placement) || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                    delay(100)
                }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    ads.showInterstitial(activity, placement, onContinue = onNext)
                } else {
                    onNext()
                }
                return@launch
            }

            val shouldShowStartupInterstitial = ads?.prepareAndConsumeHomeStartup(context) == true
            if (shouldShowStartupInterstitial && ads != null) {
                val placement = AdPlacement.INTER_POST_SPLASH
                val deadline = SystemClock.elapsedRealtime() + ads.config.fullScreenLoadTimeoutMs
                ads.preloadInterstitial(context, placement)
                while (SystemClock.elapsedRealtime() < deadline &&
                    (!ads.isInterstitialReady(placement) || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                    delay(100)
                }
                ads.markLaunched(context)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    ads.showInterstitial(activity, placement, onContinue = onNext)
                } else {
                    onNext()
                }
                return@launch
            }

            ads?.markLaunched(context)
            onNext()
        }
    }

    LaunchedEffect(Unit) {
        if (isUninstallFlow) {
            ads?.preloadInterstitial(context, AdPlacement.INTER_POST_SPLASH)
            return@LaunchedEffect
        }
        ads?.prepareHomeStartup(context)
    }

    LaunchedEffect(isReady, bannerLoadState) {
        if (isReady && bannerLoadState == true) {
            advanceFromSplash()
        }
    }

    LaunchedEffect(isReady, splashMaxWaitMs) {
        if (!isReady) return@LaunchedEffect
        delay(splashMaxWaitMs.coerceAtLeast(0L))
        advanceFromSplash()
    }
    DisposableEffect(Unit) { onDispose { handler.removeCallbacksAndMessages(null) } }
    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                val binding = SplashScreenBinding.inflate(LayoutInflater.from(context))
                var index = 0
                val ticker = object : Runnable {
                    override fun run() {
                        index = (index + 1) % messages.size
                        binding.statusText.text = messages[index]
                        handler.postDelayed(this, 900)
                    }
                }
                handler.postDelayed(ticker, 900)
                binding.root
            }, modifier = Modifier.fillMaxSize()
        )
        PlacementBanner(
            placement = AdPlacement.BANNER_SPLASH,
            modifier = Modifier.align(Alignment.BottomCenter),
            onLoadStateChange = { state -> bannerLoadState = state },
        )
    }
}
