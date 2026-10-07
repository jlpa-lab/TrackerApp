package com.mobile.trackerapp.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase
import com.mobile.trackerapp.LanguageActivity
import com.mobile.trackerapp.SplashActivity
import com.mobile.trackerapp.TrackerApplication
import com.mobile.trackerapp.UninstallReasonActivity
import com.mobile.trackerapp.WelcomeActivity
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.inter_welcome
import com.mobile.trackerapp.onboarding.OnBoardingActivity
import com.mobile.trackerapp.utils.Routes
import kotlin.jvm.java


class AppLifecycleObserver : DefaultLifecycleObserver {

    private val listActivityDisableResume = arrayListOf(
        SplashActivity::class.java,
        LanguageActivity::class.java,
        OnBoardingActivity::class.java,
        WelcomeActivity::class.java,
        UninstallReasonActivity::class.java,
    )

    override fun onStart(owner: LifecycleOwner) {
        val currentActivity = TrackerApplication.currentActivity
        if (currentActivity != null) {
            val isDisable = listActivityDisableResume.any { clazz ->
                clazz.isInstance(currentActivity)
            }
            if (!isDisable && ResumeAdsEntryRule.shouldShowWelcomeOnResume()
                && !AppOpenManager.getInstance().isInterstitialShowing
                && !AppPurchase.getInstance().isPurchased(currentActivity.applicationContext)
                && ERainAd.getInstance().getShouldDisplayInterWelcomeBack(AdRemoteConfig.inter_welcome.enableUaCheck)
            ) {
                if (AppOpenManager.getInstance().isDisableAdResumeByClickAction)
                    AppOpenManager.getInstance().isDisableAdResumeByClickAction = false
                else
                    Routes.startWelcomeActivity(currentActivity)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {}
}
