package com.aesthetic.gym.util

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {
    const val CHANNEL_REMINDERS = "training_reminders"

    /** Creates the channel if missing. minSdk 26 => NotificationChannel always available. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) != null) return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Recordatorios de entreno",
            NotificationManager.IMPORTANCE_DEFAULT // not HIGH (intrusive), not LOW (silent)
        ).apply {
            description = "Un aviso los días y a la hora que tú elijas. Puedes apagarlo cuando quieras."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Runtime permission gate (to decide whether to launch the system dialog). */
    fun hasRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Can it actually post? Covers the permission (33+) AND a silenced channel/app. */
    fun canPostReminders(context: Context): Boolean =
        hasRuntimePermission(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The app's notification settings screen (API 26+). */
fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppDetails(context)
    }
}

/** The app's details page: from here the user reaches Battery / auto-start. */
fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
}
