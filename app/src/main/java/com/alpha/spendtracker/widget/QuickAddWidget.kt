package com.alpha.spendtracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.padding
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import com.alpha.spendtracker.R
import com.alpha.spendtracker.MainActivity

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(R.color.white),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Spendly", style = TextStyle(color = ColorProvider(R.color.black)))
                Row(modifier = GlanceModifier.padding(8.dp)) {
                    Button(
                        text = "Quick AI Log",
                        onClick = actionStartActivity<MainActivity>()
                    )
                }
            }
        }
    }
}
