package com.alpha.spendtracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.RecapPreferences
import com.alpha.spendtracker.data.SpendRecap
import com.alpha.spendtracker.data.SpendRepository
import com.alpha.spendtracker.ui.components.formatCurrencyRounded
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Daily ~7 PM check that posts the quiet Sunday recap and, early in a new month, the
 * "Wrapped is ready" nudge. Everything is computed on-device by [SpendRecap]; no network.
 *
 * One daily worker covers both instead of a weekly + a monthly one: month ends don't line up with
 * Sundays, and a daily tick self-heals when Doze pushes a run late. [SpendRecap.dueWeek] /
 * [SpendRecap.dueWrappedMonth] decide whether anything is due, and [RecapPreferences] makes each
 * notification fire at most once.
 */
@HiltWorker
class RecapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SpendRepository,
    private val aiPreferencesRepository: AiPreferencesRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RecapWorker"
        private const val WORK_NAME = "SpendRecapWork"
        private const val CHANNEL_ID = "spend_recaps"
        private const val WEEKLY_NOTIFICATION_ID = 0x5EC0
        private const val WRAPPED_NOTIFICATION_ID = 0x5EC1

        /** Intent extra (a `yyyy-MM` month key) that opens the Wrapped sheet for that month. */
        const val EXTRA_WRAPPED_MONTH = "SHOW_WRAPPED_MONTH"

        /**
         * Daily periodic work, first run at the next 7 PM. KEEP, so re-scheduling on every app
         * start doesn't reset the phase.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecapWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayToNextRecapHour(System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun delayToNextRecapHour(now: Long): Long {
            val next = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, SpendRecap.WEEKLY_RECAP_HOUR)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
            }
            return next.timeInMillis - now
        }
    }

    private val recapPreferences = RecapPreferences(applicationContext)

    override suspend fun doWork(): Result {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        // Nothing to show if the user has turned notifications off (or denied POST_NOTIFICATIONS).
        // Don't record anything either, so a recap still inside its window goes out once they're on.
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val week = SpendRecap.dueWeek(now, tz)
        val wrappedMonth = SpendRecap.dueWrappedMonth(now, tz)
        if (week == null && wrappedMonth == null) return Result.success()

        val spends = try {
            repository.getAllSpends(userId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not read spends: ${e.message}")
            return Result.retry()
        }
        val currency = SpendRecap.currencySymbol(aiPreferencesRepository.aiPreferencesFlow.first().defaultCurrency)

        if (week != null) {
            val key = "$userId:${SpendRecap.dayKey(week.start, tz)}"
            if (recapPreferences.lastWeeklyRecap() != key) {
                val recap = SpendRecap.weekly(spends, week, tz)
                // An empty week gets no notification — and no record, so a spend logged later
                // in the grace window still earns this week's recap.
                if (recap != null) {
                    showWeeklyRecap(recap, currency)
                    recapPreferences.setLastWeeklyRecap(key)
                }
            }
        }

        if (wrappedMonth != null) {
            val (year, month) = wrappedMonth
            val key = "$userId:${SpendRecap.monthKey(year, month)}"
            if (recapPreferences.lastMonthlyWrapped() != key) {
                val wrapped = SpendRecap.monthly(spends, year, month, now, tz)
                if (!wrapped.isEmpty) {
                    showWrappedReady(year, month)
                    recapPreferences.setLastMonthlyWrapped(key)
                }
            }
        }

        return Result.success()
    }

    private fun ensureChannel(): NotificationManager {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // LOW: shows in the shade without sound or heads-up — a recap is never urgent.
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.recap_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.recap_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
        return notificationManager
    }

    private fun contentIntent(requestCode: Int, wrappedMonthKey: String?): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (wrappedMonthKey != null) putExtra(EXTRA_WRAPPED_MONTH, wrappedMonthKey)
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showWeeklyRecap(recap: SpendRecap.WeeklyRecap, currency: String) {
        val ctx = applicationContext
        val money = { amount: Double -> "$currency${formatCurrencyRounded(amount)}" }
        val total = money(recap.total)

        val change = recap.changePct
        val headline = if (change == null) {
            ctx.getString(R.string.recap_weekly_no_previous, total)
        } else {
            val pct = abs(change).roundToInt()
            when {
                pct == 0 -> ctx.getString(R.string.recap_weekly_same, total)
                change < 0 -> ctx.getString(R.string.recap_weekly_less, total, pct)
                else -> ctx.getString(R.string.recap_weekly_more, total, pct)
            }
        }
        val biggestName = recap.biggest.appName.ifBlank { recap.biggest.purpose }
        val details = buildList {
            add(ctx.getString(R.string.recap_weekly_biggest, biggestName, money(recap.biggest.amount)))
            recap.topItem?.let { add(ctx.getString(R.string.recap_weekly_most_on, it.label, money(it.total))) }
        }.joinToString(" · ")
        val body = "$headline $details"

        val locale = Locale.getDefault()
        val dayFormat = SimpleDateFormat("d MMM", locale)
        val range = "${dayFormat.format(recap.week.start)} – ${dayFormat.format(recap.week.endExclusive - 1)}"

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ctx.getString(R.string.recap_weekly_title, range))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(WEEKLY_NOTIFICATION_ID, null))
            .build()

        ensureChannel().notify(WEEKLY_NOTIFICATION_ID, notification)
    }

    private fun showWrappedReady(year: Int, month: Int) {
        val ctx = applicationContext
        val monthName = SimpleDateFormat("LLLL", Locale.getDefault())
            .format(SpendRecap.monthPeriod(year, month).start)

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ctx.getString(R.string.wrapped_notification_title, monthName))
            .setContentText(ctx.getString(R.string.wrapped_notification_text, monthName))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(WRAPPED_NOTIFICATION_ID, SpendRecap.monthKey(year, month)))
            .build()

        ensureChannel().notify(WRAPPED_NOTIFICATION_ID, notification)
    }
}
