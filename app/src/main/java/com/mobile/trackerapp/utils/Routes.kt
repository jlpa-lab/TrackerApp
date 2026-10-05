package com.mobile.trackerapp.utils

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mobile.trackerapp.LanguageActivity
import com.mobile.trackerapp.MainActivity

import com.mobile.trackerapp.SplashActivity
import com.mobile.trackerapp.UninstallConfirmActivity
import com.mobile.trackerapp.app.AppConstants
import com.mobile.trackerapp.onboarding.OnBoardingActivity
import kotlin.jvm.java

object Routes {
    fun startMainActivity(fromActivity: Activity) =
        Intent(fromActivity, MainActivity::class.java).apply {
            putExtra(
                AppConstants.KEY_TRACKING_SCREEN_FROM,
                fromActivity::class.java.simpleName
            )
            fromActivity.startActivity(this)
        }
    fun startOnBoardingActivity(fromActivity: Activity) =
        Intent(fromActivity, OnBoardingActivity::class.java).apply {
           putExtra(
                AppConstants.KEY_TRACKING_SCREEN_FROM,
                fromActivity::class.java.simpleName
            )
            fromActivity.startActivity(this)

        }

    fun startLanguageActivity(fromActivity: Activity, bundle: Bundle?) =
        Intent(fromActivity, LanguageActivity::class.java).apply {
            putExtra(
                AppConstants.KEY_TRACKING_SCREEN_FROM,
                fromActivity::class.java.simpleName
            )
            bundle?.let { putExtras(it) }
            fromActivity.startActivity(this)

        }

    fun startSplashActivity(fromActivity: Activity) =
        Intent(fromActivity, SplashActivity::class.java).apply {
            putExtra(
                AppConstants.KEY_TRACKING_SCREEN_FROM,
                fromActivity::class.java.simpleName
            )
            fromActivity.startActivity(this)
        }

    fun startConfirmUninstallActivity(fromActivity: Activity) =
        Intent(fromActivity, UninstallConfirmActivity::class.java).apply {
            putExtra(
                AppConstants.KEY_TRACKING_SCREEN_FROM,
                fromActivity::class.java.simpleName
            )
            fromActivity.startActivity(this)
        }


}