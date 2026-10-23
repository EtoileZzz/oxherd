package com.oxherd.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * BroadcastReceiver triggered by AlarmManager to post high-priority pre-shift reminders.
 */
class DutyNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "巡检值班"
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: "20:00"

        createNotificationChannel(context)

        // PendingIntent targeting MainActivity or App Launcher
        val launchIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, taskId)
            } ?: Intent().apply {
                setClassName(context.packageName, "com.oxherd.app.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TASK_ID, taskId)
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt().coerceAtLeast(1),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = context.applicationInfo?.icon?.takeIf { it != 0 }
            ?: android.R.drawable.ic_dialog_info

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("巡检值班提醒 ($scheduledTime)")
            .setContentText("$taskTitle 即将开始，请按时就位开展巡查。")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$taskTitle 将于 $scheduledTime 准时开始，请提前前往各巡查点打卡就位并核验安全设备。")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = if (taskId > 0) taskId.toInt() else 1001
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission might not be granted yet
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Oxherd 巡检值班提醒"
            val descriptionText = "值班任务开始前的提前预警与到岗提醒"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "oxherd_duty_alerts"
        const val ACTION_DUTY_REMINDER = "com.oxherd.app.ACTION_DUTY_REMINDER"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    }
}
