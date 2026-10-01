package com.alpha.spendtracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.SpendRecap
import com.alpha.spendtracker.ui.theme.Radius
import com.alpha.spendtracker.ui.theme.Sizes
import com.alpha.spendtracker.ui.theme.Spacing
import com.alpha.spendtracker.util.activeAppLocale
import java.text.SimpleDateFormat

/** The month whose Wrapped the Dashboard row offers: [year] and 0-based [month], plus its total. */
data class WrappedBannerInfo(val year: Int, val month: Int, val total: Double)

/**
 * "Your September Wrapped is ready": the in-app counterpart of the Wrapped notification, shown at
 * the top of the Dashboard in the first days of a month until the user has looked at it (or
 * dismissed it).
 *
 * Deliberately a flat row, not a card: no fill, border or gradient, only theme text colours with
 * `primary` as the one accent (the "NEW" word and the icon), like the rest of the Dashboard's
 * quiet chrome. The whole row opens the Wrapped; the X only dismisses.
 *
 * No fixed heights: the text must survive the system font scale (see "Density & font scale").
 */
@Composable
fun WrappedBanner(
    info: WrappedBannerInfo,
    currency: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthName = remember(info.year, info.month) {
        SimpleDateFormat("LLLL", activeAppLocale).format(SpendRecap.monthPeriod(info.year, info.month).start)
    }
    val total = "${SpendRecap.currencySymbol(currency)}${formatCurrencyRounded(info.total)}"
    val accent = MaterialTheme.colorScheme.primary
    val newLabel = stringResource(R.string.wrapped_banner_new)
    val title = stringResource(R.string.wrapped_notification_title, monthName)
    val titleWithNew = remember(newLabel, title, accent) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(newLabel) }
            append("  ")
            append(title)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The shape only rounds the ripple; nothing is painted behind the row.
            .clip(RoundedCornerShape(Radius.md))
            .clickable(
                onClickLabel = stringResource(R.string.wrapped_banner_open),
                role = Role.Button,
                onClick = onOpen
            )
            .heightIn(min = Sizes.minTouchTarget)
            .padding(start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.padding(vertical = Spacing.sm)
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(
                text = titleWithNew,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.wrapped_banner_subtitle, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.wrapped_banner_dismiss),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
