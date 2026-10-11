package com.alpha.spendtracker.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AiResultIntent
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendRepository
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.components.splitLendBorrowNotes
import com.alpha.spendtracker.ui.viewmodel.SpendDraft
import com.alpha.spendtracker.worker.NotificationActionReceiver
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What happened to a spoken expense. [spoken] is a short sentence an assistant can read aloud. */
sealed interface TrackOutcome {
    val spoken: String

    data class Logged(val uuids: List<String>, override val spoken: String) : TrackOutcome
    data class NeedsReview(override val spoken: String) : TrackOutcome
    data class Failed(override val spoken: String) : TrackOutcome
}

/**
 * Logs an expense said to a voice assistant, with no screen: parse → save → "Logged" notification
 * with Undo. Doubtful parses are not saved; they become a "tap to confirm" notification that opens
 * the normal confirmation sheet (see [VoiceTrackPlanner]).
 *
 * Deliberately not `SpendViewModel` (its init/onCleared start and stop the singleton repository's
 * sync listeners) — same reason `QuickAddViewModel` exists.
 */
@Singleton
class VoiceTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: AiTransactionProcessor,
    private val aiPrefsRepository: AiPreferencesRepository,
    private val repository: SpendRepository,
) {
    companion object {
        private const val TAG = "VoiceTracker"
        const val CHANNEL_ID = "voice_logging"
        private const val CONFIRMATION_TIMEOUT_MS = 15_000L
    }

    suspend fun track(text: String): TrackOutcome {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return TrackOutcome.Failed(context.getString(R.string.voice_not_signed_in))

        val prefs = aiPrefsRepository.aiPreferencesFlow.first()
        val results = processor.parse(text, prefs).getOrElse {
            Log.w(TAG, "Could not parse spoken expense: ${it.message}")
            return TrackOutcome.Failed(context.getString(R.string.voice_save_failed))
        }

        return when (val plan = VoiceTrackPlanner.plan(results, prefs.defaultApp, prefs.defaultPurpose)) {
            is VoiceTrackPlanner.Plan.Review -> {
                notifyReview(results)
                TrackOutcome.NeedsReview(context.getString(plan.reason.messageRes()))
            }
            is VoiceTrackPlanner.Plan.Save -> save(user.uid, plan.drafts)
        }
    }

    private suspend fun save(userId: String, drafts: List<SpendDraft>): TrackOutcome {
        val saved = mutableListOf<SpendDraft>()
        drafts.forEach { d ->
            val now = System.currentTimeMillis()
            repository.insert(
                Spend(
                    uuid = d.uuid, userId = userId, appName = d.appName, amount = d.amount,
                    purpose = d.purpose, category = d.category, timestamp = d.timestamp,
                    notes = d.notes, updatedAt = now
                )
            ).onSuccess { saved += d }
                .onFailure { Log.e(TAG, "Could not save spoken expense: ${it.message}") }
        }
        if (saved.isEmpty()) return TrackOutcome.Failed(context.getString(R.string.voice_save_failed))

        val spoken = describe(saved)
        notifyLogged(userId, saved, spoken)
        return TrackOutcome.Logged(saved.map { it.uuid }, spoken)
    }

    private fun describe(drafts: List<SpendDraft>): String {
        if (drafts.size > 1) {
            return context.getString(
                R.string.voice_logged_many, drafts.size, formatCurrency(drafts.sumOf { it.amount })
            )
        }
        val d = drafts.single()
        val label = when (d.purpose) {
            "Lending", "Borrowing" -> splitLendBorrowNotes(d.notes).first
                .let { person -> if (person.isBlank()) d.purpose else "${d.purpose}, $person" }
            else -> d.notes.ifBlank { d.purpose }
        }
        return context.getString(R.string.voice_logged_one, formatCurrency(d.amount), label)
    }

    // ---- Notifications ----

    private fun notifyLogged(userId: String, drafts: List<SpendDraft>, spoken: String) {
        val undo = PendingIntent.getBroadcast(
            context,
            drafts.first().uuid.hashCode(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_UNDO_TRACKED
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, drafts.first().uuid.hashCode())
                putExtra(NotificationActionReceiver.EXTRA_TRACKED_USER_ID, userId)
                putExtra(NotificationActionReceiver.EXTRA_TRACKED_UUIDS, drafts.map { it.uuid }.toTypedArray())
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        post(
            id = drafts.first().uuid.hashCode(),
            builder = baseNotification()
                .setContentTitle(context.getString(R.string.voice_notif_logged_title))
                .setContentText(spoken)
                .setContentIntent(openApp(Intent(context, MainActivity::class.java), drafts.first().uuid.hashCode()))
                .addAction(0, context.getString(R.string.voice_notif_undo), undo)
                .setTimeoutAfter(CONFIRMATION_TIMEOUT_MS)
        )
    }

    private fun notifyReview(results: List<AiTransactionResponse>) {
        val id = System.currentTimeMillis().toInt()
        // The same hand-off the widget and share sheet use: MainActivity shows the confirmation
        // sheet (behind the app lock, if one is set).
        val open = AiResultIntent.put(Intent(context, MainActivity::class.java), results)
        post(
            id = id,
            builder = baseNotification()
                .setContentTitle(context.getString(R.string.voice_notif_review_title))
                .setContentText(context.getString(R.string.voice_notif_review_text))
                .setContentIntent(openApp(open, id))
        )
    }

    private fun baseNotification() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setAutoCancel(true)

    private fun openApp(intent: Intent, requestCode: Int): PendingIntent {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        // Without the permission the save still happened and the assistant still speaks the result.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.voice_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        manager.notify(id, builder.build())
    }

    private fun VoiceTrackPlanner.Reason.messageRes() = when (this) {
        VoiceTrackPlanner.Reason.NO_AMOUNT -> R.string.voice_review_no_amount
        VoiceTrackPlanner.Reason.NEEDS_PERSON -> R.string.voice_review_person
        VoiceTrackPlanner.Reason.UNSURE -> R.string.voice_review_unsure
    }
}
