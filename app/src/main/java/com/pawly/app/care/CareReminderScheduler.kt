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
import com.pawly.app.R
import java.text.SimpleDateFormat
import java.util.Locale

internal object CareReminderScheduler {
    private const val CHANNEL_ID = "pawly_care_reminders"
    private const val ACTION = "com.pawly.app.action.CARE_REMINDER"

    fun schedule(context: Context, dogName: String, careName: String, dateTime: String): Boolean {
        val triggerAt = runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                .parse(dateTime.replace("|", " "))?.time
        }.getOrNull() ?: return false
        if (triggerAt <= System.currentTimeMillis()) return false

        val requestCode = reminderId(dogName, careName)
        val intent = Intent(context, CareReminderReceiver::class.java).apply {
            action = ACTION
            putExtra("dogName", dogName)
            putExtra("careName", careName)
            putExtra("notificationId", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        return true
    }

    fun cancel(context: Context, dogName: String, careName: String) {
        val requestCode = reminderId(dogName, careName)
        val intent = Intent(context, CareReminderReceiver::class.java).apply { action = ACTION }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun reminderId(dogName: String, careName: String): Int =
        "pawly-care|$dogName|$careName".hashCode()
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
                    "pawly_care_reminders",
                    "Recordatorios de Pawly",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Avisos locales de cuidados que has programado" }
            )
        }
        val notification = NotificationCompat.Builder(context, "pawly_care_reminders")
            .setSmallIcon(R.drawable.ic_pawly)
            .setContentTitle("Un recordatorio para $dogName")
            .setContentText("Hoy toca: $careName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Habías programado este recordatorio para $dogName: $careName."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(notificationId, notification) }
    }
}
