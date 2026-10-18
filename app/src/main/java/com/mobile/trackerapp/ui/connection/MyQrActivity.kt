package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.mobile.trackerapp.R
import com.mobile.trackerapp.network.TrackerBackendClient
import com.mobile.trackerapp.data.LocalTrackerDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mobile.trackerapp.ui.DashboardActivity

class MyQrActivity : DashboardActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        setContentView(R.layout.activity_my_qr)
        val inviteCode = ConnectionInvite.code(this)
        findViewById<View>(R.id.my_qr_back).setOnClickListener { finish() }
        val sharing = getSharedPreferences("tracker_location", MODE_PRIVATE).getBoolean("enabled", false)
        findViewById<TextView>(R.id.my_qr_status).text = if (sharing) "●  Live Location Sharing Active" else "●  Location sharing is off"
        val render: (String) -> Unit = { code ->
            findViewById<TextView>(R.id.my_qr_code_text).text = code
            findViewById<ImageView>(R.id.my_qr_image).setImageBitmap(ConnectionInvite.qrBitmap(code, 640))
            findViewById<View>(R.id.my_qr_share).setOnClickListener { shareInvite(code) }
        }
        val localDb = LocalTrackerDb(this)
        localDb.read("invite_code")?.takeIf { it.isNotBlank() }?.let(render)
        CoroutineScope(Dispatchers.Main).launch { runCatching { val backend = TrackerBackendClient(this@MyQrActivity); backend.register(); backend.createFriendInvite().getString("code") }.onSuccess { localDb.write("invite_code", it); render(it) }.onFailure { if (localDb.read("invite_code").isNullOrBlank()) render(ConnectionInvite.code(this@MyQrActivity)) } }
    }

    private fun shareInvite(code: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Connect with me on GPS Tracker. My invite code is $code")
        }
        startActivity(Intent.createChooser(send, "Share friend invite"))
    }
}
