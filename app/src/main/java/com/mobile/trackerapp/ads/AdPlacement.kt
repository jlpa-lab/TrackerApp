package com.mobile.trackerapp.ads

/**
 * The 26 required advertising placements. Screens reference a strongly-typed
 * [AdPlacement] rather than raw ID/format strings, so [AdsManager] fully owns
 * format selection, eligibility, frequency, cooldown and Remote Config lookup.
 *
 * [key] is the stable identifier used for logging and Remote Config lookup.
 * [nativeStyle] applies only to NATIVE placements (regular vs large layout).
 */
enum class AdPlacement(
    val key: String,
    val format: AdFormat,
    val nativeStyle: NativeAdStyle = NativeAdStyle.NONE,
) {
    // 01
    BANNER_SPLASH("banner_splash", AdFormat.BANNER),
    // 02
    NATIVE_LANGUAGE("native_language", AdFormat.NATIVE, NativeAdStyle.LARGE),
    // 03
    INTER_LANGUAGE("inter_language", AdFormat.INTERSTITIAL),
    // 04-07
    OB_NATIVE_1("ob_native_1", AdFormat.NATIVE, NativeAdStyle.LARGE),
    OB_NATIVE_2("ob_native_2", AdFormat.NATIVE, NativeAdStyle.LARGE),
    OB_NATIVE_3("ob_native_3", AdFormat.NATIVE, NativeAdStyle.LARGE),
    OB_NATIVE_4("ob_native_4", AdFormat.NATIVE, NativeAdStyle.LARGE),
    // 08
    OB_INTER("ob_inter", AdFormat.INTERSTITIAL),
    // 09
    BANNER_ALL("banner_all", AdFormat.BANNER),
    // 10-16 feature banners
    BANNER_TEXT2IMG("banner_text2img", AdFormat.BANNER),
    BANNER_IMG2IMG("banner_img2img", AdFormat.BANNER),
    BANNER_IMG2VIDEO("banner_img2video", AdFormat.BANNER),
    BANNER_TEXT2VIDEO("banner_text2video", AdFormat.BANNER),
    BANNER_PROMO("banner_promo", AdFormat.BANNER),
    BANNER_BG("banner_bg", AdFormat.BANNER),
    BANNER_ENHANCER("banner_enhancer", AdFormat.BANNER),
    // 17
    NATIVE_HOME("native_home", AdFormat.NATIVE, NativeAdStyle.LARGE),
    // 18
    NATIVE_TEMPLATE("nativ_template", AdFormat.NATIVE, NativeAdStyle.REGULAR),
    // 19
    NATIVE_TOOLS("native_tools", AdFormat.NATIVE, NativeAdStyle.REGULAR),
    // 20
    NATIVE_HISTORY("native_history", AdFormat.NATIVE, NativeAdStyle.REGULAR),
    // 21
    NATIVE_COMPRESS("native_compress", AdFormat.NATIVE, NativeAdStyle.LARGE),
    NATIVE_UNINSTALL("native_uninstall", AdFormat.NATIVE, NativeAdStyle.LARGE),
    // 22
    INTER_ALL("inter_all", AdFormat.INTERSTITIAL),
    INTER_BACK("inter_back", AdFormat.INTERSTITIAL),
    INTER_GENERATE("inter_generate", AdFormat.INTERSTITIAL),
    // 23
    INTER_SETTING_LANGUAGE("inter_setting_language", AdFormat.INTERSTITIAL),
    // 24
    INTER_POST_SPLASH("inter_ad_post_splash", AdFormat.INTERSTITIAL),
    // 25
    APP_OPEN("app_open_ad", AdFormat.APP_OPEN),
    // 26
    REWARD_CREDITS("reward_credits", AdFormat.REWARDED),
    ;

    companion object {
        fun ofFormat(format: AdFormat): List<AdPlacement> = entries.filter { it.format == format }
        fun fromKey(key: String): AdPlacement? = entries.firstOrNull { it.key == key }
    }
}

/** Native layout variant a placement should render with. */
enum class NativeAdStyle { NONE, REGULAR, LARGE }
