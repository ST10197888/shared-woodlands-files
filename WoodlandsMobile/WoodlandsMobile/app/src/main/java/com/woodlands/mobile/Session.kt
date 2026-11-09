package com.woodlands.mobile

import android.content.Context

class Session(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("woodlands_mobile_session", Context.MODE_PRIVATE)

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) { prefs.edit().putString("user_id", value).apply() }

    /** JWT returned by /api/auth/login (or handoff redeem). Mirrored into ApiClient for requests. */
    var token: String?
        get() = prefs.getString("auth_token", null)
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove("auth_token").apply()
            else prefs.edit().putString("auth_token", value).apply()
            ApiClient.authToken = value?.takeIf { it.isNotBlank() }
        }

    val isLoggedIn: Boolean get() = userId != null

    /** Push the saved token back into ApiClient (call once at app/activity start). */
    fun restoreToken() {
        ApiClient.authToken = token?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        prefs.edit().remove("user_id").remove("auth_token").apply()
        ApiClient.authToken = null
        LocalDb.shared(appContext).clearPrivateData()
        SyncManager.forgetPrivateHashes()
    }
}
