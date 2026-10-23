package com.alpha.spendtracker.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alpha.spendtracker.util.NotificationHelper

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.showReminderNotification(context)
        SmartReminderWorker.schedule(context)
    }
}
