package com.mobile.trackerapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.ref.WeakReference

/** Tracks the visible activity so application-level resume navigation stays safe. */
class TrackerApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var currentActivity = WeakReference<Activity>(null)
    lateinit var appLifecycleObserver: AppLifecycleObserver
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        appLifecycleObserver = AppLifecycleObserver(this) { currentActivity.get() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }

    override fun onActivityResumed(activity: Activity) {
        // WeakReference prevents the application from retaining a destroyed Activity.
        currentActivity = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is WelcomeActivity) appLifecycleObserver.onWelcomeClosed()
        if (currentActivity.get() === activity) currentActivity.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
