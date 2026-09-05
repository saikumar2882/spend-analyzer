/**
 * Predefined categories and application presets for logging expenses.
 */
package com.alpha.spendtracker.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.alpha.spendtracker.ui.theme.isAppInDarkTheme
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppPreset(
    val id: String,
    val displayName: String,
    val category: String,
    val color: Color
)

val APP_PRESETS = listOf(
    AppPreset("google_pay", "Google Pay", "UPI Apps", Color(0xFF1A73E8)),
    AppPreset("phone_pe", "PhonePe", "UPI Apps", Color(0xFF5F259F)),
    AppPreset("paytm", "Paytm", "UPI Apps", Color(0xFF00B9F5)),

    AppPreset("swiggy", "Swiggy", "Quick Commerce", Color(0xFFFC8019)),
    AppPreset("zomato", "Zomato", "Quick Commerce", Color(0xFFE23744)),
    AppPreset("zepto", "Zepto", "Quick Commerce", Color(0xFF5B21B6)),
    AppPreset("blinkit", "Blinkit", "Quick Commerce", Color(0xFFFFC200)),

    AppPreset("amazon", "Amazon", "E-Commerce", Color(0xFFFF9900)),
    AppPreset("flipkart", "Flipkart", "E-Commerce", Color(0xFF1A65E6)),
    AppPreset("myntra", "Myntra", "E-Commerce", Color(0xFFE61A5B)),
    AppPreset("ajio", "Ajio", "E-Commerce", Color(0xFF0F172A)),

    AppPreset("icici", "ICICI Bank", "Banking & Cards", Color(0xFFE05F04)),
    AppPreset("yono_sbi", "Yono SBI", "Banking & Cards", Color(0xFF1E1B4B)),

    AppPreset("other", "Other Platform", "Other", Color(0xFF6B7280))
)

val APP_COLOR_BY_NAME: Map<String, Color> = APP_PRESETS.associate { it.displayName to it.color }

val CATEGORY_PRESETS = listOf(
    "UPI Apps",
    "Quick Commerce",
    "E-Commerce",
    "Banking & Cards",
    "Other"
)

val PURPOSE_PRESETS = listOf(
    "Groceries & Food",
    "Shopping & Apparels",
    "Lending",
    "Borrowing",
    "Credit Card Bill",
    "Rent & Utilities",
    "Travel & Commute",
    "Subscription & Leisure",
    "Healthcare & Medical",
    "Others"
)

fun normalizeName(name: String): String {
    return name.lowercase().replace(Regex("[^a-z0-9]"), "")
}

/**
 * Maps a user-facing app name onto its Play Store package id.
 * Supports static preset mappings as well as dynamic device lookup for installed apps.
 */
fun getAppPackageName(appName: String): String? {
    val clean = appName.trim().lowercase().replace(Regex("[^a-z0-9\\s]"), "")

    return when {
        clean.contains("gpay") || clean.contains("google pay") || clean.contains("google") -> "com.google.android.apps.nbu.paisa.user"
        clean.contains("phonepe") || clean.contains("phone pe") -> "com.phonepe.app"
        clean.contains("paytm") -> "net.one97.paytm"
        clean.contains("swiggy") -> "in.swiggy.android"
        clean.contains("zomato") -> "com.application.zomato"
        clean.contains("zepto") -> "com.kirana.consumer"
        clean.contains("blinkit") || clean.contains("grofers") -> "com.grofers.customerapp"
        clean.contains("cred") -> "com.dreamplug.android.cred"
        clean.contains("amazon") -> "in.amazon.mShop.android.shopping"
        clean.contains("flipkart") -> "com.flipkart.android"
        clean.contains("myntra") -> "com.myntra.android"
        clean.contains("ajio") -> "com.ril.ajio"
        clean.contains("icici") -> "com.csam.icici.bank.imobile"
        clean.contains("yono") || clean.contains("sbi") -> "com.sbi.lotusintouch"
        clean.contains("hdfc") || clean.contains("payzapp") -> "com.snapwork.hdfc"
        clean.contains("axis") -> "com.axis.mobile"
        clean.contains("uber") -> "com.ubercab"
        clean.contains("ola") -> "com.olacabs.customer"
        clean.contains("rapido") -> "com.rapido.passenger"
        clean.contains("netflix") -> "com.netflix.mediaclient"
        clean.contains("spotify") -> "com.spotify.music"
        else -> null
    }
}

/**
 * Context-aware lookup that falls back to dynamic device package discovery for any app installed on the device.
 */
