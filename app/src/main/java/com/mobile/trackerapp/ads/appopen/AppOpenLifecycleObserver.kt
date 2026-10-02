package com.mobile.trackerapp.ads.appopen

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.AdsManager
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows the App Open ad on genuine foreground transitions (background -> fore)
 * rather than Activity recreation. Tracks the current Activity via a WeakReference
 * (never a strong long-lived reference — no leaks) and only passes it to the SDK
 * at show time.
 *
 * Coordinates with interstitials via the centralized full-screen lock inside
 * AdsManager, so inter_post_splash and app_open never overlap.
 */
@Singleton
class AppOpenLifecycleObserver @Inject constructor(
    private val adsManager: AdsManager,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var currentActivity = WeakReference<Activity?>(null)
    private var startedFromBackground = false
    private var isFirstForeground = true

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        adsManager.preloadAppOpen(application)
    }

    // ---- Process lifecycle (foreground/background) ----

    override fun onStart(owner: LifecycleOwner) {
        // First foreground after launch is handled by the splash/post-splash
        // flow, not here, to avoid a double full-screen on cold start.
        if (isFirstForeground) {
            isFirstForeground = false
            return
        }
        val activity = currentActivity.get() ?: return
        adsManager.showAppOpen(activity, AdPlacement.APP_OPEN)
    }

    // ---- Activity tracking (weak) ----

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity.get() === activity) currentActivity.clear()
    }
}
