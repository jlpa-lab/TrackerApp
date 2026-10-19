package com.mobile.trackerapp.ui.tracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.databinding.ActivityTrackFriendBinding
import com.mobile.trackerapp.ui.connection.TrackFriendRequestsActivity
import com.mobile.trackerapp.ui.tracker.TrackFriendMapActivity
import com.mobile.trackerapp.ui.livelocation.LiveLocationActivity
import com.mobile.trackerapp.ui.connection.FriendRequestsActivity

class TrackFriendActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTrackFriendBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.trackFriendBack.setOnClickListener { finish() }
        val connect = { startActivity(Intent(this, TrackFriendRequestsActivity::class.java)) }
        binding.trackFriendViewMap.setOnClickListener { startActivity(Intent(this, TrackFriendMapActivity::class.java)) }
        binding.trackFriendConnect.setOnClickListener { connect() }
        binding.trackFriendRequests.setOnClickListener { connect() }
    }
}
