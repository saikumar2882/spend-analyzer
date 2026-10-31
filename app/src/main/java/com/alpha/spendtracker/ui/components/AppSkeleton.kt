/**
 * Skeleton loading placeholders for Spend Tracker UI.
 */
package com.alpha.spendtracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Spacing

/**
 * Animated pulse shimmer effect for skeleton placeholder blocks.
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    return this.graphicsLayer { this.alpha = alpha }
}

@Composable
fun SkeletonBlock(
    modifier: Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.md),
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .shimmerEffect()
    )
}

/**
 * Skeleton UI placeholder that mimics the main Dashboard structure.
 */
@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(top = Spacing.md, bottom = 120.dp)
    ) {
        // Header Skeleton
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column {
                        SkeletonBlock(modifier = Modifier.width(80.dp).height(12.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonBlock(modifier = Modifier.width(130.dp).height(24.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SkeletonBlock(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(Radius.md))
                    SkeletonBlock(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(Radius.md))
                }
            }
        }

        // Time Filter Selector Skeleton
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                repeat(4) {
                    SkeletonBlock(
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        // Total Spent Hero Card Skeleton
        item {
            Surface(
                shape = RoundedCornerShape(Radius.xl),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBlock(modifier = Modifier.width(100.dp).height(16.dp))
                        SkeletonBlock(modifier = Modifier.width(60.dp).height(16.dp))
                    }
                    SkeletonBlock(modifier = Modifier.width(180.dp).height(44.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SkeletonBlock(modifier = Modifier.width(110.dp).height(20.dp))
                        SkeletonBlock(modifier = Modifier.width(90.dp).height(20.dp))
                    }
                }
            }
        }

        // Stats Row Skeleton
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                repeat(2) {
                    Surface(
                        shape = RoundedCornerShape(Radius.lg),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(90.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            SkeletonBlock(modifier = Modifier.width(70.dp).height(14.dp))
                            SkeletonBlock(modifier = Modifier.width(90.dp).height(22.dp))
                        }
                    }
                }
            }
        }

        // Breakdown Chart Card Skeleton
        item {
            Surface(
                shape = RoundedCornerShape(Radius.xl),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBlock(modifier = Modifier.width(140.dp).height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        repeat(5) { index ->
                            val barHeight = (40 + index * 20).dp
                            SkeletonBlock(modifier = Modifier.width(28.dp).height(barHeight))
                        }
                    }
                }
            }
        }

        // Recent Transactions Header Skeleton
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SkeletonBlock(modifier = Modifier.width(120.dp).height(20.dp))
                SkeletonBlock(modifier = Modifier.width(60.dp).height(16.dp))
            }
        }

        // Recent Spend Items Skeleton
        items(4) {
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(68.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(modifier = Modifier.size(40.dp), shape = CircleShape)
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column {
                            SkeletonBlock(modifier = Modifier.width(110.dp).height(16.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            SkeletonBlock(modifier = Modifier.width(70.dp).height(12.dp))
                        }
                    }
                    SkeletonBlock(modifier = Modifier.width(65.dp).height(18.dp))
                }
            }
        }
    }
}
