package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mobile.trackerapp.R

class ScanQrActivity : AppCompatActivity() {
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val value = decodeQr(uri)
                if (value == null) Toast.makeText(this, "No readable QR code found in that image", Toast.LENGTH_LONG).show()
                else returnCode(value)
            }
        }
    }

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        val content = result.contents
        if (!content.isNullOrBlank()) returnCode(content)
        else Toast.makeText(this, "No QR code was scanned", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_scan_qr)
        findViewById<View>(R.id.scan_back).setOnClickListener { finish() }
        findViewById<View>(R.id.scan_camera).setOnClickListener {
            qrScanner.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan your friend's invite QR code")
                setBeepEnabled(false)
                setOrientationLocked(false)
            })
        }
        findViewById<View>(R.id.scan_gallery).setOnClickListener {
            galleryPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE))
        }
    }

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

    companion object {
        const val EXTRA_SCANNED_CODE = "scanned_invite_code"
        private const val MAX_IMAGE_SIDE = 1600
    }
}
