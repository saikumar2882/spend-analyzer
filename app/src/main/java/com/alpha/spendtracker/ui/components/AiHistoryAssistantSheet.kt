package com.alpha.spendtracker.ui.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.alpha.spendtracker.R
import com.alpha.spendtracker.data.AssistantSuggestions
import com.alpha.spendtracker.data.ChatMessage
import com.alpha.spendtracker.ui.icons.AppIcons
import com.alpha.spendtracker.ui.theme.rememberPressScale
import com.alpha.spendtracker.ui.viewmodel.AiErrorType
import com.alpha.spendtracker.ui.viewmodel.AiHistoryStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/** A suggestion as displayed: the localized [label] to show and the English [question] to scope with. */
data class Suggestion(val label: String, val question: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHistoryAssistantSheet(
    messages: List<ChatMessage>,
    status: AiHistoryStatus,
    /**
     * (what the chat shows, what the assistant reads for period/category/dues). They differ only
     * for a suggestion chip in a non-English app language; see [AssistantSuggestions].
     */
    onSendMessage: (question: String, scopeText: String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    /** False hides the Lending/Borrowing suggestions for someone with nothing to ask about. */
    hasDues: Boolean = true
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    
    val lastMessage = messages.lastOrNull()
    val userMessagesInCurrentSession = if (lastMessage != null) {
        messages.count { it.sessionId == lastMessage.sessionId && it.fromUser }
    } else 0
    val remainingMessages = (7 - userMessagesInCurrentSession).coerceAtLeast(0)

    val suggestions = AssistantSuggestions.visible(hasDues).map { s ->
        Suggestion(label = stringResource(s.labelRes), question = s.question)
    }
    // A tap sends straight away, so a second tap while the first is in flight would cancel it
    // (askAiAboutHistory supersedes the running request) and leave an unanswered question in the
    // chat. No point offering them once the daily limit is hit, either.
    val chipsEnabled = status !is AiHistoryStatus.Analyzing &&
        !(status is AiHistoryStatus.Error && status.type == AiErrorType.CLIENT_RATE_LIMIT)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.88f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.Ai,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ai_assistant_header),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ai_assistant_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val chipColor = if (remainingMessages <= 1) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
                Surface(
                    color = chipColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "$remainingMessages/7 left",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = chipColor
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    EmptyChatState(
                        suggestions = suggestions,
                        enabled = chipsEnabled,
                        onSuggestionClick = { onSendMessage(it.label, it.question) }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message ->
                            ChatBubble(
                                message = message,
                                onCopy = {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Spend Tracker", message.text)))
                                    }
                                }
                            )
                        }
                        
                        item {
                            AiStatusIndicator(status)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Persistent suggestion chips: one tap asks the question.
            if (messages.isNotEmpty() && textInput.isBlank()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(suggestions, key = { it.question }) { suggestion ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val scale = rememberPressScale(interactionSource)
                        Surface(
                            onClick = { onSendMessage(suggestion.label, suggestion.question) },
                            enabled = chipsEnabled,
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .scale(scale)
                                .alpha(if (chipsEnabled) 1f else 0.5f)
                        ) {
                            Text(
                                suggestion.label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ask_ai_placeholder), style = MaterialTheme.typography.bodyMedium) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    onSendMessage(textInput, textInput)
                                    textInput = ""
                                }
                            },
                            enabled = textInput.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                tint = if (textInput.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "AI-powered. Answers depend on your recorded transactions.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AiStatusIndicator(status: AiHistoryStatus) {
    when (status) {
        is AiHistoryStatus.Idle -> {}
        is AiHistoryStatus.Analyzing -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "AI is analyzing your history...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        is AiHistoryStatus.Error -> {
            val icon: ImageVector
            val title: String
            val color: Color

            when (status.type) {
                AiErrorType.SERVER_RATE_LIMIT -> {
                    icon = Icons.Rounded.Timer
                    title = "Server Busy"
                    color = MaterialTheme.colorScheme.error
                }
                AiErrorType.CLIENT_RATE_LIMIT -> {
                    icon = Icons.Rounded.Block
                    title = "Daily Limit Reached"
                    color = MaterialTheme.colorScheme.error
                }
                AiErrorType.API_KEY_MISSING -> {
                    icon = Icons.Rounded.Settings
                    title = "Configuration Error"
                    color = MaterialTheme.colorScheme.secondary
                }
                else -> {
                    icon = Icons.Rounded.ErrorOutline
                    title = "Unexpected Error"
                    color = MaterialTheme.colorScheme.error
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = color.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = color
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = color.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    val isUser = message.fromUser
    val arrangement = if (isUser) Arrangement.End else Arrangement.Start
    
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val timeString = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AiAvatar()
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isUser) {
                        Text(
                            text = message.text,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        MarkdownText(
                            text = message.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = (if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.6f)
                        )

                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy message",
                                tint = (if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(6.dp))
            UserAvatar()
        }
    }
}

@Composable
private fun AiAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.Ai,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun UserAvatar(
    photoUrl: String? = remember { FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() }
) {
    if (!photoUrl.isNullOrBlank()) {
        val context = LocalContext.current
        SubcomposeAsyncImage(
            model = remember(photoUrl) {
                ImageRequest.Builder(context)
                    .data(photoUrl)
                    .crossfade(true)
                    .build()
            },
            contentDescription = "User photo",
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            loading = { DefaultUserAvatar() },
            error = { DefaultUserAvatar() }
        )
    } else {
        DefaultUserAvatar()
    }
}

@Composable
private fun DefaultUserAvatar() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier.size(28.dp),
        border = null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EmptyChatState(
    suggestions: List<Suggestion>,
    enabled: Boolean,
    onSuggestionClick: (Suggestion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Eight suggestions don't fit above the keyboard on a small screen or a large font.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Ai,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Ask me anything",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.ai_tap_to_ask),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { suggestion ->
                val interactionSource = remember { MutableInteractionSource() }
                val scale = rememberPressScale(interactionSource)
                Surface(
                    onClick = { onSuggestionClick(suggestion) },
                    enabled = enabled,
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .alpha(if (enabled) 1f else 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Ai,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = suggestion.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
