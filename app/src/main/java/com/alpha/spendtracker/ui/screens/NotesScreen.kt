package com.alpha.spendtracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.Note
import com.alpha.spendtracker.data.NoteEntry
import com.alpha.spendtracker.data.NoteField
import com.alpha.spendtracker.ui.components.formatCurrency
import java.text.SimpleDateFormat
import java.util.Locale

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
    onAddNote: (title: String, colorIndex: Int) -> Unit,
    onUpdateNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onAddEntry: (noteUuid: String, label: String, amount: Double, date: Long, detail: String?, customFields: List<NoteField>) -> Unit,
    onUpdateEntry: (NoteEntry) -> Unit,
    onDeleteEntry: (NoteEntry) -> Unit,
    onShowHistory: () -> Unit = {}
) {
    var selectedNoteUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedNote = selectedNoteUuid?.let { id -> notes.find { it.uuid == id } }

    // Dialog / confirmation state (shared across both levels).
    var showAddNote by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var showAddEntry by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<NoteEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<NoteEntry?>(null) }

    // System back pops the entry view back to the note list before leaving the screen.
    BackHandler(enabled = selectedNote != null) { selectedNoteUuid = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedNote != null) selectedNote.title else "Notes",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (selectedNote != null) selectedNoteUuid = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedNote != null) {
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
            )
        }
    ) { padding ->
        if (selectedNote == null) {
            // ---------- Level 1: grid of note tiles ----------
            if (notes.isEmpty()) {
                EmptyState(
                    title = "No notes yet",
                    subtitle = "Tap + to create a custom note and fill it with entries."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(notes, key = { it.uuid }) { note ->
                        val noteEntries = entries.filter { it.noteUuid == note.uuid }
                        NoteTile(
                            note = note,
                            itemCount = noteEntries.size,
                            subtotal = noteEntries.sumOf { it.amount },
                            currencySymbol = currencySymbol,
                            onOpen = { selectedNoteUuid = note.uuid },
                            onEdit = { editingNote = note },
                            onDelete = { noteToDelete = note }
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(noteEntries, key = { it.uuid }) { entry ->
                            NoteEntryItem(
                                entry = entry,
                                accent = accent,
                                currencySymbol = currencySymbol,
                                onEdit = { editingEntry = entry },
                                onDelete = { entryToDelete = entry }
                            )
                        }
                    }
                }
            }
        }

        // ---------- Dialogs ----------
        if (showAddNote || editingNote != null) {
            NoteEditDialog(
                note = editingNote,
                onDismiss = { showAddNote = false; editingNote = null },
                onSave = { title, colorIndex ->
                    val target = editingNote
                    if (target != null) {
                        onUpdateNote(target.copy(title = title, colorIndex = colorIndex))
                    } else {
                        onAddNote(title, colorIndex)
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
                title = { Text("Delete Note") },
                text = { Text("Delete '${target.title}' and all of its entries? This can't be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // If we're deleting the note that's currently open, pop back to the list.
                            if (selectedNoteUuid == target.uuid) selectedNoteUuid = null
                            onDeleteNote(target)
                            noteToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text("Cancel") } }
            )
        }

        if (entryToDelete != null) {
            val target = entryToDelete!!
            AlertDialog(
                onDismissRequest = { entryToDelete = null },
                title = { Text("Delete Entry") },
                text = { Text("Delete '${target.label.ifBlank { "this entry" }}'?") },
                confirmButton = {
                    TextButton(
                        onClick = { onDeleteEntry(target); entryToDelete = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { entryToDelete = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.StickyNote2, null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoteTile(
    note: Note,
    itemCount: Int,
    subtotal: Double,
    currencySymbol: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = noteColor(note.colorIndex)
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().height(140.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            // Title is the heading. A short color bar underneath keeps the note's color as a
            // subtle accent rather than the dominant element.
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.width(28.dp).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menuOpen = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (itemCount == 1) "1 item" else "$itemCount items",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                if (subtotal > 0) {
                    Text(
                        "$currencySymbol${formatCurrency(subtotal)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteSummaryHeader(accent: Color, itemCount: Int, subtotal: Double, currencySymbol: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Subtotal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "$currencySymbol${formatCurrency(subtotal)}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
            Text(
                if (itemCount == 1) "1 entry" else "$itemCount entries",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteEntryItem(
    entry: NoteEntry,
    accent: Color,
    currencySymbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Accent bar ties the entry to its note's color.
            Box(modifier = Modifier.width(4.dp).height(40.dp).background(accent, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.label.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.date > 0) {
                    Text(
                        entryDateFormat.format(entry.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!entry.detail.isNullOrBlank()) {
                    Text(
                        entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                entry.customFields.filter { it.name.isNotBlank() || it.value.isNotBlank() }.forEach { field ->
                    Text(
                        buildString {
                            if (field.name.isNotBlank()) append("${field.name}: ")
                            append(field.value)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entry.amount > 0) {
                Text(
                    "$currencySymbol${formatCurrency(entry.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note == null) "New Note" else "Edit Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorPicker(selectedIndex = colorIndex, onSelect = { colorIndex = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), colorIndex) },
                enabled = title.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Add Entry" else "Edit Entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Read-only date field; a transparent overlay opens the date picker on tap
                // (a plain TextField would otherwise swallow the click).
                Box {
                    OutlinedTextField(
                        value = entryDateFormat.format(date),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date") },
                        trailingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) amount = it },
                    label = { Text("Amount (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // User-defined extra fields: each is a title + input, added via "Add field".
                fieldDrafts.forEachIndexed { index, draft ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { draft.name = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.value,
                            onValueChange = { draft.value = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
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
                    Text("Add field")
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
