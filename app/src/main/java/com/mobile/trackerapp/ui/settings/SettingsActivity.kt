package com.mobile.trackerapp.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.bindNativeAllAd
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.ui.language.LanguageActivity
import com.mobile.trackerapp.ui.rating.RateAppActivity
import com.mobile.trackerapp.pref.AppSharedPreferencesApp
import com.mobile.trackerapp.app.AppConstants

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
            } else {
                startActivity(Intent(this, RateAppActivity::class.java))
            }
        }
        root.findViewById<android.view.View>(R.id.settings_privacy).setOnClickListener {
            openLegalPage(AppConstants.LINK_PRIVACY_POLICY)
        }
        root.findViewById<android.view.View>(R.id.settings_terms).setOnClickListener {
            openLegalPage(AppConstants.LINK_TERMS_AND_CONDITIONS)
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
