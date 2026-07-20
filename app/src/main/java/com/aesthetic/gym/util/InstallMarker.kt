package com.aesthetic.gym.util

import android.content.Context

/**
 * When this install first ran.
 *
 * Deliberately outside Room and excluded from backups: if the database is restored onto a fresh
 * install, history older than this marker did not happen on this device, so the first session
 * afterwards is not a real comeback.
 */
class InstallMarker(context: Context) {

    private val prefs = context.getSharedPreferences("zenit_install", Context.MODE_PRIVATE)

    val firstSeenAt: Long
        get() {
            val stored = prefs.getLong(KEY, 0L)
            if (stored > 0L) return stored
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY, now).apply()
            return now
        }

    private companion object {
        const val KEY = "first_seen_at"
    }
}
