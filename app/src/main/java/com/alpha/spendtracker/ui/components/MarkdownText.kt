package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A simple markdown-lite renderer that handles bold text, bullet points, and newlines.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trimStart()
            val indentLevel = (line.length - trimmed.length) / 2 // Simple indent detection

            if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                BulletPointLine(trimmed.substring(2), color, style, indentLevel)
            } else if (trimmed.startsWith("#")) {
                val headerLevel = trimmed.takeWhile { it == '#' }.length
                val content = trimmed.drop(headerLevel).trim()
                Text(
                    text = parseBold(content),
                    style = style.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (style.fontSize.value + (4 - headerLevel).coerceAtLeast(0) * 2).sp
                    ),
                    color = color,
                    modifier = Modifier.padding(start = (indentLevel * 16).dp)
                )
            } else if (line.isNotBlank()) {
                Text(
                    text = parseBold(line),
                    style = style,
                    color = color,
                    modifier = Modifier.padding(start = (indentLevel * 16).dp)
                )
            }
        }
    }
}

@Composable
private fun BulletPointLine(
    content: String,
    color: androidx.compose.ui.graphics.Color,
    style: TextStyle,
    indentLevel: Int = 0
) {
    Row(modifier = Modifier.padding(start = (8 + indentLevel * 16).dp)) {
        Text(text = if (indentLevel % 2 == 0) "• " else "◦ ", style = style, color = color)
        Text(text = parseBold(content), style = style, color = color)
    }
}

/**
 * Parses **bold** markers into AnnotatedString.
 */
private fun parseBold(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val pattern = Regex("\\*\\*(.*?)\\*\\*")
        val matches = pattern.findAll(text)

        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex))
    }
}
