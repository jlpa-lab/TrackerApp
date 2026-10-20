package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdRemoteConfig
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.banner_all
import com.mobile.trackerapp.databinding.ActivityScanQrBinding
import com.mobile.trackerapp.databinding.ActivityToolsBinding
import com.mobile.trackerapp.ui.DashboardActivity

class ScanQrActivity : DashboardActivity() {

    private lateinit var binding: ActivityScanQrBinding
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val value = decodeQr(uri)
                if (value == null) Toast.makeText(this, R.string.scan_qr_not_readable, Toast.LENGTH_LONG).show()
                else returnCode(value)
            }
        }
    }

    private lateinit var barcodeView: DecoratedBarcodeView
    private var flashlightOn = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = android.graphics.Color.rgb(246, 251, 248)
        window.navigationBarColor = android.graphics.Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        binding = ActivityScanQrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        barcodeView = findViewById(R.id.scan_barcode_view)
        // DecoratedBarcodeView includes its own bottom prompt and viewfinder.
        // This screen supplies both elements itself; leaving the library
        // layers visible makes the prompt sit underneath the flashlight pill.
        barcodeView.getStatusView()?.visibility = View.GONE
        barcodeView.getViewFinder().visibility = View.GONE
        loadScanQrBanner()
        barcodeView.decodeSingle { result -> result.text?.takeIf { it.isNotBlank() }?.let(::returnCode) }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST) else barcodeView.resume()
        findViewById<View>(R.id.scan_back).setOnClickListener { finish() }
        findViewById<View>(R.id.scan_flash).setOnClickListener {
            flashlightOn = !flashlightOn
            if (flashlightOn) barcodeView.setTorchOn() else barcodeView.setTorchOff()
            findViewById<TextView>(R.id.scan_flash_label).text = getString(if (flashlightOn) R.string.scan_qr_flashlight_on else R.string.scan_qr_flashlight_off)
            findViewById<View>(R.id.scan_flash).setBackgroundResource(if (flashlightOn) R.drawable.bg_flash_on else R.drawable.bg_flash_button)
            findViewById<ImageView>(R.id.scan_flash_icon).setColorFilter(if (flashlightOn) android.graphics.Color.rgb(10, 48, 29) else android.graphics.Color.WHITE)
        }
        findViewById<View>(R.id.scan_gallery).setOnClickListener {
            galleryPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE))
        }
    }

    override fun onResume() { super.onResume(); if (::barcodeView.isInitialized && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) barcodeView.resume() }
    override fun onPause() { if (::barcodeView.isInitialized) barcodeView.pause(); super.onPause() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) barcodeView.resume() else Toast.makeText(this, R.string.scan_qr_camera_permission, Toast.LENGTH_LONG).show() }

    private fun decodeQr(uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun returnCode(code: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SCANNED_CODE, code.trim()))
        finish()
    }
    private fun loadScanQrBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }

    companion object {
        const val EXTRA_SCANNED_CODE = "scanned_invite_code"
        private const val MAX_IMAGE_SIDE = 1600
        private const val CAMERA_REQUEST = 71
    }
}
