package com.mobile.trackerapp.ui.phonelocator

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object CarrierDetector {
    private val phoneUtil = PhoneNumberUtil.getInstance()

    fun detect(context: Context, fullPhoneNumber: String, countryIso: String): PhoneCarrierInfo? {
        return try {
            val number = phoneUtil.parse(fullPhoneNumber, countryIso)
            if (!phoneUtil.isValidNumber(number)) return null
            val region = phoneUtil.getRegionCodeForNumber(number).ifEmpty { countryIso }
            PhoneCarrierInfo(Locale("", region).displayCountry, "Unknown")
        } catch (_: Exception) {
            null
        }
    }
}
