package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.DashboardActivity

class ConnectionActivity : DashboardActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = layoutInflater.inflate(R.layout.activity_connection, root, false)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(nav("Connection"), LinearLayout.LayoutParams(-1, wrap()))
        setContentView(root)
        findViewById<TextView>(R.id.connection_share_invite).setOnClickListener { shareInvite() }
        findViewById<View>(R.id.connection_show_qr).setOnClickListener { startActivity(Intent(this, MyQrActivity::class.java)) }
        findViewById<View>(R.id.connection_manage_requests).setOnClickListener { startActivity(Intent(this, FriendRequestsActivity::class.java)) }
    }

    private fun shareInvite() {
        val code = ConnectionInvite.code(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Connect with me on GPS Tracker. My invite code is $code")
        }
        startActivity(Intent.createChooser(send, "Share friend invite"))
    }
}
