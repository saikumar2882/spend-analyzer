/**
 * Settings sheet for the apps and categories the user added: add one by name, or remove one.
 * The same lists feed the log forms and the AI Track prompt (see [CustomPresets]).
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.CustomPresets
import com.alpha.spendtracker.ui.theme.Sizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCustomPresetsSheet(
    custom: CustomPresets,
    onAddApp: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.custom_presets_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.custom_presets_sheet_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                }
            }

            PresetSection(
                title = stringResource(R.string.my_apps),
                fieldLabel = stringResource(R.string.enter_app_name),
                names = custom.apps,
                isKnown = { custom.resolveApp(it) != null },
                isFull = custom.apps.size >= CustomPresets.MAX_PER_KIND,
                dotColor = { CustomPresets.customAppPreset(it).color },
                onAdd = onAddApp,
                onRemove = onRemoveApp
            )

            PresetSection(
                title = stringResource(R.string.my_categories),
                fieldLabel = stringResource(R.string.enter_category_name),
                names = custom.categories,
                isKnown = { custom.resolveCategory(it) != null },
                isFull = custom.categories.size >= CustomPresets.MAX_PER_KIND,
                dotColor = { customCategoryItem(it).getColor() },
                onAdd = onAddCategory,
                onRemove = onRemoveCategory
            )
        }
    }
}

@Composable
private fun PresetSection(
    title: String,
    fieldLabel: String,
    names: List<String>,
    isKnown: (String) -> Boolean,
    isFull: Boolean,
    dotColor: @Composable (String) -> Color,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val typed = CustomPresets.cleanName(input)
    val known = typed.isNotEmpty() && isKnown(typed)
    val canAdd = typed.isNotEmpty() && !known && !isFull
    val submit = {
        if (canAdd) {
            onAdd(typed)
            input = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(CustomPresets.MAX_NAME_LENGTH) },
            label = { Text(fieldLabel) },
            singleLine = true,
            isError = known,
            supportingText = when {
                known -> ({ Text(stringResource(R.string.custom_name_exists)) })
                isFull -> ({ Text(stringResource(R.string.custom_presets_full)) })
                else -> null
            },
            trailingIcon = {
                IconButton(onClick = submit, enabled = canAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.custom_presets_add))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        )

        if (names.isEmpty()) {
            Text(
                text = stringResource(R.string.custom_presets_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            names.forEach { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.minTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(dotColor(name), CircleShape)
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemove(name) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.remove_from_list),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
