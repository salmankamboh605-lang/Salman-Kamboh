package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.UiState
import com.example.model.UserNotification
import com.example.model.UserProfile
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SalmanCrimson
import com.example.ui.theme.SalmanCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SocialAndAdminViewModel

@Composable
fun InboxScreen(
    viewModel: SocialAndAdminViewModel,
    currentUserId: String
) {
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val creators by viewModel.activeCreators.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Notifications, 1 = Direct Messages
    var showNewChatPicker by remember { mutableStateOf(false) }

    if (activeConversation != null) {
        val conv = activeConversation!!
        val otherUid = conv.participantIds.firstOrNull { it != currentUserId }.orEmpty()
        val otherCreator = creators.firstOrNull { it.userId == otherUid }
            ?: UserProfile(
                userId = otherUid,
                username = conv.participantNames[otherUid] ?: "creator",
                displayName = conv.participantNames[otherUid] ?: "Creator"
            )

        BackHandler { viewModel.closeConversation() }

        ChatThreadScreen(
            otherUser = otherCreator,
            messages = activeMessages,
            currentUserId = currentUserId,
            onBack = { viewModel.closeConversation() },
            onSend = { text -> viewModel.sendChatMessage(otherCreator, text) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inbox",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            if (selectedTab == 1) {
                IconButton(
                    onClick = { showNewChatPicker = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurfaceElevated)
                        .testTag("new_message_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "New direct message", tint = SalmanCyan)
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianBg,
            contentColor = TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = SalmanCrimson
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Activity") },
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Messages") },
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (selectedTab == 0) {
            NotificationsList(
                state = notificationsState,
                onMarkRead = { viewModel.markNotificationRead(it) }
            )
        } else {
            ConversationsList(
                state = conversationsState,
                currentUserId = currentUserId,
                onOpenConversation = { viewModel.openConversation(it) }
            )
        }
    }

    if (showNewChatPicker) {
        val otherCreators = creators.filter { it.userId != currentUserId }
        AlertDialog(
            onDismissRequest = { showNewChatPicker = false },
            containerColor = ObsidianSurface,
            title = { Text("Message a Creator", color = TextPrimary) },
            text = {
                if (otherCreators.isEmpty()) {
                    Text("No other active creators available yet.", color = TextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(otherCreators, key = { it.userId }) { user ->
                            Surface(
                                color = ObsidianSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showNewChatPicker = false
                                        viewModel.startConversationWith(user)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("@${user.username}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewChatPicker = false }) {
                    Text("Close", color = SalmanCyan)
                }
            }
        )
    }
}

@Composable
private fun NotificationsList(
    state: UiState<List<UserNotification>>,
    onMarkRead: (String) -> Unit
) {
    when (state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SalmanCyan)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(state.message, color = SalmanCrimson)
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No activity yet. Likes, comments, mentions, and new followers appear here.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.data, key = { it.id }) { notif ->
                        NotificationCard(notif = notif, onClick = { if (!notif.read) onMarkRead(notif.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notif: UserNotification,
    onClick: () -> Unit
) {
    val icon = when (notif.type) {
        "LIKE" -> Icons.Filled.Favorite
        "FOLLOW" -> Icons.Filled.PersonAdd
        "MENTION" -> Icons.Filled.AlternateEmail
        else -> Icons.AutoMirrored.Filled.Chat
    }
    val tint = when (notif.type) {
        "LIKE" -> SalmanCrimson
        "FOLLOW" -> SalmanCyan
        else -> TextPrimary
    }

    Surface(
        color = if (notif.read) ObsidianSurface else ObsidianSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (notif.read) OutlineSubtle else SalmanCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = notif.type, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${notif.actorUsername}",
                    style = MaterialTheme.typography.labelLarge,
                    color = SalmanCyan
                )
                Text(
                    text = notif.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
            if (!notif.read) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SalmanCrimson)
                )
            }
        }
    }
}

@Composable
private fun ConversationsList(
    state: UiState<List<Conversation>>,
    currentUserId: String,
    onOpenConversation: (Conversation) -> Unit
) {
    when (state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SalmanCyan)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(state.message, color = SalmanCrimson)
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No direct messages yet. Tap the compose button above to message a creator.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.data, key = { it.id }) { conv ->
                        val otherUid = conv.participantIds.firstOrNull { it != currentUserId }.orEmpty()
                        val otherName = conv.participantNames[otherUid] ?: "Creator"
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                                .clickable { onOpenConversation(conv) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianSurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = otherName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SalmanCyan
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "@$otherName",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = conv.lastMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadScreen(
    otherUser: UserProfile,
    messages: List<ChatMessage>,
    currentUserId: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to messages", tint = TextPrimary)
            }
            Text(
                text = "@${otherUser.username}",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == currentUserId
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        color = if (isMine) SalmanCrimson else ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(1000) },
                placeholder = { Text("Write a message...", color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dm_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank()) SalmanCyan else ObsidianSurfaceElevated)
                    .testTag("dm_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = ObsidianBg
                )
            }
        }
    }
}
