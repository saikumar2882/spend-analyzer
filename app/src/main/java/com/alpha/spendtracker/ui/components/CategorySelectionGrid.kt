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
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Atm
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Work
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
    // High Frequency
    CategoryPresetItem("Food & Dining", "Food & Dining", Icons.Rounded.Restaurant, Color(0xFFEF4444), Color(0xFFF87171), listOf("Food", "Dining", "Restaurants", "Cafés", "Snacks", "Takeaway")),
    CategoryPresetItem("Groceries", "Groceries", Icons.Rounded.ShoppingBasket, Color(0xFF10B981), Color(0xFF34D399), listOf("Groceries", "Supermarket", "Kirana", "Vegetables", "Daily Essentials")),
    CategoryPresetItem("Housing", "Housing", Icons.Rounded.Home, Color(0xFF8B5CF6), Color(0xFFA78BFA), listOf("Housing", "Rent", "House Rent", "Maintenance", "Repairs")),
    CategoryPresetItem("Commute", "Commute", Icons.Rounded.DirectionsCar, Color(0xFFF97316), Color(0xFFFB923C), listOf("Commute", "Transportation", "Metro", "Bus", "Cab", "Auto")),
    CategoryPresetItem("Lending", "Lending", Icons.AutoMirrored.Rounded.CallMade, Color(0xFF10B981), Color(0xFF34D399), listOf("Lending", "Money Given", "Lent")),
    CategoryPresetItem("Borrowing", "Borrowing", Icons.AutoMirrored.Rounded.CallReceived, Color(0xFFEF4444), Color(0xFFF87171), listOf("Borrowing", "Money Received", "Borrowed")),
    CategoryPresetItem("Shopping", "Shopping", Icons.Rounded.ShoppingBag, Color(0xFF06B6D4), Color(0xFF22D3EE), listOf("Shopping", "Clothing", "Footwear", "Electronics", "Accessories")),

    // Medium Frequency
    CategoryPresetItem("Bills & Utilities", "Bills & Utilities", Icons.AutoMirrored.Rounded.ReceiptLong, Color(0xFF84CC16), Color(0xFFA3E635), listOf("Bills", "Utilities", "Electricity", "Water", "Gas", "Wi-Fi", "DTH", "Mobile Recharge")),
    CategoryPresetItem("EMIs & Cards", "EMIs & Cards", Icons.Rounded.CreditCard, Color(0xFFF59E0B), Color(0xFFFBBF24), listOf("EMIs", "Credit Cards", "Loans", "Loan Principal")),
    CategoryPresetItem("Investments", "Investments", Icons.AutoMirrored.Rounded.ShowChart, Color(0xFF6366F1), Color(0xFF818CF8), listOf("Investments", "Mutual Funds", "SIPs", "Stocks", "Gold")),
    CategoryPresetItem("Savings", "Savings", Icons.Rounded.Savings, Color(0xFF10B981), Color(0xFF34D399), listOf("Savings", "Emergency Fund", "Recurring Deposits")),
    CategoryPresetItem("Subscriptions", "Subscriptions", Icons.Rounded.Subscriptions, Color(0xFF3B82F6), Color(0xFF60A5FA), listOf("Subscriptions", "Digital Services", "OTT", "Music", "Apps")),
    CategoryPresetItem("Healthcare", "Healthcare", Icons.Rounded.LocalHospital, Color(0xFFEC4899), Color(0xFFF472B6), listOf("Healthcare", "Medical", "Medicines", "Doctors", "Hospital")),
    CategoryPresetItem("Personal Care", "Personal Care", Icons.Rounded.Face, Color(0xFFEC4899), Color(0xFFF472B6), listOf("Personal Care", "Grooming", "Salon", "Skincare", "Cosmetics")),

    // Low Frequency
    CategoryPresetItem("Education", "Education", Icons.Rounded.School, Color(0xFF3B82F6), Color(0xFF60A5FA), listOf("Education", "Learning", "Courses", "Tuition", "Books")),
    CategoryPresetItem("Entertainment", "Entertainment", Icons.Rounded.AutoAwesome, Color(0xFF8B5CF6), Color(0xFFA78BFA), listOf("Entertainment", "Leisure", "Movies", "Gaming", "Events")),
    CategoryPresetItem("Travel", "Travel", Icons.Rounded.Flight, Color(0xFF3B82F6), Color(0xFF60A5FA), listOf("Travel", "Accommodation", "Flights", "Trains", "Hotels", "Holiday")),
    CategoryPresetItem("Insurance & Taxes", "Insurance & Taxes", Icons.Rounded.Shield, Color(0xFF06B6D4), Color(0xFF22D3EE), listOf("Insurance", "Taxes", "Life Insurance", "Vehicle Insurance")),
    CategoryPresetItem("Gifts & Donations", "Gifts & Donations", Icons.Rounded.CardGiftcard, Color(0xFFF43F5E), Color(0xFFFB7185), listOf("Gifts", "Donations", "Family", "Charity", "Festivals")),
    CategoryPresetItem("Fitness", "Fitness", Icons.Rounded.FitnessCenter, Color(0xFFF97316), Color(0xFFFB923C), listOf("Fitness", "Sports", "Gym", "Classes")),
    CategoryPresetItem("Pets", "Pets", Icons.Rounded.Pets, Color(0xFFA855F7), Color(0xFFC084FC), listOf("Pets", "Pet Food", "Grooming", "Veterinary")),
    CategoryPresetItem("Work Expenses", "Work Expenses", Icons.Rounded.Work, Color(0xFF64748B), Color(0xFF94A3B8), listOf("Work", "Professional Expenses", "Office Expenses")),
    CategoryPresetItem("Miscellaneous", "Miscellaneous", Icons.Rounded.MoreHoriz, Color(0xFF64748B), Color(0xFF94A3B8), listOf("Miscellaneous", "Unexpected", "Repairs", "Emergencies"))
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

