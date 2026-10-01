package com.alpha.spendtracker.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendRecap
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.asMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Monthly "Wrapped": a recap of one month, opened from the Wrapped notification or Settings.
 * The card is drawn into a graphics layer so "Share" can export exactly what is on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrappedSheet(
    spends: List<Spend>,
    initialYear: Int,
    initialMonth: Int,
    currency: String,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    var year by rememberSaveable { mutableStateOf(initialYear) }
    var month by rememberSaveable { mutableStateOf(initialMonth) }

    val now = System.currentTimeMillis()
    val nowCal = remember { Calendar.getInstance() }
    val isCurrentMonth = year == nowCal.get(Calendar.YEAR) && month == nowCal.get(Calendar.MONTH)
    val wrapped = remember(spends, year, month) { SpendRecap.monthly(spends, year, month, now) }

    val locale = LocalConfiguration.current.locales[0]
    val monthLabel = remember(year, month, locale) {
        SimpleDateFormat("LLLL yyyy", locale).format(wrapped.period.start)
    }
    val symbol = SpendRecap.currencySymbol(currency)
    val money = { amount: Double -> "$symbol${formatCurrencyRounded(amount)}" }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardLayer = rememberGraphicsLayer()
    val shareChooser = stringResource(R.string.wrapped_share_chooser)
    val shareText = stringResource(R.string.wrapped_share_text, monthLabel)

    fun shiftMonth(delta: Int) {
        val cal = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, delta) }
        year = cal.get(Calendar.YEAR)
        month = cal.get(Calendar.MONTH)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { shiftMonth(-1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.wrapped_previous_month))
                }
                Text(
                    text = stringResource(R.string.wrapped_title, monthLabel),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { shiftMonth(1) }, enabled = !isCurrentMonth) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.wrapped_next_month))
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))

            // Recorded into cardLayer (on an opaque background, since the card itself is
            // translucent) and drawn back unchanged, so the shared image matches the screen.
            Box(
                modifier = Modifier
                    .drawWithContent {
                        cardLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(cardLayer)
                    }
                    .background(sheetColor)
            ) {
                WrappedCard(wrapped = wrapped, monthLabel = monthLabel, money = money)
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = {
                    scope.launch {
                        val bitmap = cardLayer.toImageBitmap().asAndroidBitmap()
                        shareWrappedImage(context, bitmap, shareText, shareChooser)
                    }
                },
                enabled = !wrapped.isEmpty,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(stringResource(R.string.wrapped_share), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WrappedCard(
    wrapped: SpendRecap.MonthlyWrapped,
    monthLabel: String,
    money: (Double) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = stringResource(
                    if (wrapped.inProgress) R.string.wrapped_spent_so_far else R.string.wrapped_total_spent,
                    monthLabel
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = money(wrapped.total),
                style = MaterialTheme.typography.displaySmall.asMoney(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            val change = wrapped.changePct
            Text(
                text = when {
                    wrapped.isEmpty -> stringResource(R.string.wrapped_empty)
                    change == null -> pluralStringResource(R.plurals.wrapped_transactions, wrapped.count, wrapped.count)
                    abs(change) < 0.5 -> stringResource(R.string.wrapped_change_same)
                    change < 0 -> stringResource(R.string.wrapped_change_less, abs(change).roundToInt())
                    else -> stringResource(R.string.wrapped_change_more, change.roundToInt())
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = when {
                    change == null || wrapped.isEmpty -> MaterialTheme.colorScheme.onSurfaceVariant
                    change <= 0 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )

            if (!wrapped.isEmpty) {
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(Spacing.xs))

                wrapped.topCategory?.let { (purpose, total) ->
                    WrappedStatRow(
                        icon = Icons.Rounded.Category,
                        label = stringResource(R.string.wrapped_top_category),
                        value = getLocalizedPresetName(purpose),
                        detail = money(total)
                    )
                }
                wrapped.topApp?.let { app ->
                    WrappedStatRow(
                        icon = Icons.Rounded.Payments,
                        label = stringResource(R.string.wrapped_top_app),
                        value = app.appName,
                        detail = pluralStringResource(R.plurals.wrapped_times, app.count, app.count)
                    )
                }
                wrapped.biggest?.let { spend ->
                    WrappedStatRow(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        label = stringResource(R.string.wrapped_biggest),
                        value = spend.notes.ifBlank { getLocalizedPresetName(spend.purpose) },
                        detail = money(spend.amount)
                    )
                }
                if (wrapped.topItems.isNotEmpty()) {
                    val top = wrapped.topItems.first()
                    WrappedStatRow(
                        icon = Icons.Rounded.ShoppingBasket,
                        label = stringResource(R.string.wrapped_most_on),
                        value = wrapped.topItems.joinToString(" · ") { it.label },
                        detail = "${money(top.total)} · ${pluralStringResource(R.plurals.wrapped_times, top.count, top.count)}"
                    )
                }
                if (wrapped.daysCounted > 0) {
                    WrappedStatRow(
                        icon = Icons.Rounded.EventAvailable,
                        label = stringResource(R.string.wrapped_no_spend_days),
                        value = stringResource(R.string.wrapped_no_spend_value, wrapped.noSpendDays, wrapped.daysCounted),
                        detail = pluralStringResource(
                            R.plurals.wrapped_longest_streak,
                            wrapped.longestNoSpendStreak,
                            wrapped.longestNoSpendStreak
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedStatRow(icon: ImageVector, label: String, value: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium.asMoney(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private suspend fun shareWrappedImage(context: Context, bitmap: Bitmap, text: String, chooserTitle: String) {
    val uri = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "spendly_wrapped.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.e("WrappedSheet", "Could not write Wrapped image", e)
            null
        }
    } ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
