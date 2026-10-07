package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R

class FriendRequestsActivity : AppCompatActivity() {
    private val scanFriendQr = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(ScanQrActivity.EXTRA_SCANNED_CODE)?.let {
                findViewById<EditText>(R.id.friend_code_input).setText(it)
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_friend_requests)
        findViewById<View>(R.id.friend_back).setOnClickListener { finish() }
        findViewById<View>(R.id.friend_add).setOnClickListener { showPanel(R.id.friend_add) }
        findViewById<View>(R.id.friend_received).setOnClickListener { showPanel(R.id.friend_received) }
        findViewById<View>(R.id.friend_sent).setOnClickListener { showPanel(R.id.friend_sent) }
        findViewById<View>(R.id.scan_friend_qr).setOnClickListener {
            scanFriendQr.launch(Intent(this, ScanQrActivity::class.java))
        }
        findViewById<View>(R.id.show_my_qr_button).setOnClickListener {
            startActivity(Intent(this, MyQrActivity::class.java))
        }
        findViewById<View>(R.id.share_invite_button).setOnClickListener { shareInvite() }
        findViewById<TextView>(R.id.my_invite_code).text = ConnectionInvite.code(this)
        findViewById<View>(R.id.friend_connect_button).setOnClickListener { submitInviteCode() }
        showPanel(R.id.friend_add)
    }

    private fun submitInviteCode() {
        val entered = findViewById<EditText>(R.id.friend_code_input).text.toString().trim().uppercase()
        if (entered.isBlank()) {
            findViewById<EditText>(R.id.friend_code_input).error = "Enter a friend's invite code"
            return
        }
        if (entered == ConnectionInvite.code(this)) {
            Toast.makeText(this, "That is your own invite code", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Friend requests aren't connected to a server yet", Toast.LENGTH_LONG).show()
    }

    private fun shareInvite() {
        val code = ConnectionInvite.code(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Connect with me on GPS Tracker. My invite code is $code")
        }
        startActivity(Intent.createChooser(send, "Share friend invite"))
    }

    private fun showPanel(selectedId: Int) {
        listOf(R.id.friend_add, R.id.friend_received, R.id.friend_sent).forEach { id ->
            val tab = findViewById<TextView>(id)
            val selected = id == selectedId
            tab.setBackgroundColor(if (selected) Color.rgb(232, 245, 233) else Color.TRANSPARENT)
            tab.setTextColor(if (selected) Color.rgb(0, 150, 60) else Color.rgb(117, 126, 143))
            tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
        findViewById<View>(R.id.add_panel).visibility = if (selectedId == R.id.friend_add) View.VISIBLE else View.GONE
        findViewById<View>(R.id.received_panel).visibility = if (selectedId == R.id.friend_received) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sent_panel).visibility = if (selectedId == R.id.friend_sent) View.VISIBLE else View.GONE
    }
}
