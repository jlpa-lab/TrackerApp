package com.mobile.trackerapp.ads

/** The five AdMob ad formats supported by the app. */
enum class AdFormat { BANNER, NATIVE, INTERSTITIAL, REWARDED, APP_OPEN }

/**
 * Remote-Config-compatible advertising configuration with mandatory local
 * defaults. Every value here can later be overridden by Firebase Remote Config
 * via [AdsConfigStore] parsing; nothing in the ads layer reads raw ID strings
 * or Remote Config directly — they all read this snapshot.
 *
 * This is a pure immutable data holder (configuration only — never secrets).
 */
data class AdsConfig(
    // ---- Global + format toggles ----
    val adsEnabled: Boolean = true,
    val bannerEnabled: Boolean = true,
    val nativeEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val rewardedEnabled: Boolean = true,
    val appOpenEnabled: Boolean = true,

    /** Remote-controlled diagnostics flag. It never swaps in local test IDs. */
    val testMode: Boolean = false,

    // ---- Per-placement enable map (key -> enabled). Missing key = enabled. ----
    val placementEnabled: Map<String, Boolean> = emptyMap(),

    // ---- Frequency / cooldown ----
    /** Independent trigger counts for feature exits and task starts. */
    val interBackClickInterval: Int = 2,
    val interGenerateClickInterval: Int = 2,
    val interstitialPlacementCooldownMs: Long = 30_000L,
    val globalFullScreenCooldownMs: Long = 30_000L,
    val maxInterstitialsPerSession: Int = 20,
    val appOpenMinIntervalMs: Long = 60_000L,
    val appOpenMaxCacheMs: Long = 4 * 60 * 60 * 1000L,
    val splashMaxWaitMs: Long = 6_000L,
    val fullScreenLoadTimeoutMs: Long = 8_000L,

    // ---- Rewarded ----
    val rewardCreditsAmount: Int = 2,
    val rewardMaxClaimsPerDay: Int = 3,

    // ---- List insertion intervals for native-in-list placements ----
    val nativeTemplateInterval: Int = 3,
    val nativeHistoryInterval: Int = 3,

    // ---- Native styling (validated; malformed values fall back to defaults) ----
    val nativeStyle: NativeStyleConfig = NativeStyleConfig(),

    // ---- Per-placement ad unit IDs, expected from Remote Config ----
    val adUnitIds: Map<String, String> = emptyMap(),

    // ---- Unlimited credits override (remote-controlled tester access) ----
    /** Master switch for the unlimited-credits login override. */
    val unlimitedCreditsEnabled: Boolean = false,
    /** Email that triggers unlimited credits when matched at login. */
    val unlimitedEmail: String = "",
    /** Password that triggers unlimited credits when matched at login. */
    val unlimitedPassword: String = "",

    // ---- Subscription feature toggle ----
    /**
     * Master switch for the entire subscription / paywall feature.
     * When false: subscription acquisition entry points are hidden and direct
     * navigation immediately exits before rendering a paywall. Defaults to false;
     * existing subscribers and purchase entitlement checks remain unaffected.
     */
    val subscriptionEnabled: Boolean = false,

    /**
     * Optional JSON array of plan objects that override the in-app default plans.
     * Format (each element):
     *   { "id":"pro_monthly","name":"Pro Monthly","price":"$19.99",
     *     "billing_period":"per month","highlighted":false,
     *     "features":["500 credits/month","HD image generation"] }
     * Blank/invalid JSON falls back to the ViewModel default plans silently.
     */
    val subscriptionPlansJson: String = "",

    // ---- Legal links ----
    val privacyPolicyUrl: String = "https://lumoraai.example/privacy-policy",
    val termsAndConditionsUrl: String = "https://lumoraai.example/terms-and-conditions",
    val appShareUrl: String = "https://play.google.com/store/apps/details?id=com.deep.lumoraai",
    val supportEmail: String = "lumoraaisupport@gmail.com",
) {
    fun formatEnabled(format: AdFormat): Boolean = adsEnabled && when (format) {
        AdFormat.BANNER -> bannerEnabled
        AdFormat.NATIVE -> nativeEnabled
        AdFormat.INTERSTITIAL -> interstitialEnabled
        AdFormat.REWARDED -> rewardedEnabled
        AdFormat.APP_OPEN -> appOpenEnabled
    }

    fun isPlacementEnabled(placement: AdPlacement): Boolean =
        placementEnabled[placement.key] ?: true

    /** Resolve the effective ad unit ID. Test mode uses official Google test IDs. */
    fun unitIdFor(placement: AdPlacement): String? {
        if (testMode) return TestIds.of(placement.format)
        return adUnitIds[placement.key]?.takeIf { it.isNotBlank() }
    }

    companion object {
        /** The immutable local default used before Remote Config resolves. */
        val DEFAULT = AdsConfig()
    }
}

/** Google's official Android test ad unit IDs, centralized in one place. */
object TestIds {
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"

    fun of(format: AdFormat): String = when (format) {
        AdFormat.BANNER -> BANNER
        AdFormat.NATIVE -> NATIVE
        AdFormat.INTERSTITIAL -> INTERSTITIAL
        AdFormat.REWARDED -> REWARDED
        AdFormat.APP_OPEN -> APP_OPEN
    }
}

/**
 * App-owned native container styling. All values validated on parse; malformed
 * remote values are replaced with these defaults so rendering never crashes.
 */
data class NativeStyleConfig(
    val backgroundColor: Long = 0xFF0E172A,
    val titleColor: Long = 0xFFFFFFFF,
    val bodyColor: Long = 0xFF94A0B8,
    val ctaBackgroundColor: Long = 0xFFD6FF2F,
    val ctaTextColor: Long = 0xFF081020,
    val cornerRadiusDp: Int = 14,
    val paddingDp: Int = 14,
    val titleSp: Int = 15,
    val bodySp: Int = 12,
    val shimmerEnabled: Boolean = true,
)
