package com.alpha.spendtracker.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpendSummaryUpdaterEntryPoint {
    fun database(): AppDatabase
    fun aiPreferencesRepository(): AiPreferencesRepository
}

/**
 * Keeps [SpendSummaryWidget] current without touching any write path.
 *
 * Instead of calling `updateAll` from every insert/edit/delete, it watches Room's
 * invalidation tracker for the `spends` table — so local edits, trash restores, the
 * Firestore listeners, SyncWorker and RecurringBillWorker are all covered by one hook.
 * Sign-in/out and a default-currency change also refresh it. Bursts (a snapshot applying
 * hundreds of rows) are debounced into a single update, and `updateAll` is a no-op when no
 * summary widget is placed. "Today" rolling over is handled by [SpendSummaryRefreshWorker].
 */
object SpendSummaryWidgetUpdater {
    private const val TAG = "SpendSummaryWidget"
    private const val MIDNIGHT_WORK = "SpendSummaryWidgetMidnight"
    private const val DEBOUNCE_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    @Volatile private var started = false

    /** Call once from `Application.onCreate`. */
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, SpendSummaryUpdaterEntryPoint::class.java)

        scope.launch {
            refreshRequests.debounce(DEBOUNCE_MS).collect { refreshNow(appContext) }
        }
        scope.launch {
            entryPoint.database().invalidationTracker
                .createFlow("spends", emitInitialState = false)
                .collect { requestRefresh() }
        }
        scope.launch {
            entryPoint.aiPreferencesRepository().aiPreferencesFlow
                .map { it.defaultCurrency }
                .distinctUntilChanged()
                .drop(1)
                .collect { requestRefresh() }
        }
        // Fires once on registration too, which doubles as a catch-up refresh at process start.
        // Guarded: FirebaseApp is absent under Robolectric, and app start must never crash on it.
        if (com.google.firebase.FirebaseApp.getApps(appContext).isNotEmpty()) {
            FirebaseAuth.getInstance().addAuthStateListener { requestRefresh() }
        }
    }

    /** Debounced refresh; safe to call from anywhere, any thread. */
    fun requestRefresh() {
        refreshRequests.tryEmit(Unit)
    }

    suspend fun refreshNow(context: Context) {
        try {
            SpendSummaryWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh failed", e)
        }
    }

    /**
     * Arms a one-shot refresh just after the next local midnight. KEEP leaves an already
     * pending run alone; the worker re-arms itself with APPEND_OR_REPLACE, because while it
     * is running KEEP would see its own entry and skip scheduling the next day.
     */
    fun scheduleMidnightRefresh(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val delay = (nextMidnight - System.currentTimeMillis()).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<SpendSummaryRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT_WORK, policy, request)
    }

    fun cancelMidnightRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MIDNIGHT_WORK)
    }
}

/** Rolls "today" over on the summary widget, then re-arms for the following midnight. */
class SpendSummaryRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SpendSummaryWidgetUpdater.refreshNow(applicationContext)
        SpendSummaryWidgetUpdater.scheduleMidnightRefresh(
            applicationContext,
            ExistingWorkPolicy.APPEND_OR_REPLACE
        )
        return Result.success()
    }
}
