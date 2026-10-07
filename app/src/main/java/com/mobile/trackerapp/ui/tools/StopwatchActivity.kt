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
import com.mobile.trackerapp.ui.DashboardActivity
import java.util.Locale

class StopwatchActivity : DashboardActivity() {
    private val timerHandler = Handler(Looper.getMainLooper())
    private lateinit var timer: StopwatchTimerState
    private lateinit var elapsedText: TextView
    private lateinit var stateText: TextView
    private lateinit var currentLapText: TextView
    private lateinit var lapCountText: TextView
    private lateinit var lapList: LinearLayout
    private lateinit var leftButton: TextView
    private lateinit var mainButton: TextView

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
        setContentView(buildScreen())
        renderState()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 251, 248))
            fitsSystemWindows = true
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(16))
        }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -1))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(INK)
            contentDescription = "Back"
            isClickable = true; isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(32)).apply { marginEnd = dp(14) })
        header.addView(label("Stopwatch", 28f, INK, true))
        content.addView(header)

        val intro = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        intro.addView(ImageView(this).apply {
            setImageResource(R.drawable.tool_stopwatch)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Stopwatch"
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(12) })
        val introText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        introText.addView(label("Make every second count", 18f, INK, true))
        introText.addView(label("Precise timing with easy lap tracking", 14f, MUTED).apply { setPadding(0, dp(3), 0, 0) })
        intro.addView(introText)
        content.addView(intro, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(24) })

        val clockCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(52), dp(18), dp(48))
            background = rounded(Color.rgb(243, 239, 255), dp(20))
        }
        stateText = label("Ready to start", 11f, GREEN, true).apply { gravity = Gravity.CENTER }
        clockCard.addView(stateText)
        elapsedText = label("00:00.00", 52f, INK, false).apply {
            gravity = Gravity.CENTER
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(0, dp(1), 0, 0)
        }
        clockCard.addView(elapsedText)
        clockCard.addView(label("MINUTES  ·  SECONDS  ·  HUNDREDTHS", 10f, MUTED).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        })
        val currentLap = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(2), 0)
        }
        currentLap.addView(label("Current lap", 13f, MUTED), LinearLayout.LayoutParams(0, wrap(), 1f))
        currentLapText = label("00:00.00", 13f, INK, true).apply { fontFeatureSettings = "tnum" }
        currentLap.addView(currentLapText)
        clockCard.addView(currentLap, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(5) })
        content.addView(clockCard, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(22) })

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        leftButton = actionButton("⚑  Lap", false)
        mainButton = actionButton("▶  Start", true)
        controls.addView(leftButton, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginEnd = dp(6) })
        controls.addView(mainButton, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginStart = dp(6) })
        content.addView(controls, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(14) })
        leftButton.setOnClickListener {
            if (timer.isRunning) {
                timer.recordLap()
                renderLaps()
                renderState()
            } else if (timer.elapsedMillis() > 0L || timer.laps.isNotEmpty()) {
                timer.reset()
                renderLaps()
                renderState()
            }
        }
        mainButton.setOnClickListener {
            if (timer.isRunning) timer.pause() else timer.start()
            renderState()
            if (timer.isRunning) startTicker() else timerHandler.removeCallbacks(ticker)
        }

        val lapHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        lapHeader.addView(label("Lap times", 16f, INK, true), LinearLayout.LayoutParams(0, wrap(), 1f))
        lapCountText = label("0 completed", 12f, MUTED)
        lapHeader.addView(lapCountText)
        content.addView(lapHeader, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(22); bottomMargin = dp(9) })

        lapList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = bordered(Color.WHITE, Color.rgb(229, 236, 232), dp(16))
        }
        val lapPanelHeight = (resources.displayMetrics.heightPixels * LAP_PANEL_HEIGHT_RATIO).toInt()
        content.addView(lapList, LinearLayout.LayoutParams(-1, lapPanelHeight))

        val resetStatus = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val resetHint = label("Reset", 13f, MUTED)
        resetStatus.addView(resetHint, LinearLayout.LayoutParams(0, wrap(), 1f))
        stateText.tag = "state-label"
        val status = label("Nothing to reset", 12f, MUTED).apply { tag = "status-label" }
        resetStatus.addView(status)
        content.addView(resetStatus, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })

        val tip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(235, 249, 242), dp(14))
        }
        tip.addView(TextView(this).apply { text = "◉"; textSize = 20f; setTextColor(GREEN) }, LinearLayout.LayoutParams(wrap(), wrap()).apply { marginEnd = dp(10) })
        tip.addView(label("Tap Lap to save a split without stopping. Pause the timer before resetting.", 12f, MUTED))
        content.addView(tip, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })
        renderLaps()
        return root
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
        currentLapText.text = formatTime(timer.currentLapMillis())
        val status = findViewWithTag<TextView>("status-label")
        val enabled = timer.isRunning || elapsed > 0L || timer.laps.isNotEmpty()
        if (timer.isRunning) {
            stateText.text = "● Running"
            stateText.setTextColor(GREEN)
            mainButton.text = "Ⅱ  Pause"
            leftButton.text = "⚑  Lap"
            setButtonStyle(leftButton, true, false)
            status?.text = "Timer is running"
        } else {
            stateText.text = if (elapsed == 0L) "Ready to start" else "Paused"
            stateText.setTextColor(if (elapsed == 0L) MUTED else GREEN)
            mainButton.text = if (elapsed == 0L) "▶  Start" else "▶  Resume"
            leftButton.text = if (enabled) "↻  Reset" else "⚑  Lap"
            setButtonStyle(leftButton, enabled, false)
            status?.text = if (elapsed == 0L) "Nothing to reset" else "Timer is paused"
        }
        currentLapText.text = formatTime(timer.currentLapMillis())
        lapCountText.text = "${timer.laps.size} completed"
    }

    private fun setButtonStyle(button: TextView, enabled: Boolean, primary: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.48f
        button.setTextColor(if (primary) Color.WHITE else GREEN)
        button.background = if (primary) rounded(GREEN, dp(28)) else bordered(Color.WHITE, Color.rgb(224, 234, 228), dp(28))
    }

    private fun renderLaps() {
        if (!::lapList.isInitialized) return
        lapList.removeAllViews()
        if (timer.laps.isEmpty()) {
            lapList.addView(TextView(this).apply {
                text = "⚑"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(GREEN)
                background = rounded(Color.rgb(235, 249, 242), dp(24))
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            lapList.addView(label("No laps yet", 13f, INK, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(5) })
            lapList.addView(label("Tap Start to begin timing, then tap Lap to record your first split.", 10f, MUTED).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(3) })
            return
        }
        val tableHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tableHeader.addView(label("LAP", 9f, MUTED, true), LinearLayout.LayoutParams(0, wrap(), 1f))
        tableHeader.addView(label("LAP TIME", 9f, MUTED, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, wrap(), 1f))
        tableHeader.addView(label("TOTAL", 9f, MUTED, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, wrap(), 1f))
        lapList.addView(tableHeader, LinearLayout.LayoutParams(-1, wrap()).apply { bottomMargin = dp(5) })
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
            row.addView(label("Lap ${lap.number}", 11f, INK, true), LinearLayout.LayoutParams(0, wrap(), 1f))
            row.addView(label(formatTime(lap.splitMillis), 11f, splitColor, true).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }, LinearLayout.LayoutParams(0, wrap(), 1f))
            row.addView(label(formatTime(lap.totalMillis), 10f, MUTED).apply { gravity = Gravity.END; typeface = Typeface.MONOSPACE }, LinearLayout.LayoutParams(0, wrap(), 1f))
            lapList.addView(row)
        }
    }

    private fun startTicker() {
        timerHandler.removeCallbacks(ticker)
        timerHandler.post(ticker)
    }

    private fun updateClock() {
        if (!::timer.isInitialized || !::elapsedText.isInitialized) return
        elapsedText.text = formatTime(timer.elapsedMillis())
        currentLapText.text = formatTime(timer.currentLapMillis())
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
