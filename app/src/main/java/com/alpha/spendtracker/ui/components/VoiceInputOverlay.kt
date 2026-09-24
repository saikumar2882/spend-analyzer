package com.alpha.spendtracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpha.spendtracker.util.VoiceInputHelper
import com.alpha.spendtracker.util.VoiceListener

@Composable
fun VoiceInputOverlay(
    selectedLanguage: String,
    onLanguageChanged: (String) -> Unit,
    onFinalTranscript: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var partialTranscript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(true) }
    var speechErrorMessage by remember { mutableStateOf<String?>(null) }

    // Re-start recognition whenever selectedLanguage changes
    DisposableEffect(selectedLanguage) {
        isListening = true
        speechErrorMessage = null
        partialTranscript = ""

        val listener = object : VoiceListener {
            override fun onPartialTranscript(text: String) {
                partialTranscript = text
                speechErrorMessage = null
            }

            override fun onFinalTranscript(text: String) {
                isListening = false
                partialTranscript = text
                onFinalTranscript(text)
            }

            override fun onError(errorMessage: String) {
                isListening = false
                speechErrorMessage = errorMessage
            }
        }

        VoiceInputHelper.startListening(context, selectedLanguage, listener)

        onDispose {
            VoiceInputHelper.destroy()
        }
    }

    // Pulsing animation for the listening mic
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Language Segmented Selector + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language Selector (తెలుగు | English | తెలుగు + Eng)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LanguageChip(
                            label = "తెలుగు",
                            selected = selectedLanguage == "te-IN",
                            onClick = { if (selectedLanguage != "te-IN") onLanguageChanged("te-IN") }
                        )
                        LanguageChip(
                            label = "English",
                            selected = selectedLanguage == "en-IN",
                            onClick = { if (selectedLanguage != "en-IN") onLanguageChanged("en-IN") }
                        )
                        LanguageChip(
                            label = "తెలుగు + Eng",
                            selected = selectedLanguage == "te-en",
                            onClick = { if (selectedLanguage != "te-en") onLanguageChanged("te-en") }
                        )
                    }
                }

                // Close / Cancel Button
                IconButton(
                    onClick = {
                        VoiceInputHelper.destroy()
                        onDismiss()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Pulsing Mic Button Area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(96.dp)
            ) {
                if (isListening) {
                    // Outer pulsing aura
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .scale(pulseScale)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                CircleShape
                            )
                    )
                }

                // Minimal Center Mic Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape
                        )
                        .clickable {
                            if (!isListening) {
                                isListening = true
                                speechErrorMessage = null
                                VoiceInputHelper.startListening(
                                    context,
                                    selectedLanguage,
                                    object : VoiceListener {
                                        override fun onPartialTranscript(text: String) {
                                            partialTranscript = text
                                            speechErrorMessage = null
                                        }

                                        override fun onFinalTranscript(text: String) {
                                            isListening = false
                                            partialTranscript = text
                                            onFinalTranscript(text)
                                        }

                                        override fun onError(errorMessage: String) {
                                            isListening = false
                                            speechErrorMessage = errorMessage
                                        }
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = if (isListening) "Listening" else "Tap to retry",
                        tint = if (isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Transcript text or Placeholder or Error
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    speechErrorMessage != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = speechErrorMessage!!,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tap mic to try again",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    partialTranscript.isNotBlank() -> {
                        Text(
                            text = partialTranscript,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    else -> {
                        val placeholder = when (selectedLanguage) {
                            "te-IN" -> "వింటున్నాము... మాట్లాడండి"
                            "te-en" -> "వింటున్నాము... speak now"
                            else -> "Listening... Speak now"
                        }
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Minimal Cancel Text Button
            TextButton(
                onClick = {
                    VoiceInputHelper.destroy()
                    onDismiss()
                }
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}
