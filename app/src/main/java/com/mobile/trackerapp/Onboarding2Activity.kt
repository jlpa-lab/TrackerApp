//package com.mobile.trackerapp
//
//import android.app.Activity
//import android.content.Intent
//import android.graphics.Color
//import android.os.Bundle
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowCompat
//import androidx.core.view.WindowInsetsCompat
//import androidx.recyclerview.widget.RecyclerView
//import androidx.viewpager2.widget.ViewPager2
//import com.google.firebase.analytics.FirebaseAnalytics
//
///** Hosts the four horizontally swipeable introduction pages. */
//class Onboarding2Activity : Activity() {
//    private lateinit var pager: ViewPager2
//    private lateinit var nextButton: TextView
//    private lateinit var indicators: List<View>
//
//    private val pages = listOf(
//        OnboardingPage(R.drawable.onboarding_1_map, 480, "Find Your Family in a Crowd", "See the live location of people who share their location with you, all on one map."),
//        OnboardingPage(R.drawable.onboarding_2_map, 480, "Stay Connected", "Create a private circle with family and friends for simple, secure location sharing."),
//        OnboardingPage(R.drawable.onboarding_3_map, 480, "Know When They Arrive", "Get notified when someone arrives at or leaves an important place."),
//        OnboardingPage(R.drawable.onboarding_4_map, 480, "Explore Around You", "Find nearby places and use helpful GPS tools wherever you go.")
//    )
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        WindowCompat.setDecorFitsSystemWindows(window, false)
//        window.statusBarColor = Color.WHITE
//        window.navigationBarColor = Color.WHITE
//        WindowCompat.getInsetsController(window, window.decorView).apply {
//            isAppearanceLightStatusBars = true
//            isAppearanceLightNavigationBars = true
//        }
//        setContentView(R.layout.activity_onboarding)
//
//        val root = findViewById<View>(R.id.onboarding_root)
//        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
//            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
//            insets
//        }
//        ViewCompat.requestApplyInsets(root)
//
//        pager = findViewById(R.id.onboarding_pager)
//        nextButton = findViewById(R.id.next_button)
//        indicators = listOf(
//            findViewById(R.id.indicator_1),
//            findViewById(R.id.indicator_2),
//            findViewById(R.id.indicator_3),
//            findViewById(R.id.indicator_4)
//        )
//        pager.adapter = OnboardingAdapter(pages)
//        updateControls(0)
//
//        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
//            override fun onPageSelected(position: Int) {
//                updateControls(position)
//                logPage(position)
//            }
//        })
//
//        nextButton.setOnClickListener {
//            val position = pager.currentItem
//            FirebaseAnalytics.getInstance(this).logEvent("Onboarding_${position + 1}_next", null)
//            if (position < pages.lastIndex) {
//                pager.setCurrentItem(position + 1, true)
//            } else {
//                // Remember completion so resume behavior is enabled after setup.
//                getSharedPreferences("app_preferences", MODE_PRIVATE).edit()
//                    .putBoolean("onboarding_complete", true)
//                    .apply()
//                startActivity(Intent(this, MainActivity::class.java))
//                finish()
//            }
//        }
//    }
//
//    private fun updateControls(position: Int) {
//        // The active page uses a 30dp pill; other pages use 8dp dots.
//        val density = resources.displayMetrics.density
//        indicators.forEachIndexed { index, indicator ->
//            indicator.layoutParams = indicator.layoutParams.apply {
//                width = ((if (index == position) 30 else 8) * density).toInt()
//                height = (8 * density).toInt()
//            }
//            indicator.setBackgroundResource(
//                if (index == position) R.drawable.bg_indicator_active
//                else R.drawable.bg_indicator_inactive
//            )
//        }
//        nextButton.setText(if (position == pages.lastIndex) R.string.get_started else R.string.next)
//        nextButton.setBackgroundResource(
//            if (position == pages.lastIndex) R.drawable.bg_onboarding_get_started
//            else R.drawable.bg_onboarding_next
//        )
//        nextButton.setTextColor(
//            getColor(if (position == pages.lastIndex) android.R.color.white else R.color.tracker_green_dark)
//        )
//    }
//
//    private fun logPage(position: Int) {
//        val event = "OnboardingActivity_${position + 1}"
//        Log.d("AppEvent", event)
//        FirebaseAnalytics.getInstance(this).logEvent(event, null)
//    }
//
//    private data class OnboardingPage(
//        val imageRes: Int,
//        val imageHeightDp: Int,
//        val title: String,
//        val description: String
//    )
//
//    private class OnboardingAdapter(private val pages: List<OnboardingPage>) : RecyclerView.Adapter<PageViewHolder>() {
//        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
//            return PageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false))
//        }
//
//        override fun onBindViewHolder(holder: PageViewHolder, position: Int) = holder.bind(pages[position])
//        override fun getItemCount(): Int = pages.size
//    }
//
//    private class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        private val image: ImageView = view.findViewById(R.id.onboarding_image)
//        private val hero: View = view.findViewById(R.id.onboarding_hero)
//        private val title: TextView = view.findViewById(R.id.onboarding_title)
//        private val description: TextView = view.findViewById(R.id.onboarding_description)
//
//        fun bind(page: OnboardingPage) {
//            // Per-page hero heights preserve the supplied artwork composition.
//            image.setImageResource(page.imageRes)
//            hero.layoutParams = hero.layoutParams.apply {
//                height = (page.imageHeightDp * itemView.resources.displayMetrics.density).toInt()
//            }
//            title.text = page.title
//            description.text = page.description
//        }
//    }
//}
