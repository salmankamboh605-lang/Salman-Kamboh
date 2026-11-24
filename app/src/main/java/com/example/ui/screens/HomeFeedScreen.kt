package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.UiState
import com.example.model.VideoFilterPreset
import com.example.model.VideoPost
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.ReportDialog
import com.example.ui.components.VideoPlayerBox
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOverlay
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SalmanCrimson
import com.example.ui.theme.SalmanCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.FeedAndDiscoverViewModel

@Composable
fun HomeFeedScreen(
    viewModel: FeedAndDiscoverViewModel,
    currentUserId: String,
    onNavigateToUpload: () -> Unit,
    onNavigateToDiscoverTag: (String) -> Unit,
    onOpenCreatorProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val feedState by viewModel.publicFeedState.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val activeCommentsVideo by viewModel.activeCommentsVideo.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(1) } // 0 = Following, 1 = For You
    var reportTargetVideo by remember { mutableStateOf<VideoPost?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        when (val state = feedState) {
            is UiState.Loading -> {
                CircularProgressIndicator(
                    color = SalmanCyan,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = SalmanCrimson,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.seedFeaturedReels() },
                        colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson)
                    ) {
                        Text(stringResource(R.string.action_load_featured))
                    }
                }
            }
            is UiState.Success -> {
                val displayedVideos = remember(state.data, selectedTab, followingIds) {
                    if (selectedTab == 0) {
                        state.data.filter { it.userId in followingIds }
                    } else {
                        state.data
                    }
                }

                if (displayedVideos.isEmpty()) {
                    EmptyFeedView(
                        isFollowingTab = selectedTab == 0,
                        onUploadClick = onNavigateToUpload,
                        onLoadFeaturedClick = { viewModel.seedFeaturedReels() },
                        onSwitchToForYou = { selectedTab = 1 }
                    )
                } else {
                    val pagerState = rememberPagerState(pageCount = { displayedVideos.size })
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_vertical_pager"),
                        beyondViewportPageCount = 1
                    ) { page ->
                        val video = displayedVideos[page]
                        val isCurrentPage = pagerState.currentPage == page
                        val isLiked = video.id in likedIds
                        val isFollowingCreator = video.userId in followingIds

                        VideoFeedPage(
                            video = video,
                            isActive = isCurrentPage,
                            isOwnVideo = video.userId == currentUserId,
                            isLiked = isLiked,
                            isFollowing = isFollowingCreator,
                            onToggleLike = { viewModel.toggleLike(video) },
                            onOpenComments = { viewModel.openComments(video) },
                            onToggleFollow = { viewModel.toggleFollowCreator(video.userId, video.creatorUsername) },
                            onShare = {
                                viewModel.recordShare(video.id)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Watch @${video.creatorUsername} on Salman Video: \"${video.caption}\"\n${video.videoUrl}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                            },
                            onReport = { reportTargetVideo = video },
                            onBlockCreator = { viewModel.blockUser(video.userId, video.creatorUsername) },
                            onHashtagClick = onNavigateToDiscoverTag,
                            onCreatorClick = { onOpenCreatorProfile(video.userId) },
                            onPlaybackStarted = { viewModel.recordView(video.id) }
                        )
                    }
                }
            }
        }

        // Top Floating Feed Mode Selector (Following | For You)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(ObsidianOverlay)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedModeChip(
                label = stringResource(R.string.feed_following),
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("tab_following")
            )
            Text("·", color = TextSecondary)
            FeedModeChip(
                label = stringResource(R.string.feed_for_you),
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("tab_for_you")
            )
        }
    }

    activeCommentsVideo?.let { video ->
        CommentsBottomSheet(
            video = video,
            commentsState = commentsState,
            onDismiss = { viewModel.closeComments() },
            onSendComment = { text, parentId ->
                viewModel.addComment(video, text, parentId)
            }
        )
    }

    reportTargetVideo?.let { video ->
        ReportDialog(
            targetLabel = "@${video.creatorUsername}'s video",
            onDismiss = { reportTargetVideo = null },
            onSubmit = { reason, details ->
                viewModel.submitReport("VIDEO", video.id, reason, details)
                reportTargetVideo = null
            }
        )
    }
}

