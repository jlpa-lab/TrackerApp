package com.location.and.phone.tracker.locationnphonetracker.app

import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.inter_welcome_back
import com.location.and.phone.tracker.locationnphonetracker.ads.native_welcome
import com.location.and.phone.tracker.locationnphonetracker.ads.open_resume

enum class ResumeAdsEntryMode {
    OPEN_RESUME,
    WELCOME,
    NONE,
}

object ResumeAdsEntryRule {
    @Volatile
    private var suppressNextResume = false

    /**
     * Suppresses the next process-resume ad/screen. This is used for external
     * system flows such as App Settings where returning must reveal the
     * feature screen that opened the flow.
     */
    fun suppressNextResume() {
        suppressNextResume = true
    }

    fun consumeResumeSuppression(): Boolean {
        if (!suppressNextResume) return false
        suppressNextResume = false
        return true
    }

    fun currentMode(): ResumeAdsEntryMode {
        if (!AdRemoteConfig.isInitialized()) return ResumeAdsEntryMode.NONE

        val canUseOpenResume = AdRemoteConfig.open_resume.isEnable
        if (canUseOpenResume) return ResumeAdsEntryMode.OPEN_RESUME

        val canUseWelcome = AdRemoteConfig.native_welcome.isEnable && AdRemoteConfig.inter_welcome_back.isEnable
        return if (canUseWelcome) ResumeAdsEntryMode.WELCOME else ResumeAdsEntryMode.NONE
    }

    fun shouldEnableOpenResume(): Boolean = currentMode() == ResumeAdsEntryMode.OPEN_RESUME

    fun shouldShowWelcomeOnResume(): Boolean =
        currentMode() == ResumeAdsEntryMode.WELCOME && !AdRemoteConfig.open_resume.isEnable
}








