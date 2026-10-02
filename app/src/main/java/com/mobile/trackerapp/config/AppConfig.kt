package com.mobile.trackerapp.config

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.mobile.trackerapp.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.mobile.trackerapp.R


object AppConfig {
    private const val TAG = "AppConfig"

    private const val DEFAULT_BACKEND_BASE_URL = "https://lingoai-backend-zej0.onrender.com"
    private const val DEFAULT_PRIVACY_POLICY_URL = "https://lingocoach.app/privacy"
    private const val DEFAULT_TERMS_OF_SERVICE_URL = "https://lingocoach.app/terms"
    private const val DEFAULT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.mk.lingocoach&hl=en_IN"
    private const val DEFAULT_ONESIGNAL_APP_ID = "46957d03-f3f9-435c-b76c-e5cd0b8089b5"

    private const val KEY_BACKEND_BASE_URL = "backend_base_url"
    private const val KEY_PRIVACY_POLICY_URL = "privacy_policy_url"
    private const val KEY_TERMS_OF_SERVICE_URL = "terms_of_service_url"
    private const val KEY_PLAY_STORE_URL = "play_store_url"
    private const val KEY_ONESIGNAL_APP_ID = "onesignal_app_id"
    private const val KEY_DAILY_REMINDER_MORNING_HOUR = "daily_reminder_morning_hour"
    private const val KEY_DAILY_REMINDER_EVENING_HOUR = "daily_reminder_evening_hour"
    private const val KEY_MINIMUM_SUPPORTED_VERSION_CODE = "minimum_supported_version_code"
    private const val KEY_FORCE_UPDATE_ENABLED = "force_update_enabled"
    private const val KEY_MAINTENANCE_MODE_ENABLED = "maintenance_mode_enabled"
    private const val KEY_ADS_ENABLED = "ads_enabled"
    private const val KEY_BANNER_ADS_ENABLED = "banner_ad_enabled"
    private const val KEY_NATIVE_ADS_ENABLED = "native_ad_enabled"
    private const val KEY_REWARDED_ADS_ENABLED = "rewarded_ad_enabled"
    private const val KEY_APP_OPEN_ADS_ENABLED = "app_open_ad_enabled"
    private const val KEY_INTERSTITIAL_ADS_ENABLED = "interstitial_ads_enabled"
    private const val KEY_INTERSTITIAL_AD_ENABLED = "interstitial_ad_enabled"
    private const val KEY_INTERSTITIAL_MIN_INTERVAL_SECONDS = "interstitial_min_interval_seconds"
    private const val KEY_INTERSTITIAL_FIRST_DELAY_SECONDS = "interstitial_first_delay_seconds"
    private const val KEY_INTERSTITIAL_SESSION_CAP = "interstitial_session_cap"
    private const val KEY_INTERSTITIAL_HOME_NAVIGATION_FREQUENCY = "interstitial_home_navigation_frequency"
    private const val KEY_INTERSTITIAL_ENABLED_PLACEMENTS = "interstitial_enabled_placements"
    private const val KEY_INTERSTITIAL_ACTION_GAP = "interstitial_action_gap"
    private const val KEY_REWARDED_DAILY_LIMIT = "rewarded_daily_limit"

    private val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    val backendBaseUrl: String
        get() = remoteString(KEY_BACKEND_BASE_URL, DEFAULT_BACKEND_BASE_URL).trimEnd('/')

    val privacyPolicyUrl: String
        get() = remoteString(KEY_PRIVACY_POLICY_URL, DEFAULT_PRIVACY_POLICY_URL)

    val termsOfServiceUrl: String
        get() = remoteString(KEY_TERMS_OF_SERVICE_URL, DEFAULT_TERMS_OF_SERVICE_URL)

    val playStoreUrl: String
        get() = remoteString(KEY_PLAY_STORE_URL, DEFAULT_PLAY_STORE_URL)

    val oneSignalAppId: String
        get() = remoteString(KEY_ONESIGNAL_APP_ID, DEFAULT_ONESIGNAL_APP_ID)

    val dailyReminderMorningHour: Int
        get() = remoteConfig.getLong(KEY_DAILY_REMINDER_MORNING_HOUR).takeIf { it in 0..23 }?.toInt() ?: 10

