package com.mobile.trackerapp.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live [AdsConfig] snapshot.
 *
 * Starts from safe local defaults with no bundled ad unit IDs. Firebase Remote
 * Config should call [applyRemoteConfig] with the fetched instance before ads
 * load. Parsing is fully defensive — malformed/partial/absent values simply keep
 * the previous (or default) value, never a crash.
 *
 * This is the ONLY place that knows the Remote Config parameter names, keeping
 * the rest of the ads layer decoupled from Firebase.
 *
 * The flat parameter layout mirrors docs/ads_remote_config_template.json:
 *   - Ad unit IDs  : "{placement_key}_adunit"  (e.g. "banner_splash_adunit")
 *   - Enable flags : "{placement_key}_enabled" (e.g. "banner_splash_enabled")
 *   - Style values : "ads_native_style_*"       (individual string/int params)
 *   - Everything else is a scalar top-level key (no nested JSON objects).
 */
@Singleton
class AdsConfigStore @Inject constructor() {

    private val ref = AtomicReference(AdsConfig.DEFAULT)

    val current: AdsConfig
        get() {
            // Register snapshot reads so config-driven screens update in realtime.
            version
            return ref.get()
        }

    var version by mutableIntStateOf(0)
        private set

    // ------------------------------------------------------------------
    // Primary entry point — reads the flat Firebase Remote Config layout.
    // ------------------------------------------------------------------

    fun applyRemoteConfig(rc: FirebaseRemoteConfig) {
        runCatching { applyRemoteConfigInternal(rc) }.onFailure { e ->
            AdsLogger.w("AdsConfig: unexpected error applying remote config", e)
        }
    }

    private fun applyRemoteConfigInternal(rc: FirebaseRemoteConfig) {
        val base = ref.get()

        // ---------- global toggles & scalars ----------
        val updated = base.copy(
            adsEnabled            = rc.boolOr("ads_enabled", base.adsEnabled),
            bannerEnabled         = rc.boolOr("ads_banner_enabled", base.bannerEnabled),
            nativeEnabled         = rc.boolOr("ads_native_enabled", base.nativeEnabled),
            interstitialEnabled   = rc.boolOr("ads_interstitial_enabled", base.interstitialEnabled),
            rewardedEnabled       = rc.boolOr("ads_rewarded_enabled", base.rewardedEnabled),
            appOpenEnabled        = rc.boolOr("ads_app_open_enabled", base.appOpenEnabled),
            testMode              = rc.boolOr("ads_test_mode", base.testMode),

            interBackClickInterval          = rc.intIn("interval_inter_back", 1, 100, base.interBackClickInterval),
            interGenerateClickInterval      = rc.intIn("interval_inter_generate", 1, 100, base.interGenerateClickInterval),
            interstitialPlacementCooldownMs = rc.longIn("inter_cooldown_ms", 0, 600_000, base.interstitialPlacementCooldownMs),
            globalFullScreenCooldownMs      = rc.longIn("fullscreen_cooldown_ms", 0, 600_000, base.globalFullScreenCooldownMs),
            maxInterstitialsPerSession      = rc.intIn("max_inter_per_session", 0, 200, base.maxInterstitialsPerSession),
            appOpenMinIntervalMs            = rc.longIn("app_open_min_interval_ms", 0, 3_600_000, base.appOpenMinIntervalMs),
            appOpenMaxCacheMs               = rc.longIn("app_open_max_cache_ms", 0, 14_400_000, base.appOpenMaxCacheMs),
            splashMaxWaitMs                 = rc.longIn("splash_max_wait_ms", 0, 20_000, base.splashMaxWaitMs),
            fullScreenLoadTimeoutMs         = rc.longIn("fullscreen_load_timeout_ms", 1_000, 30_000, base.fullScreenLoadTimeoutMs),
            rewardCreditsAmount             = rc.intIn("reward_credits_amount", 1, 1_000, base.rewardCreditsAmount),
            rewardMaxClaimsPerDay           = rc.intIn("reward_max_claims_per_day", 0, 100, base.rewardMaxClaimsPerDay),
            nativeTemplateInterval          = rc.intIn("native_template_interval", 2, 50, base.nativeTemplateInterval),
            nativeHistoryInterval           = rc.intIn("native_history_interval", 2, 50, base.nativeHistoryInterval),

            // ---------- per-placement ad unit IDs ----------
            adUnitIds = buildAdUnitIds(rc, base.adUnitIds),

            // ---------- per-placement enable flags ----------
            placementEnabled = buildPlacementEnabled(rc, base.placementEnabled),

            // ---------- native card style ----------
            nativeStyle = buildNativeStyle(rc, base.nativeStyle),

            // ---------- unlimited credits override ----------
            unlimitedCreditsEnabled = rc.boolOr(
                "unlimited_credits_enabled",
                rc.boolOr("unlimited_creds", base.unlimitedCreditsEnabled),
            ),
            unlimitedEmail          = rc.getString("unlimited_email").trim().ifBlank { base.unlimitedEmail },
            unlimitedPassword       = rc.stringOr(
                key = "unlimited_password",
                fallback = rc.stringOr("unlimited_pswd", base.unlimitedPassword),
            ),

            // ---------- subscription feature toggle ----------
            subscriptionEnabled   = rc.boolOr("subscription_enabled", false),
            subscriptionPlansJson = rc.getString("subscription_plans_json").trim(),

            // ---------- legal links ----------
            privacyPolicyUrl = rc.stringOr("privacy_policy_url", base.privacyPolicyUrl),
            termsAndConditionsUrl = rc.stringOr("terms_and_conditions_url", base.termsAndConditionsUrl),
            appShareUrl = rc.stringOr("app_share_url", base.appShareUrl),
            supportEmail = rc.stringOr("support_email", base.supportEmail),
        )

        ref.set(updated)
        version += 1
        AdsLogger.d("AdsConfig updated from Remote Config (testMode=${updated.testMode}, adsEnabled=${updated.adsEnabled})")
    }

