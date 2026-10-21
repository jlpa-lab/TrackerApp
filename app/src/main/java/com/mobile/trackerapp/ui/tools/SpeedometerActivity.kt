package com.mobile.trackerapp.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityScanQrBinding
import com.mobile.trackerapp.databinding.ActivitySpeedometerBinding
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.ui.rating.RateAppManager
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** GPS speed display and trip recorder, opened from Tools. */
class SpeedometerActivity : DashboardActivity(), LocationListener {
    private val green by lazy { ContextCompat.getColor(this, R.color.tracker_green) }
    private val dark by lazy { android.graphics.Color.rgb(13, 22, 39) }
    private val muted by lazy { android.graphics.Color.rgb(119, 130, 148) }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private lateinit var gauge: SpeedometerGauge
    private lateinit var status: TextView
    private lateinit var unit: TextView
    private lateinit var average: TextView
    private lateinit var maximum: TextView
    private lateinit var distance: TextView
    private lateinit var tripState: TextView
    private lateinit var timer: TextView
    private lateinit var resetButton: TextView
    private lateinit var startButton: TextView
    private var mph = false
    private var trip = TripState.NOT_STARTED
    private var speedKmh = 0f
    private var maxKmh = 0f
    private var speedSum = 0.0
    private var speedSamples = 0
    private var rateUseReported = false
    private var distanceMeters = 0.0
    private var previousTripLocation: Location? = null
    private var elapsedBeforeRun = 0L
    private var runStartedAt = 0L
    private var locationUpdatesStarted = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            updateTripLabels()
            if (trip == TripState.RUNNING) timerHandler.postDelayed(this, 1000L)
        }
    }
    private lateinit var binding: ActivitySpeedometerBinding
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = android.graphics.Color.rgb(246, 251, 248)
        window.navigationBarColor = android.graphics.Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        binding = ActivitySpeedometerBinding.inflate(layoutInflater)
        setContentView(binding.root); binding.speedometerBack.setOnClickListener { finish() }
        loadSpeedometerBanner()
        gauge = SpeedometerGauge(this); binding.speedometerGaugeHost.addView(
            gauge,
            android.widget.FrameLayout.LayoutParams(-1, -1)
        )
        status = binding.speedometerStatus; unit = binding.speedometerUnit; average =
            binding.speedometerAverage; maximum = binding.speedometerMaximum
        distance = binding.speedometerDistance; tripState = binding.speedometerTripState; timer =
            binding.speedometerTimer; resetButton = binding.speedometerReset; startButton =
            binding.speedometerStart
        unit.setPadding(dp(10), dp(4), dp(10), dp(4))
        unit.compoundDrawablePadding = dp(4)
        resetButton.layoutParams = resetButton.layoutParams.apply { height = dp(44) }
        startButton.layoutParams = startButton.layoutParams.apply { height = dp(44) }
        resetButton.textSize = 12f
        startButton.textSize = 12f
        average.textSize = 21f
        maximum.textSize = 21f
        distance.textSize = 21f
        (average.parent?.parent as? android.view.ViewGroup)?.let { stats ->
            stats.setPadding(dp(8), dp(14), dp(8), dp(14))
            for (index in 0 until stats.childCount) {
                (stats.getChildAt(index) as? android.view.ViewGroup)?.let { column ->
                    (column.getChildAt(0) as? TextView)?.textSize = 10f
                    (column.getChildAt(2) as? TextView)?.textSize = 10f
                    (column.getChildAt(1)?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                        params.topMargin = dp(5)
                        column.getChildAt(1).layoutParams = params
                    }
                }
            }
        }
        unit.setOnClickListener { showUnitMenu(unit) }; resetButton.setOnClickListener { resetTrip() }; startButton.setOnClickListener { toggleTrip() }
        updateTripLabels()
        updateStats()
        if (!hasLocationPermission()) requestLocationPermission()
    }

    private fun statColumn(parent: LinearLayout, title: String): TextView {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        }
        column.addView(label(title, 14f, muted))
        val number = label("—", 25f, dark, true).apply { gravity = Gravity.CENTER }
        column.addView(number, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(4) })
        column.addView(label(if (title == "Distance") "km" else "km/h", 14f, muted))
        parent.addView(column, LinearLayout.LayoutParams(0, wrap(), 1f))
        return number
    }

    private fun actionButton(textValue: String, primary: Boolean) = TextView(this).apply {
        text = textValue; textSize = 16f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) android.graphics.Color.WHITE else green)
        setPadding(dp(8), dp(13), dp(8), dp(13))
        background = if (primary) rounded(
            green,
            dp(30).toFloat()
        ) else borderedRounded(
            android.graphics.Color.WHITE,
            android.graphics.Color.rgb(222, 232, 227),
            dp(30).toFloat()
        )
        isClickable = true; isFocusable = true
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = textValue; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun showUnitMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("km/h")
            menu.add("mph")
            setOnMenuItemClickListener { item -> mph = item.title == "mph"; updateStats(); true }
            show()
        }
    }

    private fun toggleTrip() {
        when (trip) {
            TripState.NOT_STARTED, TripState.PAUSED -> {
                trip = TripState.RUNNING
                runStartedAt = SystemClock.elapsedRealtime()
                previousTripLocation = null
                timerHandler.removeCallbacks(timerTick)
                timerHandler.post(timerTick)
            }

            TripState.RUNNING -> {
                elapsedBeforeRun += SystemClock.elapsedRealtime() - runStartedAt
                trip = TripState.PAUSED
                timerHandler.removeCallbacks(timerTick)
            }
        }
        updateTripLabels()
        updateStats()
    }

    private fun resetTrip() {
        trip = TripState.NOT_STARTED
        elapsedBeforeRun = 0L
        runStartedAt = 0L
        distanceMeters = 0.0
        previousTripLocation = null
        maxKmh = 0f
        speedSum = 0.0
        speedSamples = 0
        timerHandler.removeCallbacks(timerTick)
        updateTripLabels()
        updateStats()
    }

    private fun elapsedMillis(): Long =
        elapsedBeforeRun + if (trip == TripState.RUNNING) SystemClock.elapsedRealtime() - runStartedAt else 0L

    private fun updateTripLabels() {
        tripState.text = when (trip) {
            TripState.NOT_STARTED -> getString(R.string.speed_trip_not_started)
            TripState.RUNNING -> getString(R.string.speed_trip_in_progress)
            TripState.PAUSED -> getString(R.string.speed_trip_paused)
        }
        val totalSeconds = elapsedMillis() / 1000L
        timer.text = String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60
        )
        resetButton.isEnabled = trip != TripState.NOT_STARTED
        resetButton.alpha = if (resetButton.isEnabled) 1f else 0.48f
        resetButton.setTextColor(
            if (resetButton.isEnabled) android.graphics.Color.rgb(0, 165, 72)
            else android.graphics.Color.rgb(185, 192, 201)
        )
        startButton.text = when (trip) {
            TripState.RUNNING -> getString(R.string.speed_pause)
            TripState.PAUSED -> getString(R.string.speed_resume)
            TripState.NOT_STARTED -> getString(R.string.speed_start)
        }
    }

    private fun updateStats() {
        val factor = if (mph) 0.621371f else 1f
        unit.text = getString(if (mph) R.string.speed_unit_mph else R.string.speed_unit_kmh)
        average.text = if (speedSamples == 0) "—" else String.format(
            Locale.getDefault(),
            "%.0f",
            speedSum.toFloat() / speedSamples * factor
        )
        maximum.text = if (speedSamples == 0) "—" else String.format(
            Locale.getDefault(),
            "%.0f",
            maxKmh * factor
        )
        distance.text = if (distanceMeters <= 0.0) "—" else String.format(
            Locale.getDefault(),
            "%.1f",
            distanceMeters / 1000.0 * if (mph) 0.621371 else 1.0
        )
        gauge.speed = speedKmh * factor
        gauge.invalidate()
        (gauge.parent as? View)?.invalidate()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startGpsUpdates()
        else status.setText(R.string.speed_location_permission_needed)
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) startGpsUpdates()
    }

    private fun loadSpeedometerBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun startGpsUpdates() {
        if (locationUpdatesStarted || !hasLocationPermission()) return
        try {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
            status.setText(R.string.speed_turn_on_location)
                return
            }
            locationManager.requestLocationUpdates(provider, 500L, 0f, this, Looper.getMainLooper())
            locationUpdatesStarted = true
            status.setText(R.string.speed_gps_connected)
            locationManager.getLastKnownLocation(provider)?.let(::onLocationChanged)
        } catch (_: SecurityException) {
            status.setText(R.string.speed_location_permission_needed)
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            if (!rateUseReported) {
                rateUseReported = true
                RateAppManager.markGpsToolUsed()
            }
            speedKmh = location.speed * 3.6f
            if (trip == TripState.RUNNING) {
                if (speedKmh > maxKmh) maxKmh = speedKmh
                if (speedKmh > 0f) {
                    speedSum += speedKmh; speedSamples++
                }
            }
        }
        if (trip == TripState.RUNNING) {
            val previous = previousTripLocation
            if (previous != null) {
                val segment = previous.distanceTo(location)
                if (segment in 0f..1000f) distanceMeters += segment
            }
            previousTripLocation = Location(location)
        }
        status.setText(R.string.speed_gps_connected)
        updateStats()
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, statusValue: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) {
        status.setText(R.string.speed_gps_connected)
    }

    override fun onProviderDisabled(provider: String) {
        status.setText(R.string.speed_turn_on_location)
    }

    override fun onPause() {
        if (locationUpdatesStarted) {
            locationManager.removeUpdates(this)
            locationUpdatesStarted = false
        }
        super.onPause()
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerTick)
        super.onDestroy()
    }

    private fun rounded(color: Int, radius: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius = radius
        }

    private fun borderedRounded(color: Int, stroke: Int, radius: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius = radius; setStroke(dp(1), stroke)
        }

    private enum class TripState { NOT_STARTED, RUNNING, PAUSED }

    /** Responsive, square-ish canvas gauge; sizing and all drawing scale with available width. */
    private inner class SpeedometerGauge(context: android.content.Context) : View(context) {
        var speed = 0f
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(218, 232, 226); style =
            Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(252, 254, 253); style = Paint.Style.FILL
        }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = green; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(184, 195, 205); strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted; textAlign = Paint.Align.CENTER; typeface =
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val needlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.FILL }
        private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; style = Paint.Style.FILL
        }
        private val hubCenterPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.FILL }
        private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dark; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted; textAlign = Paint.Align.CENTER; typeface =
            Typeface.create("sans-serif", Typeface.NORMAL)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val desired = (width * 0.86f).toInt()
            val height =
                if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(
                    heightMeasureSpec
                ) else desired
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = min(width.toFloat(), height.toFloat())
            if (side <= 0f) return
            val cx = width / 2f
            val cy = height * 0.50f
            val radius = side * 0.43f
            val stroke = radius * 0.045f
            canvas.drawCircle(cx, cy, radius + stroke * 2.5f, facePaint)
            trackPaint.strokeWidth = stroke
            arcPaint.strokeWidth = stroke
            val arc = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arc, 150f, 240f, false, trackPaint)
            val sweep = (speed.coerceIn(0f, 180f) / 180f) * 240f
            canvas.drawArc(arc, 150f, sweep, false, arcPaint)

            for (i in 0..36) {
                val angle = Math.toRadians((150f + i * (240f / 36f)).toDouble())
                val major = i % 8 == 0 || i == 36
                val outer = radius + stroke * 0.8f
                val inner = radius - if (major) radius * 0.075f else radius * 0.035f
                tickPaint.strokeWidth = if (major) stroke * 0.22f else stroke * 0.13f
                canvas.drawLine(
                    cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                    cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, tickPaint
                )
            }
            val labels = listOf(0, 40, 80, 120, 160, 180)
            labels.forEach { n ->
                val angle = Math.toRadians((150.0 + n / 180.0 * 240.0))
                val labelRadius = radius * 0.70f
                textPaint.textSize = radius * 0.095f
                canvas.drawText(
                    n.toString(), cx + cos(angle).toFloat() * labelRadius,
                    cy + sin(angle).toFloat() * labelRadius + textPaint.textSize * 0.35f, textPaint
                )
            }

            val needleAngle =
                Math.toRadians((150f + speed.coerceIn(0f, 180f) / 180f * 240f).toDouble())
            val tipRadius = radius * 0.57f
            val tipX = cx + cos(needleAngle).toFloat() * tipRadius
            val tipY = cy + sin(needleAngle).toFloat() * tipRadius
            val halfWidth = radius * 0.024f
            val perpX = -sin(needleAngle).toFloat() * halfWidth
            val perpY = cos(needleAngle).toFloat() * halfWidth
            val path = Path().apply {
                moveTo(cx - perpX, cy - perpY)
                lineTo(tipX, tipY)
                lineTo(cx + perpX, cy + perpY)
                close()
            }
            canvas.drawPath(path, needlePaint)
            canvas.drawCircle(cx, cy, radius * 0.052f, hubPaint)
            canvas.drawCircle(cx, cy, radius * 0.026f, hubCenterPaint)

            speedTextPaint.textSize = 44f * resources.displayMetrics.scaledDensity
            canvas.drawText(
                String.format(Locale.getDefault(), "%.0f", speed), cx,
                cy + radius * 0.54f + speedTextPaint.textSize * 0.35f, speedTextPaint
            )
            unitTextPaint.textSize = radius * 0.095f
            canvas.drawText(
                if (mph) "mph" else "km/h", cx,
                cy + radius * 0.78f + unitTextPaint.textSize * 0.35f, unitTextPaint
            )
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 61
    }
}
