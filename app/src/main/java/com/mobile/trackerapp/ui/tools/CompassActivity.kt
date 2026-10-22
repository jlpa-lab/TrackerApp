package com.location.and.phone.tracker.locationnphonetracker.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.banner_all
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityCompassBinding
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityStopwatchBinding
import com.location.and.phone.tracker.locationnphonetracker.ui.DashboardActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.permission.LocationPermissionFlow
import com.location.and.phone.tracker.locationnphonetracker.ui.rating.RateAppManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Live magnetic compass, opened from the Compass row in Tools. */
class CompassActivity : DashboardActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityCompassBinding
    private val ink = android.graphics.Color.rgb(13, 22, 39)
    private val green = android.graphics.Color.rgb(0, 164, 76)
    private val muted = android.graphics.Color.rgb(119, 130, 148)
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravityValues: FloatArray? = null
    private var magneticValues: FloatArray? = null
    private var currentHeading = 0f
    private var hasHeading = false
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var latestLocation: Location? = null
    private var rateUseReported = false
    private lateinit var dial: CompassDialView
    private lateinit var headingValue: TextView
    private lateinit var directionValue: TextView
    private lateinit var coordinatesValue: TextView
    private lateinit var altitudeValue: TextView
    private lateinit var accuracyValue: TextView
    private val locationPermissionFlow by lazy {
        LocationPermissionFlow(
            activity = this,
            onGranted = { startLocationUpdates() },
            onDenied = { renderLocation(null) }
        )
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        locationPermissionFlow
        window.statusBarColor = android.graphics.Color.rgb(246, 251, 248)
        window.navigationBarColor = android.graphics.Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        binding = ActivityCompassBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.compassBack.setOnClickListener { finish() }
        loadCompassBanner()
        dial = CompassDialView()
        binding.compassDialHost.addView(dial, android.widget.FrameLayout.LayoutParams(-1, -1))
        headingValue = binding.compassHeading; directionValue = binding.compassDirection
        coordinatesValue = binding.compassCoordinates; altitudeValue = binding.compassAltitude; accuracyValue = binding.compassAccuracy
        binding.compassCalibrate.setOnClickListener { showCalibrationHelp() }
        renderHeading()
        renderLocation(null)
        renderAccuracy()
        if (!hasLocationPermission()) locationPermissionFlow.request()
    }
    private fun loadCompassBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
    private fun detailRow(title: String): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        row.addView(text(title, 14f, muted), LinearLayout.LayoutParams(0, wrap(), 1f))
        val value = text("Location unavailable", 14f, ink, true).apply { gravity = Gravity.END }
        row.addView(value, LinearLayout.LayoutParams(0, wrap(), 1.4f))
        return row to value
    }

    private fun renderHeading() {
        if (!::headingValue.isInitialized) return
        val heading = (currentHeading.toInt() + 360) % 360
        headingValue.text = if (hasHeading) "$heading°" else "—°"
        directionValue.text = cardinal(currentHeading)
        dial.heading = currentHeading
        dial.invalidate()
    }

    private fun cardinal(degrees: Float): String = when (((degrees + 22.5f) / 45f).toInt() % 8) {
        0 -> "N"; 1 -> "NE"; 2 -> "E"; 3 -> "SE"
        4 -> "S"; 5 -> "SW"; 6 -> "W"; else -> "NW"
    }

    private fun renderAccuracy() {
        if (!::accuracyValue.isInitialized) return
        val label = when (sensorAccuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> getString(R.string.compass_high)
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> getString(R.string.compass_medium)
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> getString(R.string.compass_low)
            else -> if (hasHeading) getString(R.string.compass_uncalibrated) else getString(R.string.compass_waiting_sensor)
        }
        accuracyValue.text = label
        accuracyValue.setTextColor(if (sensorAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) green else muted)
    }

    private fun renderLocation(location: Location?) {
        if (!::coordinatesValue.isInitialized) return
        latestLocation = location ?: latestLocation
        val fix = latestLocation
        if (fix == null) {
            coordinatesValue.text = getString(if (hasLocationPermission()) R.string.compass_waiting_location else R.string.compass_location_access)
            altitudeValue.text = "—"
            return
        }
        val latitude = "%.4f° %s".format(Locale.getDefault(), abs(fix.latitude), if (fix.latitude >= 0) "N" else "S")
        val longitude = "%.4f° %s".format(Locale.getDefault(), abs(fix.longitude), if (fix.longitude >= 0) "E" else "W")
        coordinatesValue.text = "$latitude, $longitude"
        altitudeValue.text = if (fix.hasAltitude()) "%.0f m above sea level".format(Locale.getDefault(), fix.altitude) else getString(R.string.compass_unavailable)
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        try {
            val providers = locationManager.getProviders(true)
            providers.forEach { provider ->
                locationManager.getLastKnownLocation(provider)?.let { location ->
                    if (latestLocation == null || location.time > latestLocation!!.time) renderLocation(location)
                }
                locationManager.requestLocationUpdates(provider, LOCATION_INTERVAL_MS, LOCATION_DISTANCE_M, this)
            }
        } catch (_: SecurityException) {
            renderLocation(null)
        } catch (_: IllegalArgumentException) {
            renderLocation(null)
        }
    }

    private fun stopLocationUpdates() {
        try { locationManager.removeUpdates(this) } catch (_: SecurityException) { }
    }

    private fun showCalibrationHelp() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.compass_calibrate_title))
            .setMessage(getString(R.string.compass_calibrate_message))
            .setPositiveButton(R.string.compass_got_it, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        locationPermissionFlow.onHostResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            ?: run {
                accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            }
        startLocationUpdates()
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            applyOrientation(matrix)
            return
        }
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> gravityValues = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> magneticValues = event.values.clone()
        }
        val gravity = gravityValues ?: return
        val magnetic = magneticValues ?: return
        val matrix = FloatArray(9)
        val orientation = FloatArray(3)
        if (SensorManager.getRotationMatrix(matrix, null, gravity, magnetic)) applyOrientation(matrix)
    }

    private fun applyOrientation(matrix: FloatArray) {
        val orientation = FloatArray(3)
        SensorManager.getOrientation(matrix, orientation)
        val headingMatrix = if (abs(Math.toDegrees(orientation[1].toDouble())) > 45.0) {
            FloatArray(9).also { SensorManager.remapCoordinateSystem(matrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, it) }
        } else matrix
        if (headingMatrix !== matrix) SensorManager.getOrientation(headingMatrix, orientation)
        var degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (degrees < 0f) degrees += 360f
        if (!hasHeading) {
            currentHeading = degrees
            hasHeading = true
            if (!rateUseReported) {
                rateUseReported = true
                RateAppManager.markGpsToolUsed()
            }
        } else {
            val delta = ((degrees - currentHeading + 540f) % 360f) - 180f
            currentHeading = (currentHeading + delta * HEADING_SMOOTHING + 360f) % 360f
        }
        renderHeading()
        renderAccuracy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
        renderAccuracy()
    }

    override fun onLocationChanged(location: Location) = renderLocation(location)

    private inner class CompassDialView : View(this@CompassActivity) {
        var heading = 0f
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(252, 254, 253); style = Paint.Style.FILL }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(224, 233, 229); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat() }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(185, 196, 207); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textAlign = Paint.Align.CENTER }
        private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(225, 38, 38); style = Paint.Style.FILL }
        private val needleTailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(179, 215, 226); style = Paint.Style.FILL }
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.FILL }
        private val northMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green; style = Paint.Style.FILL }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val desired = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) resources.displayMetrics.widthPixels else availableWidth
            val width = resolveSize(desired, widthMeasureSpec)
            // Keep the dial naturally shorter than its available width so the details and
            // calibration guidance remain reachable without making the compass face tiny.
            val height = resolveSize((width * DIAL_HEIGHT_RATIO).toInt(), heightMeasureSpec)
            setMeasuredDimension(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.47f
            canvas.drawCircle(cx, cy, radius, facePaint)
            val innerRadius = radius * 0.62f
            canvas.drawCircle(cx, cy, innerRadius, ringPaint)

            canvas.save()
            canvas.rotate(-heading, cx, cy)
            for (degree in 0 until 360 step 5) {
                val major = degree % 30 == 0
                val mediumTick = degree % 10 == 0
                val outer = radius * 0.94f
                val inner = when { major -> radius * 0.84f; mediumTick -> radius * 0.87f; else -> radius * 0.90f }
                tickPaint.strokeWidth = when { major -> radius * 0.009f; mediumTick -> radius * 0.006f; else -> radius * 0.004f }
                val radians = Math.toRadians((degree - 90).toDouble())
                canvas.drawLine(
                    cx + cos(radians).toFloat() * inner, cy + sin(radians).toFloat() * inner,
                    cx + cos(radians).toFloat() * outer, cy + sin(radians).toFloat() * outer, tickPaint
                )
                if (degree % 90 == 0) {
                    labelPaint.textSize = radius * 0.075f
                    val labelRadius = radius * 0.76f
                    canvas.drawText(degree.toString(), cx + cos(radians).toFloat() * labelRadius, cy + sin(radians).toFloat() * labelRadius + labelPaint.textSize * 0.34f, labelPaint)
                }
            }
            drawCardinal(canvas, cx, cy - innerRadius * 0.78f, "N", true)
            drawCardinal(canvas, cx + innerRadius * 0.78f, cy, "E", false)
            drawCardinal(canvas, cx, cy + innerRadius * 0.78f, "S", false)
            drawCardinal(canvas, cx - innerRadius * 0.78f, cy, "W", false)

            val needle = Path().apply {
                moveTo(cx, cy - innerRadius * 0.77f)
                lineTo(cx - radius * 0.045f, cy + radius * 0.04f)
                lineTo(cx, cy + radius * 0.015f)
                lineTo(cx + radius * 0.045f, cy + radius * 0.04f)
                close()
            }
            canvas.drawPath(needle, pointerPaint)
            val tail = Path().apply {
                moveTo(cx, cy + innerRadius * 0.77f)
                lineTo(cx - radius * 0.04f, cy - radius * 0.025f)
                lineTo(cx, cy + radius * 0.01f)
                lineTo(cx + radius * 0.04f, cy - radius * 0.025f)
                close()
            }
            canvas.drawPath(tail, needleTailPaint)
            canvas.restore()

            val marker = Path().apply {
                moveTo(cx, cy - radius * 0.88f)
                lineTo(cx - radius * 0.045f, cy - radius * 1.02f)
                lineTo(cx + radius * 0.045f, cy - radius * 1.02f)
                close()
            }
            canvas.drawPath(marker, northMarkPaint)
            canvas.drawCircle(cx, cy, radius * 0.065f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
            canvas.drawCircle(cx, cy, radius * 0.035f, centerPaint)
        }

        private fun drawCardinal(canvas: Canvas, x: Float, y: Float, label: String, north: Boolean) {
            cardinalPaint.color = if (north) green else ink
            cardinalPaint.textSize = radiusTextSize()
            canvas.save()
            canvas.rotate(heading, x, y)
            canvas.drawText(label, x, y - (cardinalPaint.ascent() + cardinalPaint.descent()) / 2f, cardinalPaint)
            canvas.restore()
        }

        private fun radiusTextSize() = min(width, height) * 0.055f

    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun rounded(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun bordered(color: Int, stroke: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    companion object {
        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val LOCATION_DISTANCE_M = 5f
        private const val HEADING_SMOOTHING = 0.22f
        private const val DIAL_HEIGHT_RATIO = 0.82f
    }
}
