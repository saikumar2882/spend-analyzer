/**
 * Clean "Log a Transaction" form layout for logging new spending or editing existing transactions.
 * Designed with refined form controls, soft tonal fills, tabular numeric typography, and clear focus.
 */
package com.alpha.spendtracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Tune
import com.alpha.spendtracker.ui.components.CustomizeAppPresetsSheet
import com.alpha.spendtracker.ui.components.CustomizeCategoryPresetsSheet
import com.alpha.spendtracker.ui.components.getOrderedAppPresets
import com.alpha.spendtracker.ui.components.getOrderedCategoryGridItems
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.alpha.spendtracker.data.AiParser
import androidx.compose.ui.res.stringResource
import com.alpha.spendtracker.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.data.CustomPresets
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.AppIconImage
import com.alpha.spendtracker.ui.components.AppPreset
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import com.alpha.spendtracker.ui.components.CategorySelectionGrid
import com.alpha.spendtracker.ui.components.NotificationType
import com.alpha.spendtracker.ui.components.PresetGridCard
import com.alpha.spendtracker.ui.components.getLocalizedPresetName
import com.alpha.spendtracker.ui.components.buildLendBorrowNotes
import com.alpha.spendtracker.ui.components.parseLendBorrowNotes
import com.alpha.spendtracker.ui.components.withStoredCategory
import com.alpha.spendtracker.ui.theme.MyApplicationTheme
import com.alpha.spendtracker.ui.theme.asMoney
import com.alpha.spendtracker.ui.theme.rememberPressScale
import com.alpha.spendtracker.util.findActivity
import com.alpha.spendtracker.util.formatShortDate
import com.alpha.spendtracker.util.isSameDay
import com.alpha.spendtracker.util.yesterdayMillis
import kotlinx.coroutines.launch
import java.util.Calendar

