package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A rich representative Markdown renderer that handles tables, group cards,
 * callouts/insights, bullet points with progress bars, headers, and bold text.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> RenderHeader(block)
                is MarkdownBlock.InsightCallout -> RenderInsightCallout(block)
                is MarkdownBlock.Table -> RenderTable(block)
                is MarkdownBlock.GroupCard -> RenderGroupCard(block)
                is MarkdownBlock.BulletList -> RenderBulletList(block, color, style)
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseBold(block.text),
                        style = style,
                        color = color
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Block Models & Parser
// ------------------------------------------------------------------------------------------

sealed interface MarkdownBlock {
    data class Header(val title: String, val level: Int) : MarkdownBlock
    data class InsightCallout(val title: String, val content: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data class GroupCard(
        val title: String,
        val totalAmount: String?,
        val percentage: String?,
        val items: List<GroupItem>
    ) : MarkdownBlock
    data class BulletList(val items: List<BulletItem>) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

data class GroupItem(
    val date: String?,
    val amount: String?,
    val note: String?,
    val rawText: String
)

data class BulletItem(
    val content: String,
    val level: Int = 0,
    val percentage: Float? = null
)

internal fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    if (rawText.isBlank()) return emptyList()

    val cleanText = cleanCurrency(rawText)
    val lines = cleanText.lines()
    val blocks = mutableListOf<MarkdownBlock>()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isBlank()) {
            i++
            continue
        }

        // 1. Table Detection
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size) {
            val nextTrimmed = lines[i + 1].trim()
            if (nextTrimmed.startsWith("|") && nextTrimmed.contains("-")) {
                val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                i += 2 // skip header and separator
                val tableRows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val rowCells = lines[i].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (rowCells.isNotEmpty()) {
                        tableRows.add(rowCells)
                    }
                    i++
                }
                blocks.add(MarkdownBlock.Table(headers, tableRows))
                continue
            }
        }

        // 2. Insight / Callout Detection
        val insightRegex = Regex("""^(\*\*|__)?(Insight|Note|Tip|Summary|Recommendation)\s*:?\s*(\*\*|__)?\s*(.*)$""", RegexOption.IGNORE_CASE)
        val insightMatch = insightRegex.find(trimmed)
        if (insightMatch != null) {
            val title = insightMatch.groupValues[2].replaceFirstChar { it.uppercase() }
            val rest = insightMatch.groupValues[4]
            val contentBuilder = StringBuilder(rest)
            i++
            while (i < lines.size) {
                val next = lines[i].trim()
                if (next.isBlank() || next.startsWith("#") || next.startsWith("* ") || next.startsWith("- ") || next.startsWith("|")) {
                    break
                }
                contentBuilder.append(" ").append(next)
                i++
            }
            blocks.add(MarkdownBlock.InsightCallout(title = title, content = contentBuilder.toString().trim()))
            continue
        }

        // 3. Group Card Detection (e.g. "* **Roja** (Total: **₹100,000.00**)" followed by indented sub-items)
        if ((trimmed.startsWith("* ") || trimmed.startsWith("- ")) && i + 1 < lines.size) {
            val nextLine = lines[i + 1]
            if (nextLine.startsWith("  ") || nextLine.startsWith("\t")) {
                val parentText = trimmed.substring(2).trim()
                val (groupTitle, totalAmount, percentage) = extractGroupTitleAndTotal(parentText)

                val items = mutableListOf<GroupItem>()
                i++ // move to sub-items
                while (i < lines.size) {
                    val subLine = lines[i]
                    if (!subLine.startsWith("  ") && !subLine.startsWith("\t") && subLine.isNotBlank()) {
                        break
                    }
                    val subTrimmed = subLine.trim()
                    if (subTrimmed.startsWith("- ") || subTrimmed.startsWith("* ")) {
                        val itemText = subTrimmed.substring(2).trim()
                        items.add(parseGroupItem(itemText))
                    } else if (subTrimmed.isNotBlank()) {
                        items.add(parseGroupItem(subTrimmed))
                    }
                    i++
                }

                blocks.add(
                    MarkdownBlock.GroupCard(
                        title = groupTitle,
                        totalAmount = totalAmount,
                        percentage = percentage,
                        items = items
                    )
                )
                continue
            }
        }

        // 4. Header Detection
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val title = trimmed.drop(level).trim()
            blocks.add(MarkdownBlock.Header(title = title, level = level))
            i++
            continue
        } else if (trimmed.startsWith("**") && trimmed.endsWith("**") && trimmed.length > 4 && !trimmed.contains("\n")) {
            // Standalone bold line as section header
            val title = trimmed.substring(2, trimmed.length - 2).trim()
            blocks.add(MarkdownBlock.Header(title = title, level = 2))
            i++
            continue
        }

        // 5. Bullet List Detection
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
            val bulletItems = mutableListOf<BulletItem>()
            while (i < lines.size) {
                val bLine = lines[i]
                val bTrimmed = bLine.trim()
                if (!bTrimmed.startsWith("* ") && !bTrimmed.startsWith("- ") && !bTrimmed.startsWith("• ")) {
                    break
                }
                val indentLevel = (bLine.length - bLine.trimStart().length) / 2
                val content = bTrimmed.substring(2).trim()
                val pct = extractPercentage(content)
                bulletItems.add(BulletItem(content = content, level = indentLevel, percentage = pct))
                i++
            }
            blocks.add(MarkdownBlock.BulletList(bulletItems))
            continue
        }

        // 6. Paragraph Fallback
        blocks.add(MarkdownBlock.Paragraph(trimmed))
        i++
    }

    return blocks
}

