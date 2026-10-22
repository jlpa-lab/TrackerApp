package com.location.and.phone.tracker.locationnphonetracker.utils
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.location.and.phone.tracker.locationnphonetracker.app.AppConstants
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ui.home.HomeActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.language.LanguageActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.onboarding.OnBoardingActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.splash.SplashActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.uninstall.ConfirmUninstallActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.welcome.WelcomeActivity
object Routes {
    private fun Activity.launch(target: Class<*>, bundle: Bundle? = null) { startActivity(Intent(this, target).apply { putExtra(AppConstants.KEY_TRACKING_SCREEN_FROM, this@launch::class.java.simpleName); bundle?.let(::putExtras) }) }
    fun startMainActivity(fromActivity: Activity) {
        fromActivity.launch(HomeActivity::class.java)
    }
    fun startOnBoardingActivity(fromActivity: Activity) = fromActivity.launch(OnBoardingActivity::class.java)
    fun startLanguageActivity(fromActivity: Activity, bundle: Bundle?) = fromActivity.launch(LanguageActivity::class.java, bundle)
    fun startSplashActivity(fromActivity: Activity) = fromActivity.launch(SplashActivity::class.java)
    fun startConfirmUninstallActivity(fromActivity: Activity) = fromActivity.launch(ConfirmUninstallActivity::class.java)
    fun startWelcomeActivity(fromActivity: Activity) = fromActivity.launch(WelcomeActivity::class.java)
}
