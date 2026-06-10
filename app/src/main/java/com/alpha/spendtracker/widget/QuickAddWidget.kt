package com.alpha.spendtracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import com.alpha.spendtracker.MainActivity
import com.alpha.spendtracker.R

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
                // Main Pill: Track with AI
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(56.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(28.dp)
                        .clickable(intentAction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = GlanceModifier.width(16.dp))
                    
                    Image(
                        provider = ImageProvider(R.drawable.ic_ai_stars),
                        contentDescription = null,
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                    )
                    
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    
                    Text(
                        text = "Track with AI...",
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 15.sp
                        )
                    )
                    
                    Spacer(modifier = GlanceModifier.width(16.dp))
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Separate Circular Button: Send
                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(28.dp)
                        .clickable(intentAction),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_send_widget),
                        contentDescription = null,
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                    )
                }
            }
        }
    }
}
