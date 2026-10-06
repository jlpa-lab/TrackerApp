package com.mobile.trackerapp

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.ads.module.admob.AppOpenManager
import com.ads.module.ads.ERainAd
import com.ads.module.funtion.AdCallback
import com.itg.template.data.pref.AppSharedPref
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.AdsManager.loadNativeLanguage
import com.mobile.trackerapp.ads.RemoteConfigUtils
import com.mobile.trackerapp.ads.banner_splash
import com.mobile.trackerapp.ads.banner_splash_uninstall
import com.mobile.trackerapp.ads.inter_splash
import com.mobile.trackerapp.ads.inter_splash_uninstall
import com.mobile.trackerapp.ads.open_resume
import com.mobile.trackerapp.app.AppConstants
import com.mobile.trackerapp.app.ResumeAdsEntryRule
import com.mobile.trackerapp.bases.ConsentHandler
import com.mobile.trackerapp.bases.ext.isNetwork
import com.mobile.trackerapp.databinding.ActivitySplashBinding
import com.mobile.trackerapp.pref.AppSharedPreferencesApp
import com.mobile.trackerapp.utils.Routes
import com.onesignal.OneSignal

/** Displays the branded launch artwork while startup services initialize. */
class SplashActivity : AppCompatActivity() , RemoteConfigUtils.Listener {
    private val handler = Handler(Looper.getMainLooper())

    private var getConfigSuccess = false
    private var loaderAnimator: ObjectAnimator? = null
    private var showLanguageNextTime = true
    private lateinit var binding: ActivitySplashBinding
    private lateinit var consentHandler: ConsentHandler
    private val appSharedPref: AppSharedPref by lazy {
        AppSharedPreferencesApp(applicationContext)
    }
    private fun shouldShowLanguageNextTime() = showLanguageNextTime

    private val isFromUninstallShortcut: Boolean
        get() = intent.getStringExtra(AppConstants.FROM_SHORTCUT) == AppConstants.ACTION_OPEN_UNINSTALL
                || intent.action == "android.intent.action.SHORTCUT_UNINSTALL_APP"

//    private val openLanguageScreen = Runnable {
//        startActivity(Intent(this, LanguageActivity::class.java))
//        finish()
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivitySplashBinding.inflate(layoutInflater)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        // Display the same layout instance used by binding so the splash banner is visible.
        setContentView(binding.root)
        Log.d("AppEvent", "SplashActivity")
        OneSignal.initWithContext(applicationContext, getString(R.string.onesignal_app_id))

        val track = findViewById<View>(R.id.splash_loader_track)
        val segment = findViewById<View>(R.id.splash_loader_segment)

        // Move a fixed segment continuously across the loading track.
        track.post {
            loaderAnimator = ObjectAnimator.ofFloat(
                segment,
                View.TRANSLATION_X,
                -segment.width.toFloat(),
                track.width.toFloat()
            ).apply {
                duration = 700L
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
        loadingRemoteConfig()
//        consentHandler = ConsentHandler(
//            activity = this,
//            appSharedPref = appSharedPref,
//            trackingSuffix = 1,
//            onConsentFlowCompleted = { loadingRemoteConfig() })
//        if (appSharedPref.isConfirmConsent.not() && appSharedPref.isUserGlobal.not() && isNetwork()) {
//            consentHandler.requestConsent()
//        } else {
//            loadingRemoteConfig()
//        }
       // handler.postDelayed(openLanguageScreen, SPLASH_DURATION_MS)
//        loadSplashBanner()
        // Preload the language ad while Splash is visible, as required by the sample flow.
        AdsManager.loadNativeLanguage(
            this,
            true,
            R.layout.layout_native_language_click
        )
    }
    private fun loadingRemoteConfig() {
        object : CountDownTimer(AppConstants.DEFAULT_TIME_SPLASH, 100) {
            override fun onTick(millisUntilFinished: Long) {
                if (getConfigSuccess && millisUntilFinished < AppConstants.DEFAULT_LIMIT_TIME_SPLASH) {
                    checkRemoteConfigResult()
                    cancel()
                }
            }

            override fun onFinish() {
                if (!getConfigSuccess) {
                    checkRemoteConfigResult()
                }
            }
        }.start()
    }

    override fun loadSuccess() {
        getConfigSuccess = true
    }

//    override fun onResume() {
//        super.onResume()
//        ERainAd.getInstance().onCheckShowSplashWhenFail(this, object : AdCallback() {
//            override fun onNextAction() {
//                super.onNextAction()
//                moveActivity()
//            }
//        }, 1000)
//    }

    private fun checkRemoteConfigResult() {
       // AdRemoteConfig.initialize(this, RemoteConfigUtils.getAdRemoteConfig())
        loadSplashBanner()
        if (!isFromUninstallShortcut) {
            loadNativeLanguage(
                this@SplashActivity,
                appSharedPref.firstLanguage,
                R.layout.layout_native_language
            )
        }
        val splashInterConfig = if (isFromUninstallShortcut) {
            AdRemoteConfig.inter_splash_uninstall
        } else {
            AdRemoteConfig.inter_splash
        }
        if (splashInterConfig.isEnable && isNetwork(this@SplashActivity)) {
            ERainAd.getInstance().loadSplashInterstitialAds(
                this, splashInterConfig.id, 30000, 5000, object : AdCallback() {
                    override fun onNextAction() {
                        super.onNextAction()
                        moveActivity()
                    }

                    override fun onAdLoaded() {
                        super.onAdLoaded()
                    }
                })
        } else {
            moveActivity()
        }
        if (ResumeAdsEntryRule.shouldEnableOpenResume()) {
            AppOpenManager.getInstance().setAppResumeAdId(AdRemoteConfig.open_resume.id)
            AppOpenManager.getInstance().enableAppResume()
        } else {
            AppOpenManager.getInstance().disableAppResume()
        }
    }


    private fun moveActivity() {
        when {
            isFromUninstallShortcut -> Routes.startConfirmUninstallActivity(this)
            shouldShowLanguageNextTime() || appSharedPref.firstOnBoarding -> Routes.startLanguageActivity(
                this,
                null
            )

            else -> Routes.startMainActivity(this)
        }
        finish()
    }
    private fun loadSplashBanner() {
        val bannerConfig = if (isFromUninstallShortcut) {
            AdRemoteConfig.banner_splash_uninstall
        } else {
            AdRemoteConfig.banner_splash
        }
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    override fun onDestroy() {
        if (::consentHandler.isInitialized) {
            consentHandler.clear()
        }
        loaderAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        // Use the project-defined Splash duration instead of the old 1-second shortcut.
        private const val SPLASH_DURATION_MS = AppConstants.DEFAULT_TIME_SPLASH
    }
}
