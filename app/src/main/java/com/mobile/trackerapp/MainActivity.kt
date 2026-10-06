package com.mobile.trackerapp

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.mobile.trackerapp.uninstall.ShortcutManager

/** Temporary home destination displayed after onboarding. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcuts = getSystemService(android.content.pm.ShortcutManager::class.java)
            if (shortcuts.dynamicShortcuts.size < 3) {
                ShortcutManager.initShortCut(this)
            }
        }
        Log.d("AppEvent", "MainActivity")
    }
}
