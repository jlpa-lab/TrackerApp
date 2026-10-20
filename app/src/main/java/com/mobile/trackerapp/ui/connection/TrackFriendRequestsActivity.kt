package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import com.mobile.trackerapp.ui.DashboardActivity
import com.mobile.trackerapp.service.LocationSharingCoordinator
import com.mobile.trackerapp.ui.tracker.TrackFriendMapActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class TrackFriendRequestsActivity : DashboardActivity() {
    private val layoutResource: Int = R.layout.activity_track_friend_requests
    private lateinit var backend: TrackerBackendClient
    private var myInviteCode: String = ""
    private lateinit var localDb: LocalTrackerDb
    private var codeWave: ValueAnimator? = null
    private var backendReady = false
    private val currentDeviceId by lazy { Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty() }
    override fun onDestroy() { codeWave?.cancel(); super.onDestroy() }

    override fun onResume() {
        super.onResume()
        LocationSharingCoordinator.syncAcceptedFriends(this)
    }
    private val scanFriendQr = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(ScanQrActivity.EXTRA_SCANNED_CODE)?.let {
                findViewById<EditText>(R.id.friend_code_input).setText(it)
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(246, 251, 248)
        window.navigationBarColor = Color.rgb(246, 251, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        hideNavigationBar()
        setContentView(layoutResource)
        val scroll = findViewById<android.widget.ScrollView>(R.id.friend_requests_scroll)
        val friendCodeInput = findViewById<EditText>(R.id.friend_code_input)
        val initialBottomPadding = scroll.paddingBottom
        fun revealFriendCodeInput() {
            val inputRect = Rect()
            val visibleFrame = Rect()
            if (!friendCodeInput.getGlobalVisibleRect(inputRect)) return
            scroll.getWindowVisibleDisplayFrame(visibleFrame)
            val safeBottom = visibleFrame.bottom - dp(12)
            val requiredScroll = inputRect.bottom - safeBottom
            if (requiredScroll > 0) scroll.smoothScrollBy(0, requiredScroll)
        }
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight,
                initialBottomPadding + imeBottom)
            if (imeBottom > 0) friendCodeInput.post { revealFriendCodeInput() }
            insets
        }
        friendCodeInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) friendCodeInput.postDelayed({
                if (ViewCompat.getRootWindowInsets(scroll)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true) revealFriendCodeInput()
            }, 250)
        }
        scroll.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val inputRect = Rect()
                friendCodeInput.getGlobalVisibleRect(inputRect)
                if (!inputRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    hideFriendKeyboard()
                }
            }
            false
        }
        AdsManager.showNativeAllAndPreload(this, R.layout.layout_native_ad_small)
        bindNativeAllAd(this, findViewById(R.id.native_all_container))
        findViewById<TextView>(R.id.my_invite_code)?.text = "------"
        localDb = LocalTrackerDb(this)
        backend = TrackerBackendClient(this)
        findViewById<TextView>(R.id.friend_received).setText(R.string.friend_received)
        findViewById<TextView>(R.id.friend_sent).setText(R.string.friend_sent)
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
            }.onSuccess {
                backendReady = true
                loadReceivedRequests()
                loadSentRequests()
            }.onFailure { Toast.makeText(this@TrackFriendRequestsActivity, getString(R.string.connection_backend_unavailable, it.message.orEmpty()), Toast.LENGTH_LONG).show() }
        }
        findViewById<View>(R.id.friend_back).setOnClickListener { finish() }
        findViewById<View>(R.id.friend_add).setOnClickListener { hideFriendKeyboard(); showPanel(R.id.friend_add) }
        findViewById<View>(R.id.friend_received).setOnClickListener { hideFriendKeyboard(); showPanel(R.id.friend_received); loadReceivedRequests() }
        findViewById<View>(R.id.friend_sent).setOnClickListener { hideFriendKeyboard(); showPanel(R.id.friend_sent); loadSentRequests() }
        findViewById<View>(R.id.scan_friend_qr)?.setOnClickListener {
            scanFriendQr.launch(Intent(this, ScanQrActivity::class.java))
        }
        findViewById<View>(R.id.show_my_qr_button).setOnClickListener {
            startActivity(Intent(this, MyQrActivity::class.java))
        }
        findViewById<View>(R.id.share_invite_button).setOnClickListener { shareInvite() }
        findViewById<View>(R.id.friend_connect_button).setOnClickListener { submitInviteCode() }
        showPanel(when (intent.getStringExtra("open_tab")) {
            "received" -> R.id.friend_received
            "sent" -> R.id.friend_sent
            else -> R.id.friend_add
        })
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
        if (!backendReady) {
            Toast.makeText(this, R.string.connection_secure_retry, Toast.LENGTH_SHORT).show()
            return
        }
        val entered = findViewById<EditText>(R.id.friend_code_input).text.toString().trim().uppercase()
        if (entered.isBlank()) {
            findViewById<EditText>(R.id.friend_code_input).error = "Enter a friend's invite code"
            return
        }
        if (entered == myInviteCode) {
            Toast.makeText(this, R.string.connection_own_code, Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.sendFriendRequest(entered) }
                .onSuccess {
                    Toast.makeText(this@TrackFriendRequestsActivity, R.string.connection_request_sent, Toast.LENGTH_SHORT).show()
                    showPanel(R.id.friend_sent)
                    loadSentRequests()
                }
                .onFailure { Toast.makeText(this@TrackFriendRequestsActivity, it.message ?: getString(R.string.connection_request_failed), Toast.LENGTH_LONG).show() }
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
        if (!backendReady) return
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                backend.listRequests()
            }.onSuccess { result ->
                localDb.write("received_requests", result.toString())
                val list = findViewById<LinearLayout>(R.id.request_list) ?: return@onSuccess
                list.removeAllViews()
                val requests = result.optJSONArray("requests")
                val pendingRequests = buildList {
                    if (requests != null) {
                        for (index in 0 until requests.length()) {
                            val request = requests.optJSONObject(index) ?: continue
                            if (request.optString("status", "pending") == "pending") add(request)
                        }
                    }
                }
                val requestCount = pendingRequests.size
                findViewById<TextView>(R.id.friend_received).text = getString(R.string.connection_received_count, requestCount)
                findViewById<TextView>(R.id.received_request_count).text = getString(R.string.connection_request_count, requestCount)
                if (pendingRequests.isEmpty()) {
                    list.addView(emptyRequestMessage("No pending invitations yet."))
                } else for ((i, request) in pendingRequests.withIndex()) {
                    val requestId = request.getString("id")
                    val name = request.optString("from_name", request.optString("from_id"))
                    val handle = request.optString("from_handle").ifBlank { request.optString("from_username") }
                        .ifBlank { fallbackHandle(name) }
                    list.addView(receivedRequestCard(
                        requestId = requestId,
                        friendId = request.optString("from_id"),
                        name = name,
                        handle = handle,
                        time = relativeTime(request.opt("created_at")),
                        status = "pending"
                    ), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(if (i == 0) 12 else 10) })
                }
                appendLocationRequests(list, incoming = true)
            }.onFailure { Toast.makeText(this@TrackFriendRequestsActivity, it.message ?: getString(R.string.connection_requests_load_failed), Toast.LENGTH_LONG).show() }
        }
    }

    private fun decideRequest(id: String, action: String, friendName: String = "") {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.decideRequest(id, action) }
                .onSuccess {
                    if (action == "accept") startLocationSharingAfterAcceptance(friendName)
                    Toast.makeText(this@TrackFriendRequestsActivity, if (action == "accept") R.string.connection_request_accepted else R.string.connection_request_rejected, Toast.LENGTH_SHORT).show()
                    loadReceivedRequests()
                }
                .onFailure { Toast.makeText(this@TrackFriendRequestsActivity, it.message ?: getString(R.string.connection_request_update_failed), Toast.LENGTH_LONG).show() }
        }
    }

    private fun startLocationSharingAfterAcceptance(friendName: String) {
        LocationSharingCoordinator.markAcceptedFriend(this, friendName)
        if (!LocationSharingCoordinator.hasRequiredPermissions(this)) {
            startActivity(Intent(this, TrackFriendMapActivity::class.java).putExtra("auto_start_sharing", true))
        }
    }

    private fun loadSentRequests() {
        if (!backendReady) return
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                backend.listSentRequests()
            }.onSuccess { result ->
                val panel = findViewById<LinearLayout>(R.id.sent_panel)
                val requests = result.optJSONArray("requests")
                val pendingRequests = buildList {
                    if (requests != null) {
                        for (index in 0 until requests.length()) {
                            val request = requests.optJSONObject(index) ?: continue
                            if (request.optString("status", "pending") == "pending") add(request)
                        }
                    }
                }
                val requestCount = pendingRequests.size
                findViewById<TextView>(R.id.friend_sent).text = getString(R.string.connection_sent_count, requestCount)
                findViewById<TextView>(R.id.sent_request_count).text = getString(R.string.connection_request_count, requestCount)
                val list = findViewById<LinearLayout>(R.id.sent_request_list)
                list.removeAllViews()
                if (pendingRequests.isEmpty()) {
                    list.addView(emptyRequestMessage("You haven’t sent any invitations yet."))
                } else for ((i, request) in pendingRequests.withIndex()) {
                    val name = request.optString("to_name", request.optString("to_id"))
                    val handle = request.optString("to_handle").ifBlank { request.optString("to_username") }
                        .ifBlank { fallbackHandle(name) }
                    list.addView(sentRequestCard(
                        requestId = request.getString("id"),
                        friendId = request.optString("to_id"),
                        name = name,
                        handle = handle,
                        time = relativeTime(request.opt("created_at")),
                        status = "pending"
                    ), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = if (i == 0) dp(12) else dp(10) })
                }
                appendLocationRequests(list, incoming = false)
            }.onFailure { Toast.makeText(this@TrackFriendRequestsActivity, it.message ?: getString(R.string.connection_requests_load_failed), Toast.LENGTH_LONG).show() }
        }
    }

    private fun receivedRequestCard(requestId: String, friendId: String, name: String, handle: String, time: String, status: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_friend_request_item)

            val identity = identityRow(name, handle, time)
            addView(identity)
            if (status == "pending") {
                val actions = LinearLayout(this@TrackFriendRequestsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                actions.addView(
                    actionButton(getString(R.string.connection_accept), true) { decideRequest(requestId, "accept", name) },
                    LinearLayout.LayoutParams(0, dp(32), 1f)
                )
                actions.addView(actionButton(getString(R.string.connection_decline), false) {
                    decideRequest(
                        requestId,
                        "reject"
                    )
                }, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(8) })
                addView(actions, LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(12) })
            } else {
                addView(
                    statusLabel(status),
                    LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(10) })
            }
        }

    private fun sentRequestCard(requestId: String, friendId: String, name: String, handle: String, time: String, status: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_friend_request_item)

            val top = identityRow(name, handle, "")
            val statusView = TextView(this@TrackFriendRequestsActivity).apply {
                text = "◷  ${status.replaceFirstChar { it.uppercase() }}"
                textSize = 11f
                setTextColor(Color.parseColor("#787E8D"))
                typeface = ResourcesCompat.getFont(this@TrackFriendRequestsActivity, R.font.inter_regular)
                gravity = Gravity.CENTER_VERTICAL
            }
            (top as LinearLayout).addView(statusView, LinearLayout.LayoutParams(wrap(), wrap()))
            addView(top)

            val bottom = LinearLayout(this@TrackFriendRequestsActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            bottom.addView(TextView(this@TrackFriendRequestsActivity).apply {
                text = if (status == "pending") "Sent $time" else status.replaceFirstChar { it.uppercase() }
                textSize = 13f
                setTextColor(Color.parseColor("#787E8D"))
                typeface = ResourcesCompat.getFont(this@TrackFriendRequestsActivity, R.font.inter_regular)
            }, LinearLayout.LayoutParams(0, wrap(), 1f))
            if (status == "pending") {
                bottom.addView(
                    actionButton(getString(R.string.connection_cancel_request), false) {
                        cancelRequest(requestId)
                    }.apply {
                        textSize = 12f
                        setTextColor(Color.parseColor("#787E8D"))
                    },
                    LinearLayout.LayoutParams(dp(116), dp(32))
                )
            }
            addView(bottom, LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(12) })
        }

    private fun identityRow(name: String, handle: String, time: String): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        val avatar = TextView(this@TrackFriendRequestsActivity).apply {
            text = name.trim().firstOrNull()?.uppercase() ?: "?"
            textSize = 13f
            textSize = 18f
            setTextColor(Color.parseColor("#0A1120"))
            setTextColor(Color.rgb(17, 24, 39))
            gravity = Gravity.CENTER
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                dp(28),
                dp(28)
            )
            background = rounded(Color.parseColor("#E8F3E8"), 100)
        }
        addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
        val names = LinearLayout(this@TrackFriendRequestsActivity).apply { orientation = LinearLayout.VERTICAL }
        names.addView(TextView(this@TrackFriendRequestsActivity).apply {
            text = name
            textSize = 13f
            setTextColor(Color.parseColor("#0A1120"))
            typeface = ResourcesCompat.getFont(this@TrackFriendRequestsActivity, R.font.inter_bold)
        })
        names.addView(TextView(this@TrackFriendRequestsActivity).apply {
            text = handle
            textSize = 11f
            setTextColor(Color.parseColor("#787E8D"))
            typeface = ResourcesCompat.getFont(this@TrackFriendRequestsActivity, R.font.inter_regular)
        }, LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(2) })
        addView(names, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginStart = dp(12) })
        if (time.isNotBlank()) addView(TextView(this@TrackFriendRequestsActivity).apply {
            text = time
            textSize = 11f
            setTextColor(Color.parseColor("#787E8D"))
            typeface = ResourcesCompat.getFont(this@TrackFriendRequestsActivity, R.font.inter_regular)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(wrap(), wrap()))
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(if (primary) Color.WHITE else Color.rgb(117, 126, 143))
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        background = getDrawable(if (primary) R.drawable.bg_friend_action_primary else R.drawable.bg_friend_action_secondary)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun statusLabel(status: String) = TextView(this).apply {
        text = status.replaceFirstChar { it.uppercase() }
        textSize = 13f
        setTextColor(Color.rgb(117, 126, 143))
    }

    private fun emptyRequestMessage(message: String) = TextView(this).apply {
        text = message
        textSize = 14f
        setTextColor(Color.rgb(117, 126, 143))
        gravity = Gravity.CENTER
        setPadding(0, dp(20), 0, dp(20))
    }

    private fun cancelRequest(id: String) {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.cancelFriendRequest(id) }
                .onSuccess {
                    Toast.makeText(this@TrackFriendRequestsActivity, R.string.connection_request_cancelled, Toast.LENGTH_SHORT).show()
                    loadSentRequests()
                }
                .onFailure { Toast.makeText(this@TrackFriendRequestsActivity, it.message ?: getString(R.string.connection_cancel_failed), Toast.LENGTH_LONG).show() }
        }
    }

    private fun appendLocationRequests(list: LinearLayout, incoming: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.listLocationRequests() }.onSuccess { result ->
                val requests = result.optJSONArray("requests") ?: return@onSuccess
                val selected = (0 until requests.length()).mapNotNull { requests.optJSONObject(it) }
                    .filter { request ->
                        request.optString("status") == "pending" &&
                            if (incoming) request.optString("to_id") == currentDeviceId else request.optString("from_id") == currentDeviceId
                    }
                if (selected.isEmpty()) return@onSuccess
                list.addView(TextView(this@TrackFriendRequestsActivity).apply {
                    text = if (incoming) "Location sharing requests" else "Location requests sent"
                    textSize = 16f
                    setTextColor(Color.rgb(17, 24, 39))
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    setPadding(0, dp(18), 0, dp(4))
                })
                selected.forEach { request ->
                    val friendName = if (incoming) request.optString("from_name") else request.optString("to_name")
                    val friendId = if (incoming) request.optString("from_id") else request.optString("to_id")
                    list.addView(locationRequestCard(request, friendName, friendId, incoming), LinearLayout.LayoutParams(-1, wrap()).apply { topMargin = dp(8) })
                }
            }
        }
    }

    private fun locationRequestCard(request: org.json.JSONObject, friendName: String, friendId: String, incoming: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = getDrawable(R.drawable.bg_friend_request_item)
            val displayName = friendName.ifBlank { friendId }
            addView(identityRow(displayName, fallbackHandle(displayName), relativeTime(request.opt("created_at"))))
            addView(actionButton(if (incoming) getString(R.string.common_share) else getString(R.string.connection_cancel_request), incoming) {
                CoroutineScope(Dispatchers.Main).launch {
                    runCatching {
                        backend.decideLocationRequest(request.optString("id"), if (incoming) "accept" else "cancel")
                    }.onSuccess {
                        Toast.makeText(this@TrackFriendRequestsActivity, if (incoming) R.string.connection_sharing_enabled else R.string.connection_location_cancelled, Toast.LENGTH_SHORT).show()
                        if (incoming) loadReceivedRequests() else loadSentRequests()
                    }.onFailure {
                        Toast.makeText(this@TrackFriendRequestsActivity, it.message ?: getString(R.string.connection_location_update_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        }

    private fun fallbackHandle(name: String): String {
        val normalized = name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), ".").trim('.')
        return if (normalized.isBlank()) "@friend" else "@$normalized"
    }

    private fun relativeTime(raw: Any?): String {
        val value = raw?.toString().orEmpty()
        val timestamp = value.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000 else it }
            ?: listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss"
            ).firstNotNullOfOrNull { pattern ->
                runCatching { SimpleDateFormat(pattern, Locale.US).parse(value)?.time }.getOrNull()
            }
        if (timestamp == null) return "Recently"
        val seconds = abs(System.currentTimeMillis() - timestamp) / 1000L
        return when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60} minutes ago"
            seconds < 86_400 -> "${seconds / 3600} hours ago"
            seconds < 172_800 -> "Yesterday"
            else -> "${seconds / 86_400} days ago"
        }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun showPanel(selectedId: Int) {
        listOf(R.id.friend_add, R.id.friend_received, R.id.friend_sent).forEach { id ->
            val tab = findViewById<TextView>(id)
            val selected = id == selectedId
            tab.setBackgroundResource(if (selected) R.drawable.bg_friend_tab_selected else android.R.color.transparent)
            tab.setTextColor(
                Color.parseColor(if (selected) "#008D31" else "#787E8D")
            )

            tab.typeface = resources.getFont(
                if (selected) R.font.inter_bold
                else R.font.inter_regular
            )
        }
        findViewById<View>(R.id.add_panel).visibility = if (selectedId == R.id.friend_add) View.VISIBLE else View.GONE
        findViewById<View>(R.id.received_panel).visibility = if (selectedId == R.id.friend_received) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sent_panel).visibility = if (selectedId == R.id.friend_sent) View.VISIBLE else View.GONE
        findViewById<View>(R.id.received_guidance).visibility = if (selectedId == R.id.friend_received) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sent_guidance).visibility = if (selectedId == R.id.friend_sent) View.VISIBLE else View.GONE
    }

    private fun hideFriendKeyboard() {
        val focusedView = currentFocus ?: window.decorView
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
        focusedView.clearFocus()
    }
}


