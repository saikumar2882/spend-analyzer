package com.alpha.spendtracker.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendRepository
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * BroadcastReceiver handling system notification actions ("Mark as read", "Mark as paid")
 * for recurring bills reminders.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationAction"
        const val ACTION_MARK_AS_READ = "com.alpha.spendtracker.ACTION_MARK_AS_READ"
        const val ACTION_MARK_AS_PAID = "com.alpha.spendtracker.ACTION_MARK_AS_PAID"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_BILL_USER_ID = "extra_bill_user_id"
        const val EXTRA_BILL_NAME = "extra_bill_name"
        const val EXTRA_BILL_APP = "extra_bill_app"
        const val EXTRA_BILL_PURPOSE = "extra_bill_purpose"
        const val EXTRA_BILL_NOTES = "extra_bill_notes"
        const val EXTRA_BILL_AMOUNT = "extra_bill_amount"

        private const val CONFIRMATION_TIMEOUT_MS = 4_000L

        // Serializes "Mark as paid" taps so a double tap can't pass the already-logged check twice.
        private val markAsPaidLock = Mutex()
    }

    // Receivers can't take constructor injection; pull the singleton repository from Hilt instead.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun spendRepository(): SpendRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_AS_READ -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notificationId != -1) {
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)
                }
            }
            ACTION_MARK_AS_PAID -> {
                val appContext = context.applicationContext
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        markAsPaid(appContext, intent)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    /**
     * Logs the bill as a Spend identical to one saved from the bill tracking sheet, then swaps the
     * reminder for a short silent confirmation. On failure the reminder stays up, so the user can
     * still fall back to "Track spend".
     */
    private suspend fun markAsPaid(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val userId = intent.getStringExtra(EXTRA_BILL_USER_ID).orEmpty()
        val billName = intent.getStringExtra(EXTRA_BILL_NAME).orEmpty()
        val appName = intent.getStringExtra(EXTRA_BILL_APP).orEmpty()
        val purpose = intent.getStringExtra(EXTRA_BILL_PURPOSE).orEmpty()
        val notes = intent.getStringExtra(EXTRA_BILL_NOTES).orEmpty()
        val amount = intent.getDoubleExtra(EXTRA_BILL_AMOUNT, 0.0)
        if (notificationId == -1 || amount <= 0 || userId.isBlank()) return

        // Room is shared across accounts on a device: never log a bill for someone who signed out.
        if (FirebaseAuth.getInstance().currentUser?.uid != userId) {
            Log.w(TAG, "Ignoring Mark as paid: bill owner is not the signed-in user")
            return
        }

        val repository = EntryPointAccessors
            .fromApplication(context, ReceiverEntryPoint::class.java)
            .spendRepository()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val logged = markAsPaidLock.withLock {
            // Same check RecurringBillWorker uses, so a logged bill also silences the other window.
            val existing = repository.findMatchingSpend(userId, appName, purpose, startOfDay, endOfDay)
                .getOrElse { error ->
                    Log.e(TAG, "Could not check bill $billName: ${error.message}")
                    return
                }
            if (existing != null) {
                Log.d(TAG, "Bill $billName already logged today. Not logging again.")
                true
            } else {
                // Mirrors the bill tracking sheet: category comes from the app preset, not the bill.
                val appPreset = APP_PRESETS.find { it.displayName == appName } ?: APP_PRESETS.last()
                val now = System.currentTimeMillis()
                val spend = Spend(
                    uuid = UUID.randomUUID().toString(),
                    userId = userId,
                    appName = appName,
                    amount = amount,
                    purpose = purpose,
                    category = appPreset.category,
                    timestamp = now,
                    notes = notes,
                    updatedAt = now
                )
                repository.insert(spend)
                    .onFailure { Log.e(TAG, "Could not log bill $billName: ${it.message}") }
                    .isSuccess
            }
        }
        if (!logged) return

        val confirmation = NotificationCompat.Builder(context, RecurringBillWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(billName)
            .setContentText(
                context.getString(
                    R.string.bill_logged_confirmation,
                    String.format(Locale.getDefault(), "%.2f", amount),
                    billName
                )
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Same id: replaces the reminder in place instead of stacking a second notification.
        notificationManager.notify(notificationId, confirmation)
    }
}
