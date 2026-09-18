package com.obpartner.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 精准闹钟广播接收器
 * Exact Alarm Broadcast Receiver
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_TRIGGER_ALARM) {
            val title = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_TITLE) ?: "日程待办"
            val path = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_PATH) ?: return
            val time = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_TIME, System.currentTimeMillis())
            val relPath = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_VAULT_REL_PATH)

            val notificationHelper = NotificationHelper(context)
            notificationHelper.showEventNotification(title, path, time, relPath)
        }
    }
}
