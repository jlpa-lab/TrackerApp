package com.mobile.trackerapp.ui.phonelocator
import android.Manifest

import android.content.Intent
import android.provider.ContactsContract
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.app.Dialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.bindNativeAllAd
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.databinding.ActivityPhoneLocatorBinding
import java.util.Locale

class PhoneLocatorActivity : AppCompatActivity() {
    private companion object { const val CONTACT_PICKER_REQUEST = 7001 }
    private var selectedIso = "VN"
    private var selectedDialCode = "+84"
    private var currentNumber = ""
    private var pendingLookupNumber = ""
    private val contactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingLookupNumber.isNotBlank()) {
            applyKnownContact(pendingLookupNumber)
        }
        pendingLookupNumber = ""
    }

    private lateinit var binding: ActivityPhoneLocatorBinding
    private var countryDialog: Dialog? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityPhoneLocatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AdsManager.showNativeAllAndPreload(this, R.layout.layout_native_ad_small)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        AdsManager.loadInterHome(this, ignoreLimit = true)
        AdsManager.loadInterBack(this, ignoreLimit = true)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
        showForm()
        val input = findViewById<EditText>(R.id.phone_input)
        input.background = null
        val clear = findViewById<View>(R.id.phone_clear)
        val contacts = findViewById<View>(R.id.phone_contacts)

        findViewById<View>(R.id.phone_back).setOnClickListener {
            onBackPressed()
        }
        findViewById<View>(R.id.country_selector).setOnClickListener { showCountryPicker() }
        findViewById<View>(R.id.phone_search).setOnClickListener { searchNumber() }
        findViewById<View>(R.id.phone_retry).setOnClickListener { showForm() }
        findViewById<View>(R.id.phone_cancel).setOnClickListener { finish() }
        findViewById<View>(R.id.details_call).setOnClickListener { dialNumber() }
        findViewById<View>(R.id.details_sms).setOnClickListener { messageNumber() }
        findViewById<View>(R.id.details_navigate).setOnClickListener { navigateToNumber() }
        clear.setOnClickListener { input.text.clear() }
        contacts.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                CONTACT_PICKER_REQUEST
            )
        }

        // Show contacts icon when empty, clear (x) button when there is text
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                clear.visibility = if (hasText) View.VISIBLE else View.GONE
                contacts.visibility = if (hasText) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ---------- screen state helpers ----------
    private fun showForm() {
        findViewById<View>(R.id.phone_result).visibility = View.GONE
        findViewById<View>(R.id.phone_details).visibility = View.GONE
        findViewById<View>(R.id.phone_form).visibility = View.VISIBLE
        findViewById<TextView>(R.id.phone_title).setText(R.string.phone_locator_title)
        findViewById<View>(R.id.phone_subtitle).visibility = View.VISIBLE
    }

    private fun showDetails() {
        findViewById<View>(R.id.phone_form).visibility = View.GONE
        findViewById<View>(R.id.phone_result).visibility = View.GONE
        findViewById<View>(R.id.phone_details).visibility = View.VISIBLE
        findViewById<TextView>(R.id.phone_title).setText(R.string.phone_details_title)
        findViewById<View>(R.id.phone_subtitle).visibility = View.GONE
    }

    // ---------- country picker ----------
    private fun showCountryPicker() {
        val util = PhoneNumberUtil.getInstance()
        val regions = util.supportedRegions.sortedBy { Locale("", it).displayCountry }
        data class CountryItem(val iso: String, val name: String, val code: String)
        val allCountries = regions.map {
            CountryItem(it, Locale("", it).displayCountry, "+" + util.getCountryCodeForRegion(it))
        }

        countryDialog?.dismiss()
        val view = layoutInflater.inflate(R.layout.dialog_country_picker, null)
        view.setBackgroundResource(R.drawable.bg_country_sheet)
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { setCountryPickerBlurred(false) }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.32f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        countryDialog = dialog
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setCountryPickerBlurred(true)
        val countrySearch = view.findViewById<EditText>(R.id.country_search)
        countrySearch.background = null
        countrySearch.setTextColor(Color.rgb(17, 24, 39))
        countrySearch.setHintTextColor(Color.rgb(176, 183, 195))
        countrySearch.alpha = 1f
        countrySearch.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        countrySearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(countrySearch.windowToken, 0)
                countrySearch.clearFocus()
                true
            } else false
        }
        val list = view.findViewById<ListView>(R.id.country_list)
        list.setBackgroundColor(Color.WHITE)
        list.cacheColorHint = Color.TRANSPARENT
        val visibleCountries = allCountries.toMutableList()
        val selectedBg = Color.parseColor("#EDF9F3")

        val adapter = object : BaseAdapter() {
            override fun getCount() = visibleCountries.size
            override fun getItem(position: Int) = visibleCountries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled ?: layoutInflater.inflate(R.layout.country_picker_row, parent, false)
                val item = getItem(position)
                val isSelected = item.iso == selectedIso
                row.setBackgroundColor(if (isSelected) selectedBg else Color.TRANSPARENT)
                row.findViewById<TextView>(R.id.country_flag).apply {
                    text = regionToFlag(item.iso)
                    alpha = 1f
                    setTextColor(Color.BLACK)
                }
                row.findViewById<TextView>(R.id.country_name).apply {
                    text = item.name
                    alpha = 1f
                }
                row.findViewById<TextView>(R.id.country_code).apply {
                    text = item.code
                    alpha = 1f
                }
                row.findViewById<View>(R.id.country_check).visibility = if (isSelected) View.VISIBLE else View.GONE
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, which, _ ->
            val selected = visibleCountries[which]
            selectedIso = selected.iso
            selectedDialCode = selected.code
            findViewById<TextView>(R.id.country_selector).text = regionToFlag(selected.iso) + " " + selected.code
            adapter.notifyDataSetChanged()
        }

        view.findViewById<View>(R.id.country_close).setOnClickListener { hideCountryPicker() }
        view.findViewById<View>(R.id.country_done).setOnClickListener { hideCountryPicker() }
        countrySearch.isFocusableInTouchMode = true
        countrySearch.setOnClickListener {
            countrySearch.requestFocus()
            countrySearch.post {
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(countrySearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        countrySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                visibleCountries.clear()
                visibleCountries.addAll(allCountries.filter {
                    it.name.lowercase(Locale.getDefault()).contains(query) ||
                        it.code.contains(query) ||
                        it.iso.lowercase(Locale.getDefault()).contains(query)
                })
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.requestLayout()
    }

    private fun hideCountryPicker() {
        countryDialog?.dismiss()
        countryDialog = null
        setCountryPickerBlurred(false)
    }

    private fun setCountryPickerBlurred(blurred: Boolean) {
        // The dialog window already dims the Phone Locator screen behind it.
        // Do not apply a RenderEffect to the activity root: on some devices it
        // also affects the dialog window and makes the picker text/flags blurry.
    }

    @Deprecated("Deprecated in Android API; intercept back while the picker overlay is open")
    override fun onBackPressed() {
        if (countryDialog?.isShowing == true) {
            hideCountryPicker()
        } else if (findViewById<View>(R.id.phone_form).visibility == View.VISIBLE) {
            AdsManager.showInterHomeBack(this) {
                finish()
            }
        } else {
            showForm()
        }
    }
    private fun hideKeyboard() {
        val view = currentFocus ?: window.decorView
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }
    private fun regionToFlag(region: String): String {
        val flag = StringBuilder()
        region.uppercase(Locale.US).forEach { letter ->
            flag.appendCodePoint(0x1F1E6 + (letter.code - 'A'.code))
        }
        flag.appendCodePoint(0xFE0F)
        return flag.toString()
    }

    // ---------- search ----------
    private fun searchNumber() {
        hideKeyboard()
        val raw = findViewById<EditText>(R.id.phone_input).text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, R.string.phone_enter_number_to_search, Toast.LENGTH_SHORT).show()
            return
        }
        val digitCount = raw.count(Char::isDigit)
        if (digitCount !in 4..15) {
            Toast.makeText(this, R.string.phone_invalid_number, Toast.LENGTH_SHORT).show()
            return
        }
        val phoneUtil = PhoneNumberUtil.getInstance()
        val parsed = try {
            phoneUtil.parse(raw, selectedIso)
        } catch (_: Exception) {
            null
        }
        // The global boundary is 4–15 digits, but the number must also be
        // valid for the selected country. This prevents values such as "12"
        // from being accepted for India or any other country.
        if (parsed == null || !phoneUtil.isValidNumberForRegion(parsed, selectedIso)) {
            showNoResult()
            return
        }

        val fullNumber = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        val info = CarrierDetector.detect(this, fullNumber, selectedIso) ?: run {
            showNoResult()
            return
        }

        currentNumber = fullNumber
        showDetails()
        findViewById<TextView>(R.id.details_number).text = fullNumber
        findViewById<TextView>(R.id.details_country).text = info.country
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            applyKnownContact(fullNumber)
        } else {
            pendingLookupNumber = fullNumber
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun applyKnownContact(number: String) {
        var contactName: String? = null
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(Uri.encode(number))
            .build()
        contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) contactName = cursor.getString(0)
        }
        if (contactName.isNullOrBlank()) {
            val targetDigits = number.filter(Char::isDigit).takeLast(10)
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val savedDigits = cursor.getString(numberIndex).orEmpty().filter(Char::isDigit)
                    if (savedDigits.takeLast(10) == targetDigits) {
                        contactName = cursor.getString(nameIndex)
                        break
                    }
                }
            }
        }
        val nameView = findViewById<TextView>(R.id.details_name)
        val locationView = findViewById<View>(R.id.details_location)
        if (contactName.isNullOrBlank()) {
            nameView.setText(R.string.phone_unknown)
            locationView.setBackgroundResource(R.drawable.bg_phone_pill_unknown)
        } else {
            nameView.text = contactName
            locationView.setBackgroundResource(R.drawable.bg_phone_pill_known)
        }
    }

    private fun dialNumber() {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$currentNumber")))
    }

    private fun messageNumber() {
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$currentNumber")))
    }

    private fun navigateToNumber() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(currentNumber)}")
        )
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }

    private fun showNoResult() {
        findViewById<View>(R.id.phone_form).visibility = View.GONE
        findViewById<View>(R.id.phone_details).visibility = View.GONE
        findViewById<View>(R.id.phone_result).visibility = View.VISIBLE

        val value = findViewById<EditText>(R.id.phone_input).text.toString().trim()
        val displayedNumber = if (value.isEmpty()) selectedDialCode else "$selectedDialCode $value"

        findViewById<TextView>(R.id.result_title).setText(R.string.phone_no_results)
        findViewById<TextView>(R.id.result_message).text = getString(R.string.phone_result_message, displayedNumber)
        findViewById<TextView>(R.id.result_flag).text = regionToFlag(selectedIso)
        findViewById<TextView>(R.id.result_code).text = selectedDialCode
        findViewById<TextView>(R.id.result_number).text = value
    }

    @Deprecated("Deprecated in Android API; retained for the contact picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CONTACT_PICKER_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                findViewById<EditText>(R.id.phone_input).setText(
                    cursor.getString(0).orEmpty()
                )
            }
        }
    }
}
