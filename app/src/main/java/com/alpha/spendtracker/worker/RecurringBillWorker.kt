package com.alpha.spendtracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.SpendRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@HiltWorker
class RecurringBillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SpendRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RecurringBillWorker"
        private const val CHANNEL_ID = "bill_reminders"
        private const val CHANNEL_NAME = "Bill Reminders"
        
        /** 
         * Set to true to trigger notifications every minute for testing.
         * Bypasses time-of-day checks and notification flags.
         */
        private const val TEST_MODE = true
    }

    override suspend fun doWork(): Result {
        val calendar = Calendar.getInstance()
        val todayDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        Log.d(TAG, "Checking for due bills on $todayStr (Day: $todayDay, Time: $currentHour:$currentMinute, TestMode: $TEST_MODE)")

        val dueBills = repository.getBillsDueOn(todayDay)
        if (dueBills.isEmpty()) return Result.success()

        for (bill in dueBills) {
            if (TEST_MODE) {
                showNotification(bill)
                continue
            }

            // 1. Reset flags if it's a new day
            var updatedBill = if (bill.lastNotifiedDate != todayStr) {
                bill.copy(lastNotifiedDate = todayStr, notifiedAt1230 = false, notifiedAt2200 = false)
            } else {
                bill
            }

            // 2. Check if already tracked for today
            val startOfDay = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val endOfDay = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val matchingSpend = repository.findMatchingSpend(
                bill.userId,
                bill.appName,
                bill.purpose,
                startOfDay,
                endOfDay
            )

            if (matchingSpend != null) {
                Log.d(TAG, "Bill ${bill.name} already tracked for today. Skipping notification.")
                if (updatedBill != bill) {
                    repository.updateRecurringBill(updatedBill)
                }
                continue
            }

            // 3. Check trigger windows
            // Window 1: 12:30 PM (12:30 - 13:00)
            if (!updatedBill.notifiedAt1230 && (currentHour > 12 || (currentHour == 12 && currentMinute >= 30))) {
                showNotification(updatedBill)
                updatedBill = updatedBill.copy(notifiedAt1230 = true)
            }
            // Window 2: 10:00 PM (22:00 onwards)
            else if (!updatedBill.notifiedAt2200 && currentHour >= 22) {
                showNotification(updatedBill)
                updatedBill = updatedBill.copy(notifiedAt2200 = true)
            }

            if (updatedBill != bill) {
                repository.updateRecurringBill(updatedBill)
            }
        }

        return Result.success()
    }

    private fun showNotification(bill: RecurringBill) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("BILL_UUID", bill.uuid)
            putExtra("BILL_NAME", bill.name)
            putExtra("BILL_APP", bill.appName)
            putExtra("BILL_PURPOSE", bill.purpose)
            putExtra("BILL_CATEGORY", bill.category)
            putExtra("BILL_NOTES", bill.notes)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            bill.uuid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentTitle("Bill Reminder: ${bill.name}")
            .setContentText("Your recurring bill is due today. Tap to track it.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(bill.uuid.hashCode(), notification)
    }
}
