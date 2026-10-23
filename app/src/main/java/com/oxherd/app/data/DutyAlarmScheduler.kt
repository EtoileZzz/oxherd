package com.oxherd.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Manages pre-shift alarm reminders using AlarmManager.
 */
class DutyAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Schedules a pre-shift reminder alarm for the given duty task.
     * Alarm triggers [task.remindAdvanceMinutes] minutes before [task.scheduledTime].
     *
     * @return true if alarm was successfully scheduled, false otherwise
     */
    fun schedulePreShiftAlarm(task: DutyTask): Boolean {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager service not available")
            return false
        }

        val triggerMillis = calculateReminderTimeMillis(task)
        val now = System.currentTimeMillis()

        if (triggerMillis <= now) {
            Log.w(TAG, "Calculated reminder time ($triggerMillis) is in the past, skipping schedule.")
            return false
        }

        val pendingIntent = createAlarmPendingIntent(task.id, task.title, task.scheduledTime)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
            Log.i(TAG, "Scheduled pre-shift reminder for task ${task.id} at $triggerMillis (${task.scheduledDate} ${task.scheduledTime})")
            return true
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while scheduling exact alarm, falling back to standard alarm", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                return true
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed fallback alarm scheduling", fallbackEx)
                return false
            }
        }
    }

    /**
     * Cancels any scheduled alarm for the given task ID.
     */
    fun cancelPreShiftAlarm(taskId: Long) {
        if (alarmManager == null) return

        val intent = Intent(context, DutyNotificationReceiver::class.java).apply {
            action = DutyNotificationReceiver.ACTION_DUTY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Cancelled pre-shift reminder for task $taskId")
        }
    }

    /**
     * Computes the exact timestamp in milliseconds when the reminder should fire.
     */
    fun calculateReminderTimeMillis(task: DutyTask): Long {
        return try {
            val date = LocalDate.parse(task.scheduledDate)
            val time = LocalTime.parse(task.scheduledTime)
            val dutyStart = LocalDateTime.of(date, time)
            val reminderTime = dutyStart.minusMinutes(task.remindAdvanceMinutes.toLong())
            reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing task date/time: ${task.scheduledDate} ${task.scheduledTime}", e)
            System.currentTimeMillis() - 1000 // In the past
        }
    }

    private fun createAlarmPendingIntent(taskId: Long, title: String, scheduledTime: String): PendingIntent {
        val intent = Intent(context, DutyNotificationReceiver::class.java).apply {
            action = DutyNotificationReceiver.ACTION_DUTY_REMINDER
            putExtra(DutyNotificationReceiver.EXTRA_TASK_ID, taskId)
            putExtra(DutyNotificationReceiver.EXTRA_TASK_TITLE, title)
            putExtra(DutyNotificationReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "DutyAlarmScheduler"
    }
}
