package com.alpha.spendtracker.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.SpendDao
import com.alpha.spendtracker.ui.components.formatCurrencyRounded
import com.alpha.spendtracker.ui.components.getLocalizedPresetName
import com.alpha.spendtracker.util.activeAppLocale
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Same palette as [QuickAddWidget]'s bar, so the two read as siblings on the home screen,
 * plus a muted text tone for labels and a tonal fill behind the "+" button.
 */
private object SummaryWidgetColors {
    val background = ColorProvider(
        day = Color(0xFFF1EFF9),
        night = Color(0xFF252A38)
    )
    val text = ColorProvider(
        day = Color(0xFF191A23),
        night = Color(0xFFFFFFFF)
    )
    val mutedText = ColorProvider(
        day = Color(0xFF5E5F70),
        night = Color(0xFFB7BBCB)
    )
    val accent = ColorProvider(
        day = Color(0xFF5D45E8),
        night = Color(0xFF9D8BFF)
    )
    val addButton = ColorProvider(
        day = Color(0xFFE2DDFB),
        night = Color(0xFF353B50)
    )
}

/** Hilt graph access for the widget, which Glance instantiates itself (no injection). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpendSummaryWidgetEntryPoint {
    fun spendDao(): SpendDao
    fun aiPreferencesRepository(): AiPreferencesRepository
}

/** Everything the widget renders, resolved to display strings before composition. */
private sealed interface SummaryState {
    data object SignedOut : SummaryState
    data class Loaded(
        val today: String,
        val month: String,
        val topPurpose: String?
    ) : SummaryState
}

private data class SummaryLabels(
    val today: String,
    val monthLine: (String) -> String,
    val topLine: (String) -> String,
    val signedOut: String,
    val quickAdd: String
)

/**
 * Home-screen summary: today's spend big, this month's total below it, and the month's top
 * purpose when there is room. Dues (Lending/Borrowing) and tombstones are excluded, matching
 * the Dashboard. Refreshed by [SpendSummaryWidgetUpdater]; the data is read once per update in
 * [provideGlance], so nothing stays subscribed between refreshes.
 */
class SpendSummaryWidget : GlanceAppWidget() {

    // Tall enough for the third line only on the larger size; Exact lets LocalSize decide.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val localized = localizedContext(context)
        val labels = SummaryLabels(
            today = localized.getString(R.string.summary_widget_today),
            monthLine = { localized.getString(R.string.summary_widget_this_month, it) },
            topLine = { localized.getString(R.string.summary_widget_top_purpose, it) },
            signedOut = localized.getString(R.string.summary_widget_signed_out),
            quickAdd = localized.getString(R.string.summary_widget_quick_add)
        )
        val state = loadSummary(context)

        provideContent {
            GlanceTheme {
                WidgetContent(state, labels)
            }
        }
    }

    private suspend fun loadSummary(context: Context): SummaryState {
        // Room is shared by every account that signed in on this device — scope to the
        // current user, and show nothing personal once signed out.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return SummaryState.SignedOut

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SpendSummaryWidgetEntryPoint::class.java
        )
        val currency = entryPoint.aiPreferencesRepository().aiPreferencesFlow.first().defaultCurrency

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val tomorrowStart = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = cal.timeInMillis
        val nextMonthStart = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis

        val monthSpends = entryPoint.spendDao().getSummaryWidgetSpends(uid, monthStart, nextMonthStart)
        val todayTotal = monthSpends
            .filter { it.timestamp >= todayStart && it.timestamp < tomorrowStart }
            .sumOf { it.amount }
        val monthTotal = monthSpends.sumOf { it.amount }
        val topPurpose = monthSpends
            .filter { it.purpose.isNotBlank() }
            .groupBy { it.purpose }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .maxByOrNull { it.value }
            ?.takeIf { it.value > 0 }
            ?.key

        return SummaryState.Loaded(
            today = "$currency${formatCurrencyRounded(todayTotal)}",
            month = "$currency${formatCurrencyRounded(monthTotal)}",
            topPurpose = topPurpose?.let { getLocalizedPresetName(it) }
        )
    }

    /**
     * The app switches language through AppCompat per-app locales, which a widget's plain
     * application context doesn't pick up below API 33 — resolve strings against it explicitly.
     */
    private fun localizedContext(context: Context): Context {
        val config = Configuration(context.resources.configuration).apply { setLocale(activeAppLocale) }
        return context.createConfigurationContext(config)
    }

    @Composable
    private fun WidgetContent(state: SummaryState, labels: SummaryLabels) {
        val openApp = actionStartActivity<MainActivity>()
        val quickAdd = actionStartActivity<QuickAddInputActivity>()
        val showTopLine = LocalSize.current.height >= TOP_LINE_MIN_HEIGHT

        // Outer container transparent over wallpaper, same inset as QuickAddWidget
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(SummaryWidgetColors.background)
                    .cornerRadius(18.dp)
                    .clickable(openApp)
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (state) {
                        SummaryState.SignedOut -> Text(
                            text = labels.signedOut,
                            maxLines = 2,
                            style = TextStyle(
                                color = SummaryWidgetColors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        is SummaryState.Loaded -> {
                            Text(
                                text = labels.today,
                                maxLines = 1,
                                style = TextStyle(
                                    color = SummaryWidgetColors.mutedText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = state.today,
                                maxLines = 1,
                                style = TextStyle(
                                    color = SummaryWidgetColors.text,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = labels.monthLine(state.month),
                                maxLines = 1,
                                style = TextStyle(
                                    color = SummaryWidgetColors.mutedText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            if (showTopLine && state.topPurpose != null) {
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = labels.topLine(state.topPurpose),
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = SummaryWidgetColors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // "+" opens the same AI quick-add overlay as QuickAddWidget (which itself
                // redirects to sign-in when signed out).
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(SummaryWidgetColors.addButton)
                        .cornerRadius(20.dp)
                        .clickable(quickAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_add_widget),
                        contentDescription = labels.quickAdd,
                        modifier = GlanceModifier.size(22.dp),
                        colorFilter = ColorFilter.tint(SummaryWidgetColors.accent)
                    )
                }
            }
        }
    }

    private companion object {
        val TOP_LINE_MIN_HEIGHT = 110.dp
    }
}
