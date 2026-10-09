/**
 * Clean, modern Category Selection Grid component matching the app's Material 3 design system.
 * Displays category items with soft tinted icon badges, crisp icons, and localized labels.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.ui.theme.isAppInDarkTheme
import com.alpha.spendtracker.ui.theme.rememberPressScale

/**
 * Representation of a category preset item with its icon, theme color, and aliases.
 */
data class CategoryPresetItem(
    val canonicalName: String,
    val displayName: String,
    val icon: ImageVector,
    val lightColor: Color,
    val darkColor: Color,
    val aliases: List<String> = emptyList()
) {
    @Composable
    fun getColor(): Color = if (isAppInDarkTheme) darkColor else lightColor
}

val ALL_CATEGORY_GRID_ITEMS = listOf(
    CategoryPresetItem(
        canonicalName = "Food & Drinks",
        displayName = "Food & Drinks",
        icon = Icons.Rounded.Restaurant,
        lightColor = Color(0xFFEF4444),
        darkColor = Color(0xFFF87171),
        aliases = listOf("Food & Drinks", "Food", "Dining", "Restaurants", "Food & Dining")
    ),
    CategoryPresetItem(
        canonicalName = "Groceries",
        displayName = "Groceries",
        icon = Icons.Rounded.ShoppingBasket,
        lightColor = Color(0xFF10B981),
        darkColor = Color(0xFF34D399),
        aliases = listOf("Groceries", "Supermarket", "Kirana", "Grocery")
    ),
    CategoryPresetItem(
        canonicalName = "Shopping & Apparels",
        displayName = "Shopping",
        icon = Icons.Rounded.ShoppingBag,
        lightColor = Color(0xFF06B6D4),
        darkColor = Color(0xFF22D3EE),
        aliases = listOf("Shopping & Apparels", "Shopping", "Apparel", "Clothes", "E-Commerce")
    ),
    CategoryPresetItem(
        canonicalName = "Rent & Utilities",
        displayName = "Bills & Utilities",
        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
        lightColor = Color(0xFF84CC16),
        darkColor = Color(0xFFA3E635),
        aliases = listOf("Rent & Utilities", "Bills", "Bills & Utilities", "Utilities", "Rent")
    ),
    CategoryPresetItem(
        canonicalName = "Credit Card Bill",
        displayName = "EMI & Cards",
        icon = Icons.Rounded.CreditCard,
        lightColor = Color(0xFFF59E0B),
        darkColor = Color(0xFFFBBF24),
        aliases = listOf("Credit Card Bill", "EMI", "EMI & Cards", "Credit Card", "Loan")
    ),
    CategoryPresetItem(
        canonicalName = "Fuel",
        displayName = "Fuel",
        icon = Icons.Rounded.LocalGasStation,
        lightColor = Color(0xFFF97316),
        darkColor = Color(0xFFFB923C),
        aliases = listOf("Fuel", "Petrol", "Gas", "Diesel")
    ),
    CategoryPresetItem(
        canonicalName = "Travel & Commute",
        displayName = "Travel",
        icon = Icons.Rounded.DirectionsCar,
        lightColor = Color(0xFF3B82F6),
        darkColor = Color(0xFF60A5FA),
        aliases = listOf("Travel & Commute", "Travel", "Commute", "Cab", "Taxi", "Flight", "Bus")
    ),
    CategoryPresetItem(
        canonicalName = "Subscription & Leisure",
        displayName = "Entertainment",
        icon = Icons.Rounded.AutoAwesome,
        lightColor = Color(0xFF8B5CF6),
        darkColor = Color(0xFFA78BFA),
        aliases = listOf("Subscription & Leisure", "Entertainment", "Leisure", "Subscriptions", "Movies")
    ),
    CategoryPresetItem(
        canonicalName = "Healthcare & Medical",
        displayName = "Health",
        icon = Icons.Rounded.Favorite,
        lightColor = Color(0xFFEC4899),
        darkColor = Color(0xFFF472B6),
        aliases = listOf("Healthcare & Medical", "Health", "Medical", "Pharmacy", "Doctor")
    ),
    CategoryPresetItem(
        canonicalName = "Investment",
        displayName = "Investment",
        icon = Icons.Rounded.Savings,
        lightColor = Color(0xFF6366F1),
        darkColor = Color(0xFF818CF8),
        aliases = listOf("Investment", "Investments", "Savings", "Stocks", "Mutual Funds")
    ),
    CategoryPresetItem(
        canonicalName = "Lending",
        displayName = "Lending",
        icon = Icons.AutoMirrored.Rounded.CallMade,
        lightColor = Color(0xFF10B981),
        darkColor = Color(0xFF34D399),
        aliases = listOf("Lending", "Lend", "Money Given", "Transfer", "Send")
    ),
    CategoryPresetItem(
        canonicalName = "Borrowing",
        displayName = "Borrowing",
        icon = Icons.AutoMirrored.Rounded.CallReceived,
        lightColor = Color(0xFFEF4444),
        darkColor = Color(0xFFF87171),
        aliases = listOf("Borrowing", "Borrow", "Money Taken")
    ),
    CategoryPresetItem(
        canonicalName = "Others",
        displayName = "Other",
        icon = Icons.Rounded.MoreHoriz,
        lightColor = Color(0xFF64748B),
        darkColor = Color(0xFF94A3B8),
        aliases = listOf("Others", "Other", "Misc", "Custom")
    )
)

