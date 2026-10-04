package com.mobile.trackerapp

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_splash
import com.mobile.trackerapp.ads.banner_splash_uninstall
import com.mobile.trackerapp.app.AppConstants
import com.mobile.trackerapp.databinding.ActivitySplashBinding
import com.onesignal.OneSignal

/** Displays the branded launch artwork while startup services initialize. */
class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var loaderAnimator: ObjectAnimator? = null
    private lateinit var binding: ActivitySplashBinding

    private val isFromUninstallShortcut: Boolean
        get() = intent.getStringExtra(AppConstants.FROM_SHORTCUT) == AppConstants.ACTION_OPEN_UNINSTALL

    private val openLanguageScreen = Runnable {
        startActivity(Intent(this, LanguageActivity::class.java))
        finish()
    }

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
        setContentView(R.layout.activity_splash)
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
        handler.postDelayed(openLanguageScreen, SPLASH_DURATION_MS)
        loadSplashBanner()
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
        handler.removeCallbacks(openLanguageScreen)
        loaderAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1000L
    }
}
