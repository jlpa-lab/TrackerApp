package com.mobile.trackerapp.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R

class TrackFriendActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_coming_soon)
        findViewById<android.widget.TextView>(R.id.feature_title)
            .setText(R.string.feature_track_friend)
    }
}
