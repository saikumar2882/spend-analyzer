package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NotificationType {
    SUCCESS, ERROR, INFO
}

@Composable
fun AppNotification(
    message: String,
    type: NotificationType = NotificationType.INFO
) {
    val accent = when (type) {
        NotificationType.SUCCESS -> MaterialTheme.colorScheme.secondary
        NotificationType.ERROR -> MaterialTheme.colorScheme.error
        NotificationType.INFO -> MaterialTheme.colorScheme.primary
    }

    val icon = when (type) {
        NotificationType.SUCCESS -> Icons.Rounded.CheckCircle
        NotificationType.ERROR -> Icons.Rounded.Error
        NotificationType.INFO -> Icons.Rounded.Info
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .background(
                            color = accent,
                            shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                        )
                )
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.18f)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
