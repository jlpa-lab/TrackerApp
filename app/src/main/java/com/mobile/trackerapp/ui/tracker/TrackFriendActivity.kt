package com.mobile.trackerapp.ui.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.connection.FriendRequestsActivity

class TrackFriendActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_friend)
        findViewById<View>(R.id.track_friend_back).setOnClickListener { finish() }
        val connect = View.OnClickListener { startActivity(Intent(this, FriendRequestsActivity::class.java)) }
        findViewById<View>(R.id.track_friend_connect).setOnClickListener(connect)
        findViewById<View>(R.id.track_friend_requests).setOnClickListener(connect)
    }
}
