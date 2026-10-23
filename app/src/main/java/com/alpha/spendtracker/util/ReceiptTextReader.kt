package com.alpha.spendtracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.alpha.spendtracker.data.ReceiptOcrText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Reads the text off a shared receipt image or PDF with ML Kit's **on-device** recognizer: the file
 * never leaves the phone, and only the cleaned text (see `SharedPaymentText`) goes on to the AI.
 * Latin script covers the amount, date and app/merchant names on payment receipts.
 */
object ReceiptTextReader {

    /** The receipt's text, or null if the file can't be opened or holds no text. */
    suspend fun read(context: Context, uri: Uri): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val mimeType = context.contentResolver.getType(uri)
            val isPdf = (mimeType == "application/pdf") || uri.toString().endsWith(".pdf", ignoreCase = true)

            val inputImage = if (isPdf) {
                renderPdfToImage(context, uri) ?: return null
            } else {
                InputImage.fromFilePath(context, uri)
            }

            val result = recognizer.process(inputImage).await()
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

    private fun renderPdfToImage(context: Context, uri: Uri): InputImage? {
        return try {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val densityDpi = context.resources.displayMetrics.densityDpi
                        val width = (page.width * densityDpi / 72).coerceAtLeast(page.width * 2)
                        val height = (page.height * densityDpi / 72).coerceAtLeast(page.height * 2)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        InputImage.fromBitmap(bitmap, 0)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
