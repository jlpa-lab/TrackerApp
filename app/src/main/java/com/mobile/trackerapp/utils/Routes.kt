package com.mobile.trackerapp.utils
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mobile.trackerapp.app.AppConstants
import com.mobile.trackerapp.ui.home.HomeActivity
import com.mobile.trackerapp.ui.language.LanguageActivity
import com.mobile.trackerapp.ui.onboarding.OnBoardingActivity
import com.mobile.trackerapp.ui.splash.SplashActivity
import com.mobile.trackerapp.ui.uninstall.ConfirmUninstallActivity
import com.mobile.trackerapp.ui.welcome.WelcomeActivity
object Routes {
    private fun Activity.launch(target: Class<*>, bundle: Bundle? = null) { startActivity(Intent(this, target).apply { putExtra(AppConstants.KEY_TRACKING_SCREEN_FROM, this@launch::class.java.simpleName); bundle?.let(::putExtras) }) }
    fun startMainActivity(fromActivity: Activity) = fromActivity.launch(HomeActivity::class.java)
    fun startOnBoardingActivity(fromActivity: Activity) = fromActivity.launch(OnBoardingActivity::class.java)
    fun startLanguageActivity(fromActivity: Activity, bundle: Bundle?) = fromActivity.launch(LanguageActivity::class.java, bundle)
    fun startSplashActivity(fromActivity: Activity) = fromActivity.launch(SplashActivity::class.java)
    fun startConfirmUninstallActivity(fromActivity: Activity) = fromActivity.launch(ConfirmUninstallActivity::class.java)
    fun startWelcomeActivity(fromActivity: Activity) = fromActivity.launch(WelcomeActivity::class.java)
}
