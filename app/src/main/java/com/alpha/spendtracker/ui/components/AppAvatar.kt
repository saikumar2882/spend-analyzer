/**
 * Composable for displaying an application icon or avatar based on the app name.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppAvatar(
    name: String,
    color: Color,
    size: Dp = 44.dp
) {
    val initials = remember(name) { initialsFor(name) }
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = 0.16f),
                        color.copy(alpha = 0.04f)
                    )
                ),
                CircleShape
            )
            .border(1.2.dp, color.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = (size.value * 0.36f).sp
            )
        )
    }
}

private fun initialsFor(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val parts = trimmed.split(' ', '_', '-').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        trimmed.length >= 2 -> trimmed.take(2).uppercase()
        else -> trimmed.take(1).uppercase()
    }
}