@Composable
private fun FeedModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        ),
        color = if (selected) TextPrimary else TextSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoFeedPage(
    video: VideoPost,
    isActive: Boolean,
    isOwnVideo: Boolean,
    isLiked: Boolean,
    isFollowing: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleFollow: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onBlockCreator: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onCreatorClick: () -> Unit,
    onPlaybackStarted: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val discTransition = rememberInfiniteTransition(label = "vinyl")
    val discRotation by discTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_angle"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        VideoPlayerBox(
            videoUriString = video.videoUrl,
            isActive = isActive,
            filterPreset = VideoFilterPreset.fromRaw(video.filterPreset),
            overlayText = video.overlayText,
            trimStartMs = video.trimStartMs,
            trimEndMs = video.trimEndMs,
            volumeLevel = video.volumeLevel.toFloat(),
            audioTrackUrl = video.audioUrl,
            modifier = Modifier.fillMaxSize(),
            onDoubleTapLike = { if (!isLiked) onToggleLike() },
            onPlaybackStarted = onPlaybackStarted
        )

        // Bottom readability scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.85f))
                    )
                )
        )

        // Right-side vertical action bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .border(2.dp, TextPrimary, CircleShape)
                        .background(ObsidianSurfaceElevated)
                        .clickable(onClick = onCreatorClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (video.creatorAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = video.creatorAvatarUrl,
                            contentDescription = stringResource(R.string.cd_creator_avatar),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = video.creatorUsername.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = SalmanCyan
                        )
                    }
                }
                if (!isOwnVideo && !isFollowing) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(SalmanCrimson)
                            .clickable(onClick = onToggleFollow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Follow creator",
                            tint = TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            RailActionItem(
                icon = Icons.Filled.Favorite,
                countText = formatCompactCount(video.likesCount),
                tint = if (isLiked) SalmanCrimson else TextPrimary,
                contentDescription = stringResource(R.string.cd_like_video),
                onClick = onToggleLike,
                modifier = Modifier.testTag("like_button_${video.id}")
            )

            RailActionItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                countText = formatCompactCount(video.commentsCount),
                tint = TextPrimary,
                contentDescription = stringResource(R.string.cd_comment_video),
                onClick = onOpenComments,
                modifier = Modifier.testTag("comment_button_${video.id}")
            )

            RailActionItem(
                icon = Icons.Filled.Share,
                countText = formatCompactCount(video.sharesCount),
                tint = TextPrimary,
                contentDescription = stringResource(R.string.cd_share_video),
                onClick = onShare,
                modifier = Modifier.testTag("share_button_${video.id}")
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_report_video),
                        tint = TextPrimary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = ObsidianSurface
                ) {
                    DropdownMenuItem(
                        text = { Text("Report Video", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = SalmanCrimson) },
                        onClick = {
                            menuExpanded = false
                            onReport()
                        }
                    )
                    if (!isOwnVideo) {
                        DropdownMenuItem(
                            text = { Text("Block @${video.creatorUsername}", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null, tint = TextSecondary) },
                            onClick = {
                                menuExpanded = false
                                onBlockCreator()
                            }
                        )
                    }
                }
            }

            // Spinning vinyl audio badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .rotate(if (isActive) discRotation else 0f)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(listOf(ObsidianSurfaceElevated, SalmanCrimson, ObsidianSurfaceElevated, SalmanCyan))
                    )
                    .padding(9.dp)
                    .clip(CircleShape)
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Bottom-left creator & video metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 84.dp, bottom = 22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "@${video.creatorUsername}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    modifier = Modifier.clickable(onClick = onCreatorClick)
                )
                if (video.filterPreset != VideoFilterPreset.ORIGINAL.name) {
                    Surface(
                        color = ObsidianOverlay,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = VideoFilterPreset.fromRaw(video.filterPreset).label,
                            style = MaterialTheme.typography.labelSmall,
                            color = SalmanCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = video.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (video.hashtags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    video.hashtags.take(5).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = SalmanCyan,
                            modifier = Modifier.clickable { onHashtagClick(tag) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${video.audioTrackTitle} · ${video.audioArtist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RailActionItem(
    icon: ImageVector,
    countText: String,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(30.dp)
            )
        }
        Text(
            text = countText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
    }
}

@Composable
private fun EmptyFeedView(
    isFollowingTab: Boolean,
    onUploadClick: () -> Unit,
    onLoadFeaturedClick: () -> Unit,
    onSwitchToForYou: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(18.dp))
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = SalmanCyan,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isFollowingTab) "No Following Videos Yet" else stringResource(R.string.feed_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isFollowingTab) {
                        "Follow creators on the For You feed or Discover tab to see their latest reels here."
                    } else {
                        stringResource(R.string.feed_empty_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (isFollowingTab) {
                    Button(
                        onClick = onSwitchToForYou,
                        colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Explore For You Feed")
                    }
                } else {
                    Button(
                        onClick = onLoadFeaturedClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("load_featured_reels_button")
                    ) {
                        Text(stringResource(R.string.action_load_featured))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onUploadClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Record or Upload Video", color = SalmanCyan)
                    }
                }
            }
        }
    }
}

fun formatCompactCount(count: Long): String = when {
    count >= 1_000_000L -> String.format("%.1fM", count / 1_000_000f)
    count >= 1_000L -> String.format("%.1fK", count / 1_000f)
    else -> count.toString()
}