fun getAppPackageName(context: Context, appName: String): String? {
    val staticPkg = getAppPackageName(appName)
    if (staticPkg != null) {
        val isInstalled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(staticPkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(staticPkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
        if (isInstalled) return staticPkg
    }
    return AppIconCache.findInstalledPackageName(context, appName)
}

/**
 * Resolves domain name for brand icons based on app name.
 */
fun getDomainForApp(appName: String): String {
    val clean = appName.trim().lowercase()
    return when {
        clean.contains("zepto") -> "zeptonow.com"
        clean.contains("swiggy") -> "swiggy.com"
        clean.contains("zomato") -> "zomato.com"
        clean.contains("blinkit") || clean.contains("grofers") -> "blinkit.com"
        clean.contains("google") || clean.contains("gpay") -> "pay.google.com"
        clean.contains("phonepe") || clean.contains("phone pe") -> "phonepe.com"
        clean.contains("paytm") -> "paytm.com"
        clean.contains("cred") -> "cred.club"
        clean.contains("amazon") -> "amazon.in"
        clean.contains("flipkart") -> "flipkart.com"
        clean.contains("myntra") -> "myntra.com"
        clean.contains("ajio") -> "ajio.com"
        clean.contains("icici") -> "icicibank.com"
        clean.contains("sbi") || clean.contains("yono") -> "sbi.co.in"
        clean.contains("hdfc") || clean.contains("payzapp") -> "hdfcbank.com"
        clean.contains("axis") -> "axisbank.com"
        clean.contains("uber") -> "uber.com"
        clean.contains("ola") -> "olacabs.com"
        clean.contains("rapido") -> "rapido.bike"
        clean.contains("netflix") -> "netflix.com"
        clean.contains("spotify") -> "spotify.com"
        else -> if (clean.contains(".")) clean else "$clean.com"
    }
}

/**
 * Brand logo for an app via unavatar.io.
 */
fun getHighResLogoUrl(appName: String): String {
    val domain = getDomainForApp(appName)
    return "https://unavatar.io/$domain"
}

/**
 * High-quality 128x128 favicon from Google's S2 Favicon service.
 */
fun getGoogleFaviconUrl(appName: String): String {
    val domain = getDomainForApp(appName)
    return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
}

/**
 * Resolves launcher icons for installed apps, off the main thread and memoised per package.
 */
private object AppIconCache {
    private val cache = ConcurrentHashMap<String, Optional>()
    private val packageLookupCache = ConcurrentHashMap<String, String>()

    /** ConcurrentHashMap cannot store nulls, so a miss is cached as an empty box. */
    private class Optional(val value: Drawable?)

    fun findInstalledPackageName(context: Context, appName: String): String? {
        val cleanName = appName.trim().lowercase()
        if (cleanName.isBlank() || cleanName == "other" || cleanName == "other platform") return null

        packageLookupCache[cleanName]?.let {
            return if (it == "NONE") null else it
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val cleanApp = cleanName.replace(Regex("[^a-z0-9]"), "")

        for (info in resolveInfos) {
            val pName = info.activityInfo.packageName
            val label = try {
                info.loadLabel(pm).toString().lowercase()
            } catch (_: Exception) {
                ""
            }
            val cleanLabel = label.replace(Regex("[^a-z0-9]"), "")
            val cleanPName = pName.lowercase()

            if (cleanLabel == cleanApp ||
                (cleanApp.length >= 3 && cleanLabel.contains(cleanApp)) ||
                (cleanLabel.length >= 3 && cleanApp.contains(cleanLabel)) ||
                cleanPName.contains(cleanApp)
            ) {
                packageLookupCache[cleanName] = pName
                return pName
            }
        }

        packageLookupCache[cleanName] = "NONE"
        return null
    }

    suspend fun load(context: Context, packageName: String): Drawable? {
        cache[packageName]?.let { return it.value }
        return withContext(Dispatchers.IO) {
            val icon = try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
            cache[packageName] = Optional(icon)
            icon
        }
    }
}

/**
 * The app avatar, resolved in four tiers:
 * 1. The installed app's launcher icon (if installed on device)
 * 2. Brand logo from unavatar.io
 * 3. High-res favicon from Google's S2 Favicon service
 * 4. Solid brand-coloured initial avatar fallback
 */
@Composable
fun AppIconImage(
    appName: String,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val packageName = remember(appName, context) { getAppPackageName(context, appName) }

    val installedIcon by produceState<Drawable?>(initialValue = null, appName, packageName) {
        value = packageName?.let { AppIconCache.load(context, it) }
    }

    val initials = remember(appName) {
        val trimmed = appName.trim()
        val parts = trimmed.split(' ', '_', '-').filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            trimmed.length >= 2 -> trimmed.take(2).uppercase()
            trimmed.isNotEmpty() -> trimmed.take(1).uppercase()
            else -> "S"
        }
    }

    val avatar: @Composable () -> Unit = { InitialAvatar(initials, fallbackColor) }

    val primaryUrl = remember(appName) { getHighResLogoUrl(appName) }
    val googleFaviconUrl = remember(appName) { getGoogleFaviconUrl(appName) }

    var currentModel by remember(installedIcon, primaryUrl) {
        mutableStateOf<Any?>(installedIcon ?: primaryUrl)
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(currentModel)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription ?: appName,
        modifier = modifier.clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = { avatar() },
        error = {
            if (currentModel == primaryUrl && googleFaviconUrl != primaryUrl) {
                currentModel = googleFaviconUrl
            } else {
                avatar()
            }
        }
    )
}

/**
 * Solid brand-coloured disc with the app's initial. Sized off the box it is given so it reads
 * correctly at every call site (20dp in a row, 32dp in the picker) without a font-size parameter.
 */
@Composable
private fun InitialAvatar(initials: String, color: Color) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Pale brand colours (Blinkit yellow, Paytm cyan) need dark text to stay legible.
        val onColor = if (color.luminance() > 0.55f) Color(0xFF14141F) else Color.White
        Text(
            text = initials.take(1),
            color = onColor,
            fontSize = (maxWidth.value * 0.44f).coerceAtLeast(8f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Solid coloured disc with a person's first letter initial for Lend/Borrow cards.
 */
@Composable
fun PersonInitialAvatar(
    personName: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val initial = remember(personName) {
        val trimmed = personName.trim()
        if (trimmed.isNotBlank()) trimmed.first().uppercase() else "U"
    }
    val isDark = isAppInDarkTheme
    val initialColor = if (isDark) Color.White else Color(0xFF14141F)

    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = initialColor,
            fontSize = (maxWidth.value * 0.44f).coerceAtLeast(8f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

