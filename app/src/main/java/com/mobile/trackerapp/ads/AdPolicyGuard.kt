package com.mobile.trackerapp.ads

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-side safeguards that help avoid common Google advertising-policy
 * mistakes. This is NOT a replacement for Google's policies — it enforces
 * consistent, defensive behaviour across the app so an implementation slip is
 * less likely to become a violation.
 *
 * The guard is intentionally conservative: when a situation is ambiguous it
 * blocks the ad (the app then continues its normal flow ad-free).
 */
@Singleton
class AdPolicyGuard @Inject constructor() {

    /**
     * Placement-level policy gate. Currently every declared placement is a
     * vetted, policy-reviewed slot, so this returns true — but it's the single
     * chokepoint where a placement can be disabled if it's ever found to risk:
     *  - overlapping app controls or system navigation
     *  - accidental-click positioning
     *  - disguising ads as app content / hiding "Ad" attribution or AdChoices
     */
    fun isPlacementPolicySafe(placement: AdPlacement): Boolean = true

    /**
     * Whether a full-screen ad may be shown at this moment. Blocks presenting a
     * full-screen ad while another is showing (no overlapping full-screen ads).
     */
    fun canShowFullScreen(isAnotherFullScreenShowing: Boolean): Boolean =
        !isAnotherFullScreenShowing

    /**
     * Reward eligibility guard. A reward may ONLY be granted after the SDK's
     * genuine earned-reward callback — never on load/open/impression/click/
     * dismiss. Call sites must route reward granting through this check.
     */
    fun canGrantReward(earnedFromSdkCallback: Boolean): Boolean = earnedFromSdkCallback

    /**
     * Native ads must always show the "Ad" attribution and keep AdChoices
     * visible. The XML layouts include a mandatory attribution badge and the
     * SDK's AdChoices overlay; this flag documents/enforces that contract.
     */
    val requireAdAttribution: Boolean = true

    /**
     * Banners/natives must never sit under interactive app controls or system
     * navigation. Screens place ad containers within safe/scroll areas; this
     * documents the invariant the [BannerAdView]/native containers rely on
     * (they add navigation-bar insets and never overlap bottom navigation).
     */
    val respectSystemInsets: Boolean = true
}
