package com.location.and.phone.tracker.locationnphonetracker.ui.phonelocator
import android.Manifest

import android.content.Intent
import android.provider.ContactsContract
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.app.Dialog
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.bindNativeAllAd
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityPhoneLocatorBinding
import com.location.and.phone.tracker.locationnphonetracker.localization.LocaleManager
import java.util.Locale

class PhoneLocatorActivity : AppCompatActivity() {
    private companion object { const val CONTACT_PICKER_REQUEST = 7001 }
    private data class KnownContact(
        val number: String,
        val name: String?,
        val address: String?,
        val photoUri: String? = null
    )

    private var selectedIso = "VN"
    private var selectedDialCode = "+84"
    private var currentNumber = ""
    private var currentAddress = ""
    private var pendingLookupNumber = ""
    private var selectedContact: KnownContact? = null
    private var applyingContactSelection = false
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
    private var countryPickerKeyboardVisible = false
    private var countryPickerExpanded = false
    private var countryPickerContentView: View? = null
    private var countryPickerListView: ListView? = null
    private var countryPickerDefaultListHeight = 312

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        LocaleManager.applyPersisted(this)
        binding = ActivityPhoneLocatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
        binding.root.isClickable = true
        binding.root.setOnClickListener { hideKeyboard() }
        AdsManager.loadNativeAll(this, R.layout.layout_native_ad_small)
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
            onBackPressedDispatcher.onBackPressed()
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
                if (!applyingContactSelection) selectedContact = null
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
        hideKeyboard()
        val util = PhoneNumberUtil.getInstance()
        val regions = util.supportedRegions.sortedBy { Locale("", it).displayCountry }
        data class CountryItem(val iso: String, val name: String, val code: String)
        val allCountries = regions.map {
            CountryItem(it, Locale("", it).displayCountry, "+" + util.getCountryCodeForRegion(it))
        }