    val dailyReminderEveningHour: Int
        get() = remoteConfig.getLong(KEY_DAILY_REMINDER_EVENING_HOUR).takeIf { it in 0..23 }?.toInt() ?: 19

    val minimumSupportedVersionCode: Long
        get() = remoteConfig.getLong(KEY_MINIMUM_SUPPORTED_VERSION_CODE).coerceAtLeast(1L)

    val forceUpdateEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_FORCE_UPDATE_ENABLED)

    val maintenanceModeEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_MAINTENANCE_MODE_ENABLED)

    val adsEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_ADS_ENABLED)

    val bannerAdsEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_BANNER_ADS_ENABLED)

    val nativeAdsEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_NATIVE_ADS_ENABLED)

    val rewardedAdsEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_REWARDED_ADS_ENABLED)

    val appOpenAdsEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_APP_OPEN_ADS_ENABLED)

    val interstitialAdsEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_INTERSTITIAL_ADS_ENABLED) ||
            remoteConfig.getBoolean(KEY_INTERSTITIAL_AD_ENABLED)


    val interstitialMinIntervalMillis: Long
        get() = remoteConfig.getLong(KEY_INTERSTITIAL_MIN_INTERVAL_SECONDS)
            .coerceIn(30L, 3600L) * 1000L

    val interstitialFirstDelayMillis: Long
        get() = remoteConfig.getLong(KEY_INTERSTITIAL_FIRST_DELAY_SECONDS)
            .coerceIn(0L, 3600L) * 1000L

    val interstitialSessionCap: Int
        get() = remoteConfig.getLong(KEY_INTERSTITIAL_SESSION_CAP)
            .coerceIn(0L, 50L)
            .toInt()

    val interstitialHomeNavigationFrequency: Int
        get() = remoteConfig.getLong(KEY_INTERSTITIAL_HOME_NAVIGATION_FREQUENCY)
            .coerceIn(1L, 20L)
            .toInt()

    val interstitialActionGap: Int
        get() = remoteConfig.getLong(KEY_INTERSTITIAL_ACTION_GAP)
            .coerceIn(1L, 20L)
            .toInt()

    val rewardedDailyLimit: Int
        get() = remoteConfig.getLong(KEY_REWARDED_DAILY_LIMIT)
            .coerceIn(0L, 25L)
            .toInt()

    val interstitialEnabledPlacements: Set<String>
        get() = remoteString(KEY_INTERSTITIAL_ENABLED_PLACEMENTS, "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun placementEnabled(placement: String): Boolean =
        remoteConfig.getBoolean("${placement}_enabled")

    fun placementAdUnitId(placement: String): String =
        remoteConfig.getString("${placement}_ad_unit_id")

    fun canShowBannerPlacement(placement: String): Boolean =
        adsEnabled &&
            bannerAdsEnabled &&
            placementEnabled(placement) &&
            placementAdUnitId(placement).isNotBlank()

    fun canShowNativePlacement(placement: String): Boolean =
        adsEnabled &&
            nativeAdsEnabled &&
            placementEnabled(placement) &&
            placementAdUnitId(placement).isNotBlank()

    fun placementFrequency(placement: String): Int =
        remoteConfig.getLong("${placement}_frequency")
            .coerceIn(1L, 50L)
            .toInt()

    fun placementCooldownMillis(placement: String): Long =
        remoteConfig.getLong("${placement}_cooldown_seconds")
            .coerceIn(0L, 86400L) * 1000L

    fun initialize(context: Context) {
        FirebaseApp.initializeApp(context)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        val minimumFetchIntervalSeconds = if (BuildConfig.DEBUG) 0L else 3600L
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = minimumFetchIntervalSeconds
            }
        )
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { updated ->
                Log.d(TAG, "Remote Config activated. Updated=$updated")
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Remote Config fetch failed; using defaults.", error)
                FirebaseCrashlytics.getInstance().recordException(error)
            }
    }

    private fun remoteString(key: String, fallback: String): String {
        return remoteConfig.getString(key).takeIf { it.isNotBlank() } ?: fallback
    }

}