data class NewSpend(
    val preset: AppPreset,
    val amount: Double,
    val purpose: String,
    val notes: String,
    val customAppName: String,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpendScreen(
    editingSpend: Spend? = null,
    prefilledSpend: NewSpend? = null,
    userAppOrder: List<String> = emptyList(),
    userCategoryOrder: List<String> = emptyList(),
    onSaveAppOrder: (List<String>) -> Unit = {},
    onSaveCategoryOrder: (List<String>) -> Unit = {},
    onResetAppOrder: () -> Unit = {},
    onResetCategoryOrder: () -> Unit = {},
    customPresets: CustomPresets = CustomPresets.EMPTY,
    onAddCustomApp: (String) -> Unit = {},
    onAddCustomCategory: (String) -> Unit = {},
    onRemoveCustomApp: (String) -> Unit = {},
    onRemoveCustomCategory: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onShowNotification: (String, NotificationType) -> Unit,
    onSave: (NewSpend) -> Unit
) {
    val orderedAppPresets = remember(userAppOrder, customPresets) { getOrderedAppPresets(userAppOrder, customPresets) }
    val orderedCategoryItems = remember(userCategoryOrder, customPresets) { getOrderedCategoryGridItems(userCategoryOrder, customPresets) }
    // An old log can carry a purpose the grid no longer offers (a legacy twin, a removed custom category);
    // the picker gets a tile for it so it shows selected, while the customize sheet keeps the plain list.
    val storedPurpose = editingSpend?.purpose ?: prefilledSpend?.purpose
    val pickerItems = remember(orderedCategoryItems, storedPurpose) { withStoredCategory(orderedCategoryItems, storedPurpose) }

    var showCustomizeAppsSheet by remember { mutableStateOf(false) }
    var showCustomizeCategoriesSheet by remember { mutableStateOf(false) }

    var amountInput by rememberSaveable { 
        mutableStateOf(
            editingSpend?.amount?.let { if (it > 0) formatPlainAmount(it) else "" } 
                ?: (if (prefilledSpend != null && prefilledSpend.amount > 0) formatPlainAmount(prefilledSpend.amount) else "")
        ) 
    }
    var selectedPreset by remember(orderedAppPresets) { 
        mutableStateOf(
            when {
                editingSpend != null -> orderedAppPresets.find { it.displayName == editingSpend.appName } ?: orderedAppPresets.find { it.id == "other" } ?: orderedAppPresets.last()
                prefilledSpend != null -> prefilledSpend.preset
                else -> orderedAppPresets.firstOrNull() ?: APP_PRESETS.first()
            }
        ) 
    }
    var purposeInput by rememberSaveable { 
        mutableStateOf(
            editingSpend?.purpose ?: prefilledSpend?.purpose ?: CANONICAL_PURPOSES.first()
        ) 
    }

    val initialLendBorrowParsed = remember(editingSpend) {
        if (editingSpend != null && (editingSpend.purpose == "Lending" || editingSpend.purpose == "Borrowing")) {
            parseLendBorrowNotes(editingSpend.notes, editingSpend.appName)
        } else null
    }

    var personNameInput by rememberSaveable { 
        mutableStateOf(initialLendBorrowParsed?.first ?: "") 
    }
    var notesInput by rememberSaveable { 
        mutableStateOf(initialLendBorrowParsed?.second ?: editingSpend?.notes ?: prefilledSpend?.notes ?: "") 
    }
    var customAppNameInput by rememberSaveable { 
        mutableStateOf(
            if (selectedPreset.id == "other") {
                editingSpend?.appName ?: prefilledSpend?.customAppName ?: ""
            } else ""
        ) 
    }
    var transactionTimestamp by rememberSaveable { 
        mutableLongStateOf(
            editingSpend?.timestamp ?: prefilledSpend?.timestamp ?: System.currentTimeMillis()
        ) 
    }

    // "Add new" works like "Other Platform": pick it, type a name, and the name is saved to the list.
    var addingCategory by rememberSaveable { mutableStateOf(false) }
    var newCategoryInput by rememberSaveable { mutableStateOf("") }
    val typedCategory = CustomPresets.cleanName(newCategoryInput)
    // A typed name that already exists ("food", "Lending") means that category, so the person field follows suit.
    val effectivePurpose = if (addingCategory && typedCategory.isNotBlank()) {
        customPresets.resolveCategory(typedCategory) ?: typedCategory
    } else purposeInput

    val isLendBorrow = effectivePurpose.equals("Lending", ignoreCase = true) || effectivePurpose.equals("Borrowing", ignoreCase = true)

    LaunchedEffect(prefilledSpend) {
        if (prefilledSpend != null && editingSpend == null) {
            amountInput = if (prefilledSpend.amount > 0) formatPlainAmount(prefilledSpend.amount) else ""
            selectedPreset = prefilledSpend.preset
            purposeInput = prefilledSpend.purpose
            notesInput = prefilledSpend.notes
            customAppNameInput = prefilledSpend.customAppName
            transactionTimestamp = prefilledSpend.timestamp
        }
    }

    var amountError by remember { mutableStateOf<String?>(null) }
    var customAppError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    val categoryRequiredMessage = stringResource(R.string.category_name_required)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Opening an existing log keeps its stored purpose: the app-based suggestions below are for when
    // the user changes the app, not for the first composition (which would turn a Swiggy log saved
    // as "Groceries" into "Food & Dining" just by opening it).
    val openedPresetId = remember { selectedPreset.id }
    LaunchedEffect(selectedPreset) {
        val reopened = editingSpend != null && selectedPreset.id == openedPresetId && purposeInput == editingSpend.purpose
        if (!reopened) purposeInput = suggestedPurposeFor(selectedPreset, purposeInput)
        if (selectedPreset.id != "other") customAppError = null
    }

    LaunchedEffect(customAppNameInput) {
        val reopened = editingSpend != null && customAppNameInput == editingSpend.appName
        if (!reopened && selectedPreset.id == "other" && customAppNameInput.isNotBlank()) {
            AiParser.inferPurpose(customAppNameInput)?.let { inferred ->
                purposeInput = inferred
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (editingSpend != null) stringResource(R.string.edit_transaction) else stringResource(R.string.log_transaction),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
            item {
                AmountInputCard(
                    amount = amountInput,
                    onAmountChange = {
                        amountInput = it
                        amountError = null
                    },
                    onClear = {
                        amountInput = ""
                        amountError = null
                    },
                    isError = amountError != null,
                    errorMessage = amountError
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(
                        text = stringResource(R.string.select_payment_app),
                        onActionClick = { showCustomizeAppsSheet = true }
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val gap = 12.dp
                        val cardWidth = (maxWidth - gap * 3) / 4
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            items(orderedAppPresets, key = { it.id }) { preset ->
                                Box(modifier = Modifier.width(cardWidth)) {
                                    PresetGridCard(
                                        preset = preset,
                                        isSelected = selectedPreset.id == preset.id,
                                        onClick = { selectedPreset = preset }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPreset.id == "other") {
                item {
                    OutlinedTextField(
                        value = customAppNameInput,
                        onValueChange = {
                            customAppNameInput = it.take(CustomPresets.MAX_NAME_LENGTH)
                            customAppError = null
                        },
                        label = { Text(stringResource(R.string.enter_app_name)) },
                        placeholder = { Text("E.g. Cred, Jupiter, Cash, Bank Transfer...") },
                        leadingIcon = if (customAppNameInput.isNotBlank()) {
                            {
                                AppIconImage(
                                    appName = customAppNameInput,
                                    fallbackColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else null,
                        singleLine = true,
                        isError = customAppError != null,
                        supportingText = when {
                            customAppError != null -> ({ Text(customAppError.orEmpty()) })
                            customAppNameInput.isNotBlank() && customPresets.resolveApp(customAppNameInput) == null ->
                                ({ Text(stringResource(R.string.saved_for_next_time)) })
                            else -> null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.date_and_time))
                    DateSelectorRow(
                        timestamp = transactionTimestamp,
                        onTimestampChange = { transactionTimestamp = it },
                        onShowNotification = onShowNotification
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(
                        text = stringResource(R.string.category),
                        onActionClick = { showCustomizeCategoriesSheet = true }
                    )
                    CategorySelectionGrid(
                        selectedCategory = purposeInput,
                        onCategorySelected = {
                            purposeInput = it
                            addingCategory = false
                            categoryError = null
                        },
                        items = pickerItems,
                        onAddNew = { addingCategory = true },
                        addNewSelected = addingCategory
                    )
                    if (addingCategory) {
                        OutlinedTextField(
                            value = newCategoryInput,
                            onValueChange = {
                                newCategoryInput = it.take(CustomPresets.MAX_NAME_LENGTH)
                                categoryError = null
                            },
                            label = { Text(stringResource(R.string.enter_category_name)) },
                            singleLine = true,
                            isError = categoryError != null,
                            supportingText = when {
                                categoryError != null -> ({ Text(categoryError.orEmpty()) })
                                typedCategory.isNotBlank() && customPresets.resolveCategory(typedCategory) == null ->
                                    ({ Text(stringResource(R.string.saved_for_next_time)) })
                                else -> null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            if (isLendBorrow) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Name of Person")
                        OutlinedTextField(
                            value = personNameInput,
                            onValueChange = { personNameInput = it },
                            placeholder = { Text("E.g. Alex, Ram, Rahul...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(stringResource(R.string.note_detail_optional))
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("E.g. Groceries, Lunch, Movie tickets...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val parsedAmount = amountInput.toDoubleOrNull()
                        val amtIssue = when {
                            parsedAmount == null -> "Enter an amount to continue"
                            parsedAmount <= 0.0 -> "Amount must be greater than ₹0"
                            else -> null
                        }

                        amountError = amtIssue
                        customAppError = null
                        categoryError = null

                        if (amtIssue != null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(amtIssue) }
                            return@Button
                        }
                        if (addingCategory && typedCategory.isBlank()) {
                            categoryError = categoryRequiredMessage
                            coroutineScope.launch { snackbarHostState.showSnackbar(categoryRequiredMessage) }
                            return@Button
                        }

                        val finalNotes = if (isLendBorrow) buildLendBorrowNotes(personNameInput, notesInput) else notesInput

                        // A name typed under "Other Platform" that is already in the list means that app.
                        val typedApp = CustomPresets.cleanName(customAppNameInput)
                        val knownApp = if (selectedPreset.id == "other" && typedApp.isNotBlank()) customPresets.resolveApp(typedApp) else null
                        val finalPreset = knownApp ?: selectedPreset
                        if (finalPreset.id == "other" && typedApp.isNotBlank()) onAddCustomApp(typedApp)
                        if (addingCategory && customPresets.resolveCategory(typedCategory) == null) onAddCustomCategory(typedCategory)

                        onSave(
                            NewSpend(
                                preset = finalPreset,
                                amount = parsedAmount!!,
                                purpose = effectivePurpose.ifBlank { "Miscellaneous" },
                                notes = finalNotes,
                                customAppName = if (finalPreset.id == "other") typedApp.ifBlank { "Other" } else "",
                                timestamp = transactionTimestamp
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 1.dp
                    )
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.save),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
    }

    if (showCustomizeAppsSheet) {
        CustomizeAppPresetsSheet(
            currentOrderedPresets = orderedAppPresets,
            onSaveOrder = onSaveAppOrder,
            onResetDefault = onResetAppOrder,
            onDismiss = { showCustomizeAppsSheet = false },
            onRemoveApp = { onRemoveCustomApp(it.displayName) }
        )
    }

    if (showCustomizeCategoriesSheet) {
        CustomizeCategoryPresetsSheet(
            currentOrderedItems = orderedCategoryItems,
            onSaveOrder = onSaveCategoryOrder,
            onResetDefault = onResetCategoryOrder,
            onDismiss = { showCustomizeCategoriesSheet = false },
            customCategoryNames = customPresets.categories,
            onRemoveCategory = onRemoveCustomCategory
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onActionClick != null) {
            IconButton(
                onClick = onActionClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Customize",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AmountInputCard(
    amount: String,
    onAmountChange: (String) -> Unit,
    onClear: () -> Unit,
    isError: Boolean,
    errorMessage: String?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        // The only state that earns an outline here is the error state.
        border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "₹",
                    style = MaterialTheme.typography.headlineMedium.asMoney(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                TextField(
                    value = amount,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) onAmountChange(input)
                    },
                    placeholder = {
                        Text(
                            "0",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            style = MaterialTheme.typography.displaySmall.asMoney()
                        )
                    },
                    textStyle = MaterialTheme.typography.displaySmall.asMoney().copy(
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = isError,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (amount.isNotBlank()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Clear,
                            contentDescription = "Clear amount",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DateSelectorRow(
    timestamp: Long,
    onTimestampChange: (Long) -> Unit,
    onShowNotification: (String, NotificationType) -> Unit
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val isTodaySelected = isSameDay(timestamp, now)
    val isYesterdaySelected = isSameDay(timestamp, yesterdayMillis())
    val isCustomSelected = !isTodaySelected && !isYesterdaySelected

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        DateChip(
            label = stringResource(R.string.today),
            isSelected = isTodaySelected,
            weight = 1.0f,
            onClick = { onTimestampChange(System.currentTimeMillis()) }
        )
        DateChip(
            label = stringResource(R.string.yesterday),
            isSelected = isYesterdaySelected,
            weight = 1.0f,
            onClick = { onTimestampChange(yesterdayMillis()) }
        )
        DateChip(
            label = if (isCustomSelected) formatShortDate(timestamp) else stringResource(R.string.custom),
            isSelected = isCustomSelected,
            weight = 1.3f,
            icon = Icons.Rounded.Event,
            onClick = {
                val activity = context.findActivity() ?: run {
                    onShowNotification("Could not open the date picker", NotificationType.ERROR)
                    return@DateChip
                }
                val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                DatePickerDialog(
                    activity,
                    { _, year, month, dayOfMonth ->
                        val updated = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        }
                        onTimestampChange(updated.timeInMillis)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        )
    }
}

@Composable
private fun RowScope.DateChip(
    label: String,
    isSelected: Boolean,
    weight: Float,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
        border = null,
        modifier = Modifier
            .weight(weight)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatPlainAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        amount.toString()
    }
}

private fun suggestedPurposeFor(preset: AppPreset, current: String): String =
    when (preset.id) {
        "swiggy", "zomato" -> "Food & Dining"
        "zepto", "blinkit" -> "Groceries"
        "amazon", "flipkart", "myntra", "ajio" -> "Shopping"
        "icici", "yono_sbi", "cred" -> "EMIs & Cards"
        "mobikwik", "phone_pe", "google_pay", "paytm", "cash" -> current
        else -> current
    }

@Preview(showBackground = true)
@Composable
fun AddSpendScreenPreview() {
    MyApplicationTheme {
        AddSpendScreen(
            onDismiss = {},
            onShowNotification = { _, _ -> },
            onSave = {}
        )
    }
}

