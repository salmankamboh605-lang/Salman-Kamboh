package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SalmanCrimson
import com.example.ui.theme.SalmanCyan
import com.example.ui.theme.SalmanGold
import com.example.ui.theme.SalmanSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SocialAndAdminViewModel

@Composable
fun AdminConsoleScreen(
    viewModel: SocialAndAdminViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val videos by viewModel.adminVideos.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    if (!isAdmin) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Administrator privileges required.", color = SalmanCrimson, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Back") }
        }
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
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Admin Moderation Console", style = MaterialTheme.typography.titleLarge, color = SalmanGold)
        }

        // Analytics Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdminMetricCard(title = "Users", value = users.size.toString(), modifier = Modifier.weight(1f))
            AdminMetricCard(title = "Videos", value = videos.size.toString(), modifier = Modifier.weight(1f))
            AdminMetricCard(
                title = "Open Reports",
                value = reports.count { it.status == "OPEN" }.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianBg,
            contentColor = TextPrimary,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                    color = SalmanGold
                )
            }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Reports") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Videos") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Users") })
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    if (reports.isEmpty()) {
                        item { Text("No reports submitted.", color = TextSecondary) }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${report.targetType} · ${report.targetId}", color = SalmanCyan, style = MaterialTheme.typography.labelLarge)
                                        Text(report.status, color = SalmanGold, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(report.reason, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                    if (report.details.isNotBlank()) {
                                        Text(report.details, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.updateReportStatus(report.id, "RESOLVED") },
                                            colors = ButtonDefaults.buttonColors(containerColor = SalmanSuccess.copy(alpha = 0.2f))
                                        ) {
                                            Text("Resolve", color = SalmanSuccess)
                                        }
                                        OutlinedButton(onClick = { viewModel.updateReportStatus(report.id, "DISMISSED") }) {
                                            Text("Dismiss", color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    items(videos, key = { it.id }) { video ->
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("@${video.creatorUsername}", color = SalmanCyan, style = MaterialTheme.typography.labelMedium)
                                    Text(video.caption, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    Text("Status: ${video.moderationStatus}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                                val isRemoved = video.moderationStatus == "REMOVED"
                                Button(
                                    onClick = {
                                        viewModel.moderateVideo(video.id, if (isRemoved) "APPROVED" else "REMOVED")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRemoved) ObsidianSurfaceElevated else SalmanCrimson
                                    )
                                ) {
                                    Text(if (isRemoved) "Restore" else "Remove")
                                }
                            }
                        }
                    }
                }
                else -> {
                    items(users, key = { it.userId }) { user ->
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("@${user.username}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${user.followersCount} followers · Status: ${user.accountStatus}",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                val suspended = user.accountStatus == "suspended"
                                Button(
                                    onClick = {
                                        viewModel.setUserAccountStatus(user.userId, if (suspended) "active" else "suspended")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (suspended) ObsidianSurfaceElevated else SalmanCrimson
                                    )
                                ) {
                                    Text(if (suspended) "Activate" else "Suspend")
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
private fun AdminMetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, OutlineSubtle, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = SalmanCyan)
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}
