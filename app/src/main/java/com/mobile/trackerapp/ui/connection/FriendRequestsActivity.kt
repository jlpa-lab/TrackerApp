package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.bindNativeAllAd
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.network.TrackerBackendClient
import com.mobile.trackerapp.data.LocalTrackerDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.animation.ValueAnimator
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

open class FriendRequestsActivity : AppCompatActivity() {
    protected open val layoutResource: Int = R.layout.activity_friend_requests
    private lateinit var backend: TrackerBackendClient
    private var myInviteCode: String = ""
    private lateinit var localDb: LocalTrackerDb
    private var codeWave: ValueAnimator? = null
    override fun onDestroy() { codeWave?.cancel(); super.onDestroy() }
    private val scanFriendQr = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(ScanQrActivity.EXTRA_SCANNED_CODE)?.let {
                findViewById<EditText>(R.id.friend_code_input).setText(it)
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!getSharedPreferences("tracker_profile", MODE_PRIVATE).getBoolean("complete", false)) {
            startActivity(Intent(this, CreateProfileActivity::class.java)); finish(); return
        }
        setContentView(layoutResource)
        AdsManager.loadNativeAll(this, R.layout.layout_native_ad_small)
        AdsManager.loadInterHome(this, ignoreLimit = true)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        findViewById<TextView>(R.id.my_invite_code)?.text = "------"
        localDb = LocalTrackerDb(this)
        backend = TrackerBackendClient(this)
        findViewById<LinearLayout>(R.id.received_panel).addView(LinearLayout(this).apply { id = R.id.request_list; orientation = LinearLayout.VERTICAL })
        findViewById<TextView>(R.id.friend_received).text = "Received"
        findViewById<TextView>(R.id.friend_sent).text = "Sent"
        myInviteCode = localDb.read("invite_code").orEmpty()
        if (myInviteCode.isNotBlank()) findViewById<TextView>(R.id.my_invite_code).text = myInviteCode
        else startCodeWave(findViewById(R.id.my_invite_code))
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                backend.register()
                myInviteCode = backend.createFriendInvite().getString("code")
                localDb.write("invite_code", myInviteCode)
                codeWave?.cancel()
                findViewById<TextView>(R.id.my_invite_code).text = myInviteCode
                findViewById<TextView>(R.id.my_invite_code).text = myInviteCode
            }.onFailure { Toast.makeText(this@FriendRequestsActivity, "Backend unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
        }
        findViewById<View>(R.id.friend_back).setOnClickListener { onBackPressed() }
        findViewById<View>(R.id.friend_add).setOnClickListener { showPanel(R.id.friend_add) }
        findViewById<View>(R.id.friend_received).setOnClickListener { showPanel(R.id.friend_received); loadReceivedRequests() }
        findViewById<View>(R.id.friend_sent).setOnClickListener { showPanel(R.id.friend_sent); loadSentRequests() }
        findViewById<View>(R.id.scan_friend_qr)?.setOnClickListener {
            scanFriendQr.launch(Intent(this, ScanQrActivity::class.java))
        }
        findViewById<View>(R.id.show_my_qr_button).setOnClickListener {
            startActivity(Intent(this, MyQrActivity::class.java))
        }
        findViewById<View>(R.id.share_invite_button).setOnClickListener { shareInvite() }
        findViewById<View>(R.id.friend_connect_button).setOnClickListener { submitInviteCode() }
        showPanel(R.id.friend_add)
    }

    private fun startCodeWave(view: TextView) {
        val placeholder = view.text.toString().ifBlank { "------" }
        codeWave = ValueAnimator.ofInt(0, placeholder.length * 2).apply {
            duration = 1100L; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val center = animation.animatedValue as Int
                val styled = SpannableString(placeholder)
                placeholder.indices.forEach { index ->
                    val distance = kotlin.math.abs(index * 2 - center)
                    val color = if (distance <= 2) Color.rgb(0, 154, 59) else Color.rgb(150, 160, 154)
                    styled.setSpan(ForegroundColorSpan(color), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                view.text = styled
            }
            start()
        }
    }

    private fun submitInviteCode() {
        val entered = findViewById<EditText>(R.id.friend_code_input).text.toString().trim().uppercase()
        if (entered.isBlank()) {
            findViewById<EditText>(R.id.friend_code_input).error = "Enter a friend's invite code"
            return
        }
        if (entered == myInviteCode) {
            Toast.makeText(this, "That is your own invite code", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.sendFriendRequest(entered) }
                .onSuccess {
                    Toast.makeText(this@FriendRequestsActivity, "Friend request sent", Toast.LENGTH_SHORT).show()
                    showPanel(R.id.friend_sent)
                    loadSentRequests()
                }
                .onFailure { Toast.makeText(this@FriendRequestsActivity, it.message ?: "Request failed", Toast.LENGTH_LONG).show() }
        }
    }

    private fun shareInvite() {
        val code = myInviteCode
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Connect with me on GPS Tracker. My invite code is $code")
        }
        startActivity(Intent.createChooser(send, "Share friend invite"))
    }

    private fun loadReceivedRequests() {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.listRequests() }.onSuccess { result ->
                localDb.write("received_requests", result.toString())
                val list = findViewById<LinearLayout>(R.id.request_list) ?: return@onSuccess
                list.removeAllViews()
                val requests = result.optJSONArray("requests")
                findViewById<TextView>(R.id.friend_received).text = "Received · ${requests?.length() ?: 0}"
                if (requests == null || requests.length() == 0) {
                    list.addView(TextView(this@FriendRequestsActivity).apply { text = "No pending invitations yet."; setPadding(0, 24, 0, 24) })
                    return@onSuccess
                }
                for (i in 0 until requests.length()) {
                    val request = requests.getJSONObject(i)
                    val requestId = request.getString("id")
                    val status = request.optString("status", "pending")
                    val row = LinearLayout(this@FriendRequestsActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 12) }
                    row.addView(TextView(this@FriendRequestsActivity).apply { text = "Friend request from ${request.optString("from_name", request.optString("from_id"))} ($status)"; textSize = 16f })
                    val actions = LinearLayout(this@FriendRequestsActivity)
                    val accept = Button(this@FriendRequestsActivity).apply { text = if (status == "accepted") "Accepted" else "Accept"; isEnabled = status != "accepted"; setOnClickListener { decideRequest(requestId, "accept") } }
                    val reject = Button(this@FriendRequestsActivity).apply { text = "Reject"; isEnabled = status != "rejected"; setOnClickListener { decideRequest(requestId, "reject") } }
                    actions.addView(accept); actions.addView(reject); row.addView(actions); list.addView(row)
                }
            }.onFailure { Toast.makeText(this@FriendRequestsActivity, it.message ?: "Could not load requests", Toast.LENGTH_LONG).show() }
        }
    }

    private fun decideRequest(id: String, action: String) {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.decideRequest(id, action) }
                .onSuccess {
                    Toast.makeText(this@FriendRequestsActivity, if (action == "accept") "Friend request accepted" else "Friend request rejected", Toast.LENGTH_SHORT).show()
                    loadReceivedRequests()
                }
                .onFailure { Toast.makeText(this@FriendRequestsActivity, it.message ?: "Request update failed", Toast.LENGTH_LONG).show() }
        }
    }

    private fun loadSentRequests() {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.listSentRequests() }.onSuccess { result ->
                val panel = findViewById<LinearLayout>(R.id.sent_panel)
                val requests = result.optJSONArray("requests")
                findViewById<TextView>(R.id.friend_sent).text = "Sent · ${requests?.length() ?: 0}"
                if (panel.childCount > 0) panel.removeViews(0, panel.childCount)
                val list = LinearLayout(this@FriendRequestsActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
                if (requests == null || requests.length() == 0) {
                    list.addView(TextView(this@FriendRequestsActivity).apply { text = "You haven’t sent any invitations yet."; setPadding(0, 24, 0, 24) })
                } else for (i in 0 until requests.length()) {
                    val request = requests.getJSONObject(i)
                    list.addView(TextView(this@FriendRequestsActivity).apply { text = "Request to ${request.optString("to_name")} (${request.optString("status")})"; textSize = 16f; setPadding(0, 12, 0, 12) })
                }
                panel.addView(list)
            }.onFailure { Toast.makeText(this@FriendRequestsActivity, it.message ?: "Could not load sent requests", Toast.LENGTH_LONG).show() }
        }
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

    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
    }
}
