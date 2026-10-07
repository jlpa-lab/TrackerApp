package com.mobile.trackerapp.ui.welcome
import com.mobile.trackerapp.R

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.os.Handler
import android.os.Looper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.app.AppConstants
import com.mobile.trackerapp.ui.phonelocator.PhoneLocatorActivity
import com.mobile.trackerapp.ui.tracker.TrackFriendActivity

/** No-ad welcome screen displayed after returning from the background. */
class WelcomeActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var startReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(AppConstants.FROM_SHORTCUT)) {
            AppConstants.ACTION_OPEN_PHONE_LOCATOR -> {
                startActivity(Intent(this, PhoneLocatorActivity::class.java))
                finish()
                return
            }
            AppConstants.ACTION_OPEN_TRACK_FRIEND -> {
                startActivity(Intent(this, TrackFriendActivity::class.java))
                finish()
                return
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // CHANGE: keep the Welcome Back screen immersive by hiding the device navigation bar.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.statusBarColor = Color.rgb(255, 254, 250)
        window.navigationBarColor = Color.rgb(255, 254, 250)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_welcome)
        applySystemInsets(findViewById(R.id.welcome_root))
        Log.d("AppEvent", "WelcomeActivity")

        // CHANGE: preload the Welcome interstitial before allowing Start, matching the reference flow.
        AdsManager.loadInterWelcome(this)
        handler.postDelayed({ startReady = true }, AppConstants.DEFAULT_TIME_DELAY_LOAD_INTER_WELCOME)

        findViewById<View>(R.id.welcome_start_button).setOnClickListener {
            Log.d("AppEvent", "WelcomeActivity_start")
            if (!startReady) return@setOnClickListener
            AdsManager.showInterWelcome(this) { finish() }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun applySystemInsets(root: View) {
        // Keep content clear of status and navigation bars on every device.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}










