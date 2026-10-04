package com.mobile.trackerapp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import java.lang.ref.WeakReference
import com.ads.module.application.AdsMultiDexApplication
import com.ads.module.admob.Admob
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.billing.AppPurchase
import com.ads.module.config.AdjustConfig
import com.ads.module.config.ERainAdConfig
import com.google.firebase.FirebaseApp
import com.itg.devconfig.DevConfig
import com.mobile.trackerapp.BuildConfig
import com.mobile.trackerapp.ads.AdRemoteConfig

/** Tracks the visible activity so application-level resume navigation stays safe. */
class TrackerApplication : AdsMultiDexApplication(), Application.ActivityLifecycleCallbacks {
    private var currentActivity = WeakReference<Activity>(null)
    lateinit var appLifecycleObserver: AppLifecycleObserver
        private set

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        appLifecycleObserver = AppLifecycleObserver(this) { currentActivity.get() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        //FirebaseApp.initializeApp(this)
        MobileAds.initialize(this) {}
        DevConfig.init(
            context = this,
            nkhStudioVersion = BuildConfig.ERAIN_STUDIO_VERSION,
            playServicesAdsVersion = BuildConfig.PLAY_SERVICES_ADS_VERSION,
            gdprModuleVersion = BuildConfig.GDPR_MODULE_VERSION
        )
        AdRemoteConfig.initializeFromAssets(this)
        // 4. Initialize Ads Config & SDK
        initAds()

        // 5. Initialize Billing IAP
        initBilling()

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

    private fun initAds() {
        val environment =
            if (BuildConfig.DEBUG) ERainAdConfig.ENVIRONMENT_DEVELOP else ERainAdConfig.ENVIRONMENT_PRODUCTION
        mERainAdConfig = ERainAdConfig(this, environment)

        val adjustConfig = AdjustConfig(true, resources.getString(R.string.adjust_token))
        mERainAdConfig.adjustConfig = adjustConfig
        mERainAdConfig.facebookClientToken = resources.getString(R.string.facebook_client_token)
        mERainAdConfig.adjustTokenTiktok = resources.getString(R.string.event_token)
        mERainAdConfig.intervalInterstitialAd = 35

        mERainAdConfig.idAdResume = ""


        ERainAd.getInstance().init(this, mERainAdConfig)

        Admob.getInstance().setDisableAdResumeWhenClickAds(true)
        Admob.getInstance().setOpenActivityAfterShowInterAds(false)
        AppOpenManager.getInstance().disableAppResumeWithActivity(SplashActivity::class.java)
        AppOpenManager.getInstance().disableAppResumeWithActivity(LanguageActivity::class.java)
//        AppOpenManager.getInstance().disableAppResumeWithActivity(OnBoardingActivity::class.java)
//        AppOpenManager.getInstance().disableAppResumeWithActivity(ConfirmUninstallActivity::class.java)

    }
    private fun initBilling() {
        val listIAP: MutableList<String?> = ArrayList<String?>()
        listIAP.add("android.test.purchased")
        val listSub: MutableList<String?> = ArrayList<String?>()
        AppPurchase.getInstance().initBilling(this, listIAP, listSub)
    }
    fun Context.getSystemLocaleString(@StringRes resId: Int): String {
        val systemConfig = Resources.getSystem().configuration
        val systemLocale = systemConfig.locales[0]

        val config = Configuration(resources.configuration)
        config.setLocale(systemLocale)

        val systemContext = createConfigurationContext(config)
        return systemContext.getString(resId)
    }
}