    /** Read "{placement.key}_adunit" for every placement. Missing/blank → keep existing. */
    private fun buildAdUnitIds(rc: FirebaseRemoteConfig, current: Map<String, String>): Map<String, String> {
        val result = current.toMutableMap()
        AdPlacement.entries.forEach { placement ->
            val rcKey = "${placement.key}_adunit"
            val value = rc.getString(rcKey).trim().ifBlank {
                if (placement == AdPlacement.INTER_ALL) rc.getString("Inter_all_adunit").trim() else ""
            }
            if (value.isNotBlank()) result[placement.key] = value
        }
        return result
    }

    /** Read "{placement.key}_enabled" for every placement. Missing → keep existing (defaults to true). */
    private fun buildPlacementEnabled(rc: FirebaseRemoteConfig, current: Map<String, Boolean>): Map<String, Boolean> {
        val result = current.toMutableMap()
        AdPlacement.entries.forEach { placement ->
            val rcKey = "${placement.key}_enabled"
            // FirebaseRemoteConfig.getString returns "" for unknown keys, so only
            // apply the value when the key is actually present in the fetched config.
            val raw = rc.getString(rcKey).trim()
            if (raw.isNotBlank()) {
                result[placement.key] = rc.getBoolean(rcKey)
            }
        }
        mapOf(
            AdPlacement.INTER_ALL to "enable_inter_all",
            AdPlacement.INTER_BACK to "enable_inter_back",
            AdPlacement.INTER_GENERATE to "enable_inter_generate",
        ).forEach { (placement, key) ->
            if (rc.getString(key).isNotBlank()) result[placement.key] = rc.getBoolean(key)
        }
        return result
    }

    private fun buildNativeStyle(rc: FirebaseRemoteConfig, base: NativeStyleConfig): NativeStyleConfig {
        return base.copy(
            backgroundColor    = rc.colorOr("ads_native_style_background_color", base.backgroundColor),
            titleColor         = rc.colorOr("ads_native_style_title_color", base.titleColor),
            bodyColor          = rc.colorOr("ads_native_style_body_color", base.bodyColor),
            ctaBackgroundColor = rc.colorOr("ads_native_style_cta_background_color", base.ctaBackgroundColor),
            ctaTextColor       = rc.colorOr("ads_native_style_cta_text_color", base.ctaTextColor),
            cornerRadiusDp     = rc.intIn("ads_native_style_corner_radius_dp", 0, 48, base.cornerRadiusDp),
            paddingDp          = rc.intIn("ads_native_style_padding_dp", 0, 48, base.paddingDp),
            titleSp            = rc.intIn("ads_native_style_title_sp", 8, 40, base.titleSp),
            bodySp             = rc.intIn("ads_native_style_body_sp", 8, 40, base.bodySp),
            shimmerEnabled     = rc.boolOr("ads_native_style_shimmer_enabled", base.shimmerEnabled),
        )
    }

    // ------------------------------------------------------------------
    // Legacy entry point — kept so existing tests / tooling still compile.
    // Parses the old single-blob "ads_config_json" format.
    // ------------------------------------------------------------------