// ------------------------------------------------------------------------------------------
// UI Composables for Blocks
// ------------------------------------------------------------------------------------------

@Composable
private fun RenderHeader(header: MarkdownBlock.Header) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = parseBold(header.title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (header.level <= 2) 16.sp else 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RenderGroupCard(group: MarkdownBlock.GroupCard) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = group.title.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (group.totalAmount != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = group.totalAmount,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (group.items.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.date != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = item.date,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = item.note ?: item.rawText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (item.amount != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.amount,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderTable(table: MarkdownBlock.Table) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                table.headers.forEach { header ->
                    Text(
                        text = header.trim().replace("**", ""),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .widthIn(min = 90.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Data Rows
            table.rows.forEachIndexed { rowIndex, row ->
                val bg = if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainerLow

                Row(
                    modifier = Modifier
                        .background(bg, RoundedCornerShape(6.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { cell ->
                        Text(
                            text = parseBold(cell.trim()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .widthIn(min = 90.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderInsightCallout(insight: MarkdownBlock.InsightCallout) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = parseBold(insight.content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RenderBulletList(list: MarkdownBlock.BulletList, color: Color, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.items.forEach { item ->
            Column(modifier = Modifier.padding(start = (item.level * 16).dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (item.level % 2 == 0) "• " else "◦ ",
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = parseBold(item.content),
                        style = style,
                        color = color,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (item.percentage != null && item.percentage > 0f) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { item.percentage.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .padding(start = 16.dp, top = 2.dp)
                            .fillMaxWidth(0.85f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Helper Parsing Functions
// ------------------------------------------------------------------------------------------

private fun cleanCurrency(text: String): String {
    return text
        .replace("\u2011", "-")
        .replace("\u2212", "-")
        .replace("₹‑", "₹")
        .replace("₹-", "₹")
        .replace("₹ -", "₹")
}

private fun extractGroupTitleAndTotal(text: String): Triple<String, String?, String?> {
    val clean = text.replace("**", "").trim()
    val totalRegex = Regex("""\(?(Total\s*:?\s*)?([₹$][\d,]+(\.\d{2})?)\)?""", RegexOption.IGNORE_CASE)
    val match = totalRegex.find(clean)

    val totalAmount = match?.groupValues?.get(2)
    var title = if (match != null) clean.removeRange(match.range).trim() else clean
    title = title.removeSuffix(":").removeSuffix("-").trim()

    val pctMatch = Regex("""(\d{1,3})%""").find(text)
    val percentage = pctMatch?.value

    return Triple(title.ifBlank { "Group" }, totalAmount, percentage)
}

private fun parseGroupItem(raw: String): GroupItem {
    val clean = raw.replace("**", "").trim()
    val dateRegex = Regex("""\b(20\d{2}-\d{2}-\d{2})\b""")
    val amountRegex = Regex("""([₹$][\d,]+(\.\d{2})?)""")

    val dateMatch = dateRegex.find(clean)
    val amountMatch = amountRegex.find(clean)

    val date = dateMatch?.value
    val amount = amountMatch?.value

    var rest = clean
    if (dateMatch != null) rest = rest.replace(dateMatch.value, "")
    if (amountMatch != null) rest = rest.replace(amountMatch.value, "")

    rest = rest.replace("—", "").replace("-", "").replace(":", "").trim()

    return GroupItem(
        date = date,
        amount = amount,
        note = rest.ifBlank { null },
        rawText = clean
    )
}

private fun extractPercentage(text: String): Float? {
    val match = Regex("""\(?(\d{1,3})%\)?""").find(text) ?: return null
    val pctInt = match.groupValues[1].toFloatOrNull() ?: return null
    return pctInt / 100f
}

/**
 * Parses **bold** markers into AnnotatedString.
 */
private fun parseBold(text: String): AnnotatedString {
    val clean = cleanCurrency(text)
    return buildAnnotatedString {
        var currentIndex = 0
        val pattern = Regex("\\*\\*(.*?)\\*\\*")
        val matches = pattern.findAll(clean)

        for (match in matches) {
            append(clean.substring(currentIndex, match.range.first))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        append(clean.substring(currentIndex))
    }
}

