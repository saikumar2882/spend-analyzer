package com.alpha.spendtracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.HistoryType
import com.alpha.spendtracker.data.NoteHistory
import com.alpha.spendtracker.data.NoteItemType
import com.alpha.spendtracker.ui.components.formatCurrency
import java.text.SimpleDateFormat
import java.util.Locale

private val historyDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun NotesHistoryScreen(
    deletedHistory: List<NoteHistory>,
    updatedHistory: List<NoteHistory>,
    currencySymbol: String,
    onRestore: (NoteHistory) -> Unit,
    onPermanentlyDelete: (NoteHistory) -> Unit,
    onEmptyTrash: () -> Unit,
    onClearUpdateHistory: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    val isDeletedView = selectedTab == 0
    val tabs = listOf("Recycle Bin", "Update History")

    BackHandler(onBack = onBack)

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = {
                Text(
                    text = if (isDeletedView) "Empty Recycle Bin?" else "Clear Update History?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isDeletedView) "This will permanently delete all notes in the Recycle Bin. This action cannot be undone."
                    else "This will permanently remove all tracked modifications. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isDeletedView) onEmptyTrash() else onClearUpdateHistory()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Notes History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                            val count = if (index == 0) deletedHistory.size else updatedHistory.size
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (selectedTab == index) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentItems = if (isDeletedView) deletedHistory else updatedHistory

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentItems.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirmation = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isDeletedView) "Empty Trash" else "Clear All")
                }
            }
        }

        if (isDeletedView) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Deleted items are kept for 30 days before permanent removal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (currentItems.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isDeletedView) Icons.Rounded.AutoDelete else Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isDeletedView) "Your trash is empty" else "No modifications tracked yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(currentItems, key = { it.historyUuid }) { item ->
                    NoteHistoryCard(
                        history = item,
                        currencySymbol = currencySymbol,
                        onRestore = { onRestore(item) },
                        onDelete = { onPermanentlyDelete(item) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteHistoryCard(
    history: NoteHistory,
    currencySymbol: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDeleted = history.historyType == HistoryType.DELETED
    val isNote = history.itemType == NoteItemType.NOTE
    val accent = if (isNote) {
        val i = ((history.colorIndex % NOTE_COLORS.size) + NOTE_COLORS.size) % NOTE_COLORS.size
        NOTE_COLORS[i]
    } else {
        MaterialTheme.colorScheme.primary
    }

    val daysLeft = if (isDeleted) {
        val millisInDay = 24L * 60 * 60 * 1000
        val elapsed = System.currentTimeMillis() - history.recordedAt
        (30 - (elapsed / millisInDay)).toInt().coerceAtLeast(0)
    } else null

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(84.dp).background(accent))
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = history.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(text = if (isNote) "NOTE" else "ENTRY", container = accent.copy(alpha = 0.18f), content = accent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Badge(
                            text = history.historyType,
                            container = if (isDeleted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            content = if (isDeleted) MaterialTheme.colorScheme.onErrorContainer
                                      else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (daysLeft != null) {
                        Text(
                            text = if (daysLeft == 0) "Expires today" else "$daysLeft days left",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (daysLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isNote) {
                        val meta = buildString {
                            if (history.amount > 0) append("$currencySymbol${formatCurrency(history.amount)}")
                            if (history.date > 0) {
                                if (isNotEmpty()) append(" · ")
                                append(historyDateFormat.format(history.date))
                            }
                        }
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!history.detail.isNullOrBlank()) {
                            Text(
                                text = history.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onRestore,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = "Restore", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (isDeleted) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
                            contentDescription = if (isDeleted) "Permanently delete" else "Remove history",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(color = container, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold),
            color = content
        )
    }
}
