/**
 * The search box shared by the History and Dues screens.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes

/**
 * Outlined search box used by every list screen.
 *
 * ⚠️ Deliberately does **not** force a height. The call sites used to pass
 * `Modifier.height(50.dp)`, but `OutlinedTextField` reserves a 56dp minimum internally — so a 50dp
 * height did not shrink the field, it *clipped* it, which is why the box rendered with its bottom
 * (and, once focused, its text baseline) cut off. The clipping got worse as the system font scale
 * grew, because the field's intrinsic height grows with the text while the hard 50dp did not.
 *
 * Letting the field size itself keeps it whole at every font scale; the action buttons beside it
 * align to it with `heightIn`, not with a matching fixed height, for the same reason.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = Sizes.minTouchTarget),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconAction)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Rounded.Clear,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(Sizes.iconAction)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Radius.md),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}
