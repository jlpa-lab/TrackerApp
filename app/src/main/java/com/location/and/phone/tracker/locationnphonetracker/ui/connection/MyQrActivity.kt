package com.location.and.phone.tracker.locationnphonetracker.ui.connection

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.location.and.phone.tracker.locationnphonetracker.BuildConfig
import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.banner_all
import com.location.and.phone.tracker.locationnphonetracker.data.LocalTrackerDb
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityMyQrBinding
import com.location.and.phone.tracker.locationnphonetracker.network.TrackerBackendClient
import com.location.and.phone.tracker.locationnphonetracker.ui.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MyQrActivity : DashboardActivity() {
    private lateinit var binding: ActivityMyQrBinding
    private var qrBitmap: Bitmap? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        binding = ActivityMyQrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadMyQrBanner()
        val inviteCode = ConnectionInvite.code(this)
        findViewById<View>(R.id.my_qr_back).setOnClickListener { finish() }
        val sharing = getSharedPreferences("tracker_location", MODE_PRIVATE).getBoolean("enabled", false)
        findViewById<TextView>(R.id.my_qr_status).text = getString(if (sharing) R.string.qr_sharing_active else R.string.connection_sharing_off)
        val render: (String) -> Unit = { code ->
            findViewById<TextView>(R.id.my_qr_code_text).text = code
            qrBitmap = ConnectionInvite.qrBitmap(code, 640)
            findViewById<ImageView>(R.id.my_qr_image).setImageBitmap(qrBitmap)
            findViewById<View>(R.id.my_qr_share).setOnClickListener { shareInvite(code) }
        }
        val localDb = LocalTrackerDb(this)
        localDb.read("invite_code")?.takeIf { it.isNotBlank() }?.let(render)
        CoroutineScope(Dispatchers.Main).launch { runCatching { val backend = TrackerBackendClient(this@MyQrActivity); backend.register(); backend.createFriendInvite().getString("code") }.onSuccess { localDb.write("invite_code", it); render(it) }.onFailure { if (localDb.read("invite_code").isNullOrBlank()) render(ConnectionInvite.code(this@MyQrActivity)) } }
    }

    private fun shareInvite(code: String) {
        val bitmap = qrBitmap ?: ConnectionInvite.qrBitmap(code, 640).also { qrBitmap = it }
        val shareDir = File(cacheDir, "shared_qr").apply { mkdirs() }
        val shareFile = File(shareDir, "tracker_invite_qr.png")
        val saved = shareFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        if (!saved) {
            shareTextOnly(code)
            return
        }
        val qrUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            shareFile
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            setDataAndType(qrUri, "image/png")
            putExtra(Intent.EXTRA_STREAM, qrUri)
            putExtra(Intent.EXTRA_TEXT, getString(R.string.connection_invite_share_text, code))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("QR code", qrUri)
        }
        startActivity(Intent.createChooser(send, getString(R.string.connection_share_friend_invite)))
    }

    private fun shareTextOnly(code: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.connection_invite_share_text, code))
        }
        startActivity(Intent.createChooser(send, getString(R.string.connection_share_friend_invite)))
    }
    private fun loadMyQrBanner() {
        val bannerConfig = AdRemoteConfig.banner_all
        AdsManager.loadBanner(
            this, bannerConfig, binding.frBanner, isCollapse = false
        )
    }
}
