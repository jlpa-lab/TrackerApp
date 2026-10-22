package com.location.and.phone.tracker.locationnphonetracker.ui.tracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.location.and.phone.tracker.locationnphonetracker.databinding.ActivityTrackFriendBinding
import com.location.and.phone.tracker.locationnphonetracker.localization.LocaleManager
import com.location.and.phone.tracker.locationnphonetracker.ui.connection.TrackFriendRequestsActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.tracker.TrackFriendMapActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.livelocation.LiveLocationActivity
import com.location.and.phone.tracker.locationnphonetracker.ui.connection.FriendRequestsActivity

class TrackFriendActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleManager.applyPersisted(this)
        val binding = ActivityTrackFriendBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.trackFriendBack.setOnClickListener { finish() }
        val connect = { startActivity(Intent(this, TrackFriendRequestsActivity::class.java)) }
        binding.trackFriendViewMap.setOnClickListener { startActivity(Intent(this, TrackFriendMapActivity::class.java)) }
        binding.trackFriendConnect.setOnClickListener { connect() }
        binding.trackFriendRequests.setOnClickListener { connect() }
    }
}
