package com.alpha.spendtracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendCalendar
import com.alpha.spendtracker.data.SpendCalendar.AmountUnit
import com.alpha.spendtracker.data.SpendRecap
import com.alpha.spendtracker.ui.components.HistorySpendCard
import com.alpha.spendtracker.ui.components.SwipeableLogCard
import com.alpha.spendtracker.ui.components.formatCurrency
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.ui.theme.asMoney
import com.alpha.spendtracker.ui.theme.isAppInDarkTheme
import com.alpha.spendtracker.ui.theme.rememberPressScale
import com.alpha.spendtracker.util.activeAppLocale
import com.alpha.spendtracker.util.formatDate
import com.alpha.spendtracker.util.formatMonth
import java.text.DateFormatSymbols
import java.util.TimeZone

/**
 * List-or-calendar choice and the month being browsed on History.
 *
 * ⚠️ Like [MonthChoices], this is created above the screen in `MainContainer`: `AnimatedContent`
 * drops a screen's own state when you navigate away, so after editing a log from the calendar the
 * user would land back on the list, in the current month.
 *
 * [monthIndex] is a [SpendCalendar.monthIndex], or [NO_MONTH] to follow the current month until
 * the user steps to another one.
 */
@Stable
class HistoryCalendarState(showCalendar: Boolean = false, monthIndex: Int = NO_MONTH) {
    var showCalendar by mutableStateOf(showCalendar)
    var monthIndex by mutableIntStateOf(monthIndex)

    companion object {
        const val NO_MONTH = -1

        val Saver: Saver<HistoryCalendarState, Any> = listSaver<HistoryCalendarState, Any>(
            save = { listOf(it.showCalendar, it.monthIndex) },
            restore = { HistoryCalendarState(it[0] as Boolean, it[1] as Int) }
        )
    }
}

@Composable
fun rememberHistoryCalendarState(): HistoryCalendarState =
    rememberSaveable(saver = HistoryCalendarState.Saver) { HistoryCalendarState() }

// Shade of a day cell by SpendCalendar level; neutral accent only, so a dark cell never reads as "bad".
// Dark mode runs lower: the same alpha of a light accent on a near-black canvas glows.
private val LevelAlpha = floatArrayOf(0f, 0.05f, 0.12f, 0.22f, 0.34f)
private val DarkLevelAlpha = floatArrayOf(0f, 0.05f, 0.09f, 0.14f, 0.20f)

/**
 * The one colour the grid is tinted with. Dark mode's primary is an electric violet that turned
 * busy days into glowing blocks and today's outline into the loudest thing on screen, so it is
 * pulled toward the neutral text colour: same hue family as the canvas, far less saturation. Derived
 * from the scheme rather than hard-coded so dynamic colour and OLED follow along.
 */
@Composable
private fun calendarAccent(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isAppInDarkTheme) lerp(scheme.primary, scheme.onSurfaceVariant, 0.6f) else scheme.primary
}

/** Same look as the Search and Filter buttons: tinted while the calendar is the active view. */
@Composable
internal fun CalendarToggleButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interaction)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .size(Sizes.minTouchTarget)
            .scale(pressScale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = stringResource(
                    if (active) R.string.history_show_list else R.string.history_show_calendar
                ),
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconAction)
            )
        }
    }
}