/**
 * Finds the matching [CategoryPresetItem] for any given category or purpose string.
 */
fun findCategoryPresetItem(name: String): CategoryPresetItem {
    if (name.isBlank()) return ALL_CATEGORY_GRID_ITEMS.last()
    val clean = name.trim()
    return ALL_CATEGORY_GRID_ITEMS.firstOrNull { item ->
        item.canonicalName.equals(clean, ignoreCase = true) ||
        item.displayName.equals(clean, ignoreCase = true) ||
        item.aliases.any { it.equals(clean, ignoreCase = true) }
    } ?: ALL_CATEGORY_GRID_ITEMS.firstOrNull { item ->
        item.canonicalName.contains(clean, ignoreCase = true) ||
        clean.contains(item.displayName, ignoreCase = true) ||
        item.aliases.any { clean.contains(it, ignoreCase = true) }
    } ?: ALL_CATEGORY_GRID_ITEMS.last()
}

/**
 * A clean, grid-based Category Selector with soft tinted icon badges and clear labels.
 */
@Composable
fun CategorySelectionGrid(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4
) {
    val items = ALL_CATEGORY_GRID_ITEMS

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    val isSelected = isCategorySelected(selectedCategory, item)

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        CategoryGridTile(
                            item = item,
                            isSelected = isSelected,
                            onClick = { onCategorySelected(item.canonicalName) }
                        )
                    }
                }
                // Pad out empty slots in incomplete rows
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun isCategorySelected(currentSelected: String, item: CategoryPresetItem): Boolean {
    if (currentSelected.isBlank()) return item.canonicalName == "Food & Drinks"
    val clean = currentSelected.trim()
    return item.canonicalName.equals(clean, ignoreCase = true) ||
           item.displayName.equals(clean, ignoreCase = true) ||
           item.aliases.any { it.equals(clean, ignoreCase = true) }
}

@Composable
private fun CategoryGridTile(
    item: CategoryPresetItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource, pressedScale = 0.95f)
    val isDark = isAppInDarkTheme

    val categoryColor = item.getColor()

    // Icon tint: Touch of category color
    val targetIconTint = if (isSelected) {
        categoryColor
    } else {
        categoryColor.copy(alpha = if (isDark) 0.95f else 0.88f)
    }

    val animatedIconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = spring(),
        label = "categoryIconTint"
    )

    // Label text color: Crisp theme-aware black (light) / white (dark) text, category accent when selected
    val targetTextColor = if (isSelected) {
        categoryColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = spring(),
        label = "categoryTextColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(),
        label = "categoryTileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale * scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.displayName,
            tint = animatedIconTint,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = getLocalizedPresetName(item.displayName),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 13.sp
            ),
            color = animatedTextColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Reusable dropdown item row displaying category icon badge alongside category name.
 */
@Composable
fun CategoryDropdownItemContent(
    categoryName: String,
    modifier: Modifier = Modifier
) {
    val item = findCategoryPresetItem(categoryName)
    val categoryColor = item.getColor()
    val isDark = isAppInDarkTheme

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(categoryColor.copy(alpha = if (isDark) 0.22f else 0.12f))
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = getLocalizedPresetName(item.displayName),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
