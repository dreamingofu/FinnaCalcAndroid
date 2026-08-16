//
// Notifier.kt
//
// The Android side of what iOS gets from UNUserNotificationCenter: one place
// that owns the channels, the permission state, and posting.
//
// Two channels, because the two kinds of alert are worth muting separately:
// goal progress (investing + budgeting goals) and bill reminders (detected
// subscriptions). iOS has no channel concept, so this is a platform addition,
// not a behaviour change.
//
// Permission is asked at the CONSENT MOMENT — saving a goal that has alerts,
// or turning on a reminder — never on launch, matching iOS's
// requestPermissionIfNeeded.
//

package com.finnacalc.android.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.finnacalc.android.MainActivity
import com.finnacalc.android.R

enum class NotificationChannelId(val id: String, val title: String, val description: String) {
    Goals(
        "finnacalc.goals",
        "Goal progress",
        "When a savings or investing goal crosses a mark you set.",
    ),
    Bills(
        "finnacalc.bills",
        "Bill reminders",
        "Before a subscription or recurring charge is due.",
    ),
}

object Notifier {

    private var appContext: Context? = null

    /** Called once from FinnaApp.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
        createChannels(context)
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        NotificationChannelId.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = channel.description }
            )
        }
    }

    /**
     * Whether the app may post at all. On Android 13+ this is a runtime
     * permission; below it, notifications are on unless the user turned the
     * app's off in settings.
     */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** True when the runtime permission hasn't been decided either way yet. */
    fun needsRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

    /**
     * Posts immediately. A denied permission drops the notification silently,
     * exactly as iOS does with an unauthorized center — the alternative is
     * crashing on a feature the user already declined.
     */
    fun post(
        identifier: String,
        title: String,
        body: String,
        channel: NotificationChannelId,
        context: Context? = appContext,
    ) {
        val ctx = context ?: return
        if (!canPost(ctx)) return
        NotificationManagerCompat.from(ctx).notify(identifier.hashCode(), build(ctx, title, body, channel))
    }

    /** Cancels a previously posted or scheduled notification by identifier. */
    fun cancel(identifier: String, context: Context? = appContext) {
        val ctx = context ?: return
        NotificationManagerCompat.from(ctx).cancel(identifier.hashCode())
    }

    internal fun build(
        context: Context,
        title: String,
        body: String,
        channel: NotificationChannelId,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }
}
