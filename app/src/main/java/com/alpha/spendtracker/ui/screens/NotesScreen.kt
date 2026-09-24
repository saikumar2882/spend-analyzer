package com.alpha.spendtracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.data.Note
import com.alpha.spendtracker.data.NoteEntry
import com.alpha.spendtracker.data.NoteField
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.icons.AppIcons
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
    onShowHistory: () -> Unit = {},
    onLogAsTransaction: (Note) -> Unit = {},
    // When set (e.g. from tapping a note-linked transaction in History), auto-open this note.
    // Consumed once, then cleared via [onInitialNoteConsumed].
    initialNoteUuid: String? = null,
    onInitialNoteConsumed: () -> Unit = {}
) {
    var selectedNoteUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedNote = selectedNoteUuid?.let { id -> notes.find { it.uuid == id } }

    // Independent layout toggles: Level 1 (Notes section) vs Level 2 (Logs inside a note)
    var isNotesGridView by rememberSaveable { mutableStateOf(true) }
    var isEntriesGridView by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(initialNoteUuid) {
        if (initialNoteUuid != null) {
            selectedNoteUuid = initialNoteUuid
            onInitialNoteConsumed()
        }
    }

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
                    // View Mode Toggle (Grid vs List) — operates independently for Notes vs Entries inside a Note
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
                        NoteTile(
                            note = note,
                            itemCount = noteEntries.size,
                            subtotal = noteEntries.sumOf { it.amount },
                            currencySymbol = currencySymbol,
                            isGridView = isNotesGridView,
                            onOpen = { selectedNoteUuid = note.uuid },
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
                            SwipeableLogCard(
                                onEdit = { editingEntry = entry },
                                onDelete = { entryToDelete = entry },
                                modifier = Modifier.animateItem()
                            ) {
                                NoteEntryCard(
                                    entry = entry,
                                    accent = accent,
                                    currencySymbol = currencySymbol,
                                    isGridView = isEntriesGridView,
                                    onClick = { editingEntry = entry }
                                )
                            }
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

@Composable
private fun NoteTile(
    note: Note,
    itemCount: Int,
    subtotal: Double,
    currencySymbol: String,
    isGridView: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLogAsTransaction: () -> Unit
) {
    val accent = noteColor(note.colorIndex)
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGridView) Modifier.heightIn(min = 108.dp) else Modifier)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (isGridView) {
            // M-size Box mode for Grid View (2 columns, 108dp min height, clean spacing)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 108.dp)
                    .padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Title + Accent bar + Options menu
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title.ifBlank { "Untitled" },
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

                // Bottom: Items count & Subtotal
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
            // List mode layout (1 column full-width card with comfortable height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            text = { Text("Log as transaction") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null) },
            onClick = { onDismiss(); onLogAsTransaction() }
        )
        DropdownMenuItem(
            text = { Text("Edit") },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = { onDismiss(); onEdit() }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
private fun NoteEntryCard(
    entry: NoteEntry,
    accent: Color,
    currencySymbol: String,
    isGridView: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGridView) Modifier.heightIn(min = 96.dp) else Modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (isGridView) {
            // Grid View: Consistent M-size Box format (min 96dp height, non-congested)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .padding(11.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Accent indicator + Title Label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(18.dp)
                            .background(accent, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.label.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Middle: Detail or Custom field note preview (if available)
                val noteText = entry.detail?.takeIf { it.isNotBlank() }
                    ?: entry.customFields.firstOrNull { it.value.isNotBlank() }?.let {
                        if (it.name.isNotBlank()) "${it.name}: ${it.value}" else it.value
                    }

                if (!noteText.isNullOrBlank()) {
                    Text(
                        text = noteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // Bottom: Amount & Date
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
            // List View: Comfortable 1-column Row format
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    entry.customFields.filter { it.name.isNotBlank() || it.value.isNotBlank() }.forEach { field ->
                        Text(
                            text = buildString {
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
                        text = "$currencySymbol${formatCurrency(entry.amount)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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
        title = { Text(if (note == null) "New Note" else "Edit Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
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
                        label = { Text("Date") },
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
                    label = { Text("Amount (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = cleanTextFieldColors
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Note (Optional)") },
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
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = cleanTextFieldColors
                        )
                        OutlinedTextField(
                            value = draft.value,
                            onValueChange = { draft.value = it },
                            label = { Text("Value") },
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
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Cancel") } }
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
