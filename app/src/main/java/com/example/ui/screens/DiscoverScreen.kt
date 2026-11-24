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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
import com.example.ui.viewmodel.FeedAndDiscoverViewModel

@Composable
fun DiscoverScreen(
    viewModel: FeedAndDiscoverViewModel,
    currentUserId: String,
    onOpenCreatorProfile: (String) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedHashtag by viewModel.selectedHashtag.collectAsStateWithLifecycle()
    val feedState by viewModel.publicFeedState.collectAsStateWithLifecycle()
    val creators by viewModel.activeCreators.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()

    var previewVideo by remember { mutableStateOf<VideoPost?>(null) }

    val allVideos = (feedState as? UiState.Success)?.data.orEmpty()
    val allTags = remember(allVideos) {
        allVideos.flatMap { it.hashtags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(12)
            .ifEmpty { listOf("salmanvideo", "trending", "cinema", "neon", "travel") }
    }

    val queryLower = searchQuery.trim().lowercase()
    val filteredCreators = remember(creators, queryLower) {
        if (queryLower.isBlank()) creators
        else creators.filter {
            it.username.lowercase().contains(queryLower) ||
                it.displayName.lowercase().contains(queryLower)
        }
    }

    val filteredVideos = remember(allVideos, queryLower, selectedHashtag) {
        allVideos.filter { video ->
            val matchesTag = selectedHashtag == null || video.hashtags.any { it.equals(selectedHashtag, ignoreCase = true) }
            val matchesQuery = queryLower.isBlank() ||
                video.caption.lowercase().contains(queryLower) ||
                video.creatorUsername.lowercase().contains(queryLower) ||
                video.hashtags.any { it.lowercase().contains(queryLower) }
            matchesTag && matchesQuery
        }
    }

    val trendingVideos = remember(allVideos) {
        allVideos.sortedByDescending { it.likesCount * 3L + it.viewsCount }
            .take(8)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Discover",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search creators, videos, or #hashtags", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SalmanCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_search_input")
                )
            }
        }

        // Hashtag Chips
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allTags) { tag ->
                    val selected = selectedHashtag == tag
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectHashtag(tag) },
                        label = { Text("#$tag") },
                        leadingIcon = {
                            Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianSurface,
                            labelColor = TextSecondary,
                            selectedContainerColor = SalmanCrimson,
                            selectedLabelColor = TextPrimary
                        )
                    )
                }
            }
        }

        // Popular Creators
        item {
            Column {
                Text(
                    text = "Popular Creators",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (filteredCreators.isEmpty()) {
                    Text(
                        text = "No matching creators found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredCreators, key = { it.userId }) { creator ->
                            CreatorHighlightCard(
                                creator = creator,
                                isSelf = creator.userId == currentUserId,
                                isFollowing = creator.userId in followingIds,
                                onCardClick = { onOpenCreatorProfile(creator.userId) },
                                onFollowClick = {
                                    viewModel.toggleFollowCreator(creator.userId, creator.username)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Trending Reels
        if (trendingVideos.isNotEmpty() && queryLower.isBlank() && selectedHashtag == null) {
            item {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = SalmanGold)
                        Text(
                            text = "Trending Now",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(trendingVideos, key = { it.id }) { video ->
                            DiscoverVideoCard(
                                video = video,
                                onClick = { previewVideo = video },
                                modifier = Modifier.width(156.dp)
                            )
                        }
                    }
                }
            }
        }

        // Recommended / Search Results
        item {
            Text(
                text = if (queryLower.isNotBlank() || selectedHashtag != null) {
                    "Matching Videos (${filteredVideos.size})"
                } else {
                    "Recommended Content"
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (filteredVideos.isEmpty()) {
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "No videos match your filter yet. Try clearing the search or uploading a new reel!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            val rows = filteredVideos.chunked(2)
            items(rows) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { video ->
                        DiscoverVideoCard(
                            video = video,
                            onClick = { previewVideo = video },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
                    Icon(Icons.Filled.Close, contentDescription = "Close player", tint = TextPrimary)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "@${video.creatorUsername}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = video.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatorHighlightCard(
    creator: UserProfile,
    isSelf: Boolean,
    isFollowing: Boolean,
    onCardClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(148.dp)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurfaceElevated)
                    .border(1.5.dp, Brush.linearGradient(listOf(SalmanCrimson, SalmanCyan)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (creator.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = creator.avatarUrl,
                        contentDescription = creator.username,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = creator.username.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = SalmanCyan
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = creator.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${creator.username}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatCompactCount(creator.followersCount)} followers",
                style = MaterialTheme.typography.labelSmall,
                color = SalmanCyan
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (!isSelf) {
                if (isFollowing) {
                    OutlinedButton(
                        onClick = onFollowClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Following", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }
                } else {
                    Button(
                        onClick = onFollowClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SalmanCrimson),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Follow", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverVideoCard(
    video: VideoPost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 14f)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurfaceElevated)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E142B), Color(0xFF101E2B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.85f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = video.caption,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "@${video.creatorUsername}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SalmanCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatCompactCount(video.likesCount)} ♥",
                    style = MaterialTheme.typography.labelSmall,
                    color = SalmanCrimson
                )
            }
        }
    }
}
