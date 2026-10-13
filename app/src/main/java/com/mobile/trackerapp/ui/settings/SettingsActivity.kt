package com.mobile.trackerapp.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.ui.language.LanguageActivity
import com.mobile.trackerapp.ui.rating.RateAppActivity

class SettingsActivity : DashboardActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = layoutInflater.inflate(R.layout.activity_settings, null) as LinearLayout
        root.findViewById<android.view.View>(R.id.settings_language).setOnClickListener {
            startActivity(Intent(this, LanguageActivity::class.java))
        }
        root.findViewById<android.view.View>(R.id.settings_rate).setOnClickListener { startActivity(Intent(this, RateAppActivity::class.java)) }
        root.addView(nav("Setting"), LinearLayout.LayoutParams(-1, wrap()))
        setContentView(root)
        hideNavigationBar()
    }
}