    fun applyRemoteJson(json: String) {
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: run {
            AdsLogger.w("AdsConfig: remote JSON unparseable, keeping current config")
            return
        }
        val base = ref.get()
        val updated = base.copy(
            adsEnabled            = parsed.optBoolOr("ads_enabled", base.adsEnabled),
            bannerEnabled         = parsed.optBoolOr("ads_banner_enabled", base.bannerEnabled),
            nativeEnabled         = parsed.optBoolOr("ads_native_enabled", base.nativeEnabled),
            interstitialEnabled   = parsed.optBoolOr("ads_interstitial_enabled", base.interstitialEnabled),
            rewardedEnabled       = parsed.optBoolOr("ads_rewarded_enabled", base.rewardedEnabled),
            appOpenEnabled        = parsed.optBoolOr("ads_app_open_enabled", base.appOpenEnabled),
            testMode              = parsed.optBoolOr("ads_test_mode", base.testMode),
            placementEnabled      = parsePlacementEnabledJson(parsed, base.placementEnabled),
            interBackClickInterval          = parsed.optIntIn("interval_inter_back", 1, 100, base.interBackClickInterval),
            interGenerateClickInterval      = parsed.optIntIn("interval_inter_generate", 1, 100, base.interGenerateClickInterval),
            interstitialPlacementCooldownMs = parsed.optLongIn("inter_cooldown_ms", 0, 600_000, base.interstitialPlacementCooldownMs),
            globalFullScreenCooldownMs      = parsed.optLongIn("fullscreen_cooldown_ms", 0, 600_000, base.globalFullScreenCooldownMs),
            maxInterstitialsPerSession      = parsed.optIntIn("max_inter_per_session", 0, 200, base.maxInterstitialsPerSession),
            appOpenMinIntervalMs            = parsed.optLongIn("app_open_min_interval_ms", 0, 3_600_000, base.appOpenMinIntervalMs),
            appOpenMaxCacheMs               = parsed.optLongIn("app_open_max_cache_ms", 0, 14_400_000, base.appOpenMaxCacheMs),
            splashMaxWaitMs                 = parsed.optLongIn("splash_max_wait_ms", 0, 20_000, base.splashMaxWaitMs),
            fullScreenLoadTimeoutMs         = parsed.optLongIn("fullscreen_load_timeout_ms", 1_000, 30_000, base.fullScreenLoadTimeoutMs),
            rewardCreditsAmount             = parsed.optIntIn("reward_credits_amount", 1, 1_000, base.rewardCreditsAmount),
            rewardMaxClaimsPerDay           = parsed.optIntIn("reward_max_claims_per_day", 0, 100, base.rewardMaxClaimsPerDay),
            nativeTemplateInterval          = parsed.optIntIn("native_template_interval", 2, 50, base.nativeTemplateInterval),
            nativeHistoryInterval           = parsed.optIntIn("native_history_interval", 2, 50, base.nativeHistoryInterval),
            unlimitedCreditsEnabled         = parsed.optBoolOr("unlimited_credits_enabled", parsed.optBoolOr("unlimited_creds", base.unlimitedCreditsEnabled)),
            unlimitedEmail                  = parsed.optStringOrNull("unlimited_email") ?: base.unlimitedEmail,
            unlimitedPassword               = parsed.optStringOrNull("unlimited_password")
                ?: parsed.optStringOrNull("unlimited_pswd")
                ?: base.unlimitedPassword,
            subscriptionEnabled             = parsed.optBoolOr("subscription_enabled", false),
            subscriptionPlansJson           = parsed.optStringOrNull("subscription_plans_json") ?: base.subscriptionPlansJson,
            privacyPolicyUrl                = parsed.optStringOrNull("privacy_policy_url") ?: base.privacyPolicyUrl,
            termsAndConditionsUrl           = parsed.optStringOrNull("terms_and_conditions_url") ?: base.termsAndConditionsUrl,
            appShareUrl                     = parsed.optStringOrNull("app_share_url") ?: base.appShareUrl,
            supportEmail                    = parsed.optStringOrNull("support_email") ?: base.supportEmail,
            nativeStyle = parseNativeStyleJson(parsed, base.nativeStyle),
            adUnitIds   = parseAdUnitIdsJson(parsed, base.adUnitIds),
        )
        ref.set(updated)
        version += 1
        AdsLogger.d("AdsConfig updated from JSON blob (testMode=${updated.testMode}, adsEnabled=${updated.adsEnabled})")
    }

    // ---- legacy JSON helpers ----

    private fun parsePlacementEnabledJson(json: JSONObject, current: Map<String, Boolean>): Map<String, Boolean> {
        val obj = json.optJSONObject("placement_enabled") ?: return current
        val result = current.toMutableMap()
        AdPlacement.entries.forEach { placement ->
            placement.legacyRemoteKeys.forEach { key ->
                if (obj.has(key)) result[placement.key] = obj.optBoolean(key, true)
            }
        }
        return result
    }

