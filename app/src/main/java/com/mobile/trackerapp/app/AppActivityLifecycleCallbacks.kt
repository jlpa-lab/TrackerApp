package com.mobile.trackerapp.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.mobile.trackerapp.TrackerApplication

class AppActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        TrackerApplication.currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (TrackerApplication.currentActivity == activity) {
            TrackerApplication.currentActivity = null
        }
    }
}
