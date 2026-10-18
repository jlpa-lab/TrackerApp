package com.mobile.trackerapp.ui.tracker

import com.mobile.trackerapp.R
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ui.livelocation.LiveLocationActivity

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
    override fun onBackPressed() {
        AdsManager.showInterHomeBack(this) {
            finish()
        }
    }
}
