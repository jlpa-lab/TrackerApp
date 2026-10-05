package com.mobile.trackerapp

import android.app.Activity
import android.content.Intent
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mobile.trackerapp.onboarding.OnBoardingActivity

/** Shows the welcome screen when a fully onboarded user returns from the background. */
class AppLifecycleObserver(
    private val application: TrackerApplication,
    private val currentActivity: () -> Activity?
) : DefaultLifecycleObserver {
    private var hasStartedOnce = false
    private var wentToBackground = false
    private var welcomeShowing = false

    override fun onStart(owner: LifecycleOwner) {
        // A fresh launch already has its own splash and setup flow.
        if (!hasStartedOnce) {
            hasStartedOnce = true
            return
        }
        if (!wentToBackground || welcomeShowing) return
        wentToBackground = false

        val activity = currentActivity() ?: return
        if (!shouldShowWelcomeOnResume(activity)) return

        welcomeShowing = true
        activity.runOnUiThread {
            activity.startActivity(Intent(activity, WelcomeActivity::class.java))
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        wentToBackground = true
    }

    fun onWelcomeClosed() {
        welcomeShowing = false
    }

    private fun shouldShowWelcomeOnResume(activity: Activity): Boolean {
        // Do not interrupt setup or stack another welcome screen.
        val disabledScreen = activity is SplashActivity ||
            activity is LanguageActivity ||
            activity is OnBoardingActivity ||
            activity is WelcomeActivity
        if (disabledScreen) return false

        return application.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getBoolean("onboarding_complete", false)
    }
}
