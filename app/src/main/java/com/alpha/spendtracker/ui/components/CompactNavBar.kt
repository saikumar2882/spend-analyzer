package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.rememberPressScale

/** One destination in [CompactNavBar]. */
data class NavBarItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Bottom navigation for compact screens, about 40% shorter than Material's 80dp `NavigationBar`:
 * a small icon over a one-line label, with the selected item shown by colour alone (no pill
 * indicator, no tonal elevation). Heights are minimums, so a larger system font grows the bar
 * instead of cropping the labels.
 */
@Composable
fun CompactNavBar(items: List<NavBarItem>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val scale = rememberPressScale(interaction)
                val tint = if (item.selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Sizes.minTouchTarget + 4.dp)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = item.onClick
                        )
                        .padding(vertical = Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
