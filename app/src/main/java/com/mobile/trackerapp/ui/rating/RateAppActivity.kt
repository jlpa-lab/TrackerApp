package com.location.and.phone.tracker.locationnphonetracker.ui.rating

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.network.TrackerBackendClient
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RateAppActivity : AppCompatActivity() {
    private lateinit var entryPoint: RateAppManager.EntryPoint
    private var rating = 0
    private var submitting = false
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var illustration: ImageView
    private lateinit var stars: LinearLayout
    private lateinit var feedback: EditText
    private lateinit var count: TextView
    private lateinit var action: TextView
    private lateinit var later: TextView

    private lateinit var rate_feedback_container: RelativeLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        entryPoint = intent.getStringExtra(EXTRA_ENTRY_POINT)
            ?.let { value -> runCatching { RateAppManager.EntryPoint.valueOf(value) }.getOrNull() }
            ?: RateAppManager.EntryPoint.AUTOMATIC
        if (!RateAppManager.beginRateDialog(this, entryPoint)) {
            finish()
            return
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.activity_rate_app)
        title = findViewById(R.id.rate_title);
        subtitle = findViewById(R.id.rate_subtitle)
        illustration = findViewById(R.id.rate_illustration);
        stars = findViewById(R.id.rate_stars)
        feedback = findViewById(R.id.rate_feedback);
        count = findViewById(R.id.rate_count);
        action = findViewById(R.id.rate_action)
        later = findViewById(R.id.rate_later)
        rate_feedback_container = findViewById(R.id.rate_feedback_container)
        findViewById<View>(R.id.rate_close).setOnClickListener {
            RateAppManager.onRateDialogDismissed(); finish()
        }
        later.setOnClickListener {
            RateAppManager.onRateDialogDismissed()
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                RateAppManager.onRateDialogDismissed()
                finish()
            }
        })
        showStart()
        stars.visibility = View.VISIBLE;
        rate_feedback_container.visibility = View.GONE;
    }

    private fun showStart() {
        configure(R.drawable.rate_initial, "Enjoying the app?", "Your feedback helps us improve.")
        renderStars();
        action.text = "Rate us";
        action.isEnabled = rating > 0
        action.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(
                if (rating > 0) "#00A548" else "#BBBBBB"
            )
        )
        action.setOnClickListener { if (rating > 0) showResult() }
        later.visibility = View.VISIBLE
        later.setTextColor(Color.rgb(190, 190, 190))
    }

    private fun showResult() {
        configure(
            if (rating <= 2) R.drawable.rate_sorry else if (rating == 3) R.drawable.rate_thanks else R.drawable.rate_glad,
            if (rating <= 2) "Sorry to hear that! 😔" else if (rating == 3) "Thanks for your feedback!" else "Glad you're enjoying it! 🎉",
            if (rating <= 2) "What can we improve?" else if (rating == 3) "What could make it better?" else "Would you like to rate us on Google Play?"
        )
        renderStars();
        action.text = if (rating >= 4) "Rate on Google Play" else "Give feedback"; action.isEnabled = true
        action.isEnabled = rating > 0
        action.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(
                if (rating > 0) "#00A548" else "#BBBBBB"
            )
        )
        action.setOnClickListener { if (rating >= 4) openPlay() else showFeedback() }
        later.visibility = View.VISIBLE
        later.setTextColor(Color.rgb(0, 164, 76))
    }

    private fun showFeedback() {
        configure(
            R.drawable.rate_feedback,
            getString(R.string.rate_share_feedback),
            getString(R.string.rate_feedback_detail)
        )
        rate_feedback_container.visibility = View.VISIBLE;
        stars.visibility = View.GONE;
        later.visibility = View.GONE
        feedback.visibility = View.VISIBLE
        count.visibility = View.VISIBLE;
        count.text = getString(R.string.rate_character_count, feedback.length())
        action.setText(R.string.rate_submit)
        action.setOnClickListener { submitFeedback() }
        feedback.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                this@RateAppActivity.count.text = getString(R.string.rate_character_count, s?.length ?: 0)
            }

            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun submitFeedback() {
        val text = feedback.text.toString().trim()
        if (text.isEmpty()) {
            feedback.error = getString(R.string.rate_feedback_required); return
        }
        if (submitting) return
        submitting = true; action.isEnabled = false; action.setText(R.string.rate_saving)
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                TrackerBackendClient(this@RateAppActivity).register(); TrackerBackendClient(
                this@RateAppActivity
            ).submitFeedback(rating, text) }
                .onSuccess {
                    RateAppManager.onUserRated(this@RateAppActivity)
                    Toast.makeText(this@RateAppActivity, R.string.rate_feedback_thanks, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .onFailure {
                    submitting = false; action.isEnabled = true; action.text =
                    getString(R.string.rate_submit); Toast.makeText(
                    this@RateAppActivity,
                    getString(R.string.rate_save_failed),
                    Toast.LENGTH_LONG
                ).show()
                }
        }
    }

    private fun configure(image: Int, heading: String, detail: String) {
        illustration.setImageResource(image); title.text = heading; subtitle.text = detail
        feedback.visibility = View.GONE; count.visibility = View.GONE; action.visibility =
            View.VISIBLE
    }

    private fun renderStars() {
        stars.removeAllViews()
        repeat(5) { index ->
            stars.addView(TextView(this).apply {
                text = if (index < rating) "★" else "☆"; textSize = 38f; gravity =
                android.view.Gravity.CENTER
                setTextColor(
                    if (index < rating) Color.rgb(255, 174, 0) else Color.rgb(
                        184,
                        217,
                        189
                    )
                )
                setOnClickListener {
                    rating = index + 1
                    if (rating <= 3) {
                        // The rating decision itself is final. Feedback upload is
                        // best-effort and must not make the user eligible again.
                        RateAppManager.onUserRated(this@RateAppActivity)
                    }
                    showResult()
                }
            }, LinearLayout.LayoutParams(50.dp, 58.dp))
        }
    }

    private fun openPlay() {
        RateAppManager.onUserRated(this)
        val id = packageName
        if (entryPoint == RateAppManager.EntryPoint.SETTINGS) {
            openPlayStore(id)
            return
        }
        runCatching {
            val reviewManager = ReviewManagerFactory.create(this)
            reviewManager.requestReviewFlow().addOnSuccessListener { info ->
                reviewManager.launchReviewFlow(this, info).addOnCompleteListener { finish() }
            }.addOnFailureListener { finish() }
        }.onFailure { finish() }
    }

    private fun openPlayStore(id: String) {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$id")
                )
            )
        }.onFailure {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$id")
                )
            )
        }
        finish()
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ENTRY_POINT = "rate_entry_point"
    }
}
