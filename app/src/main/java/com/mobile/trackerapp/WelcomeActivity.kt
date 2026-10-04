package com.mobile.trackerapp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** No-ad welcome screen displayed after returning from the background. */
class WelcomeActivity : Activity() {
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

        findViewById<View>(R.id.welcome_start_button).setOnClickListener {
            Log.d("AppEvent", "WelcomeActivity_start")
            finish()
        }
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
