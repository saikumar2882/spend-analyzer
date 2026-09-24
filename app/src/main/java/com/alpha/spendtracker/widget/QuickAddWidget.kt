package com.alpha.spendtracker.widget

import android.content.Context
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.alpha.spendtracker.R

/**
 * Adaptive Google-style search/input bar widget colors.
 * Automatically adapts to the phone's Light and Dark system themes.
 */
private object AdaptiveWidgetColors {
    val barBackground = ColorProvider(
        day = Color(0xFFF1EFF9),
        night = Color(0xFF252A38)
    )
    val text = ColorProvider(
        day = Color(0xFF191A23),
        night = Color(0xFFFFFFFF)
    )
    val iconTint = ColorProvider(
        day = Color(0xFF5D45E8),
        night = Color(0xFF9D8BFF)
    )
}

private val VOICE_PARAM_KEY = ActionParameters.Key<Boolean>("SHOW_VOICE_INPUT")

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        // Action to open AI input text overlay
        val aiInputIntentAction = actionStartActivity<QuickAddInputActivity>()

        // Action to open Voice input overlay
        val voiceIntentAction = actionStartActivity<QuickAddInputActivity>(
            actionParametersOf(VOICE_PARAM_KEY to true)
        )

        // Outer container transparent over wallpaper
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            // Unified Bar matching app theme with 18.dp corner radius
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(AdaptiveWidgetColors.barBackground)
                    .cornerRadius(18.dp)
                    .padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left region (Spark icon + "Track with AI...")
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .clickable(aiInputIntentAction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_ai_stars),
                        contentDescription = "Track with AI",
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(AdaptiveWidgetColors.iconTint)
                    )

                    Spacer(modifier = GlanceModifier.width(10.dp))

                    Text(
                        text = "Track with AI...",
                        style = TextStyle(
                            color = AdaptiveWidgetColors.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Right icons section (Mic icon BEFORE Send icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxHeight()
                ) {
                    // Mic Icon Button
                    Box(
                        modifier = GlanceModifier
                            .size(38.dp)
                            .cornerRadius(19.dp)
                            .clickable(voiceIntentAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_mic_widget),
                            contentDescription = "Voice Input",
                            modifier = GlanceModifier.size(22.dp),
                            colorFilter = ColorFilter.tint(AdaptiveWidgetColors.iconTint)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(4.dp))

                    // Send Icon Button
                    Box(
                        modifier = GlanceModifier
                            .size(38.dp)
                            .cornerRadius(19.dp)
                            .clickable(aiInputIntentAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_send_widget),
                            contentDescription = "Send",
                            modifier = GlanceModifier.size(20.dp),
                            colorFilter = ColorFilter.tint(AdaptiveWidgetColors.iconTint)
                        )
                    }
                }
            }
        }
    }
}
