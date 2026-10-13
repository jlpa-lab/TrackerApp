package com.mobile.trackerapp.ui.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R
import com.mobile.trackerapp.ui.connection.TrackFriendRequestsActivity
import com.mobile.trackerapp.ui.livelocation.LiveLocationActivity

class TrackFriendActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_friend)
        findViewById<View>(R.id.track_friend_back).setOnClickListener { finish() }
        val connect = View.OnClickListener { startActivity(Intent(this, TrackFriendRequestsActivity::class.java)) }
        findViewById<View>(R.id.track_friend_view_map).setOnClickListener { startActivity(Intent(this, LiveLocationActivity::class.java).putExtra("friends_only", true).putExtra("screen_title", "Friend Locations").putExtra("screen_subtitle", "Live locations shared with you")) }
        findViewById<View>(R.id.track_friend_connect).setOnClickListener(connect)
        findViewById<View>(R.id.track_friend_requests).setOnClickListener(connect)
    }
}