fun getOrderedCategoryGridItems(customOrder: List<String>): List<CategoryPresetItem> {
    if (customOrder.isEmpty()) return ALL_CATEGORY_GRID_ITEMS
    val itemMap = ALL_CATEGORY_GRID_ITEMS.associateBy { it.canonicalName }
    val orderedList = mutableListOf<CategoryPresetItem>()
    val visited = mutableSetOf<String>()

    for (name in customOrder) {
        itemMap[name]?.let {
            if (visited.add(it.canonicalName)) {
                orderedList.add(it)
            }
        }
    }

    for (item in ALL_CATEGORY_GRID_ITEMS) {
        if (visited.add(item.canonicalName)) {
            orderedList.add(item)
        }
    }

    return orderedList
}

/**
 * A clean, grid-based Category Selector with soft tinted icon badges and clear labels.
 */
@Composable
fun CategorySelectionGrid(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    items: List<CategoryPresetItem> = ALL_CATEGORY_GRID_ITEMS,
    columns: Int = 4
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth().height(260.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.canonicalName }) { item ->
            val isSelected = isCategorySelected(selectedCategory, item)
            CategoryGridTile(
                item = item,
                isSelected = isSelected,
                onClick = { onCategorySelected(item.canonicalName) }
            )
        }
    }
}

private fun isCategorySelected(currentSelected: String, item: CategoryPresetItem): Boolean {
    if (currentSelected.isBlank()) return item.canonicalName == "Food & Dining"
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

    val targetIconTint = if (isSelected) categoryColor else categoryColor.copy(alpha = if (isDark) 0.95f else 0.88f)
    val animatedIconTint by animateColorAsState(targetValue = targetIconTint, animationSpec = spring(), label = "categoryIconTint")

    val targetTextColor = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface
    val animatedTextColor by animateColorAsState(targetValue = targetTextColor, animationSpec = spring(), label = "categoryTextColor")

    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 1.0f, animationSpec = spring(), label = "categoryTileScale")

    val surfaceTint = if (isSelected) categoryColor.copy(alpha = 0.08f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale * scale)
            .padding(4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(surfaceTint)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
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
