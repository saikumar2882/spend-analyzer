package com.alpha.spendtracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.R

/**
 * "Neon Counter" palette mirrored from [com.alpha.spendtracker.ui.theme.Color].
 *
 * Glance widgets cannot read the Compose MaterialTheme / app ColorScheme, so the
 * accent colors are hardcoded here as day/night ColorProviders that track the
 * home screen's light/dark mode and stay in step with the redesigned app.
 */
private object WidgetColors {
    // Brand primary violet — the quick-add pill & send button fill.
    // day = LightPrimary (#5D45E8), night = DarkPrimary (#9D8BFF)
    val primary = ColorProvider(Color(0xFF5D45E8))

    // Content (icon + label) on the primary fill — mirrors the app's onPrimary tokens.
    // day = LightOnPrimary (white) on the saturated indigo; night = DarkOnPrimary
    // (#1B1340) deep ink on the pale lavender accent, where white would be low-contrast.
    val onPrimary = ColorProvider(Color(0xFFFFFFFF))
}

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
        val intentAction = actionStartActivity<MainActivity>(
            actionParametersOf(
                ActionParameters.Key<Boolean>("SHOW_AI_INPUT") to true
            )
        )

        // Outer container is transparent to show the wallpaper
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp), // Minor padding for better spacing on home screen
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Pill: Track with AI — brand primary violet fill
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(56.dp)
                        .background(WidgetColors.primary)
                        .cornerRadius(28.dp)
                        .clickable(intentAction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = GlanceModifier.width(16.dp))

                    Image(
                        provider = ImageProvider(R.drawable.ic_ai_stars),
                        contentDescription = null,
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(WidgetColors.onPrimary)
                    )

                    Spacer(modifier = GlanceModifier.width(12.dp))

                    Text(
                        text = "Track with AI...",
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = WidgetColors.onPrimary,
                            fontSize = 15.sp
                        )
                    )

                    Spacer(modifier = GlanceModifier.width(16.dp))
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Separate Circular Button: Send — matching brand primary violet fill
                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .background(WidgetColors.primary)
                        .cornerRadius(28.dp)
                        .clickable(intentAction),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_send_widget),
                        contentDescription = null,
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(WidgetColors.onPrimary)
                    )
                }
            }
        }
    }
}
