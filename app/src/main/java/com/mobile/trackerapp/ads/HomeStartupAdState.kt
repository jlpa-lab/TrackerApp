package com.mobile.trackerapp.ads

/** Process-lifetime startup decision, captured before the persisted launch flag changes. */
internal class HomeStartupAdState {
    private var prepared = false
    private var pending = false

    fun prepare(firstLaunch: Boolean) {
        if (prepared) return
        prepared = true
        pending = !firstLaunch
    }

    fun consume(): Boolean {
        val result = pending
        pending = false
        return result
    }
}
