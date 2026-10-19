package com.mobile.trackerapp.ui.rating

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R
import com.mobile.trackerapp.network.TrackerBackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RateAppActivity : AppCompatActivity() {
    private var rating = 0
    private var submitting = false
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var illustration: ImageView
    private lateinit var stars: LinearLayout
    private lateinit var feedback: EditText
    private lateinit var count: TextView
    private lateinit var action: TextView

    private lateinit var rate_feedback_container: RelativeLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.activity_rate_app)
        title = findViewById(R.id.rate_title);
        subtitle = findViewById(R.id.rate_subtitle)
        illustration = findViewById(R.id.rate_illustration);
        stars = findViewById(R.id.rate_stars)
        feedback = findViewById(R.id.rate_feedback);
        count = findViewById(R.id.rate_count);
        action = findViewById(R.id.rate_action)
        rate_feedback_container = findViewById(R.id.rate_feedback_container)
        findViewById<View>(R.id.rate_close).setOnClickListener { finish() }
        findViewById<View>(R.id.rate_later).setOnClickListener { finish() }
        showStart()
    }

    private fun showStart() {
        configure(R.drawable.rate_initial, "Enjoying the app?", "Your feedback helps us improve.")
        renderStars();
        action.text = "Rate us"; action.isEnabled = rating > 0
        action.setOnClickListener { if (rating > 0) showResult() }
    }

    private fun showResult() {
        configure(
            if (rating <= 2) R.drawable.rate_sorry else if (rating == 3) R.drawable.rate_thanks else R.drawable.rate_glad,
            if (rating <= 2) "Sorry to hear that! 😔" else if (rating == 3) "Thanks for your feedback!" else "Glad you're enjoying it! 🎉",
            if (rating <= 2) "What can we improve?" else if (rating == 3) "What could make it better?" else "Would you like to rate us on Google Play?"
        )
        renderStars(); action.text =
            if (rating >= 4) "Rate on Google Play" else "Give feedback"; action.isEnabled = true
        action.setOnClickListener { if (rating >= 4) openPlay() else showFeedback() }
    }

    private fun showFeedback() {
        configure(
            R.drawable.rate_feedback,
            "Share your feedback",
            "Let us know how we can improve."
        )
        rate_feedback_container.visibility = View.VISIBLE;
        count.visibility = View.VISIBLE;
        count.text = "${feedback.length()}/500"
        action.text = "Submit";
        action.setOnClickListener { submitFeedback() }
        feedback.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                this@RateAppActivity.count.text = "${s?.length ?: 0}/500"
            }

            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun submitFeedback() {
        val text = feedback.text.toString().trim()
        if (text.isEmpty()) {
            feedback.error = "Please enter feedback"; return
        }
        if (submitting) return
        submitting = true; action.isEnabled = false; action.text = "Saving…"
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                TrackerBackendClient(this@RateAppActivity).register(); TrackerBackendClient(
                this@RateAppActivity
            ).submitFeedback(rating, text) }
                .onSuccess {
                    configure(
                        R.drawable.rate_feedback,
                        "Thanks for your feedback!",
                        "We appreciate your help in making the app better."
                    ); action.visibility = View.GONE; feedback.visibility =
                    View.GONE; count.visibility = View.GONE
                }
                .onFailure {
                    submitting = false; action.isEnabled = true; action.text =
                    "Submit"; Toast.makeText(
                    this@RateAppActivity,
                    "Could not save feedback. Try again.",
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
                setOnClickListener { rating = index + 1; showResult() }
            }, LinearLayout.LayoutParams(50.dp, 58.dp))
        }
    }

    private fun openPlay() {
        val id = packageName; runCatching {
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
        }; finish()
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
