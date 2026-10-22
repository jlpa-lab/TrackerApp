package com.location.and.phone.tracker.locationnphonetracker.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase
import com.location.and.phone.tracker.locationnphonetracker.ui.language.LanguageActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.splash.SplashActivity
import com.location.and.phone.tracker.locationnphonetracker.TrackerApplication
import com.location.and.phone.tracker.locationnphonetracker.ui.uninstall.ReasonActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.welcome.WelcomeActivity
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.inter_welcome_back
import com.location.and.phone.tracker.locationnphonetracker.ui.onboarding.OnBoardingActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.phonelocator.PhoneLocatorActivity
import com.location.and.phone.tracker.locationnphonetracker.service.LocationSharingCoordinator
import com.location.and.phone.tracker.locationnphonetracker.utils.Routes
import kotlin.jvm.java


class AppLifecycleObserver : DefaultLifecycleObserver {

    private val listActivityDisableResume = arrayListOf(
        SplashActivity::class.java,
        LanguageActivity::class.java,
        OnBoardingActivity::class.java,
        WelcomeActivity::class.java,
        ReasonActivity::class.java,
        PhoneLocatorActivity::class.java,
    )

    override fun onStart(owner: LifecycleOwner) {
        val currentActivity = TrackerApplication.currentActivity
        if (currentActivity != null) {
            LocationSharingCoordinator.syncAcceptedFriends(currentActivity.applicationContext)
            if (ResumeAdsEntryRule.consumeResumeSuppression()) {
                // Returning from Android Settings must restore the feature
                // that opened it, without inserting Welcome Back or an app
                // open ad into that navigation path.
                AppOpenManager.getInstance().disableAppResume()
                return
            }
            val isDisable = listActivityDisableResume.any { clazz ->
                clazz.isInstance(currentActivity)
            }
            if (!isDisable && ResumeAdsEntryRule.shouldShowWelcomeOnResume()
                && !AppOpenManager.getInstance().isInterstitialShowing
                && !AppPurchase.getInstance().isPurchased(currentActivity.applicationContext)
                && ERainAd.getInstance().getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_welcome_back.enableUaCheck)
            ) {
                AppOpenManager.getInstance().disableAppResume()
                if (AppOpenManager.getInstance().isDisableAdResumeByClickAction)
                    AppOpenManager.getInstance().isDisableAdResumeByClickAction = false
                else
                    Routes.startWelcomeActivity(currentActivity)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {}
}
