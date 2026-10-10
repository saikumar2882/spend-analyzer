package com.alpha.spendtracker

import com.alpha.spendtracker.data.HistoryType
import com.alpha.spendtracker.data.Note
import com.alpha.spendtracker.data.NoteEntry
import com.alpha.spendtracker.data.NoteField
import com.alpha.spendtracker.data.NoteHistory
import com.alpha.spendtracker.data.RecurringBill
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.util.CloudEnvelope
import com.alpha.spendtracker.util.EncryptionUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end checks of the E2EE cloud format: every synced entity must survive
 * `seal -> (Firestore) -> open` unchanged, and the plaintext metadata that cloud-side queries rely
 * on must come from the row itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudEnvelopeTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val uid = "user-123"
    private val key = EncryptionUtils.deriveKey(uid)

    private inline fun <reified T : Any> roundTrip(item: T, docId: String): T {
        val adapter = moshi.adapter(T::class.java)
        val envelope = CloudEnvelope.seal(item, adapter, docId, uid, key)
        return CloudEnvelope.open(envelope[CloudEnvelope.PAYLOAD] as String, adapter, key)
    }

    @Test
    fun note_survives_round_trip() {
        val note = Note(uuid = "n1", userId = uid, title = "Groceries", colorIndex = 3, createdAt = 10, updatedAt = 20)
        assertEquals(note, roundTrip(note, "n1"))
    }

    @Test
    fun note_entry_with_custom_fields_survives_round_trip() {
        val entry = NoteEntry(
            uuid = "e1", userId = uid, noteUuid = "n1", label = "Rice", amount = 42.5,
            detail = "5 kg", date = 99, customFields = listOf(NoteField("brand", "India Gate")),
            createdAt = 1, updatedAt = 2
        )
        assertEquals(entry, roundTrip(entry, "e1"))
    }

    @Test
    fun recurring_bill_survives_round_trip_including_card_fields() {
        val bill = RecurringBill(
            uuid = "b1", userId = uid, name = "Netflix", purpose = "Subscriptions", category = "Entertainment",
            appName = "Google Pay", amount = 649.0, dayOfMonth = 12, notes = "family plan",
            isCreditCard = true, cardLast4 = "4242", untilDate = 1_900_000_000_000, updatedAt = 5
        )
        val back = roundTrip(bill, "b1")
        assertEquals(bill, back)
        assertTrue(back.isCreditCard)
        assertEquals("4242", back.cardLast4)
    }

    @Test
    fun spend_and_note_history_survive_round_trip() {
        val spend = Spend(uuid = "s1", userId = uid, appName = "Zepto", amount = 120.0, purpose = "Groceries", updatedAt = 7)
        assertEquals(spend, roundTrip(spend, "s1"))

        val history = NoteHistory(
            historyUuid = "h1", userId = uid, itemUuid = "n1", title = "Old title",
            historyType = HistoryType.DELETED, recordedAt = 50, updatedAt = 60
        )
        assertEquals(history, roundTrip(history, "h1"))
    }

    @Test
    fun envelope_exposes_row_metadata_not_upload_time() {
        val note = Note(uuid = "n1", userId = uid, title = "secret title", updatedAt = 1_000L, deleted = true)
        val envelope = CloudEnvelope.seal(note, moshi.adapter(Note::class.java), "n1", uid, key)

        // The worker's last-write-wins read and every cloud purge query use these.
        assertEquals(1_000L, envelope["updatedAt"])
        assertEquals(true, envelope["deleted"])
        assertEquals(true, envelope[CloudEnvelope.ENCRYPTED])
        // User content must never appear outside the ciphertext.
        assertFalse(envelope.values.any { it.toString().contains("secret title") })
        assertFalse(envelope.containsKey("title"))
    }

    @Test
    fun history_envelope_carries_recordedAt_and_type_for_ttl_purge() {
        val h = NoteHistory(historyUuid = "h1", userId = uid, historyType = HistoryType.UPDATED, recordedAt = 777L, updatedAt = 800L)
        val envelope = CloudEnvelope.seal(h, moshi.adapter(NoteHistory::class.java), "h1", uid, key)
        assertEquals(777L, envelope["recordedAt"])
        assertEquals(HistoryType.UPDATED, envelope["historyType"])
    }

    @Test
    fun wrong_key_fails_loudly_instead_of_returning_ciphertext() {
        val note = Note(uuid = "n1", userId = uid, title = "x", updatedAt = 1)
        val adapter = moshi.adapter(Note::class.java)
        val payload = CloudEnvelope.seal(note, adapter, "n1", uid, key)[CloudEnvelope.PAYLOAD] as String
        try {
            CloudEnvelope.open(payload, adapter, EncryptionUtils.deriveKey("someone-else"))
            fail("decrypting with another account's key must throw")
        } catch (_: Exception) {
            // expected: the sync listener catches this and skips the document
        }
    }

    @Test
    fun tampered_payload_is_rejected() {
        val note = Note(uuid = "n1", userId = uid, title = "x", updatedAt = 1)
        val adapter = moshi.adapter(Note::class.java)
        val payload = CloudEnvelope.seal(note, adapter, "n1", uid, key)[CloudEnvelope.PAYLOAD] as String
        val flipped = (if (payload[20] == 'A') 'B' else 'A').let { payload.replaceRange(20, 21, it.toString()) }
        assertNotEquals(payload, flipped)
        try {
            CloudEnvelope.open(flipped, adapter, key)
            fail("GCM must reject a modified payload")
        } catch (_: Exception) {
        }
    }

    @Test
    fun each_encryption_uses_a_fresh_iv() {
        val a = EncryptionUtils.encrypt("same text", key)
        val b = EncryptionUtils.encrypt("same text", key)
        assertNotEquals(a, b)
        assertEquals("same text", EncryptionUtils.decrypt(a, key))
        assertEquals("same text", EncryptionUtils.decrypt(b, key))
    }

    @Test
    fun flag_or_payload_alone_marks_a_document_sealed() {
        assertTrue(CloudEnvelope.isSealed(flag = true, hasPayload = false))
        assertTrue(CloudEnvelope.isSealed(flag = null, hasPayload = true))
        assertFalse(CloudEnvelope.isSealed(flag = null, hasPayload = false))
        assertFalse(CloudEnvelope.isSealed(flag = false, hasPayload = false))
    }
}
