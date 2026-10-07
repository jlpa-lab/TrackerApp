package com.mobile.trackerapp
import com.mobile.trackerapp.ui.home.HomeActivity
import com.mobile.trackerapp.ui.splash.SplashActivity
import com.mobile.trackerapp.ui.welcome.WelcomeActivity
import com.mobile.trackerapp.ui.language.LanguageActivity
import com.mobile.trackerapp.ui.uninstall.ConfirmUninstallActivity
import com.mobile.trackerapp.ui.uninstall.ReasonActivity

import android.annotation.SuppressLint
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
import com.itg.devconfig.DevConfig
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.app.AppActivityLifecycleCallbacks
import com.mobile.trackerapp.app.AppLifecycleObserver

/** Tracks the visible activity so application-level resume navigation stays safe. */
class TrackerApplication : AdsMultiDexApplication() {

    lateinit var appLifecycleObserver: AppLifecycleObserver
        private set
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: TrackerApplication

        @SuppressLint("StaticFieldLeak")
        var currentActivity: Activity? = null
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
        registerActivityLifecycleCallbacks(AppActivityLifecycleCallbacks())
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









