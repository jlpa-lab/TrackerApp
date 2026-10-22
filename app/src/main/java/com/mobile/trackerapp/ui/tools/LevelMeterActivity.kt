package com.location.and.phone.tracker.locationnphonetracker.ui.tools

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.banner_all
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityLevelMeterBinding
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityScanQrBinding
import com.location.and.phone.tracker.locationnphonetracker.ui.DashboardActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.rating.RateAppManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Accelerometer-based surface level, horizontal level, and vertical plumb tool. */
class LevelMeterActivity : DashboardActivity(), SensorEventListener {

    private lateinit var binding: ActivityLevelMeterBinding
    private val ink = android.graphics.Color.rgb(15, 23, 42)
    private val green = android.graphics.Color.rgb(8, 163, 73)
    private val muted = android.graphics.Color.rgb(119, 130, 148)
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private lateinit var instrument: InstrumentView
    private lateinit var modeTitle: TextView
    private lateinit var pitchValue: TextView
    private lateinit var rollValue: TextView
    private lateinit var holdButton: LinearLayout
    private lateinit var holdButtonLabel: TextView
    private lateinit var holdButtonIcon: ImageView
    private lateinit var tipText: TextView
    private lateinit var modeButtons: List<TextView>
    private var mode = Mode.SURFACE
    private var smoothX = 0f
    private var smoothY = 0f
    private var calibrationX = 0f
    private var calibrationY = 0f
    private var receivedSensorData = false
    private var heldReading: Reading? = null
    private var currentReading = Reading(0f, 0f)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = android.graphics.Color.rgb(246, 251, 248)
        window.navigationBarColor = android.graphics.Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        binding = ActivityLevelMeterBinding.inflate(layoutInflater)
        setContentView(binding.root); binding.levelBack.setOnClickListener { finish() }
        loadLevelMeterBanner()
        modeTitle = binding.levelModeTitle; pitchValue = binding.levelPitch; rollValue = binding.levelRoll
        instrument = InstrumentView(); binding.levelInstrumentHost.addView(instrument, android.widget.FrameLayout.LayoutParams(-1, -1))
        holdButton = binding.levelHold
        holdButtonLabel = binding.levelHoldLabel
        holdButtonIcon = binding.levelHoldIcon
        tipText = binding.levelTipText
        holdButton.setOnClickListener { toggleHold() }
        binding.levelCalibrate.setOnClickListener { calibrate() }
        modeButtons = listOf(binding.levelModeSurface, binding.levelModeHorizontal, binding.levelModeVertical)
        modeButtons.forEachIndexed { index, button -> button.setOnClickListener { selectMode(Mode.entries[index]) } }
        setHoldAppearance()
        selectMode(mode)
        renderReading(currentReading)
    }

    private fun readingColumn(title: String): Pair<LinearLayout, TextView> {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        column.addView(text(title, 10f, muted).apply { gravity = Gravity.CENTER })
        val value = text("0.0°", 19f, ink, true).apply { gravity = Gravity.CENTER }
        column.addView(value, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(2) })
        return column to value
    }

    private fun selectMode(selected: Mode) {
        mode = selected
        modeButtons.forEachIndexed { index, button ->
            val active = Mode.entries[index] == selected
            button.setTextColor(if (active) android.graphics.Color.WHITE else muted)
            button.typeface = resources.getFont(if (active) R.font.inter_semibold else R.font.inter_regular)
            button.background = rounded(if (active) green else android.graphics.Color.TRANSPARENT, dp(9).toFloat())
        }
        modeTitle.text = selected.instruction
        tipText.text = selected.tip
        instrument.mode = selected
        instrument.invalidate()
    }
    private fun loadLevelMeterBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun toggleHold() {
        RateAppManager.markGpsToolUsed()
        if (heldReading == null) {
            heldReading = currentReading.copy()
            setHoldAppearance()
        } else {
            heldReading = null
            setHoldAppearance()
            renderReading(currentReading)
        }
        instrument.invalidate()
    }

    private fun calibrate() {
        RateAppManager.markGpsToolUsed()
        calibrationX = smoothX
        calibrationY = smoothY
        heldReading = null
        setHoldAppearance()
        currentReading = Reading(0f, 0f)
        renderReading(currentReading)
        android.widget.Toast.makeText(this, getString(R.string.level_calibrated), android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val angleX = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
        val angleY = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
        if (!receivedSensorData) {
            smoothX = -angleX
            smoothY = angleY
            receivedSensorData = true
        } else {
            smoothX += ((-angleX) - smoothX) * 0.3f
            smoothY += (angleY - smoothY) * 0.3f
        }
        currentReading = Reading(smoothX - calibrationX, smoothY - calibrationY)
        if (heldReading == null) renderReading(currentReading)
    }

    private fun renderReading(reading: Reading) {
        pitchValue.text = String.format(Locale.getDefault(), "%.1f°", reading.x)
        rollValue.text = String.format(Locale.getDefault(), "%.1f°", reading.y)
        instrument.reading = reading
        instrument.isLevel = abs(reading.x) < LEVEL_THRESHOLD && abs(reading.y) < LEVEL_THRESHOLD
        modeTitle.text = if (instrument.isLevel) mode.instruction else getString(R.string.level_needs_adjustment, mode.title)
        instrument.mode = mode
        instrument.invalidate()
    }

    private fun setHoldAppearance() {
        val isHeld = heldReading != null
        holdButtonLabel.setText(if (isHeld) R.string.level_resume_reading else R.string.level_hold)
        holdButtonIcon.setImageResource(if (isHeld) R.drawable.ic_level_resume else R.drawable.ic_level_lock)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun actionButton(label: String, primary: Boolean) = TextView(this).apply {
        text = label; textSize = 12f; gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(if (primary) android.graphics.Color.WHITE else green)
        setPadding(dp(5), dp(9), dp(5), dp(9))
        background = rounded(if (primary) green else android.graphics.Color.WHITE, dp(28).toFloat())
        isClickable = true; isFocusable = true
    }

    private fun rounded(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    private fun borderedRounded(color: Int, stroke: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = radius; setStroke(dp(1), stroke)
    }

    private data class Reading(val x: Float, val y: Float)

    private enum class Mode(val title: String, val instruction: String, val tip: String) {
        SURFACE("Surface", "Surface is level", "Place your phone flat on a surface. Calibrate on a known level surface before measuring."),
        HORIZONTAL("Horizontal", "Horizontal is level", "Place your phone on its long edge. Calibrate on a known level surface before measuring."),
        VERTICAL("Vertical", "Vertical is plumb", "Place your phone upright. Calibrate on a known vertical surface before measuring.")
    }

    private inner class InstrumentView : View(this@LevelMeterActivity) {
        var mode = Mode.SURFACE
        var reading = Reading(0f, 0f)
        var isLevel = true
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(232, 219, 177); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(238, 228, 199); style = Paint.Style.STROKE }
        private val bubbleDrawable = ContextCompat.getDrawable(this@LevelMeterActivity, R.drawable.ic_level_bubble)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL) }
        private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(231, 217, 180); strokeCap = Paint.Cap.ROUND }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val desiredHeight = (width * 0.57f).toInt()
            val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
                MeasureSpec.AT_MOST -> min(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
                else -> desiredHeight
            }
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val centerX = w / 2f
            val centerY = h * 0.55f
            guide.strokeWidth = (w * 0.003f).coerceAtLeast(1f)
            guide.color = if (isLevel) android.graphics.Color.rgb(187, 225, 199) else android.graphics.Color.rgb(232, 219, 177)
            faint.strokeWidth = (w * 0.002f).coerceAtLeast(1f)
            textPaint.textSize = w * 0.027f
            val limit = if (mode == Mode.SURFACE) min(w, h) * 0.34f else w * 0.37f
            val bubbleRadius = dp(16).toFloat()
            val dx = when (mode) {
                Mode.SURFACE -> (-reading.x / 45f * limit).coerceIn(-limit + bubbleRadius, limit - bubbleRadius)
                Mode.HORIZONTAL -> (reading.x / 45f * limit).coerceIn(-limit + bubbleRadius, limit - bubbleRadius)
                Mode.VERTICAL -> 0f
            }
            val dy = when (mode) {
                Mode.SURFACE -> (reading.y / 45f * limit).coerceIn(-limit + bubbleRadius, limit - bubbleRadius)
                Mode.HORIZONTAL -> 0f
                Mode.VERTICAL -> (reading.y / 45f * limit).coerceIn(-limit + bubbleRadius, limit - bubbleRadius)
            }

            when (mode) {
                Mode.SURFACE -> {
                    val radius = min(w, h) * 0.34f
                    canvas.drawCircle(centerX, centerY, radius, white)
                    canvas.drawCircle(centerX, centerY, radius * 0.32f, faint)
                    canvas.drawCircle(centerX, centerY, radius * 0.64f, faint)
                    canvas.drawCircle(centerX, centerY, radius * 0.94f, guide)
                    canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, faint)
                    canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, faint)
                    listOf("N", "E", "S", "W").forEachIndexed { i, label ->
                        val a = Math.toRadians((i * 90 - 90).toDouble())
                        canvas.drawText(label, centerX + cos(a).toFloat() * radius * 1.06f,
                            centerY + sin(a).toFloat() * radius * 1.06f + textPaint.textSize * 0.35f, textPaint)
                    }
                    drawBubble(canvas, centerX + dx, centerY + dy, bubbleRadius)
                }
                Mode.HORIZONTAL -> {
                    val tube = RectF(w * 0.08f, centerY - h * 0.15f, w * 0.92f, centerY + h * 0.15f)
                    canvas.drawRoundRect(tube, tube.height() / 2f, tube.height() / 2f, white)
                    canvas.drawRoundRect(tube, tube.height() / 2f, tube.height() / 2f, guide)
                    for (i in 0..20) {
                        val x = tube.left + tube.width() * i / 20f
                        val major = i % 5 == 0
                        tick.strokeWidth = if (major) w * 0.003f else w * 0.0018f
                        val length = if (major) tube.height() * 0.42f else tube.height() * 0.22f
                        canvas.drawLine(x, centerY - length / 2f, x, centerY + length / 2f, tick)
                    }
                    drawBubble(canvas, (centerX + dx).coerceIn(tube.left + bubbleRadius, tube.right - bubbleRadius), centerY, bubbleRadius)
                    canvas.drawText(String.format(Locale.getDefault(), "%.1f°", reading.x), centerX, h * 0.18f, textPaint)
                    canvas.drawText("X axis", centerX, h * 0.94f, textPaint)
                }
                Mode.VERTICAL -> {
                    val tube = RectF(centerX - w * 0.105f, h * 0.11f, centerX + w * 0.105f, h * 0.91f)
                    canvas.drawRoundRect(tube, tube.width() / 2f, tube.width() / 2f, white)
                    canvas.drawRoundRect(tube, tube.width() / 2f, tube.width() / 2f, guide)
                    for (i in 0..20) {
                        val y = tube.top + tube.height() * i / 20f
                        val major = i % 5 == 0
                        tick.strokeWidth = if (major) w * 0.003f else w * 0.0018f
                        val length = if (major) tube.width() * 0.42f else tube.width() * 0.22f
                        canvas.drawLine(centerX - length / 2f, y, centerX + length / 2f, y, tick)
                    }
                    drawBubble(canvas, centerX, (centerY + dy).coerceIn(tube.top + bubbleRadius, tube.bottom - bubbleRadius), bubbleRadius)
                    canvas.drawText("Y axis", w * 0.82f, centerY + textPaint.textSize * 0.35f, textPaint)
                    canvas.drawText(String.format(Locale.getDefault(), "%.1f°", reading.y), w * 0.18f, centerY + textPaint.textSize * 0.35f, textPaint)
                }
            }
        }

        private fun drawBubble(canvas: Canvas, x: Float, y: Float, radius: Float) {
            val drawable = bubbleDrawable ?: return
            val diameter = (radius * 2f).toInt()
            drawable.setBounds(
                (x - radius).toInt(),
                (y - radius).toInt(),
                (x - radius).toInt() + diameter,
                (y - radius).toInt() + diameter
            )
            drawable.draw(canvas)
        }
    }

    companion object { private const val LEVEL_THRESHOLD = 1.5f }
}
