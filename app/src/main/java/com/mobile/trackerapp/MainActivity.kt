package com.mobile.trackerapp

import android.app.Activity
import android.os.Bundle
import android.util.Log

/** Temporary home destination displayed after onboarding. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("AppEvent", "MainActivity")
    }
}
