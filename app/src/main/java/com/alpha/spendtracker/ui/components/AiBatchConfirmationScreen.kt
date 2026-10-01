package com.alpha.spendtracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.ui.theme.Sizes
import java.text.SimpleDateFormat

/** A log in the review list. The id is stable, so removing or editing one never shuffles the others. */
private data class BatchLog(val id: Int, val response: AiTransactionResponse)

/**
 * Shown instead of [AiConfirmationScreen] when one sentence described several expenses
 * ("tea 20, auto 80 and lunch 150"): every log that is about to be saved, in one list, so nothing
 * is written that the user hasn't seen.
 *
 * Tapping a log opens the ordinary review form for just that log (so app, category, person, note
 * and date are all editable, and learned corrections still apply); the X removes it. Nothing is
 * saved until the single "Save N logs" button.
 */
@Composable
fun AiBatchConfirmationScreen(
    extracted: List<AiTransactionResponse>,
    onConfirm: (List<NewSpend>) -> Unit,
    onCancel: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onLearnCorrection: (notes: String, app: String?, purpose: String?) -> Unit = { _, _, _ -> },
    defaultApp: String = "Google Pay",
    defaultPurpose: String = "Others",
    currencySymbol: String = "₹"
) {
    val logs = remember(extracted) {
        extracted.mapIndexed { index, response -> BatchLog(index, response) }.toMutableStateList()
    }
    var editingId by remember { mutableStateOf<Int?>(null) }
    val editing = logs.firstOrNull { it.id == editingId }

    if (editing != null) {
        // The full single-log form, for this log only. It brings its own scroll container and
        // BackHandler (back = cancel the edit, which lands here on the list again).
        AiConfirmationScreen(
            extractedData = editing.response,
            confirmLabel = stringResource(R.string.ai_batch_update_log),
            onConfirm = { edited ->
                val at = logs.indexOfFirst { it.id == editing.id }
                if (at >= 0) logs[at] = BatchLog(editing.id, edited.toResponse())
                editingId = null
            },
            onCancel = { editingId = null },
            onShowNotification = onShowNotification,
            onLearnCorrection = onLearnCorrection,
            defaultApp = defaultApp,
            defaultPurpose = defaultPurpose,
            currencySymbol = currencySymbol
        )
        return
    }

    BackHandler(enabled = true) { onCancel() }

    // One clock reading for the whole list, so a log with no stated date doesn't tick.
    val now = remember(extracted) { System.currentTimeMillis() }
    val spends = logs.map { it to it.response.toNewSpend(defaultApp, defaultPurpose, now) }
    val total = spends.sumOf { it.second.amount }
    val canSave = spends.isNotEmpty() && spends.all { it.second.isSavable() }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("d MMM", locale) }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Ai,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.ai_batch_title, logs.size, logs.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ai_batch_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pluralStringResource(R.plurals.ai_batch_will_save, logs.size, logs.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$currencySymbol${formatCurrency(total)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }

        if (spends.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_batch_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally)
            )
        }

        spends.forEach { (log, spend) ->
            key(log.id) {
                BatchLogRow(
                    spend = spend,
                    currencySymbol = currencySymbol,
                    dateLabel = dateFormatter.format(spend.timestamp),
                    onEdit = { editingId = log.id },
                    onRemove = { logs.removeAll { it.id == log.id } }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) }

            Button(
                onClick = { onConfirm(spends.map { it.second }) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = canSave
            ) {
                Text(
                    pluralStringResource(R.plurals.ai_batch_save, logs.size, logs.size),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BatchLogRow(
    spend: NewSpend,
    currencySymbol: String,
    dateLabel: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val appName = if (spend.preset.id == "other") spend.customAppName else spend.preset.displayName
    val isDues = spend.purpose == "Lending" || spend.purpose == "Borrowing"
    val (person, detail) = if (isDues) splitLendBorrowNotes(spend.notes) else "" to spend.notes.trim()
    val purposeLabel = getLocalizedPresetName(spend.purpose)

    // What the log is *about* leads; the purpose follows unless the title already is the purpose.
    val title = when {
        isDues -> person.ifBlank { purposeLabel }
        detail.isNotBlank() -> detail
        else -> purposeLabel
    }
    val subtitle = listOf(
        purposeLabel.takeIf { it != title },
        detail.takeIf { isDues && it.isNotBlank() },
        getLocalizedPresetName(appName).takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString(" • ")

    val hasAmount = spend.amount > 0.0
    val editLabel = stringResource(R.string.ai_batch_edit_log)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.minTouchTarget)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AppIconImage(
                    appName = appName,
                    fallbackColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            // Amount over date, like the Dues rows: the date is least important, so it must not be
            // the part of a long subtitle that gets cut off.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (hasAmount) "$currencySymbol${formatCurrency(spend.amount)}"
                    else stringResource(R.string.ai_batch_needs_amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasAmount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.ai_batch_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
