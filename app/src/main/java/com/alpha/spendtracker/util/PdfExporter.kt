package com.alpha.spendtracker.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.formatCurrency
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

object PdfExporter {

    /**
     * Generates a clean, minimal A4 PDF report (ISO A4: 595 x 842 points)
     * containing transaction logs and summary details.
     */
    fun exportToPdf(
        context: Context,
        spends: List<Spend>,
        reportTitle: String = "Transaction History Report",
        filePrefix: String = "spend_report",
        share: Boolean = false,
        onShowNotification: (String, NotificationType) -> Unit
    ) {
        if (spends.isEmpty()) {
            onShowNotification("No transactions to export", NotificationType.INFO)
            return
        }

        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // ISO A4 width in PDF points
            val pageHeight = 842 // ISO A4 height in PDF points
            val margin = 36f // 0.5 inch margins

            val paint = Paint().apply { isAntiAlias = true }
            val titlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.BLACK
            }
            val subtitlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 9f
                color = Color.DKGRAY
            }
            val headerPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.BLACK
            }
            val textPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 9f
                color = Color.BLACK
            }
            val boldTextPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.BLACK
            }
            val notesPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 8f
                color = Color.GRAY
            }

            val locale = context.resources.configuration.locales[0]
            val sdf = SimpleDateFormat("dd MMM yy", locale)
            val generatedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date())
            val totalAmount = spends.sumOf { it.amount }

            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var y = margin

            // Draw Minimal Document Header
            fun drawDocumentHeader() {
                canvas.drawText(reportTitle, margin, y + 16f, titlePaint)
                y += 22f

                canvas.drawText("Generated on $generatedDate", margin, y + 8f, subtitlePaint)
                y += 20f

                // Clean Summary Row (No heavy background fill boxes or heavy borders)
                canvas.drawText("TOTAL AMOUNT", margin, y + 10f, notesPaint)
                val formattedTotal = "₹${formatCurrency(totalAmount)}"
                canvas.drawText(formattedTotal, margin, y + 28f, titlePaint)

                val txCountText = "${spends.size} Transactions"
                val txWidth = boldTextPaint.measureText(txCountText)
                canvas.drawText(txCountText, pageWidth - margin - txWidth, y + 24f, boldTextPaint)

                y += 38f

                paint.color = Color.parseColor("#E0E0E0")
                paint.strokeWidth = 0.8f
                canvas.drawLine(margin, y, pageWidth - margin, y, paint)
                y += 12f
            }

            // Table Header
            fun drawTableHeader() {
                val colDate = margin + 4f
                val colApp = margin + 80f
                val colPurpose = margin + 180f
                val colAmount = pageWidth - margin - 4f

                canvas.drawText("Date", colDate, y + 12f, headerPaint)
                canvas.drawText("App / Platform", colApp, y + 12f, headerPaint)
                canvas.drawText("Purpose / Notes", colPurpose, y + 12f, headerPaint)

                val amtHeader = "Amount"
                canvas.drawText(amtHeader, colAmount - headerPaint.measureText(amtHeader), y + 12f, headerPaint)

                y += 18f
                paint.color = Color.BLACK
                paint.strokeWidth = 1f
                canvas.drawLine(margin, y, pageWidth - margin, y, paint)
                y += 4f
            }

            drawDocumentHeader()
            drawTableHeader()

            val colDate = margin + 4f
            val colApp = margin + 80f
            val colPurpose = margin + 180f
            val colAmount = pageWidth - margin - 4f

            spends.forEach { spend ->
                val hasNotes = spend.notes.isNotBlank()
                val itemHeight = if (hasNotes) 26f else 18f

                // Page overflow check
                if (y + itemHeight > pageHeight - margin - 24f) {
                    canvas.drawText(
                        "Page $currentPageNumber",
                        (pageWidth / 2 - subtitlePaint.measureText("Page $currentPageNumber") / 2),
                        pageHeight - 16f,
                        subtitlePaint
                    )
                    pdfDocument.finishPage(page)

                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin

                    drawTableHeader()
                }

                val dateStr = sdf.format(Date(spend.timestamp))
                val appStr = spend.appName.take(16)
                val purposeStr = spend.purpose.take(28)
                val amtStr = "₹${formatCurrency(spend.amount)}"

                canvas.drawText(dateStr, colDate, y + 12f, textPaint)
                canvas.drawText(appStr, colApp, y + 12f, textPaint)
                canvas.drawText(purposeStr, colPurpose, y + 12f, boldTextPaint)

                if (hasNotes) {
                    canvas.drawText(spend.notes.take(40), colPurpose, y + 21f, notesPaint)
                }

                canvas.drawText(amtStr, colAmount - boldTextPaint.measureText(amtStr), y + 12f, boldTextPaint)

                y += itemHeight

                // Thin divider line
                paint.color = Color.parseColor("#E5E7EB")
                paint.strokeWidth = 0.5f
                canvas.drawLine(margin, y, pageWidth - margin, y, paint)
            }

            // End of Report footer
            y += 16f
            if (y + 16f <= pageHeight - margin) {
                val endText = "* End of Report *"
                canvas.drawText(
                    endText,
                    (pageWidth / 2 - subtitlePaint.measureText(endText) / 2),
                    y,
                    subtitlePaint
                )
            }

            canvas.drawText(
                "Page $currentPageNumber",
                (pageWidth / 2 - subtitlePaint.measureText("Page $currentPageNumber") / 2),
                pageHeight - 16f,
                subtitlePaint
            )
            pdfDocument.finishPage(page)

            // Save PDF
            val fileName = "${filePrefix}_${System.currentTimeMillis()}.pdf"

            if (share) {
                val cacheFile = File(context.cacheDir, fileName)
                FileOutputStream(cacheFile).use { pdfDocument.writeTo(it) }
                pdfDocument.close()

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, reportTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share PDF Report"))
            } else {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val uri = resolver.insert(collection, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os -> pdfDocument.writeTo(os) }
                    pdfDocument.close()
                    onShowNotification("PDF Report saved to Downloads", NotificationType.SUCCESS)
                } ?: run {
                    pdfDocument.close()
                    onShowNotification("Failed to save PDF", NotificationType.ERROR)
                }
            }
        } catch (e: Exception) {
            onShowNotification("Failed to export PDF: ${e.message}", NotificationType.ERROR)
        }
    }
}