    private fun parseAdUnitIdsJson(json: JSONObject, current: Map<String, String>): Map<String, String> {
        val obj = json.optJSONObject("ad_units") ?: return current
        val result = current.toMutableMap()
        AdPlacement.entries.forEach { placement ->
            placement.legacyRemoteKeys.firstNotNullOfOrNull { key ->
                obj.optStringOrNull(key)
            }?.let { result[placement.key] = it }
        }
        return result
    }

    private fun parseNativeStyleJson(json: JSONObject, base: NativeStyleConfig): NativeStyleConfig {
        val s = json.optJSONObject("native_style") ?: return base
        return base.copy(
            backgroundColor    = s.optColor("ads_native_style_background_color", base.backgroundColor),
            titleColor         = s.optColor("ads_native_style_title_color", base.titleColor),
            bodyColor          = s.optColor("ads_native_style_body_color", base.bodyColor),
            ctaBackgroundColor = s.optColor("ads_native_style_cta_background_color", base.ctaBackgroundColor),
            ctaTextColor       = s.optColor("ads_native_style_cta_text_color", base.ctaTextColor),
            cornerRadiusDp     = s.optIntIn("ads_native_style_corner_radius_dp", 0, 48, base.cornerRadiusDp),
            paddingDp          = s.optIntIn("ads_native_style_padding_dp", 0, 48, base.paddingDp),
            titleSp            = s.optIntIn("ads_native_style_title_sp", 8, 40, base.titleSp),
            bodySp             = s.optIntIn("ads_native_style_body_sp", 8, 40, base.bodySp),
            shimmerEnabled     = s.optBoolOr("ads_native_style_shimmer_enabled", base.shimmerEnabled),
        )
    }
}

// ---- Firebase Remote Config bounded helpers ----

private fun FirebaseRemoteConfig.boolOr(key: String, fallback: Boolean): Boolean {
    val v = getString(key).trim()
    return if (v.isBlank()) fallback else getBoolean(key)
}

private fun FirebaseRemoteConfig.stringOr(key: String, fallback: String): String =
    getString(key).trim().ifBlank { fallback }

private fun FirebaseRemoteConfig.intIn(key: String, min: Int, max: Int, fallback: Int): Int {
    val v = getString(key).trim()
    if (v.isBlank()) return fallback
    return getLong(key).toInt().coerceIn(min, max)
}

private fun FirebaseRemoteConfig.longIn(key: String, min: Long, max: Long, fallback: Long): Long {
    val v = getString(key).trim()
    if (v.isBlank()) return fallback
    return getLong(key).coerceIn(min, max)
}

private fun FirebaseRemoteConfig.colorOr(key: String, fallback: Long): Long {
    val raw = getString(key).trim()
    if (raw.isBlank()) return fallback
    return runCatching {
        val hex = raw.removePrefix("#")
        val value = hex.toLong(16)
        if (hex.length <= 6) 0xFF000000L or value else value
    }.getOrDefault(fallback)
}

// ---- Legacy placement remote-key aliases (for old JSON-blob format) ----

private val AdPlacement.legacyRemoteKeys: List<String>
    get() = when (this) {
        AdPlacement.NATIVE_TEMPLATE  -> listOf(key, "native_template")
        AdPlacement.INTER_ALL        -> listOf(key, "inter_all")
        AdPlacement.INTER_POST_SPLASH -> listOf(key, "inter_post_splash")
        AdPlacement.APP_OPEN         -> listOf(key, "app_open")
        else -> listOf(key)
    }

// ---- Legacy JSON object helpers ----

private fun JSONObject.optBoolOr(key: String, fallback: Boolean): Boolean =
    if (has(key)) optBoolean(key, fallback) else fallback

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key, "").takeIf { it.isNotBlank() }

private fun JSONObject.optIntIn(key: String, min: Int, max: Int, fallback: Int): Int {
    if (!has(key)) return fallback
    return optInt(key, fallback).coerceIn(min, max)
}

private fun JSONObject.optLongIn(key: String, min: Long, max: Long, fallback: Long): Long {
    if (!has(key)) return fallback
    return optLong(key, fallback).coerceIn(min, max)
}

private fun JSONObject.optColor(key: String, fallback: Long): Long {
    val raw = optStringOrNull(key) ?: return fallback
    return runCatching {
        val hex = raw.removePrefix("#")
        val value = hex.toLong(16)
        if (hex.length <= 6) 0xFF000000L or value else value
    }.getOrDefault(fallback)
}

/**
 * Holds the live [AdsConfig] snapshot.
 *
 * Starts from safe local defaults with no bundled ad unit IDs. Firebase Remote
 * Config should call [applyRemoteJson] with the fetched JSON before ads load;
 * parsing is fully defensive so malformed/partial/absent values simply keep the
 * previous (or default) value — never a crash.
 *
 * This is the ONLY place that knows the Remote Config JSON shape, keeping the
 * rest of the ads layer decoupled from Firebase.
 */

