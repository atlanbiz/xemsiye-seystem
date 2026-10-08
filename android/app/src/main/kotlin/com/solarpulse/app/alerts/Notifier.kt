package com.solarpulse.app.alerts

import android.Manifest
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
import com.solarpulse.app.MainActivity
import com.solarpulse.app.R
import com.solarpulse.core.model.AppNotification
import com.solarpulse.core.model.NotificationKind

/** System notifications for fired alert rules (channel "alerts"). */
class Notifier(private val context: Context) {

    /** Creates (or renames, after a language change) the alerts channel. */
    fun ensureChannel(localized: Context = context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            localized.getString(R.string.notif_channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = localized.getString(R.string.notif_channel_alertsDesc) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(n: AppNotification) {
        if (!canPost()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_LINK, n.link)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, n.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val color = when (n.kind) {
            NotificationKind.DANGER -> 0xFFEF4444.toInt()
            NotificationKind.WARNING -> 0xFFF59E0B.toInt()
            NotificationKind.SUCCESS -> 0xFF22C55E.toInt()
            NotificationKind.INFO -> 0xFF2557EB.toInt()
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setColor(color)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setPriority(if (n.kind == NotificationKind.DANGER) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(n.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // permission revoked between the check and the call
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
    }
}
