/**
 * The data layer's error vocabulary.
 */
package com.alpha.spendtracker.data

import android.database.sqlite.SQLiteException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/**
 * The only failure type that leaves [SpendRepository]. Room's `SQLiteException`, Firestore's
 * `FirebaseFirestoreException` and friends are mapped here at the repository boundary so nothing
 * above the data layer has to know which library produced a failure.
 *
 * [message] and [cause] are diagnostics for Logcat/Crashlytics. Show [userMessage] to people.
 */
sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No usable connection: offline, DNS failure, timeout. Retrying later is likely to work. */
    class Network(cause: Throwable) : DataError("Network unavailable", cause)

    /** Reached the backend and it refused or failed. [code] is Firestore's status name when known. */
    class Server(val code: String?, detail: String?, cause: Throwable? = null) :
        DataError("Server error${code?.let { " ($it)" } ?: ""}: ${detail ?: "no detail"}", cause)

    /** The caller isn't allowed to touch this data — signed out, or rules rejected the write. */
    class Unauthorized(cause: Throwable) : DataError("Not authorized", cause)

    /** The on-device database failed. This is the one that means the user's action was lost. */
    class Local(cause: Throwable) : DataError("Local storage error", cause)

    class Unknown(cause: Throwable) : DataError("Unexpected error", cause)

    /** One short sentence, safe to put in front of a user. */
    val userMessage: String
        get() = when (this) {
            is Network -> "You're offline. Your changes are saved on this device and will sync when you reconnect."
            is Server -> "The server couldn't complete that. Please try again in a moment."
            is Unauthorized -> "You don't have permission to do that. Try signing in again."
            is Local -> "Couldn't save to this device. Please try again."
            is Unknown -> "Something went wrong. Please try again."
        }
}

/**
 * Maps a caught platform exception to [DataError].
 *
 * Callers must rethrow `CancellationException` *before* calling this — cancellation is control
 * flow, not a failure, and turning it into a `DataError` would report an error for work the caller
 * itself called off.
 */
fun Throwable.toDataError(): DataError = when (this) {
    is DataError -> this
    is SQLiteException -> DataError.Local(this)
    is FirebaseNetworkException -> DataError.Network(this)
    is IOException -> DataError.Network(this)
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.ABORTED -> DataError.Network(this)

        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> DataError.Unauthorized(this)

        else -> DataError.Server(code.name, message, this)
    }
    else -> DataError.Unknown(this)
}

/**
 * The user-facing sentence for any failure coming out of the data layer.
 *
 * Defensive fallback for the non-[DataError] case: the repository maps everything at its boundary,
 * but a raw platform message ("SQLITE_FULL: database or disk is full") must never reach a person
 * even if some future path forgets to.
 */
fun Throwable.userMessageOrGeneric(): String =
    (this as? DataError)?.userMessage ?: "Something went wrong. Please try again."

/**
 * Health of the background push to Firestore.
 *
 * Kept separate from the `Result` a mutation returns, because the two mean different things in an
 * offline-first app: a failed *local* write means the user's action was lost and is a `Result`
 * failure, whereas a failed *cloud* write means the record is safe on this device but not yet
 * mirrored. Reporting the latter as a failed save would be a lie, and staying silent about it —
 * which is what the repository used to do — hides indefinite sync outages.
 */
data class SyncStatus(
    val cloudError: DataError? = null,
    /** When the cloud first started failing, so the UI can say how long it has been behind. */
    val degradedSince: Long? = null
) {
    val isDegraded: Boolean get() = cloudError != null
}
