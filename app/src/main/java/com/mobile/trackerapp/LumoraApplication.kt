package com.mobile.trackerapp

import android.app.Application
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.appopen.AppOpenLifecycleObserver
import com.mobile.trackerapp.notification.OneSignalManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LumoraApplication : Application() {
    @Inject
    lateinit var oneSignalManager: OneSignalManager

//    @Inject
//    lateinit var notificationLifecycleHandler: NotificationLifecycleHandler
//
//    @Inject
//    lateinit var notificationChannelManager: NotificationChannelManager

    @Inject
    lateinit var adsManager: AdsManager

    @Inject
    lateinit var appOpenLifecycleObserver: AppOpenLifecycleObserver

    override fun onCreate() {
        super.onCreate()

        // Observe foreground/background transitions to show App Open ads.
        appOpenLifecycleObserver.register(this)
    }
}
