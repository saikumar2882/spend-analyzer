package com.alpha.spendtracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
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
    var transcript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(true) }
    var soundLevel by remember { mutableFloatStateOf(0f) }
    var speechErrorMessage by remember { mutableStateOf<String?>(null) }

    fun createListener(): VoiceListener = object : VoiceListener {
        override fun onPartialTranscript(text: String) {
            if (text.isNotBlank()) {
                transcript = text
                speechErrorMessage = null
            }
        }

        override fun onFinalTranscript(text: String) {
            if (text.isNotBlank()) {
                transcript = text
                speechErrorMessage = null
            }
            isListening = false
        }

        override fun onError(errorMessage: String) {
            isListening = false
            // Don't obscure transcript with non-fatal errors if text was already captured
            if (transcript.isBlank()) {
                speechErrorMessage = errorMessage
            }
        }

        override fun onRmsChanged(level: Float) {
            soundLevel = level
        }

        override fun onListeningStateChanged(active: Boolean) {
            isListening = active
        }
    }

    // Re-start recognition whenever selectedLanguage changes
    DisposableEffect(selectedLanguage) {
        isListening = true
        speechErrorMessage = null
        soundLevel = 0f

        VoiceInputHelper.startListening(context, selectedLanguage, createListener())

        onDispose {
            VoiceInputHelper.destroy()
        }
    }

    // Pulsing base animation for the mic
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Dynamic scale driven by speech audio level (0.0 .. 1.0)
    val dynamicAudioScale by animateFloatAsState(
        targetValue = 1f + (soundLevel * 0.4f),
        animationSpec = tween(100, easing = LinearEasing),
        label = "audio_level_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
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

            // Pulsing & Audio-reactive Mic Button Area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                if (isListening) {
                    // Outer pulsing audio aura
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .scale(pulseScale * dynamicAudioScale)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                CircleShape
                            )
                    )
                }

                // Center Mic Toggle Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape
                        )
                        .clickable {
                            if (isListening) {
                                VoiceInputHelper.stop()
                                isListening = false
                            } else {
                                isListening = true
                                speechErrorMessage = null
                                VoiceInputHelper.startListening(context, selectedLanguage, createListener())
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = if (isListening) "Listening" else "Tap to listen",
                        tint = if (isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Status message & Transcript Text Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    speechErrorMessage != null && transcript.isBlank() -> {
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
                                text = "Tap mic to speak again",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    transcript.isNotBlank() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = transcript,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isListening) "Listening... Speak or tap Send when done" else "Tap mic to keep speaking, or tap Send",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        val placeholder = when (selectedLanguage) {
                            "te-IN" -> "వింటున్నాము... ఆలోచించి చెప్పండి"
                            "te-en" -> "వింటున్నాము... Take your time & speak"
                            else -> "Listening... Speak or take your time"
                        }
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Actions: Send button (if transcript is available) or Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (transcript.isNotBlank()) {
                    Button(
                        onClick = {
                            VoiceInputHelper.destroy()
                            onFinalTranscript(transcript)
                        },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Send",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
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
