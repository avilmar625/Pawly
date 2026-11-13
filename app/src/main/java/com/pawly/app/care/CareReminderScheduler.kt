package com.pawly.app.care

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pawly.app.MainActivity
import com.pawly.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Schedules local care reminders and persists their source data so Android can
 * recreate alarms after a reboot or application update.
 */
internal object CareReminderScheduler {
    internal const val CHANNEL_ID = "pawly_care_reminders"
    private const val ACTION = "com.pawly.app.action.CARE_REMINDER"
    private const val PREFS = "pawly_care_reminders"
    private const val PREFIX = "reminder:"

    private fun parseDateTime(value: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }.parse(value.replace("|", " "))?.time
    }.getOrNull()

    fun schedule(context: Context, dogName: String, careName: String, dateTime: String): Boolean {
        val triggerAt = parseDateTime(dateTime) ?: return false
        if (triggerAt <= System.currentTimeMillis()) return false
        val requestCode = reminderId(dogName, careName)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(
            PREFIX + requestCode,
            listOf(dogName, careName, dateTime).joinToString("\u001f")
        ).apply()
        scheduleAlarm(context, dogName, careName, triggerAt, requestCode)
        return true
    }

    private fun scheduleAlarm(context: Context, dogName: String, careName: String, triggerAt: Long, requestCode: Int) {
        val intent = Intent(context, CareReminderReceiver::class.java).apply {
            action = ACTION
            putExtra("dogName", dogName)
            putExtra("careName", careName)
            putExtra("notificationId", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.filterKeys { it.startsWith(PREFIX) }.forEach { (key, raw) ->
            val parts = (raw as? String)?.split("\u001f")
            if (parts == null || parts.size != 3) {
                editor.remove(key)
            } else {
                val triggerAt = parseDateTime(parts[2])
                if (triggerAt == null || triggerAt <= System.currentTimeMillis()) {
                    editor.remove(key)
                } else {
                    val requestCode = key.removePrefix(PREFIX).toIntOrNull()
                    if (requestCode != null) scheduleAlarm(context, parts[0], parts[1], triggerAt, requestCode)
                    else editor.remove(key)
                }
            }
        }
        editor.apply()
    }

    fun cancel(context: Context, dogName: String, careName: String) {
        val requestCode = reminderId(dogName, careName)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(PREFIX + requestCode).apply()
        val intent = Intent(context, CareReminderReceiver::class.java).apply { action = ACTION }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun reminderId(dogName: String, careName: String): Int =
        "pawly-care|$dogName|$careName".hashCode()
}

internal class CareReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CareReminderScheduler.restore(context.applicationContext)
        }
    }
}

internal class CareReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dogName = intent.getStringExtra("dogName").orEmpty().ifBlank { "tu perro" }
        val careName = intent.getStringExtra("careName").orEmpty().ifBlank { "un cuidado pendiente" }
        val notificationId = intent.getIntExtra("notificationId", careName.hashCode())
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Recordatorios de Pawly", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Avisos locales de cuidados que has programado" }
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_paw)
            .setContentIntent(PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setContentTitle("Un recordatorio para $dogName")
            .setContentText("Hoy toca: $careName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Habías programado este recordatorio para $dogName: $careName."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(notificationId, notification) }
    }
}
