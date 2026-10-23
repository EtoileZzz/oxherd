package com.oxherd.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Utility helper for Keep-Alive, Doze mode battery optimization white-listing,
 * manufacturer autostart management navigation, and instant test notification dispatch.
 */
object KeepAliveHelper {

    private const val TEST_CHANNEL_ID = "oxherd_duty_test_channel"
    private const val TEST_NOTIFICATION_ID = 9999

    /**
     * Checks if the app is currently whitelisted from system battery optimizations (Doze mode).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }

    /**
     * Prompts the user to add Oxherd to battery optimization exclusions.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (fallbackEx: Exception) {
                    Toast.makeText(context, "无法直接打开电池优化设置，请在系统设置中手动开启", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Tries to navigate to manufacturer-specific autostart / background management screens
     * (Xiaomi/MIUI, Huawei/HarmonyOS, OPPO/ColorOS, Vivo/OriginOS, Samsung, Meizu).
     * Falls back to Android Application Details settings.
     *
     * @return true if an autostart or app settings intent was successfully launched.
     */
    fun openManufacturerAutoStartSettings(context: Context): Boolean {
        val intents = listOf(
            // Xiaomi / MIUI / HyperOS
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            },

            // Huawei / HarmonyOS / EMUI
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
                )
            },

            // OPPO / ColorOS
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            },

            // vivo / iQOO / OriginOS
            Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            },
            Intent().apply {
                component = ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            },

            // Meizu
            Intent().apply {
                component = ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.permission.SmartBGActivity"
                )
            },

            // Samsung
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
        )

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // Try next manufacturer intent
            }
        }

        // Fallback: Open standard App Info page
        return try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开应用设置页", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Dispatches an immediate test notification reflecting the configured sound and vibration options.
     */
    fun sendTestNotification(context: Context, userSettings: UserSettings) {
        if (!userSettings.notifyEnabled) {
            Toast.makeText(context, "提醒总开关处于关闭状态，请先开启通知", Toast.LENGTH_SHORT).show()
            return
        }

        createTestNotificationChannel(context, userSettings)

        val launchIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: Intent()

        val pendingIntent = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = context.applicationInfo?.icon?.takeIf { it != 0 }
            ?: android.R.drawable.ic_dialog_info

        var defaults = 0
        if (userSettings.notifySound) defaults = defaults or NotificationCompat.DEFAULT_SOUND
        if (userSettings.notifyVibrate) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE

        val soundStatus = if (userSettings.notifySound) "开启" else "静音"
        val vibrateStatus = if (userSettings.notifyVibrate) "开启" else "关闭"
        val bootStatus = if (userSettings.autoRestoreOnBoot) "开启" else "关闭"

        val builder = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("🔔 Oxherd 巡检通知与震动测试")
            .setContentText("通知配置工作正常！提前预警: ${userSettings.notifyAdvanceMinutes} 分钟，声音: $soundStatus，震动: $vibrateStatus")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🔔 巡检唤醒测试成功：\n• 通知提醒：已启用\n• 提前预警：${userSettings.notifyAdvanceMinutes} 分钟\n• 提示音效：$soundStatus\n• 触感强震：$vibrateStatus\n• 开机自启恢复：$bootStatus\n点击此通知可直接秒开巡检工作台。")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (defaults != 0) {
            builder.setDefaults(defaults)
        }
        if (userSettings.notifyVibrate) {
            builder.setVibrate(longArrayOf(0, 300, 150, 300))
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(TEST_NOTIFICATION_ID, builder.build())
            Toast.makeText(context, "测试通知已发送，请下拉通知栏查看", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "通知权限未授予，请在系统设置中允许通知", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "发送通知失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createTestNotificationChannel(context: Context, userSettings: UserSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Oxherd 巡检测试通知"
            val descriptionText = "用于测试巡检到岗通知与触感震动反馈"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(TEST_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(userSettings.notifyVibrate)
                if (userSettings.notifyVibrate) {
                    vibrationPattern = longArrayOf(0, 300, 150, 300)
                } else {
                    vibrationPattern = longArrayOf(0)
                }
                if (!userSettings.notifySound) {
                    setSound(null, null)
                } else {
                    val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    setSound(defaultSoundUri, audioAttributes)
                }
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