/**
 * A month grid with each day's total. [spends] is whatever History is currently showing, so the
 * search, chips and filters narrow the calendar the same way they narrow the list. Tapping a day
 * opens that day's logs in a sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpendCalendarView(
    spends: List<Spend>,
    state: HistoryCalendarState,
    onEditSpend: (Spend) -> Unit,
    onDeleteSpend: (Spend) -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tz = remember { TimeZone.getDefault() }
    val now = remember { System.currentTimeMillis() }
    val bounds = remember(spends) { SpendCalendar.monthBounds(spends, now, tz) }
    val shownIndex = (
        if (state.monthIndex == HistoryCalendarState.NO_MONTH) SpendCalendar.monthIndexOf(now, tz)
        else state.monthIndex
    ).coerceIn(bounds)
    val month = remember(spends, shownIndex) {
        SpendCalendar.build(spends, SpendCalendar.yearOf(shownIndex), SpendCalendar.monthOf(shownIndex), tz)
    }
    val todayKey = remember(now) { SpendRecap.dayKey(now, tz) }
    var selectedDay by rememberSaveable { mutableLongStateOf(0L) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
    ) {
        MonthNavigator(
            month = month,
            canGoBack = shownIndex > bounds.first,
            canGoForward = shownIndex < bounds.last,
            onPrevious = { state.monthIndex = shownIndex - 1 },
            onNext = { state.monthIndex = shownIndex + 1 }
        )
        WeekdayHeader()
        val cells = month.leadingBlanks + month.days.size
        repeat((cells + 6) / 7) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                repeat(7) { column ->
                    val day = month.days.getOrNull(row * 7 + column - month.leadingBlanks)
                    if (day == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        DayCell(
                            day = day,
                            level = SpendCalendar.level(day, month.peak),
                            isToday = SpendRecap.dayKey(day.start, tz) == todayKey,
                            onClick = { selectedDay = day.start },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }

    if (selectedDay != 0L) {
        val daySpends = remember(spends, selectedDay) { SpendCalendar.spendsOn(spends, selectedDay, tz) }
        // Deleting the last log of the day leaves nothing to show.
        LaunchedEffect(daySpends.isEmpty()) {
            if (daySpends.isEmpty()) selectedDay = 0L
        }
        if (daySpends.isNotEmpty()) {
            DaySpendsSheet(
                dayStart = selectedDay,
                spends = daySpends,
                onDismiss = { selectedDay = 0L },
                onEditSpend = onEditSpend,
                onDeleteSpend = onDeleteSpend,
                onOpenNote = onOpenNote
            )
        }
    }
}

@Composable
private fun MonthNavigator(
    month: SpendCalendar.Month,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = canGoBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_prev_month)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatMonth(month.days.first().start),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (month.activeDays == 0) {
                    stringResource(R.string.calendar_empty_month)
                } else {
                    stringResource(
                        R.string.calendar_month_summary,
                        "₹${formatCurrency(month.total)}",
                        month.activeDays,
                        month.days.size
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month)
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    // DateFormatSymbols indexes Sunday as 1; the grid is Monday-first like the rest of the app.
    val names = remember(activeAppLocale) {
        val symbols = DateFormatSymbols.getInstance(activeAppLocale).shortWeekdays
        List(7) { symbols[(SpendRecap.WEEK_START - 1 + it) % 7 + 1] }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics { }
    ) {
        names.forEach { name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DayCell(
    day: SpendCalendar.Day,
    level: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interaction)
    val accent = calendarAccent()
    val description = if (day.hasSpend) {
        stringResource(R.string.calendar_day_description, day.dayOfMonth, "₹${formatCurrency(day.total)}")
    } else {
        day.dayOfMonth.toString()
    }
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = day.hasSpend,
        interactionSource = interaction,
        shape = RoundedCornerShape(Radius.sm),
        color = accent.copy(alpha = (if (isAppInDarkTheme) DarkLevelAlpha else LevelAlpha)[level]),
        border = if (isToday) BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null,
        modifier = modifier
            .padding(2.dp)
            .scale(pressScale)
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description }
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = Spacing.xs)
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isToday) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (day.hasSpend) {
                Text(
                    text = compactAmountText(day.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun compactAmountText(amount: Double): String {
    val compact = SpendCalendar.compact(amount)
    return when (compact.unit) {
        AmountUnit.ONES -> compact.value
        AmountUnit.THOUSAND -> stringResource(R.string.calendar_amount_thousand, compact.value)
        AmountUnit.LAKH -> stringResource(R.string.calendar_amount_lakh, compact.value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySpendsSheet(
    dayStart: Long,
    spends: List<Spend>,
    onDismiss: () -> Unit,
    onEditSpend: (Spend) -> Unit,
    onDeleteSpend: (Spend) -> Unit,
    onOpenNote: (String) -> Unit
) {
    val context = LocalContext.current
    val total = remember(spends) { spends.sumOf { it.amount } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(dayStart, context),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "₹${formatCurrency(total)}",
                    style = MaterialTheme.typography.titleMedium.asMoney(),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(spends, key = { it.uuid }) { spend ->
                    SwipeableLogCard(
                        onEdit = { onEditSpend(spend) },
                        onDelete = { onDeleteSpend(spend) }
                    ) {
                        HistorySpendCard(
                            spend = spend,
                            onEdit = { onEditSpend(spend) },
                            onDelete = { onDeleteSpend(spend) },
                            onClick = if (spend.noteUuid.isNotBlank()) {
                                { onOpenNote(spend.noteUuid) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
