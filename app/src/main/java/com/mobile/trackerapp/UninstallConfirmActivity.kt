package com.mobile.trackerapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** First shortcut screen, giving the user a chance to keep the app. */
class UninstallConfirmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_uninstall_confirm)
        applySystemInsets(findViewById(R.id.uninstall_confirm_root))
        Log.d("AppEvent", "MainActivity_uninstall_screen_01")

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.home_button).setOnClickListener { openHome() }
        findViewById<View>(R.id.keep_app_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_keep_phone_tracker")
            openHome()
        }
        findViewById<View>(R.id.continue_uninstall_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall01_continue_to_uninstall")
            startActivity(Intent(this, UninstallReasonActivity::class.java))
        }
    }

    private fun applySystemInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun openHome() {
        // Reuse home instead of adding duplicate activities to the back stack.
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
