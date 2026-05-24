/**
 * Predefined categories and application presets for logging expenses.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.ui.graphics.Color

data class AppPreset(
    val id: String,
    val displayName: String,
    val category: String,
    val color: Color
)

val APP_PRESETS = listOf(
    AppPreset("google_pay", "Google Pay", "UPI Apps", Color(0xFF1A73E8)),
    AppPreset("phone_pe", "PhonePe", "UPI Apps", Color(0xFF5F259F)),
    AppPreset("paytm", "Paytm", "UPI Apps", Color(0xFF00B9F5)),
    
    AppPreset("swiggy", "Swiggy", "Quick Commerce", Color(0xFFFC8019)),
    AppPreset("zepto", "Zepto", "Quick Commerce", Color(0xFF5B21B6)),
    AppPreset("blinkit", "Blinkit", "Quick Commerce", Color(0xFFFFC200)),
    
    AppPreset("amazon", "Amazon", "E-Commerce", Color(0xFFFF9900)),
    AppPreset("flipkart", "Flipkart", "E-Commerce", Color(0xFF1A65E6)),
    AppPreset("myntra", "Myntra", "E-Commerce", Color(0xFFE61A5B)),
    AppPreset("ajio", "Ajio", "E-Commerce", Color(0xFF0F172A)),
    
    AppPreset("icici", "ICICI Bank", "Banking & Cards", Color(0xFFE05F04)),
    AppPreset("yono_sbi", "Yono SBI", "Banking & Cards", Color(0xFF1E1B4B)),
    AppPreset("credit_card", "SBI Credit Card", "Banking & Cards", Color(0xFF0D9488)),
    
    AppPreset("other", "Other Platform", "Other", Color(0xFF6B7280))
)

val CATEGORY_PRESETS = listOf(
    "UPI Apps",
    "Quick Commerce",
    "E-Commerce",
    "Banking & Cards",
    "Other"
)

val PURPOSE_PRESETS = listOf(
    "Groceries & Food",
    "Shopping & Apparels",
    "Lending",
    "Borrowing",
    "Credit Card Bill",
    "Rent & Utilities",
    "Travel & Commute",
    "Subscription & Leisure",
    "Healthcare & Medical",
    "Others"
)
