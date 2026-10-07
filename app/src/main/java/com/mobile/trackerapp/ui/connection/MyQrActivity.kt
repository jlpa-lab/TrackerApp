package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R

class MyQrActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_qr)
        val inviteCode = ConnectionInvite.code(this)
        findViewById<View>(R.id.my_qr_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.my_qr_code_text).text = inviteCode
        findViewById<ImageView>(R.id.my_qr_image).setImageBitmap(ConnectionInvite.qrBitmap(inviteCode, 640))
        findViewById<View>(R.id.my_qr_share).setOnClickListener { shareInvite(inviteCode) }
    }

    private fun shareInvite(code: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Connect with me on GPS Tracker. My invite code is $code")
        }
        startActivity(Intent.createChooser(send, "Share friend invite"))
    }
}