        countryDialog?.dismiss()
        val view = layoutInflater.inflate(R.layout.dialog_country_picker, null)
        view.setBackgroundResource(R.drawable.bg_country_sheet)
        val basePickerPaddingBottom = view.paddingBottom
        val countrySearch = view.findViewById<EditText>(R.id.country_search)
        val list = view.findViewById<ListView>(R.id.country_list)
        val defaultListHeight = list.layoutParams.height
        countryPickerContentView = view
        countryPickerListView = list
        countryPickerDefaultListHeight = defaultListHeight
        val dialog = Dialog(this)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        var dialogBackCallback: OnBackInvokedCallback? = null
        dialog.setOnDismissListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                dialogBackCallback?.let { callback ->
                    dialog.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
                }
            }
            countryPickerKeyboardVisible = false
            countryPickerExpanded = false
            countryPickerContentView = null
            countryPickerListView = null
            if (countryDialog === dialog) countryDialog = null
            setCountryPickerBlurred(false)
        }
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.32f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            )
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        countryDialog = dialog
        dialog.show()
        hideCountryPickerNavigationBar(dialog)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dialogBackCallback = object : OnBackInvokedCallback {
                override fun onBackInvoked() {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
            dialog.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                dialogBackCallback!!
            )
        }
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.window?.decorView?.let { decorView ->
            hideCountryPickerNavigationBar(dialog)
            ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
                hideCountryPickerNavigationBar(dialog)
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) ||
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
                // Keep this state true until the first back step handles it. On
                // gesture navigation, the IME may update its insets before the
                // dialog receives the back callback.
                if (imeVisible) countryPickerKeyboardVisible = true
                // Hiding the IME is only the first back step. Keep the picker
                // expanded until the next back step explicitly collapses it.
                if (imeVisible && !countryPickerExpanded) {
                    setCountryPickerExpanded(dialog, view, list, true)
                }
                insets
            }
            ViewCompat.requestApplyInsets(decorView)
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            // Keep Done above both the regular navigation bar and the tappable
            // system-bar area while the hidden bar is temporarily revealed by
            // a swipe gesture.
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val tappableBottom = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                basePickerPaddingBottom + maxOf(navigationBottom, tappableBottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
        setCountryPickerBlurred(true)
        val hideCountryKeyboard = {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(countrySearch.windowToken, 0)
            countrySearch.clearFocus()
            countryPickerKeyboardVisible = false
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                event.action == android.view.KeyEvent.ACTION_UP
            ) {
                handleCountryPickerBack()
            } else {
                false
            }
        }
        countrySearch.background = null
        countrySearch.setTextColor(Color.rgb(17, 24, 39))
        countrySearch.setHintTextColor(Color.rgb(176, 183, 195))
        countrySearch.alpha = 1f
        countrySearch.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        countrySearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {
                hideCountryKeyboard()
                true
            } else false
        }
        view.isClickable = true
        view.setOnClickListener { hideCountryKeyboard() }
        view.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                hideCountryKeyboard()
            }
            false
        }
        list.setBackgroundColor(Color.WHITE)
        list.cacheColorHint = Color.TRANSPARENT
        list.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                hideCountryKeyboard()
            }
            false
        }
        val visibleCountries = allCountries.toMutableList()
        var pendingIso = selectedIso
        var pendingDialCode = selectedDialCode
        val selectedBg = Color.parseColor("#EDF9F3")

        val adapter = object : BaseAdapter() {
            override fun getCount() = visibleCountries.size
            override fun getItem(position: Int) = visibleCountries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val row = recycled ?: layoutInflater.inflate(R.layout.country_picker_row, parent, false)
                val item = getItem(position)
                val isSelected = item.iso == pendingIso
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
            pendingIso = selected.iso
            pendingDialCode = selected.code
            hideCountryKeyboard()
            adapter.notifyDataSetChanged()
        }

        view.findViewById<View>(R.id.country_close).setOnClickListener { hideCountryPicker() }
        view.findViewById<View>(R.id.country_done).setOnClickListener {
            selectedIso = pendingIso
            selectedDialCode = pendingDialCode
            findViewById<TextView>(R.id.country_selector).text = regionToFlag(selectedIso) + " " + selectedDialCode
            hideCountryPicker()
        }
        countrySearch.isFocusableInTouchMode = true
        countrySearch.setOnClickListener {
            countrySearch.requestFocus()
            countryPickerKeyboardVisible = true
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
        countryPickerKeyboardVisible = false
        countryPickerExpanded = false
        setCountryPickerBlurred(false)
    }

    private fun hideCountryPickerNavigationBar(dialog: Dialog) {
        val dialogWindow = dialog.window ?: return
        val decorView = dialogWindow.decorView
        val controller = WindowCompat.getInsetsController(dialogWindow, decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }

    private fun handleCountryPickerBack(): Boolean {
        if (countryDialog?.isShowing != true) return false
        if (countryPickerKeyboardVisible) {
            countryDialog?.findViewById<EditText>(R.id.country_search)?.let { hideKeyboard(it) }
            countryPickerKeyboardVisible = false
            return true
        }
        if (countryPickerExpanded) {
            val dialog = countryDialog ?: return true
            val view = countryPickerContentView ?: return true
            val list = countryPickerListView ?: return true
            setCountryPickerExpanded(dialog, view, list, false)
            return true
        }
        hideCountryPicker()
        return true
    }

    private fun setCountryPickerExpanded(dialog: Dialog, view: View, list: ListView, expanded: Boolean) {
        countryPickerExpanded = expanded
        val rootParams = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val listParams = list.layoutParams
        if (expanded) {
            dialog.window?.setGravity(Gravity.TOP)
            rootParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            listParams.height = 0
            if (listParams is LinearLayout.LayoutParams) listParams.weight = 1f
            view.layoutParams = rootParams
            list.layoutParams = listParams
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            dialog.window?.setGravity(Gravity.BOTTOM)
            rootParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            listParams.height = countryPickerDefaultListHeight
            if (listParams is LinearLayout.LayoutParams) listParams.weight = 0f
            view.layoutParams = rootParams
            list.layoutParams = listParams
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        view.requestLayout()
    }

    private fun setCountryPickerBlurred(blurred: Boolean) {
        // The dialog window already dims the Phone Locator screen behind it.
        // Do not apply a RenderEffect to the activity root: on some devices it
        // also affects the dialog window and makes the picker text/flags blurry.
    }

    private fun handleBackNavigation() {
        if (countryDialog?.isShowing == true) {
            handleCountryPickerBack()
        } else if (findViewById<View>(R.id.phone_form).visibility == View.VISIBLE) {
            AdsManager.showInterHomeBack(this) { _ ->
                finish()
            }
        } else {
            showForm()
        }
    }
    private fun hideKeyboard(target: View? = currentFocus) {
        val view = target ?: window.decorView
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
        val isInternationalNumber = raw.startsWith("+")
        val parsed = try {
            phoneUtil.parse(raw, if (isInternationalNumber) null else selectedIso)
        } catch (_: Exception) {
            null
        }
        // The global boundary is 4–15 digits, but the number must also be
        // valid for the selected country. This prevents values such as "12"
        // from being accepted for India or any other country.
        val isValid = parsed != null && if (isInternationalNumber) {
            phoneUtil.isValidNumber(parsed)
        } else {
            phoneUtil.isValidNumberForRegion(parsed, selectedIso)
        }
        if (!isValid) {
            showNoResult()
            return
        }

        val fullNumber = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        val knownContact = if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            selectedContact?.takeIf { numbersMatch(it.number, fullNumber) } ?: findKnownContact(fullNumber)
        } else {
            null
        }
        val info = CarrierDetector.detect(this, fullNumber, selectedIso)
        if (info == null && knownContact == null) {
            showNoResult()
            return
        }

        currentNumber = fullNumber
        showDetails()
        findViewById<TextView>(R.id.details_number).text = fullNumber
        val fallbackLocation = info?.country
            ?: knownContact?.address?.takeIf { it.isNotBlank() }
            ?: Locale("", phoneUtil.getRegionCodeForNumber(parsed)).displayCountry
        findViewById<TextView>(R.id.details_country).text = fallbackLocation
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            applyKnownContact(fullNumber, fallbackLocation)
        } else {
            pendingLookupNumber = fullNumber
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun applyKnownContact(number: String, fallbackLocation: String? = null) {
        val contact = selectedContact?.takeIf { numbersMatch(it.number, number) }
            ?: findKnownContact(number)
        val nameView = findViewById<TextView>(R.id.details_name)
        val locationView = findViewById<View>(R.id.details_location)
        val locationViewText = findViewById<TextView>(R.id.details_country)
        val navigateView = findViewById<View>(R.id.details_navigate)
        val avatar = findViewById<ImageView>(R.id.details_avatar)
        val isKnownContact = !contact?.name.isNullOrBlank()
        if (!isKnownContact) {
            nameView.setText(R.string.phone_unknown)
            locationView.setBackgroundResource(R.drawable.bg_phone_pill_unknown)
            navigateView.visibility = View.GONE
            currentAddress = ""
        } else {
            nameView.text = contact.name
            locationView.setBackgroundResource(R.drawable.bg_phone_pill_known)
            navigateView.visibility = View.VISIBLE
            currentAddress = contact.address
                ?.takeIf { it.isNotBlank() }
                ?: fallbackLocation
                ?: locationViewText.text?.toString().orEmpty()
        }
        val photoUri = contact?.photoUri
        if (isKnownContact && !photoUri.isNullOrBlank()) {
            avatar.setImageURI(Uri.parse(photoUri))
            avatar.setBackgroundResource(R.drawable.bg_phone_no_result_icon)
            avatar.setPadding(0, 0, 0, 0)
            avatar.scaleType = ImageView.ScaleType.CENTER_CROP
            avatar.clipToOutline = true
        } else {
            avatar.setImageResource(R.drawable.ic_phone_person)
            avatar.setBackgroundResource(R.drawable.bg_phone_no_result_icon)
            val padding = resources.getDimensionPixelSize(R.dimen.phone_details_avatar_padding)
            avatar.setPadding(padding, padding, padding, padding)
            avatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
            avatar.clipToOutline = true
        }
        locationViewText.text = contact?.address?.takeIf { it.isNotBlank() }
            ?: fallbackLocation
            ?: locationViewText.text
    }

    private fun findKnownContact(number: String): KnownContact? {
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(Uri.encode(number))
            .build()
        contentResolver.query(
            lookupUri,
            arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.CONTACT_ID,
                ContactsContract.Contacts.PHOTO_URI
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                val contactId = cursor.getLong(1)
                return KnownContact(number, name, contactAddress(contactId), cursor.getString(2))
            }
        }

        val nameIndex = 0
        val numberIndex = 1
        val contactIdIndex = 2
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.Contacts.PHOTO_URI
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val savedNumber = cursor.getString(numberIndex).orEmpty()
                if (numbersMatch(savedNumber, number)) {
                    val contactId = cursor.getLong(contactIdIndex)
                    return KnownContact(
                        number = savedNumber,
                        name = cursor.getString(nameIndex),
                        address = contactAddress(contactId),
                        photoUri = cursor.getString(3)
                    )
                }
            }
        }
        return null
    }

    private fun contactAddress(contactId: Long): String? {
        contentResolver.query(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
                ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY
            ),
            "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val formatted = cursor.getString(0).orEmpty().trim()
                if (formatted.isNotBlank()) return formatted
                val parts = (1 until cursor.columnCount)
                    .map { cursor.getString(it).orEmpty().trim() }
                    .filter { it.isNotBlank() }
                return parts.joinToString(", ").ifBlank { null }
            }
        }
        return null
    }

    private fun numbersMatch(first: String, second: String): Boolean {
        return normalizedNumberKeys(first).intersect(normalizedNumberKeys(second)).isNotEmpty()
    }

    private fun normalizedNumberKeys(number: String): Set<String> {
        val digits = number.filter(Char::isDigit)
        val keys = mutableSetOf<String>()
        if (digits.isNotBlank()) keys += digits
        if (digits.length >= 7) keys += digits.takeLast(10)
        runCatching {
            PhoneNumberUtil.getInstance().parse(number, selectedIso)
        }.getOrNull()?.let { parsed ->
            val e164 = PhoneNumberUtil.getInstance().format(
                parsed,
                PhoneNumberUtil.PhoneNumberFormat.E164
            )
            keys += e164.filter(Char::isDigit)
        }
        return keys
    }

    private fun dialNumber() {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$currentNumber")))
    }

    private fun messageNumber() {
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$currentNumber")))
    }

    private fun navigateToNumber() {
        val destination = currentAddress
            .takeIf { it.isNotBlank() }
            ?: Locale("", selectedIso).displayCountry
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=" + Uri.encode(destination))
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
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.Contacts.PHOTO_URI
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number = cursor.getString(0).orEmpty()
                if (number.isBlank()) return@use
                val contactId = cursor.getLong(2)
                selectedContact = KnownContact(
                    number = number,
                    name = cursor.getString(1),
                    address = contactAddress(contactId),
                    photoUri = cursor.getString(3)
                )
                applyingContactSelection = true
                findViewById<EditText>(R.id.phone_input).setText(number)
                applyingContactSelection = false
            }
        }
    }
}
