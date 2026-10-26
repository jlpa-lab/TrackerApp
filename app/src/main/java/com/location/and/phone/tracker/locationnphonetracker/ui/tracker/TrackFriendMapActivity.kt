package com.location.and.phone.tracker.locationnphonetracker.ui.tracker

import com.location.and.phone.tracker.locationnphonetracker.R
import com.location.and.phone.tracker.locationnphonetracker.ads.AdRemoteConfig
import com.location.and.phone.tracker.locationnphonetracker.ads.AdsManager
import com.location.and.phone.tracker.locationnphonetracker.ads.banner_all
import com.location.and.phone.tracker.locationnphonetracker.ui.livelocation.LiveLocationActivity

/** Dedicated Track Friend map entry. It owns the Track Friend map flow and its Add Friend action. */
class TrackFriendMapActivity : LiveLocationActivity() {
    override val locationLayout: Int = R.layout.activity_track_friend_map
    override fun onCreate(state: android.os.Bundle?) {
        intent.putExtra("friends_only", true)
        intent.putExtra("screen_title", "Track Friend")
        intent.putExtra("screen_subtitle", "Your people, connected safely")
        AdsManager.loadInterHome(this, ignoreLimit = true)
        super.onCreate(state)
    }
}
