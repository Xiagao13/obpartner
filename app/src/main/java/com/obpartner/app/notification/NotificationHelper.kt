package com.obpartner.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.obpartner.app.R
import com.obpartner.app.data.StorageManager
import com.obpartner.app.model.CalendarEvent

/**
 * 系统通知与精准闹钟调度辅助类
 * System Notification and Exact Alarm Scheduling Helper
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_EVENTS_ID = "obpartner_calendar_events"
        const val CHANNEL_EVENTS_NAME = "日程提醒 (Calendar Events)"
        const val ACTION_TRIGGER_ALARM = "com.obpartner.app.ACTION_TRIGGER_EVENT_ALARM"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_PATH = "extra_event_path"
        const val EXTRA_EVENT_TIME = "extra_event_time"
        const val EXTRA_EVENT_VAULT_REL_PATH = "extra_event_vault_rel_path"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_EVENTS_ID,
                CHANNEL_EVENTS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于重要日程与待办的系统级精准提醒"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 为日程设定系统级精准闹钟提醒
     * Schedule an exact alarm for a calendar event
     */
    fun scheduleEventAlarm(event: CalendarEvent) {
        // 若时间已过，无需安排闹钟 / Don't schedule if time is in the past
        if (event.start <= System.currentTimeMillis()) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra(EXTRA_EVENT_TITLE, event.title)
            putExtra(EXTRA_EVENT_PATH, event.path)
            putExtra(EXTRA_EVENT_TIME, event.start)
            putExtra(EXTRA_EVENT_VAULT_REL_PATH, event.vaultRelativePath)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.path.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    event.start,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    event.start,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Android 12+ 若缺少精准闹钟权限，做降级处理
            alarmManager.set(AlarmManager.RTC_WAKEUP, event.start, pendingIntent)
        }
    }

    /**
     * 弹出日程到达的系统通知（附带点击直接打开 Obsidian 的快捷动作）
     * Show notification with action to open directly in Obsidian
     */
    fun showEventNotification(
        title: String,
        filePath: String,
        timeMillis: Long,
        vaultRelativePath: String? = null
    ) {
        val storageManager = StorageManager(context)
        val openIntent = storageManager.createOpenObsidianIntent(filePath, vaultRelativePath)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            filePath.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("日程开始: $title")
            .setContentText("点击将在 Obsidian 中直接打开该笔记")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "打开 Obsidian",
                contentPendingIntent
            )
            .build()

        notificationManager.notify(filePath.hashCode(), notification)
    }
}
