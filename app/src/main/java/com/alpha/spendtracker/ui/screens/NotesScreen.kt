package com.alpha.spendtracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Note
import com.alpha.spendtracker.data.NoteEntry
import com.alpha.spendtracker.data.NoteField
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.theme.isAppInDarkTheme
import com.alpha.spendtracker.util.rememberTranslatedText
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/** Accent palette for note tiles, indexed by [Note.colorIndex]. Fixed hues that read well
 *  as soft tints (background) and solid accents (text) in both light and dark themes. */
val NOTE_COLORS = listOf(
    Color(0xFF6C63FF), // indigo
    Color(0xFF00BFA6), // teal
    Color(0xFFFF6B6B), // coral
    Color(0xFFFFA726), // amber
    Color(0xFF42A5F5), // blue
    Color(0xFFAB47BC), // purple
    Color(0xFF66BB6A), // green
    Color(0xFFEC407A), // pink
)

private fun noteColor(index: Int): Color = NOTE_COLORS[((index % NOTE_COLORS.size) + NOTE_COLORS.size) % NOTE_COLORS.size]

private val entryDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notes: List<Note>,
    entries: List<NoteEntry>,
    currencySymbol: String,
    onBack: () -> Unit,
    onAddNote: (title: String, colorIndex: Int, uuid: String) -> Unit = { _, _, _ -> },
    onUpdateNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onDeleteNotes: (List<Note>) -> Unit = {},
    onAddEntry: (noteUuid: String, label: String, amount: Double, date: Long, detail: String?, customFields: List<NoteField>) -> Unit,
    onUpdateEntry: (NoteEntry) -> Unit,
    onDeleteEntry: (NoteEntry) -> Unit,
    onDeleteEntries: (List<NoteEntry>) -> Unit = {},
    onMoveEntries: (entries: List<NoteEntry>, targetNoteUuid: String) -> Unit = { _, _ -> },
    onCopyEntries: (entries: List<NoteEntry>, targetNoteUuid: String) -> Unit = { _, _ -> },
    onShowHistory: () -> Unit = {},
    onLogAsTransaction: (Note) -> Unit = {},
    onAiAssistantClick: (() -> Unit)? = null,
    // When set (e.g. from tapping a note-linked transaction in History), auto-open this note.
    // Consumed once, then cleared via [onInitialNoteConsumed].
    initialNoteUuid: String? = null,
    onInitialNoteConsumed: () -> Unit = {}
) {
    var selectedNoteUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedNote = selectedNoteUuid?.let { id -> notes.find { it.uuid == id } }

    // Multi-select state tracking
    var selectedNoteUuids by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedEntryUuids by rememberSaveable { mutableStateOf(setOf<String>()) }

    val isNoteSelectionMode = selectedNote == null && selectedNoteUuids.isNotEmpty()
    val isEntrySelectionMode = selectedNote != null && selectedEntryUuids.isNotEmpty()

    // Dialog & Action Confirmation states
    var showDeleteNotesDialog by remember { mutableStateOf(false) }
    var showDeleteEntriesDialog by remember { mutableStateOf(false) }
    var showMoveEntriesDialog by remember { mutableStateOf(false) }
    var showCopyEntriesDialog by remember { mutableStateOf(false) }

    // Independent layout toggles: Level 1 (Notes section) vs Level 2 (Logs inside a note)
    var isNotesGridView by rememberSaveable { mutableStateOf(true) }
    var isEntriesGridView by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(initialNoteUuid) {
        if (initialNoteUuid != null) {
            selectedNoteUuid = initialNoteUuid
            onInitialNoteConsumed()
        }
    }

    // Dialog / editing state
    var showAddNote by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var showAddEntry by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<NoteEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<NoteEntry?>(null) }

    // System back navigation rules
    BackHandler(enabled = isEntrySelectionMode) {
        selectedEntryUuids = emptySet()
    }
    BackHandler(enabled = isNoteSelectionMode) {
        selectedNoteUuids = emptySet()
    }
    BackHandler(enabled = selectedNote != null && !isEntrySelectionMode) {
        selectedNoteUuid = null
        selectedEntryUuids = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when {
                        isEntrySelectionMode -> stringResource(R.string.items_selected, selectedEntryUuids.size)
                        isNoteSelectionMode -> stringResource(R.string.items_selected, selectedNoteUuids.size)
                        selectedNote != null -> selectedNote.title
                        else -> stringResource(R.string.notes_title)
                    }
                    Text(
                        titleText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    when {
                        isEntrySelectionMode -> {
                            IconButton(onClick = { selectedEntryUuids = emptySet() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                            }
                        }
                        isNoteSelectionMode -> {
                            IconButton(onClick = { selectedNoteUuids = emptySet() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                            }
                        }
                        selectedNote != null -> {
                            IconButton(onClick = {
                                selectedNoteUuid = null
                                selectedEntryUuids = emptySet()
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                        else -> {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                },
                actions = {
                    if (isEntrySelectionMode) {
                        val currentEntries = entries.filter { it.noteUuid == selectedNote?.uuid }
                        val allSelected = currentEntries.isNotEmpty() && selectedEntryUuids.size == currentEntries.size
                        IconButton(onClick = {
                            selectedEntryUuids = if (allSelected) emptySet() else currentEntries.map { it.uuid }.toSet()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all)
                            )
                        }
                        IconButton(onClick = { showMoveEntriesDialog = true }) {
                            Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = stringResource(R.string.move_to_note))
                        }
                        IconButton(onClick = { showCopyEntriesDialog = true }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy_to_note))
                        }
                        IconButton(onClick = { showDeleteEntriesDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                    } else if (isNoteSelectionMode) {
                        val allSelected = notes.isNotEmpty() && selectedNoteUuids.size == notes.size
                        IconButton(onClick = {
                            selectedNoteUuids = if (allSelected) emptySet() else notes.map { it.uuid }.toSet()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all)
                            )
                        }
                        IconButton(onClick = { showDeleteNotesDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        if (onAiAssistantClick != null) {
                            IconButton(onClick = onAiAssistantClick) {
                                Icon(
                                    imageVector = AppIcons.Ai,
                                    contentDescription = stringResource(R.string.ai_assistant_title),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // View Mode Toggle (Grid vs List)
                        val currentIsGrid = if (selectedNote == null) isNotesGridView else isEntriesGridView
                        IconButton(onClick = {
                            if (selectedNote == null) {
                                isNotesGridView = !isNotesGridView
                            } else {
                                isEntriesGridView = !isEntriesGridView
                            }
                        }) {
                            Icon(
                                imageVector = if (currentIsGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = if (currentIsGrid) "Switch to List View" else "Switch to Grid View"
                            )
                        }

                        if (selectedNote != null) {
                            IconButton(onClick = { onLogAsTransaction(selectedNote) }) {
                                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = "Log as transaction")
                            }
                            IconButton(onClick = { editingNote = selectedNote }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit note")
                            }
                            IconButton(onClick = { showAddEntry = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add entry")
                            }
                        } else {
                            IconButton(onClick = onShowHistory) {
                                Icon(Icons.Rounded.Restore, contentDescription = "Recycle bin & history")
                            }
                            IconButton(onClick = { showAddNote = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add note")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedNote == null) {
            // ---------- Level 1: Notes collection (Grid / List) ----------
            if (notes.isEmpty()) {
                EmptyState(
                    title = "No notes yet",
                    subtitle = "Tap + to create a custom note and fill it with entries."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isNotesGridView) 2 else 1),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(notes, key = { it.uuid }) { note ->
                        val noteEntries = entries.filter { it.noteUuid == note.uuid }
                        val isSelected = selectedNoteUuids.contains(note.uuid)
                        NoteTile(
                            note = note,
                            itemCount = noteEntries.size,
                            subtotal = noteEntries.sumOf { it.amount },
                            currencySymbol = currencySymbol,
                            isGridView = isNotesGridView,
                            isSelected = isSelected,
                            isSelectionMode = isNoteSelectionMode,
                            onClick = {
                                if (isNoteSelectionMode) {
                                    selectedNoteUuids = if (isSelected) selectedNoteUuids - note.uuid else selectedNoteUuids + note.uuid
                                } else {
                                    selectedNoteUuid = note.uuid
                                }
                            },
                            onLongClick = {
                                if (!isNoteSelectionMode) {
                                    selectedNoteUuids = setOf(note.uuid)
                                }
                            },
                            onEdit = { editingNote = note },
                            onDelete = { noteToDelete = note },
                            onLogAsTransaction = { onLogAsTransaction(note) }
                        )
                    }
                }
            }
        } else {
            // ---------- Level 2: entries inside the selected note ----------
            val accent = noteColor(selectedNote.colorIndex)
            val noteEntries = entries.filter { it.noteUuid == selectedNote.uuid }
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                NoteSummaryHeader(
                    accent = accent,
                    itemCount = noteEntries.size,
                    subtotal = noteEntries.sumOf { it.amount },
                    currencySymbol = currencySymbol
                )
                if (noteEntries.isEmpty()) {
                    EmptyState(
                        title = "No entries yet",
                        subtitle = "Tap + in the top bar to add your first entry."
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isEntriesGridView) 2 else 1),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(noteEntries, key = { it.uuid }) { entry ->
                            val isSelected = selectedEntryUuids.contains(entry.uuid)
                            SwipeableLogCard(
                                onEdit = { editingEntry = entry },
                                onDelete = { entryToDelete = entry },
                                enabled = !isEntrySelectionMode,
                                modifier = Modifier.animateItem()
                            ) {
                                NoteEntryCard(
                                    entry = entry,
                                    accent = accent,
                                    currencySymbol = currencySymbol,
                                    isGridView = isEntriesGridView,
                                    isSelected = isSelected,
                                    isSelectionMode = isEntrySelectionMode,
                                    onClick = {
                                        if (isEntrySelectionMode) {
                                            selectedEntryUuids = if (isSelected) selectedEntryUuids - entry.uuid else selectedEntryUuids + entry.uuid
                                        } else {
                                            editingEntry = entry
                                        }
                                    },
                                    onLongClick = {
                                        if (!isEntrySelectionMode) {
                                            selectedEntryUuids = setOf(entry.uuid)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- Multi-select Action Dialogs ----------
        if (showDeleteNotesDialog) {
            val count = selectedNoteUuids.size
            val selectedNotesList = notes.filter { it.uuid in selectedNoteUuids }
            AlertDialog(
                onDismissRequest = { showDeleteNotesDialog = false },
                title = { Text(stringResource(R.string.delete_selected_notes_title, count), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.delete_selected_notes_message, count)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteNotes(selectedNotesList)
                            selectedNoteUuids = emptySet()
                            showDeleteNotesDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteNotesDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showDeleteEntriesDialog) {
            val count = selectedEntryUuids.size
            val selectedEntriesList = entries.filter { it.uuid in selectedEntryUuids }
            AlertDialog(
                onDismissRequest = { showDeleteEntriesDialog = false },
                title = { Text(stringResource(R.string.delete_selected_entries_title, count), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.delete_selected_entries_message, count)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteEntries(selectedEntriesList)
                            selectedEntryUuids = emptySet()
                            showDeleteEntriesDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteEntriesDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showMoveEntriesDialog) {
            val selectedEntriesList = entries.filter { it.uuid in selectedEntryUuids }
            MoveOrCopyTargetNoteDialog(
                isMove = true,
                selectedEntries = selectedEntriesList,
                notes = notes,
                currentNoteUuid = selectedNote?.uuid,
                onDismiss = { showMoveEntriesDialog = false },
                onAddNote = onAddNote,
                onConfirm = { targetUuid ->
                    onMoveEntries(selectedEntriesList, targetUuid)
                    selectedEntryUuids = emptySet()
                    showMoveEntriesDialog = false
                }
            )
        }

        if (showCopyEntriesDialog) {
            val selectedEntriesList = entries.filter { it.uuid in selectedEntryUuids }
            MoveOrCopyTargetNoteDialog(
                isMove = false,
                selectedEntries = selectedEntriesList,
                notes = notes,
                currentNoteUuid = selectedNote?.uuid,
                onDismiss = { showCopyEntriesDialog = false },
                onAddNote = onAddNote,
                onConfirm = { targetUuid ->
                    onCopyEntries(selectedEntriesList, targetUuid)
                    selectedEntryUuids = emptySet()
                    showCopyEntriesDialog = false
                }
            )
        }

        // ---------- Single Item Dialogs ----------
        if (showAddNote || editingNote != null) {
            NoteEditDialog(
                note = editingNote,
                onDismiss = { showAddNote = false; editingNote = null },
                onSave = { title, colorIndex ->
                    val target = editingNote
                    if (target != null) {
                        onUpdateNote(target.copy(title = title, colorIndex = colorIndex))
                    } else {
                        onAddNote(title, colorIndex, UUID.randomUUID().toString())
                    }
                    showAddNote = false; editingNote = null
                }
            )
        }

        if ((showAddEntry || editingEntry != null) && selectedNote != null) {
            EntryEditDialog(
                entry = editingEntry,
                onDismiss = { showAddEntry = false; editingEntry = null },
                onSave = { label, amount, date, detail, customFields ->
                    val target = editingEntry
                    if (target != null) {
                        onUpdateEntry(target.copy(label = label, amount = amount, date = date, detail = detail, customFields = customFields))
                    } else {
                        onAddEntry(selectedNote.uuid, label, amount, date, detail, customFields)
                    }
                    showAddEntry = false; editingEntry = null
                }
            )
        }

        if (noteToDelete != null) {
            val target = noteToDelete!!
            AlertDialog(
                onDismissRequest = { noteToDelete = null },
                title = { Text(stringResource(R.string.delete_note_title)) },
                text = { Text(stringResource(R.string.delete_note_message, target.title)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (selectedNoteUuid == target.uuid) selectedNoteUuid = null
                            onDeleteNote(target)
                            noteToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (entryToDelete != null) {
            val target = entryToDelete!!
            AlertDialog(
                onDismissRequest = { entryToDelete = null },
                title = { Text(stringResource(R.string.delete_entry_title)) },
                text = { Text(stringResource(R.string.delete_entry_message, target.label.ifBlank { "this entry" })) },
                confirmButton = {
                    TextButton(
                        onClick = { onDeleteEntry(target); entryToDelete = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = { TextButton(onClick = { entryToDelete = null }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                AppIcons.Notes, null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteTile(
    note: Note,
    itemCount: Int,
    subtotal: Double,
    currencySymbol: String,
    isGridView: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLogAsTransaction: () -> Unit
) {
    val accent = noteColor(note.colorIndex)
    var menuOpen by remember { mutableStateOf(false) }
    val isDark = isAppInDarkTheme
    val displayTitle by rememberTranslatedText(note.title)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGridView) Modifier.heightIn(min = 108.dp) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                             else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (isGridView) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 108.dp)
                    .padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(3.dp)
                                .background(accent, RoundedCornerShape(1.5.dp))
                        )
                    }
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            NoteTileDropdownMenu(
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                onLogAsTransaction = onLogAsTransaction,
                                onEdit = onEdit,
                                onDelete = onDelete
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (itemCount == 1) "1 item" else "$itemCount items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (subtotal > 0) {
                        Text(
                            text = "$currencySymbol${formatCurrency(subtotal)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(38.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (itemCount == 1) "1 item" else "$itemCount items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (subtotal > 0) {
                        Text(
                            text = "$currencySymbol${formatCurrency(subtotal)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!isSelectionMode) {
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            NoteTileDropdownMenu(
                                expanded = menuOpen,
                                onDismiss = { menuOpen = false },
                                onLogAsTransaction = onLogAsTransaction,
                                onEdit = onEdit,
                                onDelete = onDelete
                            )
                        }
                    }
                }
                if (isDark) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 28.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteTileDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onLogAsTransaction: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.log_as_transaction)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null) },
            onClick = { onDismiss(); onLogAsTransaction() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.edit_details)) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = { onDismiss(); onEdit() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

@Composable
private fun NoteSummaryHeader(accent: Color, itemCount: Int, subtotal: Double, currencySymbol: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.subtotal), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "$currencySymbol${formatCurrency(subtotal)}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
            Text(
                stringResource(R.string.entries_count, itemCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteEntryCard(
    entry: NoteEntry,
    accent: Color,
    currencySymbol: String,
    isGridView: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDark = isAppInDarkTheme
    val displayLabel by rememberTranslatedText(entry.label)
    val rawNoteText = entry.detail?.takeIf { it.isNotBlank() }
        ?: entry.customFields.firstOrNull { it.value.isNotBlank() }?.let {
            if (it.name.isNotBlank()) "${it.name}: ${it.value}" else it.value
        }
    val displayNoteText by rememberTranslatedText(rawNoteText.orEmpty())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGridView) Modifier.heightIn(min = 96.dp) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                             else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (isGridView) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .padding(11.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(18.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayLabel.ifBlank { stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (displayNoteText.isNotBlank()) {
                    Text(
                        text = displayNoteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (entry.amount > 0) {
                        Text(
                            text = "$currencySymbol${formatCurrency(entry.amount)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (entry.date > 0) {
                        Text(
                            text = entryDateFormat.format(entry.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(38.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.label.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.date > 0) {
                            Text(
                                text = entryDateFormat.format(entry.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!entry.detail.isNullOrBlank()) {
                            Text(
                                text = entry.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (entry.amount > 0) {
                        Text(
                            text = "$currencySymbol${formatCurrency(entry.amount)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (isDark) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 28.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveOrCopyTargetNoteDialog(
    isMove: Boolean,
    selectedEntries: List<NoteEntry>,
    notes: List<Note>,
    currentNoteUuid: String?,
    onDismiss: () -> Unit,
    onAddNote: (title: String, colorIndex: Int, uuid: String) -> Unit,
    onConfirm: (targetNoteUuid: String) -> Unit
) {
    val availableNotes = remember(notes, currentNoteUuid, isMove) {
        if (isMove && currentNoteUuid != null) {
            notes.filter { it.uuid != currentNoteUuid }
        } else {
            notes
        }
    }

    var selectedTargetUuid by remember { mutableStateOf(availableNotes.firstOrNull()?.uuid) }

    var isCreatingNewNote by remember { mutableStateOf(false) }
    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteColorIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isMove) stringResource(R.string.move_to_note) else stringResource(R.string.copy_to_note),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_target_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isCreatingNewNote) {
                    if (availableNotes.isEmpty()) {
                        Text(
                            stringResource(R.string.no_other_notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        availableNotes.forEach { note ->
                            val isSelected = note.uuid == selectedTargetUuid
                            val accent = NOTE_COLORS[((note.colorIndex % NOTE_COLORS.size) + NOTE_COLORS.size) % NOTE_COLORS.size]
                            Surface(
                                onClick = { selectedTargetUuid = note.uuid },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(24.dp)
                                            .background(accent, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = note.title.ifBlank { "Untitled" },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { isCreatingNewNote = true },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isMove) stringResource(R.string.create_new_note_and_move)
                            else stringResource(R.string.create_new_note_and_copy)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = newNoteTitle,
                        onValueChange = { newNoteTitle = it },
                        label = { Text(stringResource(R.string.note_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        stringResource(R.string.note_color_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ColorPicker(selectedIndex = newNoteColorIndex, onSelect = { newNoteColorIndex = it })
                    TextButton(
                        onClick = { isCreatingNewNote = false },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCreatingNewNote) {
                        val newNoteUuid = UUID.randomUUID().toString()
                        onAddNote(newNoteTitle.trim(), newNoteColorIndex, newNoteUuid)
                        onConfirm(newNoteUuid)
                    } else if (selectedTargetUuid != null) {
                        onConfirm(selectedTargetUuid!!)
                    }
                },
                enabled = if (isCreatingNewNote) newNoteTitle.isNotBlank() else (selectedTargetUuid != null)
            ) {
                Text(if (isMove) stringResource(R.string.move_entries) else stringResource(R.string.copy_entries))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ColorPicker(selectedIndex: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(NOTE_COLORS.indices.toList()) { index ->
            val color = NOTE_COLORS[index]
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color, CircleShape)
                    .then(
                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                    )
                    .clickable { onSelect(index) }
            )
        }
    }
}

@Composable
private fun NoteEditDialog(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var colorIndex by remember { mutableStateOf(note?.colorIndex ?: 0) }
    val cleanTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.015f),
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note == null) stringResource(R.string.new_note) else stringResource(R.string.edit_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.note_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
                )
                Text(stringResource(R.string.note_color_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorPicker(selectedIndex = colorIndex, onSelect = { colorIndex = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), colorIndex) },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Mutable draft for one custom field row so its two text boxes edit smoothly. */
private class FieldDraft(name: String, value: String) {
    var name by mutableStateOf(name)
    var value by mutableStateOf(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditDialog(
    entry: NoteEntry?,
    onDismiss: () -> Unit,
    onSave: (label: String, amount: Double, date: Long, detail: String?, customFields: List<NoteField>) -> Unit
) {
    var label by remember { mutableStateOf(entry?.label ?: "") }
    var amount by remember { mutableStateOf(entry?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var detail by remember { mutableStateOf(entry?.detail ?: "") }
    var date by remember { mutableStateOf(entry?.date?.takeIf { it > 0 } ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val fieldDrafts = remember {
        mutableStateListOf<FieldDraft>().also { list ->
            entry?.customFields?.forEach { list.add(FieldDraft(it.name, it.value)) }
        }
    }
    val cleanTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.015f),
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) stringResource(R.string.add_entry) else stringResource(R.string.edit_entry)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.entry_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
                )
                // Read-only date field; a transparent overlay opens the date picker on tap
                Box {
                    OutlinedTextField(
                        value = entryDateFormat.format(date),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.entry_date_label)) },
                        trailingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = cleanTextFieldColors
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) amount = it },
                    label = { Text(stringResource(R.string.entry_amount_optional)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text(stringResource(R.string.entry_note_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
                )

                fieldDrafts.forEachIndexed { index, draft ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { draft.name = it },
                            label = { Text(stringResource(R.string.field_title)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors
                        )
                        OutlinedTextField(
                            value = draft.value,
                            onValueChange = { draft.value = it },
                            label = { Text(stringResource(R.string.field_value)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors
                        )
                        IconButton(onClick = { fieldDrafts.removeAt(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove field", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                TextButton(
                    onClick = { fieldDrafts.add(FieldDraft("", "")) },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add_field))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val customFields = fieldDrafts
                        .map { NoteField(it.name.trim(), it.value.trim()) }
                        .filter { it.name.isNotBlank() || it.value.isNotBlank() }
                    onSave(label.trim(), amount.toDoubleOrNull() ?: 0.0, date, detail.trim().ifBlank { null }, customFields)
                },
                enabled = label.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel)) } }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
