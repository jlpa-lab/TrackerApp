package com.mobile.trackerapp.ui.connection

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mobile.trackerapp.R
import com.mobile.trackerapp.network.TrackerBackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CreateProfileActivity : AppCompatActivity() {
    private var selectedAvatar = "🙂"
    private lateinit var backend: TrackerBackendClient
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setContentView(R.layout.activity_create_profile); backend = TrackerBackendClient(this)
        val avatars = listOf(R.id.avatar_1 to "🙂", R.id.avatar_2 to "😎", R.id.avatar_3 to "🤗", R.id.avatar_4 to "🦊")
        avatars.forEach { (id, value) -> findViewById<TextView>(id).setOnClickListener { selectedAvatar = value; avatars.forEach { (other, _) -> findViewById<TextView>(other).setTextColor(Color.DKGRAY) }; findViewById<TextView>(id).setTextColor(Color.rgb(0, 150, 60)) } }
        findViewById<TextView>(R.id.profile_continue).setOnClickListener { saveProfile() }
    }
    private fun saveProfile() {
        val name = findViewById<EditText>(R.id.profile_name).text.toString().trim()
        if (name.isBlank()) { findViewById<EditText>(R.id.profile_name).error = "Enter your name"; return }
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { backend.register(name, selectedAvatar); backend.updateProfile(name) }.onSuccess {
                getSharedPreferences("tracker_profile", MODE_PRIVATE).edit().putBoolean("complete", true).putString("name", name).putString("avatar", selectedAvatar).apply()
                startActivity(Intent(this@CreateProfileActivity, FriendRequestsActivity::class.java)); finish()
            }.onFailure { Toast.makeText(this@CreateProfileActivity, "Could not create profile: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
