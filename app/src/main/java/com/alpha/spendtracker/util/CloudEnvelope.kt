package com.alpha.spendtracker.util

import com.squareup.moshi.JsonAdapter
import javax.crypto.SecretKey

/**
 * The one definition of what an end-to-end-encrypted Firestore document looks like.
 *
 * It used to be built inline in both `SpendRepository` and `SyncWorker`. The two copies stamped the
 * envelope's `updatedAt` with the *upload time* instead of the row's own `updatedAt`, and hid
 * `deleted` / `recordedAt` / `timestamp` / `historyType` inside the ciphertext — which every cloud-side
 * query depends on (tombstone purge, history purge, chat TTL, the worker's last-write-wins read).
 *
 * The envelope therefore carries the encrypted row plus a small allow-list of plaintext metadata
 * that is copied from the row, never invented: timestamps and flags, no user content.
 */
object CloudEnvelope {

    /** Bump if the envelope layout ever changes, so a reader can tell formats apart. */
    const val VERSION = 1

    const val ENCRYPTED = "encrypted"
    const val PAYLOAD = "encryptedPayload"

    private val LONG_META = listOf("updatedAt", "recordedAt", "timestamp")

    /** Builds the Firestore map for [item]: encrypted payload + plaintext sync metadata. */
    fun <T : Any> seal(
        item: T,
        adapter: JsonAdapter<T>,
        docId: String,
        userId: String,
        key: SecretKey
    ): Map<String, Any> {
        val envelope = linkedMapOf<String, Any>(
            "uuid" to docId,
            "userId" to userId,
            ENCRYPTED to true,
            "v" to VERSION,
            PAYLOAD to EncryptionUtils.encrypt(adapter.toJson(item), key)
        )

        @Suppress("UNCHECKED_CAST")
        val fields = adapter.toJsonValue(item) as? Map<String, Any?> ?: return envelope
        LONG_META.forEach { name ->
            (fields[name] as? Number)?.let { envelope[name] = it.toLong() }
        }
        (fields["deleted"] as? Boolean)?.let { envelope["deleted"] = it }
        (fields["historyType"] as? String)?.let { envelope["historyType"] = it }
        (fields["purpose"] as? String)?.let { envelope["purpose"] = it }
        return envelope
    }

    /**
     * True when a document is an envelope rather than a plain row. Either marker is enough, so a
     * document that lost its flag but kept its payload is still decrypted instead of being read
     * field-by-field into an empty row.
     */
    fun isSealed(flag: Boolean?, hasPayload: Boolean): Boolean = flag == true || hasPayload

    /** Decrypts an envelope's payload into a row. Throws if it was tampered with or the key is wrong. */
    fun <T : Any> open(payload: String, adapter: JsonAdapter<T>, key: SecretKey): T =
        adapter.fromJson(EncryptionUtils.decrypt(payload, key))
            ?: throw java.io.IOException("Encrypted payload decoded to null")
}
