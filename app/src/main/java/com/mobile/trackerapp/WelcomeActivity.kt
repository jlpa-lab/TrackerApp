package com.mobile.trackerapp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.os.Handler
import android.os.Looper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.app.AppConstants

/** No-ad welcome screen displayed after returning from the background. */
class WelcomeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var startReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
