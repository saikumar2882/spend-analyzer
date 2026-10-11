package com.alpha.spendtracker.util

import android.content.Context
import android.net.Uri
import com.alpha.spendtracker.data.ReceiptOcrText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Reads the text off a shared receipt image with ML Kit's **on-device** recognizer: the image
 * never leaves the phone, and only the cleaned text (see `SharedPaymentText`) goes on to the AI.
 * Latin script covers the amount, date and app/merchant names on payment receipts.
 */
object ReceiptTextReader {

    /** The receipt's text, or null if the image can't be opened or holds no text. */
    suspend fun read(context: Context, uri: Uri): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(InputImage.fromFilePath(context, uri)).await()
            val lines = result.textBlocks
                .flatMap { it.lines }
                .map { ReceiptOcrText.Line(it.text, it.boundingBox?.height() ?: 0) }
            ReceiptOcrText.fromLines(lines).takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            recognizer.close()
        }
    }
}
