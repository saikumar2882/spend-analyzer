package com.alpha.spendtracker.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.components.formatCurrencyRounded
import com.alpha.spendtracker.ui.components.getLocalizedPresetName
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
        reportTitle: String = context.getString(R.string.pdf_report_title),
        filePrefix: String = "spend_report",
        monthlyBudget: Double? = null,
        includeSummaryCards: Boolean = true,
        share: Boolean = false,
        onShowNotification: (String, NotificationType) -> Unit
    ) {
        if (spends.isEmpty()) {
            onShowNotification(context.getString(R.string.no_transactions_to_export), NotificationType.INFO)
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
                color = Color.BLACK
            }
            val notesPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 8f
                color = Color.GRAY
            }

            // Paints for Financial Summary Header
            val cardBgPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            val cardLabelPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 7.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor("#64748B")
            }
            val cardValuePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor("#0F172A")
            }
            val cardSubtextPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 7.5f
                color = Color.parseColor("#64748B")
            }
            val progressBgPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.FILL
            }
            val progressFillPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#0F766E")
                style = Paint.Style.FILL
            }
            val categoryRankPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor("#0F172A")
            }
            val categoryAmtPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor("#0F172A")
            }

            val locale = activeAppLocale
            val sdf = SimpleDateFormat("dd MMM yy", locale)
            val generatedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date())
            val totalAmount = spends.sumOf { it.amount }
            val transactionCount = spends.size
            val avgPerTx = if (transactionCount > 0) totalAmount / transactionCount else 0.0

            // Top 3 spending categories
            val topCategories = spends
                .groupBy { spend ->
                    val rawName = spend.purpose.ifBlank { spend.category.ifBlank { "Others" } }
                    getLocalizedPresetName(rawName)
                }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .entries
                .sortedByDescending { it.value }
                .take(3)

            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            var y = margin

            // Draw Document Header
            fun drawDocumentHeader() {
                canvas.drawText(reportTitle, margin, y + 16f, titlePaint)
                y += 22f

                canvas.drawText(context.getString(R.string.generated_on, generatedDate), margin, y + 8f, subtitlePaint)
                y += 18f

                if (includeSummaryCards) {
                    // Clean Summary Cards Row
                    val contentWidth = pageWidth - 2 * margin // 523pt
                    val cardGap = 10f
                    val cardWidth = (contentWidth - 2 * cardGap) / 3f
                    val cardHeight = 50f

                    val formattedTotal = "₹${formatCurrency(totalAmount)}"
                    val formattedAvg = "₹${formatCurrencyRounded(avgPerTx)}"

                    // Card 1: Total Spent
                    val card1Rect = RectF(margin, y, margin + cardWidth, y + cardHeight)
                    canvas.drawRoundRect(card1Rect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(card1Rect, 6f, 6f, cardBorderPaint)

                    val labelTotalSpent = context.getString(R.string.pdf_total_spent)
                    val subTotalSpent = context.getString(R.string.pdf_avg_per_tx, formattedAvg)

                    canvas.drawText(labelTotalSpent, margin + 8f, y + 14f, cardLabelPaint)
                    canvas.drawText(formattedTotal, margin + 8f, y + 30f, cardValuePaint)
                    canvas.drawText(subTotalSpent, margin + 8f, y + 43f, cardSubtextPaint)

                    // Card 2: Transactions
                    val card2Left = margin + cardWidth + cardGap
                    val card2Rect = RectF(card2Left, y, card2Left + cardWidth, y + cardHeight)
                    canvas.drawRoundRect(card2Rect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(card2Rect, 6f, 6f, cardBorderPaint)

                    val labelTx = context.getString(R.string.pdf_transactions)
                    val valTx = "$transactionCount"
                    val subTx = context.getString(R.string.transactions_count, transactionCount)

                    canvas.drawText(labelTx, card2Left + 8f, y + 14f, cardLabelPaint)
                    canvas.drawText(valTx, card2Left + 8f, y + 30f, cardValuePaint)
                    canvas.drawText(subTx, card2Left + 8f, y + 43f, cardSubtextPaint)

                    // Card 3: Budget Progress
                    val card3Left = margin + 2 * (cardWidth + cardGap)
                    val card3Rect = RectF(card3Left, y, card3Left + cardWidth, y + cardHeight)
                    canvas.drawRoundRect(card3Rect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(card3Rect, 6f, 6f, cardBorderPaint)

                    val labelBudget = context.getString(R.string.pdf_budget_progress)
                    val (valBudget, subBudget) = if (monthlyBudget != null && monthlyBudget > 0.0) {
                        val pct = ((totalAmount / monthlyBudget) * 100).toInt().coerceAtMost(999)
                        val status = if (totalAmount <= monthlyBudget) context.getString(R.string.pdf_budget_on_track) else context.getString(R.string.pdf_budget_exceeded)
                        val sub = "₹${formatCurrencyRounded(totalAmount)} / ₹${formatCurrencyRounded(monthlyBudget)} ($pct%)"
                        Pair(status, sub)
                    } else {
                        val status = context.getString(R.string.pdf_budget_on_track)
                        val sub = context.getString(R.string.pdf_avg_per_tx, formattedAvg)
                        Pair(status, sub)
                    }

                    canvas.drawText(labelBudget, card3Left + 8f, y + 14f, cardLabelPaint)
                    canvas.drawText(valBudget, card3Left + 8f, y + 30f, cardValuePaint)
                    canvas.drawText(subBudget, card3Left + 8f, y + 43f, cardSubtextPaint)

                    y += cardHeight + 10f

                    // Top 3 Categories Section
                    if (topCategories.isNotEmpty()) {
                        val categoryCardHeight = 20f + topCategories.size * 13f
                        val catCardRect = RectF(margin, y, margin + contentWidth, y + categoryCardHeight)
                        canvas.drawRoundRect(catCardRect, 6f, 6f, cardBgPaint)
                        canvas.drawRoundRect(catCardRect, 6f, 6f, cardBorderPaint)

                        val catHeaderLabel = context.getString(R.string.pdf_top_categories)
                        canvas.drawText(catHeaderLabel, margin + 8f, y + 13f, cardLabelPaint)

                        var catY = y + 25f
                        topCategories.forEachIndexed { index, entry ->
                            val pct = if (totalAmount > 0) (entry.value / totalAmount * 100).toInt() else 0
                            val rankStr = "${index + 1}. ${entry.key.take(22)}"
                            val amtPctStr = "₹${formatCurrency(entry.value)} ($pct%)"

                            canvas.drawText(rankStr, margin + 8f, catY, categoryRankPaint)

                            val barTrackWidth = 65f
                            val barRight = margin + contentWidth - 8f
                            val barLeft = barRight - barTrackWidth
                            val amtWidth = categoryAmtPaint.measureText(amtPctStr)

                            canvas.drawText(amtPctStr, barLeft - amtWidth - 8f, catY, categoryAmtPaint)

                            val barY = catY - 5f
                            val trackRect = RectF(barLeft, barY, barRight, barY + 3.5f)
                            canvas.drawRoundRect(trackRect, 2f, 2f, progressBgPaint)

                            val fillWidth = (barTrackWidth * (pct.coerceIn(0, 100) / 100f)).coerceAtLeast(2f)
                            val fillRect = RectF(barLeft, barY, barLeft + fillWidth, barY + 3.5f)
                            canvas.drawRoundRect(fillRect, 2f, 2f, progressFillPaint)

                            catY += 13f
                        }

                        y += categoryCardHeight + 12f
                    } else {
                        y += 6f
                    }
                } else {
                    val formattedTotal = "₹${formatCurrency(totalAmount)}"
                    val summaryLine = "Total Amount: $formattedTotal  |  Transactions: $transactionCount"
                    canvas.drawText(summaryLine, margin, y + 8f, boldTextPaint)
                    y += 18f
                }

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

                canvas.drawText(context.getString(R.string.entry_date_label), colDate, y + 12f, headerPaint)
                canvas.drawText(context.getString(R.string.app_or_platform), colApp, y + 12f, headerPaint)
                canvas.drawText(context.getString(R.string.purpose_or_notes), colPurpose, y + 12f, headerPaint)

                val amtHeader = context.getString(R.string.amount)
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
                val appStr = getLocalizedPresetName(spend.appName).take(16)
                val purposeStr = getLocalizedPresetName(spend.purpose).take(28)
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
                val endText = context.getString(R.string.end_of_report)
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
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_pdf_chooser)))
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
                    onShowNotification(context.getString(R.string.pdf_saved_to_downloads), NotificationType.SUCCESS)
                } ?: run {
                    pdfDocument.close()
                    onShowNotification(context.getString(R.string.pdf_save_failed), NotificationType.ERROR)
                }
            }
        } catch (e: Exception) {
            onShowNotification(context.getString(R.string.pdf_export_failed, e.message ?: ""), NotificationType.ERROR)
        }
    }
}
