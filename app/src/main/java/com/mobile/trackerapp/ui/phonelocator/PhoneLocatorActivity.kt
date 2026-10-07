package com.mobile.trackerapp.ui.phonelocator

import android.content.Intent
import android.provider.ContactsContract
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.mobile.trackerapp.R
import java.util.Locale

class PhoneLocatorActivity : AppCompatActivity() {
    private companion object { const val CONTACT_PICKER_REQUEST = 7001 }
    private var selectedIso = "VN"
    private var selectedDialCode = "+84"
    private var currentNumber = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_phone_locator)
        showForm()

        val input = findViewById<EditText>(R.id.phone_input)
        input.background = null
        val clear = findViewById<View>(R.id.phone_clear)
        val contacts = findViewById<View>(R.id.phone_contacts)

        findViewById<View>(R.id.phone_back).setOnClickListener {
            if (findViewById<View>(R.id.country_picker_overlay).visibility == View.VISIBLE) {
                hideCountryPicker()
            } else if (findViewById<View>(R.id.phone_form).visibility == View.VISIBLE) {
                finish()
            } else {
                showForm()
            }
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
        findViewById<TextView>(R.id.phone_title).text = "Phone Locator"
        findViewById<View>(R.id.phone_subtitle).visibility = View.VISIBLE
    }

    private fun showDetails() {
        findViewById<View>(R.id.phone_form).visibility = View.GONE
        findViewById<View>(R.id.phone_result).visibility = View.GONE
        findViewById<View>(R.id.phone_details).visibility = View.VISIBLE
        findViewById<TextView>(R.id.phone_title).text = "Details"
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

        val overlay = findViewById<View>(R.id.country_picker_overlay)
        val view = findViewById<View>(R.id.country_picker_sheet)
        view.setBackgroundResource(R.drawable.bg_country_sheet)
        view.layoutParams = (view.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            val screenInset = resources.getDimensionPixelSize(R.dimen.phone_locator_screen_inset)
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = (resources.displayMetrics.heightPixels * 0.78f).toInt()
            gravity = Gravity.BOTTOM
            marginStart = screenInset
            marginEnd = screenInset
        }
        view.setOnClickListener { /* Keep taps inside the sheet from closing it. */ }
        overlay.setOnClickListener { hideCountryPicker() }
        overlay.visibility = View.VISIBLE
        val countrySearch = view.findViewById<EditText>(R.id.country_search)
        countrySearch.background = null
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
                }
                row.findViewById<TextView>(R.id.country_name).text = item.name
                row.findViewById<TextView>(R.id.country_code).text = item.code
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
        findViewById<View>(R.id.country_picker_overlay).visibility = View.GONE
    }

    @Deprecated("Deprecated in Android API; intercept back while the picker overlay is open")
    override fun onBackPressed() {
        if (findViewById<View>(R.id.country_picker_overlay).visibility == View.VISIBLE) {
            hideCountryPicker()
        } else {
            super.onBackPressed()
        }
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
        val raw = findViewById<EditText>(R.id.phone_input).text.toString().trim()
        val phoneUtil = PhoneNumberUtil.getInstance()
        val parsed = try {
            phoneUtil.parse(raw, selectedIso)
        } catch (_: Exception) {
            null
        }
        if (parsed == null || !phoneUtil.isValidNumber(parsed)) { showNoResult(); return }

        val fullNumber = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        val info = CarrierDetector.detect(this, fullNumber, selectedIso) ?: run {
            showNoResult()
            return
        }

        currentNumber = fullNumber
        showDetails()
        findViewById<TextView>(R.id.details_number).text = fullNumber
        findViewById<TextView>(R.id.details_country).text = info.country
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

        findViewById<TextView>(R.id.result_title).text = "No results found"
        findViewById<TextView>(R.id.result_message).text = "$displayedNumber couldn't be located.\nCheck the number and try again."
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
