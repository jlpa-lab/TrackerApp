package com.mobile.trackerapp.ui.tools

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityAreaCodesBinding
import com.mobile.trackerapp.databinding.ActivityStopwatchBinding
import com.mobile.trackerapp.ui.DashboardActivity
import java.util.Locale

class StopwatchActivity : DashboardActivity() {
    private val timerHandler = Handler(Looper.getMainLooper())
    private lateinit var timer: StopwatchTimerState
    private lateinit var elapsedText: TextView
    private lateinit var stateText: TextView
    private lateinit var currentLapText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var currentLapRowLabel: TextView
    private lateinit var lapCountText: TextView
    private lateinit var lapList: LinearLayout
    private lateinit var leftButton: TextView
    private lateinit var mainButton: TextView
    private lateinit var binding: ActivityStopwatchBinding

    private val ticker = object : Runnable {
        override fun run() {
            if (!::timer.isInitialized || !timer.isRunning) return
            updateClock()
            timerHandler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        timer = StopwatchTimerState()
        window.statusBarColor = Color.rgb(247, 251, 248)
        window.navigationBarColor = Color.rgb(247, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        binding = ActivityStopwatchBinding.inflate(layoutInflater)
        setContentView(binding.root); binding.stopwatchBack.setOnClickListener { finish() }
        loadStopWatchBanner()
        elapsedText = binding.stopwatchElapsed; stateText = binding.stopwatchState; currentLapText = binding.stopwatchCurrentLap
        lapCountText = binding.stopwatchLapCount; lapList = binding.stopwatchLapList; leftButton = binding.stopwatchLeft; mainButton = binding.stopwatchMain; totalTimeText = binding.textTime; currentLapRowLabel = binding.textCurrentlap
        leftButton.setOnClickListener { if (timer.isRunning) { timer.recordLap(); renderLaps(); renderState() } else if (timer.elapsedMillis() > 0L || timer.laps.isNotEmpty()) { timer.reset(); renderLaps(); renderState() } }
        mainButton.setOnClickListener { if (timer.isRunning) timer.pause() else timer.start(); renderState(); if (timer.isRunning) startTicker() else timerHandler.removeCallbacks(ticker) }
        renderState()
        renderLaps()
    }
    private fun loadStopWatchBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun actionButton(title: String, primary: Boolean) = label(title, 14f, if (primary) Color.WHITE else GREEN, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(14), dp(12), dp(14))
        background = if (primary) rounded(GREEN, dp(28)) else bordered(Color.WHITE, Color.rgb(224, 234, 228), dp(28))
        isClickable = true; isFocusable = true
    }

    private fun renderState() {
        if (!::elapsedText.isInitialized) return
        val elapsed = timer.elapsedMillis()
        elapsedText.text = formatTime(elapsed)
        currentLapRowLabel.text = if (elapsed == 0L && timer.laps.isEmpty()) "Current lap · --" else "Current lap · ${String.format(Locale.ROOT, "%02d", timer.laps.size + 1)}"
        totalTimeText.text = formatTime(timer.currentLapMillis())
        val status = findViewWithTag<TextView>("status-label")
        val enabled = timer.isRunning || elapsed > 0L || timer.laps.isNotEmpty()
        if (timer.isRunning) {
            stateText.text = "● Running"
            stateText.setTextColor(GREEN)
            mainButton.text = "Ⅱ  Pause"
            leftButton.text = "⚑  Lap"
            leftButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            setButtonStyle(leftButton, true, false)
            status?.text = "Timer is running"
        } else {
            stateText.text = if (elapsed == 0L) "Ready to start" else "Paused"
            stateText.setTextColor(if (elapsed == 0L) MUTED else GREEN)
            mainButton.text = if (elapsed == 0L) "▶  Start" else "▶  Resume"
            if (enabled) {
                leftButton.text = "↻  Reset"
                leftButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                leftButton.gravity = android.view.Gravity.CENTER
                leftButton.textAlignment = View.TEXT_ALIGNMENT_CENTER
            } else {
                leftButton.text = "⚑  Lap"
                leftButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                leftButton.textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            setButtonStyle(leftButton, enabled, false)
            status?.text = if (elapsed == 0L) "Nothing to reset" else "Timer is paused"
        }
        lapCountText.text = "${timer.laps.size} completed"
    }

    private fun setButtonStyle(button: TextView, enabled: Boolean, primary: Boolean) {
        button.gravity = android.view.Gravity.CENTER
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.48f
        button.setTextColor(if (!enabled) Color.rgb(185, 192, 201) else if (primary) Color.WHITE else GREEN)
        button.background = if (primary) rounded(GREEN, dp(28)) else bordered(Color.WHITE, Color.rgb(224, 234, 228), dp(28))
    }

    private fun renderLaps() {
        if (!::lapList.isInitialized) return
        lapList.removeAllViews()
        if (timer.laps.isEmpty()) {
            lapList.setPadding(dp(24), dp(24), dp(24), dp(24))
            lapList.gravity = Gravity.CENTER
            lapList.addView(TextView(this).apply {
                text = "⚑"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(GREEN)
                background = rounded(Color.rgb(238, 250, 245), dp(56))
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            lapList.addView(label("No laps yet", 13f, INK, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })
            lapList.addView(label("Tap Start to begin timing, then tap Lap to record your first split.", 11f, MUTED).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })
            return
        }
        lapList.setPadding(dp(14), dp(14), dp(14), dp(14))
        lapList.gravity = Gravity.NO_GRAVITY
        val tableHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tableHeader.addView(label("LAP", 9f, MUTED, true), LinearLayout.LayoutParams(0, wrap(), 1f))
        tableHeader.addView(label("LAP TIME", 9f, MUTED, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, wrap(), 1f))
        tableHeader.addView(label("TOTAL", 9f, MUTED, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, wrap(), 1f))
        lapList.addView(tableHeader, LinearLayout.LayoutParams(-1, wrap()).apply { bottomMargin = dp(8) })
        lapList.addView(View(this).apply { setBackgroundColor(Color.rgb(225, 233, 229)) }, LinearLayout.LayoutParams(-1, dp(1)))
        val fastest = timer.laps.minOf { it.splitMillis }
        val slowest = timer.laps.maxOf { it.splitMillis }
        timer.laps.forEach { lap ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val splitColor = when {
                lap.splitMillis == fastest -> GREEN
                lap.splitMillis == slowest && fastest != slowest -> Color.rgb(225, 139, 15)
                else -> INK
            }
            val lapLabel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            lapLabel.addView(label("Lap ${String.format(Locale.ROOT, "%02d", lap.number)}", 12f, INK, true))
            if (lap.splitMillis == fastest) lapLabel.addView(label("Fastest", 10f, GREEN))
            else if (lap.splitMillis == slowest && fastest != slowest) lapLabel.addView(label("Slowest", 10f, Color.rgb(210, 142, 15)))
            row.addView(lapLabel, LinearLayout.LayoutParams(0, wrap(), 1f))
            row.addView(label(formatTime(lap.splitMillis), 11f, splitColor, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }, LinearLayout.LayoutParams(0, wrap(), 1f))
            row.addView(label(formatTime(lap.totalMillis), 10f, MUTED).apply { gravity = Gravity.END; typeface = Typeface.MONOSPACE }, LinearLayout.LayoutParams(0, wrap(), 1f))
            lapList.addView(row)
            if (lap != timer.laps.last()) lapList.addView(View(this).apply { setBackgroundColor(Color.rgb(225, 233, 229)) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun startTicker() {
        timerHandler.removeCallbacks(ticker)
        timerHandler.post(ticker)
    }

    private fun updateClock() {
        if (!::timer.isInitialized || !::elapsedText.isInitialized) return
        elapsedText.text = formatTime(timer.elapsedMillis())
        currentLapRowLabel.text = if (timer.elapsedMillis() == 0L && timer.laps.isEmpty()) "Current lap · --" else "Current lap · ${String.format(Locale.ROOT, "%02d", timer.laps.size + 1)}"
        totalTimeText.text = formatTime(timer.currentLapMillis())
    }

    override fun onResume() {
        super.onResume()
        if (::timer.isInitialized && timer.isRunning) startTicker()
    }

    override fun onPause() {
        timerHandler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        timerHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun formatTime(millis: Long): String {
        val totalHundredths = millis.coerceAtLeast(0L) / 10L
        val minutes = totalHundredths / 6000L
        val seconds = (totalHundredths / 100L) % 60L
        val hundredths = totalHundredths % 100L
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    private fun <T : View> findViewWithTag(tagValue: String): T? =
        window.decorView.findViewWithTag(tagValue) as? T

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun bordered(color: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(1), stroke)
    }

    companion object {
        private const val TICK_MS = 30L
        private const val LAP_PANEL_HEIGHT_RATIO = 0.29f
        private val INK = Color.rgb(15, 23, 42)
        private val MUTED = Color.rgb(119, 130, 148)
        private val GREEN = Color.rgb(0, 169, 82)
    }
}

private class StopwatchTimerState {
    private var accumulatedMillis = 0L
    private var startedAtElapsedRealtime = 0L
    private var lastLapTotalMillis = 0L
    var isRunning: Boolean = false
        private set
    val laps = mutableListOf<StopwatchLap>()

    fun elapsedMillis(): Long = if (isRunning) {
        accumulatedMillis + (SystemClock.elapsedRealtime() - startedAtElapsedRealtime)
    } else accumulatedMillis

    fun currentLapMillis(): Long = (elapsedMillis() - lastLapTotalMillis).coerceAtLeast(0L)

    fun start() {
        if (!isRunning) {
            startedAtElapsedRealtime = SystemClock.elapsedRealtime()
            isRunning = true
        }
    }

    fun pause() {
        if (isRunning) {
            accumulatedMillis = elapsedMillis()
            isRunning = false
        }
    }

    fun recordLap() {
        if (!isRunning) return
        val total = elapsedMillis()
        val split = (total - lastLapTotalMillis).coerceAtLeast(0L)
        laps.add(0, StopwatchLap(laps.size + 1, split, total))
        lastLapTotalMillis = total
        laps.indices.forEach { index ->
            val lap = laps[index]
            laps[index] = lap.copy(number = laps.size - index)
        }
    }

    fun reset() {
        pause()
        accumulatedMillis = 0L
        lastLapTotalMillis = 0L
        laps.clear()
    }
}

data class StopwatchLap(val number: Int, val splitMillis: Long, val totalMillis: Long)
