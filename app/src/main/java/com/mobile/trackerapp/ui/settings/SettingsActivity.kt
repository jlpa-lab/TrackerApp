package com.location.and.phone.tracker.locationnphonetracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.bindNativeAllAd
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.RemoteConfigUtils
import com.location.and.phone.tracker.locationnphonetracker.ui.DashboardActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.language.LanguageActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.rating.RateAppActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.rating.RateAppManager
import com.location.and.phone.tracker.locationnphonetracker.pref.AppSharedPreferencesApp
import com.location.and.phone.tracker.locationnphonetracker.app.AppConstants

class SettingsActivity : DashboardActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = layoutInflater.inflate(R.layout.activity_settings, null) as LinearLayout
        root.findViewById<android.view.View>(R.id.settings_language).setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java).putExtra(AppConstants.KEY_SETTING, true))
        }
        root.findViewById<android.view.View>(R.id.settings_rate).setOnClickListener {
            if (AppSharedPreferencesApp(this).isRate) {
                android.widget.Toast.makeText(this, getString(R.string.settings_rating_thanks), android.widget.Toast.LENGTH_SHORT).show()
            } else if (RateAppManager.canOpenSettingsRateDialog(this)) {
                startActivity(
                    Intent(this, RateAppActivity::class.java)
                        .putExtra(RateAppActivity.EXTRA_ENTRY_POINT, RateAppManager.EntryPoint.SETTINGS.name)
                )
            }
        }
        root.findViewById<android.view.View>(R.id.settings_privacy).setOnClickListener {
            openLegalPage(RemoteConfigUtils.getPrivacyPolicyUrl())
        }
        root.findViewById<android.view.View>(R.id.settings_terms).setOnClickListener {
            openLegalPage(RemoteConfigUtils.getTermsAndConditionsUrl())
        }
        root.addView(nav(getString(R.string.common_settings)), LinearLayout.LayoutParams(-1, wrap()))
        setContentView(root)
        AdsManager.showNativeAllAndPreload(this, R.layout.layout_native_ad_small)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        hideNavigationBar()
    }

    private fun openLegalPage(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                android.widget.Toast.makeText(
                    this,
                    R.string.common_no_browser,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
    }
}
