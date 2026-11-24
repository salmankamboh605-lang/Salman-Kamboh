package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AdminRepository
import com.example.data.SalmanMessagingService
import com.example.data.SocialRepository
import com.example.data.VideoRepository
import com.example.data.attemptAutoSignIn
import com.example.data.authStateFlow
import com.example.ui.screens.AdminConsoleScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StudioUploadScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SalmanCrimson
import com.example.ui.theme.SalmanCyan
import com.example.ui.theme.SalmanVideoTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.FeedAndDiscoverViewModel
import com.example.ui.viewmodel.SocialAndAdminViewModel
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object DiscoverRoute
@Serializable data object UploadRoute
@Serializable data object InboxRoute
@Serializable data object ProfileRoute
@Serializable data object AdminConsoleRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SalmanMessagingService.ensureChannel(applicationContext)
        setContent {
            SalmanVideoTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showSplash by remember { mutableStateOf(true) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            LaunchedEffect(uid) {
                val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SocialRepository(db).ensureUserProfile(user)
            }
            SalmanVideoMainShell(currentUserId = uid)
        }
    }
}

@Composable
private fun SalmanVideoMainShell(currentUserId: String) {
    val context = LocalContext.current

    // Request POST_NOTIFICATIONS on Android 13+ when signed in
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(currentUserId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val feedDiscoverViewModel: FeedAndDiscoverViewModel = viewModel(
        key = "feed_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                FeedAndDiscoverViewModel(VideoRepository(db), SocialRepository(db), currentUserId)
            }
        }
    )

    val studioViewModel: StudioViewModel = viewModel(
        key = "studio_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                StudioViewModel(VideoRepository(db), SocialRepository(db), currentUserId)
            }
        }
    )

    val socialAdminViewModel: SocialAndAdminViewModel = viewModel(
        key = "social_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SocialAndAdminViewModel(
                    SocialRepository(db),
                    VideoRepository(db),
                    AdminRepository(db),
                    currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isAdminRoute = currentDestination?.hasRoute<AdminConsoleRoute>() == true

    val snackbarHostState = remember { SnackbarHostState() }
    val feedMsg by feedDiscoverViewModel.statusMessage.collectAsStateWithLifecycle()
    val socialMsg by socialAdminViewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(feedMsg) {
        feedMsg?.let {
            snackbarHostState.showSnackbar(it)
            feedDiscoverViewModel.clearStatusMessage()
        }
    }
    LaunchedEffect(socialMsg) {
        socialMsg?.let {
            snackbarHostState.showSnackbar(it)
            socialAdminViewModel.clearStatusMessage()
        }
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail && !isAdminRoute) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        val isHome = currentDestination?.hasRoute<HomeRoute>() == true
                        val isDiscover = currentDestination?.hasRoute<DiscoverRoute>() == true
                        val isUpload = currentDestination?.hasRoute<UploadRoute>() == true
                        val isInbox = currentDestination?.hasRoute<InboxRoute>() == true
                        val isProfile = currentDestination?.hasRoute<ProfileRoute>() == true

                        NavigationBarItem(
                            selected = isHome,
                            onClick = { navigateTopLevel(HomeRoute) },
                            icon = { Icon(if (isHome) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_home)) },
                            colors = salmanNavBarColors(),
                            modifier = Modifier.testTag("nav_home")
                        )
                        NavigationBarItem(
                            selected = isDiscover,
                            onClick = { navigateTopLevel(DiscoverRoute) },
                            icon = { Icon(if (isDiscover) Icons.Filled.Explore else Icons.Outlined.Explore, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_discover)) },
                            colors = salmanNavBarColors(),
                            modifier = Modifier.testTag("nav_discover")
                        )
                        NavigationBarItem(
                            selected = isUpload,
                            onClick = { navigateTopLevel(UploadRoute) },
                            icon = {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(width = 46.dp, height = 32.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.horizontalGradient(listOf(SalmanCyan, SalmanCrimson)))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.nav_upload), tint = ObsidianBg)
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.nav_upload)) },
                            colors = salmanNavBarColors(),
                            modifier = Modifier.testTag("nav_upload")
                        )
                        NavigationBarItem(
                            selected = isInbox,
                            onClick = { navigateTopLevel(InboxRoute) },
                            icon = { Icon(if (isInbox) Icons.Filled.Inbox else Icons.Outlined.Inbox, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_inbox)) },
                            colors = salmanNavBarColors(),
                            modifier = Modifier.testTag("nav_inbox")
                        )
                        NavigationBarItem(
                            selected = isProfile,
                            onClick = {
                                socialAdminViewModel.closeCreatorProfile()
                                navigateTopLevel(ProfileRoute)
                            },
                            icon = { Icon(if (isProfile) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_profile)) },
                            colors = salmanNavBarColors(),
                            modifier = Modifier.testTag("nav_profile")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isAdminRoute) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary
                    ) {
                        val isHome = currentDestination?.hasRoute<HomeRoute>() == true
                        val isDiscover = currentDestination?.hasRoute<DiscoverRoute>() == true
                        val isUpload = currentDestination?.hasRoute<UploadRoute>() == true
                        val isInbox = currentDestination?.hasRoute<InboxRoute>() == true
                        val isProfile = currentDestination?.hasRoute<ProfileRoute>() == true

                        NavigationRailItem(
                            selected = isHome,
                            onClick = { navigateTopLevel(HomeRoute) },
                            icon = { Icon(if (isHome) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_home)) },
                            colors = salmanRailColors()
                        )
                        NavigationRailItem(
                            selected = isDiscover,
                            onClick = { navigateTopLevel(DiscoverRoute) },
                            icon = { Icon(if (isDiscover) Icons.Filled.Explore else Icons.Outlined.Explore, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_discover)) },
                            colors = salmanRailColors()
                        )
                        NavigationRailItem(
                            selected = isUpload,
                            onClick = { navigateTopLevel(UploadRoute) },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null, tint = SalmanCrimson) },
                            label = { Text(stringResource(R.string.nav_upload)) },
                            colors = salmanRailColors()
                        )
                        NavigationRailItem(
                            selected = isInbox,
                            onClick = { navigateTopLevel(InboxRoute) },
                            icon = { Icon(if (isInbox) Icons.Filled.Inbox else Icons.Outlined.Inbox, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_inbox)) },
                            colors = salmanRailColors()
                        )
                        NavigationRailItem(
                            selected = isProfile,
                            onClick = {
                                socialAdminViewModel.closeCreatorProfile()
                                navigateTopLevel(ProfileRoute)
                            },
                            icon = { Icon(if (isProfile) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_profile)) },
                            colors = salmanRailColors()
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeFeedScreen(
                                viewModel = feedDiscoverViewModel,
                                currentUserId = currentUserId,
                                onNavigateToUpload = { navigateTopLevel(UploadRoute) },
                                onNavigateToDiscoverTag = { tag ->
                                    feedDiscoverViewModel.selectHashtag(tag)
                                    navigateTopLevel(DiscoverRoute)
                                },
                                onOpenCreatorProfile = { creatorId ->
                                    socialAdminViewModel.openCreatorProfile(creatorId)
                                    navigateTopLevel(ProfileRoute)
                                }
                            )
                        }
                        composable<DiscoverRoute> {
                            DiscoverScreen(
                                viewModel = feedDiscoverViewModel,
                                currentUserId = currentUserId,
                                onOpenCreatorProfile = { creatorId ->
                                    socialAdminViewModel.openCreatorProfile(creatorId)
                                    navigateTopLevel(ProfileRoute)
                                }
                            )
                        }
                        composable<UploadRoute> {
                            StudioUploadScreen(
                                viewModel = studioViewModel,
                                onPublishedNavigateHome = {
                                    studioViewModel.clearMessage()
                                    navigateTopLevel(HomeRoute)
                                }
                            )
                        }
                        composable<InboxRoute> {
                            InboxScreen(
                                viewModel = socialAdminViewModel,
                                currentUserId = currentUserId
                            )
                        }
                        composable<ProfileRoute> {
                            ProfileScreen(
                                viewModel = socialAdminViewModel,
                                currentUserId = currentUserId,
                                onOpenAdminConsole = {
                                    navController.navigate(AdminConsoleRoute)
                                },
                                onOpenDirectMessage = { targetUser ->
                                    socialAdminViewModel.startConversationWith(targetUser)
                                    navigateTopLevel(InboxRoute)
                                }
                            )
                        }
                        composable<AdminConsoleRoute> {
                            AdminConsoleScreen(
                                viewModel = socialAdminViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun salmanNavBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = TextPrimary,
    selectedTextColor = SalmanCyan,
    indicatorColor = SalmanCrimson.copy(alpha = 0.25f),
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)

@Composable
private fun salmanRailColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = TextPrimary,
    selectedTextColor = SalmanCyan,
    indicatorColor = SalmanCrimson.copy(alpha = 0.25f),
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)
