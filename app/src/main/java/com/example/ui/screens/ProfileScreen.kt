package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.signOutUser
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.VideoFilterPreset
import com.example.model.VideoPost
import com.example.ui.components.VideoPlayerBox
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOverlay
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SalmanCrimson
import com.example.ui.theme.SalmanCyan
import com.example.ui.theme.SalmanGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SocialAndAdminViewModel

@Composable
fun ProfileScreen(
    viewModel: SocialAndAdminViewModel,
    currentUserId: String,
    onOpenAdminConsole: () -> Unit,
    onOpenDirectMessage: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val myVideos by viewModel.myVideos.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

    val viewedCreatorId by viewModel.viewedCreatorId.collectAsStateWithLifecycle()
    val viewedCreatorProfile by viewModel.viewedCreatorProfile.collectAsStateWithLifecycle()
    val viewedCreatorVideos by viewModel.viewedCreatorVideos.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var previewVideo by remember { mutableStateOf<VideoPost?>(null) }

    if (viewedCreatorId != null && viewedCreatorId != currentUserId) {
        BackHandler { viewModel.closeCreatorProfile() }
        val creator = viewedCreatorProfile
        CreatorProfileView(
            creator = creator,
            videos = viewedCreatorVideos,
            isFollowing = viewedCreatorId in followingIds,
            onBack = { viewModel.closeCreatorProfile() },
            onToggleFollow = { creator?.let { viewModel.toggleFollow(it) } },
            onMessageClick = { creator?.let { onOpenDirectMessage(it) } },
            onShareClick = {
                creator?.let {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out @${it.username} on Salman Video!")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                }
            },
            onVideoClick = { previewVideo = it }
        )
    } else {
        when (val state = profileState) {
            is UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SalmanCyan)
            }
            is UiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(state.message, color = SalmanCrimson)
            }
            is UiState.Success -> {
                val profile = state.data ?: UserProfile(
                    userId = currentUserId,
                    username = "creator_${currentUserId.take(4)}",
                    displayName = "Salman Creator"
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg)
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "@${profile.username}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary
                                )
                                if (profile.privateAccount) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Private Account",
                                        tint = SalmanCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Row {
                                if (isAdmin) {
                                    IconButton(
                                        onClick = onOpenAdminConsole,
                                        modifier = Modifier.testTag("admin_console_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AdminPanelSettings,
                                            contentDescription = stringResource(R.string.cd_admin_panel),
                                            tint = SalmanGold
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showBlockedDialog = true },
                                    modifier = Modifier.testTag("blocked_users_button")
                                ) {
                                    Icon(Icons.Filled.Block, contentDescription = "Blocked users", tint = TextSecondary)
                                }
                                IconButton(
                                    onClick = {
                                        signOutUser(credentialManager, scope)
                                    },
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = stringResource(R.string.action_sign_out),
                                        tint = SalmanCrimson
                                    )
                                }
                            }
                        }
                    }

                    item {
                        ProfileHeaderSection(
                            profile = profile,
                            actionsContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showEditDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("edit_profile_button")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit Profile", color = TextPrimary)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Follow @${profile.username} on Salman Video!"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = SalmanCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share Profile", color = SalmanCyan)
                                    }
                                }
                            }
                        )
                    }

                    item {
                        Text(
                            text = "My Videos (${myVideos.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (myVideos.isEmpty()) {
                        item {
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "You haven't uploaded any videos yet. Tap + Create to record or upload!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    } else {
                        val rows = myVideos.chunked(3)
                        items(rows) { rowVideos ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowVideos.forEach { video ->
                                    ProfileVideoTile(
                                        video = video,
                                        showDelete = true,
                                        onClick = { previewVideo = video },
                                        onDelete = { viewModel.deleteMyVideo(video.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowVideos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (showEditDialog) {
                    EditProfileDialog(
                        profile = profile,
                        onDismiss = { showEditDialog = false },
                        onSave = { username, display, bio, avatar, isPrivate ->
                            viewModel.updateProfile(username, display, bio, avatar, isPrivate)
                            showEditDialog = false
                        }
                    )
                }

                if (showBlockedDialog) {
                    AlertDialog(
                        onDismissRequest = { showBlockedDialog = false },
                        containerColor = ObsidianSurface,
                        title = { Text("Blocked Users", color = TextPrimary) },
                        text = {
                            if (blockedUsers.isEmpty()) {
                                Text("You haven't blocked any accounts.", color = TextSecondary)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    blockedUsers.forEach { blocked ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("@${blocked.blockedUsername}", color = TextPrimary)
                                            TextButton(onClick = { viewModel.unblockUser(blocked.blockedUid) }) {
                                                Text("Unblock", color = SalmanCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showBlockedDialog = false }) {
                                Text("Close", color = TextPrimary)
                            }
                        }
                    )
                }
            }
        }
    }

    previewVideo?.let { video ->
        Dialog(
            onDismissRequest = { previewVideo = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BackHandler { previewVideo = null }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg)
            ) {
                VideoPlayerBox(
                    videoUriString = video.videoUrl,
                    isActive = true,
                    filterPreset = VideoFilterPreset.fromRaw(video.filterPreset),
                    overlayText = video.overlayText,
                    trimStartMs = video.trimStartMs,
                    trimEndMs = video.trimEndMs,
                    volumeLevel = video.volumeLevel.toFloat(),
                    audioTrackUrl = video.audioUrl,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { previewVideo = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(ObsidianOverlay)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun CreatorProfileView(
    creator: UserProfile?,
    videos: List<VideoPost>,
    isFollowing: Boolean,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit,
    onMessageClick: () -> Unit,
    onShareClick: () -> Unit,
    onVideoClick: (VideoPost) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    text = "@${creator?.username ?: "creator"}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        }
        if (creator != null) {
            item {
                ProfileHeaderSection(
                    profile = creator,
                    actionsContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onToggleFollow,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) ObsidianSurfaceElevated else SalmanCrimson
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isFollowing) "Following" else "Follow", color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = onMessageClick,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = SalmanCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Message", color = SalmanCyan)
                            }
                            IconButton(onClick = onShareClick) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Profile", tint = TextPrimary)
                            }
                        }
                    }
                )
            }
        }
        val rows = videos.chunked(3)
        items(rows) { rowVideos ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowVideos.forEach { video ->
                    ProfileVideoTile(
                        video = video,
                        showDelete = false,
                        onClick = { onVideoClick(video) },
                        onDelete = {},
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowVideos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    profile: UserProfile,
    actionsContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(ObsidianSurfaceElevated)
                .border(2.5.dp, Brush.linearGradient(listOf(SalmanCrimson, SalmanCyan)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (profile.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = profile.username.take(1).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = SalmanCyan
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        if (profile.bio.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProfileStatItem(label = "Following", value = formatCompactCount(profile.followingCount))
            ProfileStatItem(label = "Followers", value = formatCompactCount(profile.followersCount))
            ProfileStatItem(label = "Likes", value = formatCompactCount(profile.totalLikes))
        }
        Spacer(modifier = Modifier.height(18.dp))
        actionsContent()
    }
}

@Composable
private fun ProfileStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun ProfileVideoTile(
    video: VideoPost,
    showDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 14f)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceElevated)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = TextPrimary.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )
        }

        if (showDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(ObsidianOverlay)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete video", tint = SalmanCrimson, modifier = Modifier.size(16.dp))
            }
        }

        Text(
            text = "${formatCompactCount(video.likesCount)} ♥",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topEnd = 8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (username: String, displayName: String, bio: String, avatarUrl: String?, privateAccount: Boolean) -> Unit
) {
    var username by remember { mutableStateOf(profile.username) }
    var displayName by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarUrl by remember { mutableStateOf(profile.avatarUrl) }
    var privateAccount by remember { mutableStateOf(profile.privateAccount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Edit Profile", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(30) },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(50) },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(200) },
                    label = { Text("Bio") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it.take(1000) },
                    label = { Text("Avatar Image URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Private Account", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = privateAccount,
                        onCheckedChange = { privateAccount = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = SalmanCrimson)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(username, displayName, bio, avatarUrl.ifBlank { null }, privateAccount) },
                colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson)
            ) {
                Text(stringResource(R.string.action_save_profile))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
