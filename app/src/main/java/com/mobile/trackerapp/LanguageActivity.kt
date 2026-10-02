package com.mobile.trackerapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/** Handles language selection and the initial notification permission request. */
class LanguageActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var confirmButton: TextView
    private var selectedRow: View? = null
    private var selectedRadio: RadioButton? = null
    private val revealConfirmation = Runnable { confirmButton.visibility = View.VISIBLE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        val root = findViewById<View>(R.id.language_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, statusBarTop, view.paddingRight, view.paddingBottom)
            insets
        }

        Log.d("AppEvent", "LanguageActivity")
        FirebaseAnalytics.getInstance(this).logEvent("LanguageActivity", null)
        configureRemoteConfig()
        requestNotificationPermission()

        confirmButton = findViewById(R.id.confirm_language)
        populateLanguages()

        confirmButton.setOnClickListener {
            FirebaseAnalytics.getInstance(this).logEvent("LanguageActivity_confirm", null)
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    private fun populateLanguages() {
        // Inflate one shared row layout for every supported language.
        val languageList = findViewById<LinearLayout>(R.id.language_list)
        val inflater = LayoutInflater.from(this)

        languages.forEach { language ->
            val row = inflater.inflate(R.layout.item_language_option, languageList, false)
            val labels = getString(language.labelRes).split("\n", limit = 2)
            row.findViewById<TextView>(R.id.language_native_name).text = labels.first()
            row.findViewById<TextView>(R.id.language_english_name).text = labels.getOrElse(1) { labels.first() }
            val radio = row.findViewById<RadioButton>(R.id.language_radio)

            row.setOnClickListener {
                selectedRow?.isSelected = false
                selectedRadio?.isChecked = false
                row.isSelected = true
                radio.isChecked = true
                selectedRow = row
                selectedRadio = radio

                handler.removeCallbacks(revealConfirmation)
                confirmButton.visibility = View.INVISIBLE
                // The confirmation tick follows the design's 1.5-second delay.
                getSharedPreferences("app_preferences", MODE_PRIVATE).edit()
                    .putString("selected_language", language.tag)
                    .apply()
                FirebaseAnalytics.getInstance(this)
                    .logEvent("LanguageActivity_select_${language.tag}", null)
                handler.postDelayed(revealConfirmation, CONFIRMATION_DELAY_MS)
            }
            languageList.addView(row)
        }
    }

    private fun configureRemoteConfig() {
        // Bundled defaults remain available if the network fetch cannot complete.
        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
            )
            setDefaultsAsync(R.xml.remote_config_defaults)
            fetchAndActivate()
        }
    }

    private fun requestNotificationPermission() {
        // Android 13+ requires notification permission at runtime.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            FirebaseAnalytics.getInstance(this).logEvent(
                if (granted) "LanguageActivity_notification_allowed"
                else "LanguageActivity_notification_denied",
                null
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(revealConfirmation)
        super.onDestroy()
    }

    companion object {
        private const val CONFIRMATION_DELAY_MS = 1500L
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001

        private val languages = listOf(
            LanguageOption("english", R.string.language_english),
            LanguageOption("vietnamese", R.string.language_vietnamese),
            LanguageOption("japanese", R.string.language_japanese),
            LanguageOption("korean", R.string.language_korean),
            LanguageOption("french", R.string.language_french),
            LanguageOption("spanish", R.string.language_spanish),
            LanguageOption("german", R.string.language_german),
            LanguageOption("portuguese", R.string.language_portuguese),
            LanguageOption("hindi", R.string.language_hindi),
            LanguageOption("chinese", R.string.language_chinese),
            LanguageOption("arabic", R.string.language_arabic),
            LanguageOption("russian", R.string.language_russian)
        )
    }

    private data class LanguageOption(val tag: String, val labelRes: Int)
}
