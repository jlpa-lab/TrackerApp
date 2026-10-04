package com.mobile.trackerapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Collects a reason before handing uninstalling to Android's system screen. */
class UninstallReasonActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(R.layout.activity_uninstall_reason)
        applySystemInsets(findViewById(R.id.uninstall_reason_root))
        Log.d("AppEvent", "MainActivity_uninstall_screen_02")

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.home_button).setOnClickListener { openHome() }
        findViewById<View>(R.id.cancel_button).setOnClickListener {
            Log.d("AppEvent", "Uninstall02_cancel")
            openHome()
        }
        findViewById<View>(R.id.uninstall_button).setOnClickListener {
            // Android owns the final confirmation and package removal.
            val selected = findViewById<RadioGroup>(R.id.uninstall_reason_group)
                .findViewById<View>(findViewById<RadioGroup>(R.id.uninstall_reason_group).checkedRadioButtonId)
                ?.tag?.toString() ?: "not_selected"
            Log.d("AppEvent", "Uninstall02_uninstall_$selected")
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
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
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }
}
