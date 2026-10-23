package com.oxherd.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver listening for system reboot and app update events
 * to automatically reschedule duty reminder alarms.
 */
class DutyBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.i(TAG, "Received broadcast action: $action, checking alarm restore settings...")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = OxherdDatabase.getInstance(context)
                val settings = db.getUserSettings()

                if (settings.autoRestoreOnBoot && settings.notifyEnabled) {
                    val activeTask = db.getActiveTask()
                    if (activeTask != null) {
                        val scheduler = DutyAlarmScheduler(context)
                        val scheduled = scheduler.schedulePreShiftAlarm(activeTask)
                        Log.i(TAG, "Restored pre-shift alarm for task ${activeTask.id}: success=$scheduled")
                    } else {
                        Log.i(TAG, "No active duty task found to restore.")
                    }
                } else {
                    Log.i(TAG, "Alarm auto-restore disabled in settings (autoRestoreOnBoot=${settings.autoRestoreOnBoot}, notifyEnabled=${settings.notifyEnabled})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore duty alarms upon boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DutyBootReceiver"
    }
}
