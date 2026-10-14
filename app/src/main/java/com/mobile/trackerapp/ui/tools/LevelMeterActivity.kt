package com.mobile.trackerapp.ui.tools

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
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Accelerometer-based surface level, horizontal level, and vertical plumb tool. */
class LevelMeterActivity : DashboardActivity(), SensorEventListener {
    private val ink = android.graphics.Color.rgb(15, 23, 42)
    private val green = android.graphics.Color.rgb(8, 163, 73)
    private val muted = android.graphics.Color.rgb(119, 130, 148)
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private lateinit var instrument: InstrumentView
    private lateinit var modeTitle: TextView
    private lateinit var pitchValue: TextView
    private lateinit var rollValue: TextView
    private lateinit var holdButton: TextView
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
        setContentView(buildScreen())
        renderReading(currentReading)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(246, 251, 248))
            fitsSystemWindows = true
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(18))
        }
        scroll.addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_back)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            contentDescription = "Back"
        }
        header.addView(back, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(18) })
        back.setOnClickListener { finish() }
        header.addView(TextView(this).apply {
            text = "Level Meter"; textSize = 26f; setTextColor(ink); typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, wrap(), 1f))
        content.addView(header)

        val intro = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        intro.addView(ImageView(this).apply {
            setImageResource(R.drawable.tool_level_meter)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Level meter"
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        val introCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        introCopy.addView(text("Find the perfect balance", 16f, ink, true))
        introCopy.addView(text("Measure the angle of any surface", 13f, muted).apply { setPadding(0, dp(2), 0, 0) })
        intro.addView(introCopy)
        content.addView(intro, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(12) })

        val tabBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(android.graphics.Color.rgb(237, 244, 240), dp(28).toFloat())
        }
        modeButtons = Mode.entries.map { item ->
            text(item.title, 12f, muted, item == mode).apply {
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(8), dp(5), dp(8))
                background = rounded(if (item == mode) green else android.graphics.Color.TRANSPARENT, dp(24).toFloat())
                setTextColor(if (item == mode) android.graphics.Color.WHITE else muted)
                isClickable = true; isFocusable = true
                setOnClickListener { selectMode(item) }
            }.also { tabBar.addView(it, LinearLayout.LayoutParams(0, wrap(), 1f)) }
        }
        content.addView(tabBar, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(10) })

        val instrumentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
            background = rounded(android.graphics.Color.rgb(255, 247, 223), dp(18).toFloat())
        }
        modeTitle = text(mode.instruction, 12f, ink, true).apply { gravity = Gravity.CENTER }
        instrumentCard.addView(modeTitle, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(2) })
        instrument = InstrumentView()
        instrumentCard.addView(instrument, LinearLayout.LayoutParams(-1, wrap()))
        content.addView(instrumentCard, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(8) })

        val readingsCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = borderedRounded(android.graphics.Color.rgb(253, 255, 254), android.graphics.Color.rgb(229, 237, 233), dp(16).toFloat())
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }
        val pitchColumn = readingColumn("Pitch · X axis")
        pitchValue = pitchColumn.second
        val divider = View(this).apply { setBackgroundColor(android.graphics.Color.rgb(231, 237, 234)) }
        readingsCard.addView(pitchColumn.first, LinearLayout.LayoutParams(0, wrap(), 1f))
        readingsCard.addView(divider, LinearLayout.LayoutParams(dp(1), dp(42)))
        val rollColumn = readingColumn("Roll · Y axis")
        rollValue = rollColumn.second
        readingsCard.addView(rollColumn.first, LinearLayout.LayoutParams(0, wrap(), 1f))
        content.addView(readingsCard, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(8) })

        val accuracy = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        accuracy.addView(text("Accuracy tolerance", 11f, muted), LinearLayout.LayoutParams(0, wrap(), 1f))
        accuracy.addView(text("±0.5°", 11f, ink, true))
        content.addView(accuracy, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(5) })

        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER; weightSum = 2f }
        holdButton = actionButton("▣  Hold reading", false).apply { setOnClickListener { toggleHold() } }
        val calibrate = actionButton("⟳  Calibrate", true).apply { setOnClickListener { calibrate() } }
        buttons.addView(holdButton, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginEnd = dp(5) })
        buttons.addView(calibrate, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginStart = dp(5) })
        content.addView(buttons, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(8) })

        val note = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(android.graphics.Color.rgb(235, 249, 242), dp(14).toFloat())
        }
        note.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_info)
            imageTintList = android.content.res.ColorStateList.valueOf(green)
            contentDescription = "Tip"
        }, LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(8) })
        note.addView(text("Place your phone flat on a firm surface. Calibrate on a known level surface before measuring.", 10f, muted))
        content.addView(note, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(8) })

        if (accelerometer == null) {
            content.addView(text("This device does not have an accelerometer, so live leveling is unavailable.", 13f, android.graphics.Color.rgb(180, 65, 65)))
        }
        return root
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
            button.background = rounded(if (active) green else android.graphics.Color.TRANSPARENT, dp(24).toFloat())
        }
        modeTitle.text = selected.instruction
        instrument.mode = selected
        instrument.invalidate()
    }

    private fun toggleHold() {
        if (heldReading == null) {
            heldReading = currentReading.copy()
            holdButton.text = "▶  Resume reading"
        } else {
            heldReading = null
            holdButton.text = "▣  Hold reading"
            renderReading(currentReading)
        }
        instrument.invalidate()
    }

    private fun calibrate() {
        calibrationX = smoothX
        calibrationY = smoothY
        heldReading = null
        holdButton.text = "▣  Hold reading"
        currentReading = Reading(0f, 0f)
        renderReading(currentReading)
        android.widget.Toast.makeText(this, "Calibrated to current position", android.widget.Toast.LENGTH_SHORT).show()
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
        instrument.mode = mode
        instrument.invalidate()
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

    private enum class Mode(val title: String, val instruction: String) {
        SURFACE("Surface", "Surface is level"),
        HORIZONTAL("Horizontal", "Horizontal is level"),
        VERTICAL("Vertical", "Vertical is plumb")
    }

    private inner class InstrumentView : View(this@LevelMeterActivity) {
        var mode = Mode.SURFACE
        var reading = Reading(0f, 0f)
        var isLevel = true
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(232, 219, 177); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(238, 228, 199); style = Paint.Style.STROKE }
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.FILL }
        private val bubbleCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(0, 125, 57); style = Paint.Style.FILL }
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
            val dotRadius = min(w, h) * 0.036f
            val dx = when (mode) {
                Mode.SURFACE -> (-reading.x / 45f * limit).coerceIn(-limit, limit)
                Mode.HORIZONTAL -> (reading.x / 45f * limit).coerceIn(-limit, limit)
                Mode.VERTICAL -> 0f
            }
            val dy = when (mode) {
                Mode.SURFACE -> (reading.y / 45f * limit).coerceIn(-limit, limit)
                Mode.HORIZONTAL -> 0f
                Mode.VERTICAL -> (reading.y / 45f * limit).coerceIn(-limit, limit)
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
                    canvas.drawCircle(centerX + dx, centerY + dy, dotRadius, bubblePaint)
                    canvas.drawCircle(centerX + dx, centerY + dy, dotRadius * 0.38f, bubbleCore)
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
                    canvas.drawCircle(centerX, centerY, dotRadius * 0.46f, bubbleCore)
                    canvas.drawCircle((centerX + dx).coerceIn(tube.left + dotRadius, tube.right - dotRadius), centerY, dotRadius, bubblePaint)
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
                    canvas.drawCircle(centerX, centerY, dotRadius * 0.46f, bubbleCore)
                    canvas.drawCircle(centerX, (centerY + dy).coerceIn(tube.top + dotRadius, tube.bottom - dotRadius), dotRadius, bubblePaint)
                    canvas.drawText("Y axis", w * 0.82f, centerY + textPaint.textSize * 0.35f, textPaint)
                    canvas.drawText(String.format(Locale.getDefault(), "%.1f°", reading.y), w * 0.18f, centerY + textPaint.textSize * 0.35f, textPaint)
                }
            }
        }
    }

    companion object { private const val LEVEL_THRESHOLD = 1.5f }
}
