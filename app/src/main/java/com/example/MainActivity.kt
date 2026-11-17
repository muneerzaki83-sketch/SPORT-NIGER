package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.remote.FootballApiRepository
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.SportNigerRepository
import com.example.localization.AppLanguage
import com.example.localization.LanguagePreferences
import com.example.localization.LocalStrings
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.KokowaScreen
import com.example.ui.screens.LanguageOnboardingScreen
import com.example.ui.screens.LiveHubScreen
import com.example.ui.screens.LivePlayerScreen
import com.example.ui.screens.MatchDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ScoresScreen
import com.example.ui.screens.TournamentDetailScreen
import com.example.ui.screens.TournamentsScreen
import com.example.ui.screens.WrestlerDetailScreen
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DeepBlueContainer
import com.example.ui.theme.MutedSteelText
import com.example.ui.theme.PitchNavySurface
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.SportNigerTheme
import com.example.ui.viewmodel.SportNigerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object SportNigerRoutes {
    const val HOME = "home"
    const val LIVE = "live"
    const val SCORES = "scores"
    const val TOURNAMENTS = "tournaments"
    const val PROFILE = "profile"
    const val KOKOWA = "kokowa"
    const val ADMIN = "admin"
    const val PLAYER = "player/{broadcastId}"
    const val MATCH_DETAIL = "match/{matchId}"
    const val WRESTLER_DETAIL = "wrestler/{wrestlerId}"
    const val TOURNAMENT_DETAIL = "tournament/{tournamentId}"

    fun player(broadcastId: String): String = "player/${Uri.encode(broadcastId)}"
    fun matchDetail(matchId: String): String = "match/${Uri.encode(matchId)}"
    fun wrestlerDetail(wrestlerId: String): String = "wrestler/${Uri.encode(wrestlerId)}"
    fun tournamentDetail(tournamentId: String): String = "tournament/${Uri.encode(tournamentId)}"
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            SportNigerRoot()
        }
    }
}

@Composable
private fun SportNigerRoot() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val languagePreferences = remember(appContext) { LanguagePreferences(appContext) }

    val prefsState by languagePreferences.stateFlow.collectAsStateWithLifecycle(initialValue = null)
    val currentUser by AuthRepository.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )

    LaunchedEffect(Unit) {
        AuthRepository.attemptAutoSignIn(appContext)
    }

    val activeLanguage = prefsState?.language ?: AppLanguage.ENGLISH

    SportNigerTheme(language = activeLanguage) {
        val loadedPrefs = prefsState
        if (loadedPrefs == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkNavyBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrightBlueAccent)
            }
            return@SportNigerTheme
        }

        if (!loadedPrefs.hasCompletedOnboarding) {
            LanguageOnboardingScreen(
                selectedLanguage = loadedPrefs.language,
                onSelectLanguage = { lang ->
                    scope.launch {
                        languagePreferences.setLanguage(lang, markOnboarded = false)
                    }
                },
                onContinue = {
                    scope.launch {
                        languagePreferences.setLanguage(loadedPrefs.language, markOnboarded = true)
                    }
                }
            )
            return@SportNigerTheme
        }

        val user = currentUser
        if (user == null) {
            AuthScreen(
                onSelectLanguage = { lang ->
                    scope.launch {
                        languagePreferences.setLanguage(lang, markOnboarded = true)
                    }
                },
                onAuthSuccess = {
                    // authStateFlow reacts automatically once FirebaseAuth signs in
                }
            )
        } else {
            androidx.compose.runtime.key(user.uid) {
                SportNigerAuthenticatedApp(
                    currentUserId = user.uid,
                    initialLanguageCode = loadedPrefs.language.code,
                    languagePreferences = languagePreferences
                )
            }
        }
    }
}

@Composable
private fun SportNigerAuthenticatedApp(
    currentUserId: String,
    initialLanguageCode: String,
    languagePreferences: LanguagePreferences
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val strings = LocalStrings.current

    val viewModel: SportNigerViewModel = viewModel(
        key = "sport_niger_vm_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SportNigerViewModel(
                    repository = SportNigerRepository(db = db),
                    adminRepository = AdminRepository(db = db),
                    footballApiRepository = FootballApiRepository(),
                    languagePreferences = languagePreferences,
                    currentUserId = currentUserId,
                    initialLanguageCode = initialLanguageCode
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val snackbarHostState = remember { SnackbarHostState() }
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    LaunchedEffect(actionMessage) {
        val message = actionMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val topDestinations = listOf(
        TopNavItem(
            route = SportNigerRoutes.HOME,
            label = strings.navHome,
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            testTag = "nav_home"
        ),
        TopNavItem(
            route = SportNigerRoutes.LIVE,
            label = strings.navLive,
            selectedIcon = Icons.Filled.LiveTv,
            unselectedIcon = Icons.Outlined.LiveTv,
            testTag = "nav_live"
        ),
        TopNavItem(
            route = SportNigerRoutes.SCORES,
            label = strings.navScores,
            selectedIcon = Icons.Filled.SportsSoccer,
            unselectedIcon = Icons.Outlined.SportsSoccer,
            testTag = "nav_scores"
        ),
        TopNavItem(
            route = SportNigerRoutes.TOURNAMENTS,
            label = strings.navTournaments,
            selectedIcon = Icons.Filled.EmojiEvents,
            unselectedIcon = Icons.Outlined.EmojiEvents,
            testTag = "nav_tournaments"
        ),
        TopNavItem(
            route = SportNigerRoutes.PROFILE,
            label = strings.navProfile,
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            testTag = "nav_profile"
        )
    )

    val topRoutes = remember {
        setOf(
            SportNigerRoutes.HOME,
            SportNigerRoutes.LIVE,
            SportNigerRoutes.SCORES,
            SportNigerRoutes.TOURNAMENTS,
            SportNigerRoutes.PROFILE
        )
    }
    val showTopLevelNav = currentRoute in topRoutes

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkNavyBackground,
            contentWindowInsets = if (showTopLevelNav) WindowInsets.statusBars else WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = DeepBlueContainer,
                        contentColor = PureWhiteText,
                        actionColor = BrightBlueAccent
                    )
                }
            },
            bottomBar = {
                if (showTopLevelNav && !useNavigationRail) {
                    NavigationBar(
                        containerColor = PitchNavySurface,
                        contentColor = PureWhiteText,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        topDestinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PureWhiteText,
                                    selectedTextColor = BrightBlueAccent,
                                    indicatorColor = PrimaryBlue,
                                    unselectedIconColor = MutedSteelText,
                                    unselectedTextColor = MutedSteelText
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useNavigationRail) {
                    NavigationRail(
                        containerColor = PitchNavySurface,
                        contentColor = PureWhiteText,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.weight(0.15f))
                        topDestinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = PureWhiteText,
                                    selectedTextColor = BrightBlueAccent,
                                    indicatorColor = PrimaryBlue,
                                    unselectedIconColor = MutedSteelText,
                                    unselectedTextColor = MutedSteelText
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    VerticalDivider(color = BorderSlate, thickness = 1.dp)
                }

                NavHost(
                    navController = navController,
                    startDestination = SportNigerRoutes.HOME,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    composable(SportNigerRoutes.HOME) {
                        HomeScreen(
                            viewModel = viewModel,
                            onOpenBroadcast = { broadcastId ->
                                navController.navigate(SportNigerRoutes.player(broadcastId))
                            },
                            onOpenMatch = { matchId ->
                                navController.navigate(SportNigerRoutes.matchDetail(matchId))
                            },
                            onOpenKokowa = {
                                navController.navigate(SportNigerRoutes.KOKOWA)
                            },
                            onOpenWrestler = { wrestlerId ->
                                navController.navigate(SportNigerRoutes.wrestlerDetail(wrestlerId))
                            },
                            onOpenTournament = { tournamentId ->
                                navController.navigate(SportNigerRoutes.tournamentDetail(tournamentId))
                            },
                            onNavigateToLive = { navigateTopLevel(SportNigerRoutes.LIVE) },
                            onNavigateToScores = { navigateTopLevel(SportNigerRoutes.SCORES) },
                            onNavigateToTournaments = { navigateTopLevel(SportNigerRoutes.TOURNAMENTS) },
                            onOpenAdmin = {
                                navController.navigate(SportNigerRoutes.ADMIN)
                            }
                        )
                    }

                    composable(SportNigerRoutes.LIVE) {
                        LiveHubScreen(
                            viewModel = viewModel,
                            onOpenBroadcast = { broadcastId ->
                                navController.navigate(SportNigerRoutes.player(broadcastId))
                            }
                        )
                    }

                    composable(SportNigerRoutes.SCORES) {
                        ScoresScreen(
                            viewModel = viewModel,
                            onOpenMatch = { matchId ->
                                navController.navigate(SportNigerRoutes.matchDetail(matchId))
                            }
                        )
                    }

                    composable(SportNigerRoutes.TOURNAMENTS) {
                        TournamentsScreen(
                            viewModel = viewModel,
                            onOpenTournament = { tournamentId ->
                                navController.navigate(SportNigerRoutes.tournamentDetail(tournamentId))
                            }
                        )
                    }

                    composable(SportNigerRoutes.PROFILE) {
                        ProfileScreen(
                            viewModel = viewModel,
                            onOpenAdmin = {
                                navController.navigate(SportNigerRoutes.ADMIN)
                            },
                            onSignedOut = {
                                // authStateFlow redirects to AuthScreen automatically
                            }
                        )
                    }

                    composable(SportNigerRoutes.KOKOWA) {
                        KokowaScreen(
                            viewModel = viewModel,
                            onOpenBroadcast = { broadcastId ->
                                navController.navigate(SportNigerRoutes.player(broadcastId))
                            },
                            onOpenWrestler = { wrestlerId ->
                                navController.navigate(SportNigerRoutes.wrestlerDetail(wrestlerId))
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = SportNigerRoutes.PLAYER,
                        arguments = listOf(navArgument("broadcastId") { type = NavType.StringType })
                    ) { entry ->
                        val broadcastId = entry.arguments?.getString("broadcastId").orEmpty()
                        LivePlayerScreen(
                            broadcastId = broadcastId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = SportNigerRoutes.MATCH_DETAIL,
                        arguments = listOf(navArgument("matchId") { type = NavType.StringType })
                    ) { entry ->
                        val matchId = entry.arguments?.getString("matchId").orEmpty()
                        MatchDetailScreen(
                            matchId = matchId,
                            viewModel = viewModel,
                            onWatchBroadcast = { broadcastId ->
                                navController.navigate(SportNigerRoutes.player(broadcastId))
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = SportNigerRoutes.WRESTLER_DETAIL,
                        arguments = listOf(navArgument("wrestlerId") { type = NavType.StringType })
                    ) { entry ->
                        val wrestlerId = entry.arguments?.getString("wrestlerId").orEmpty()
                        WrestlerDetailScreen(
                            wrestlerId = wrestlerId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = SportNigerRoutes.TOURNAMENT_DETAIL,
                        arguments = listOf(navArgument("tournamentId") { type = NavType.StringType })
                    ) { entry ->
                        val tournamentId = entry.arguments?.getString("tournamentId").orEmpty()
                        TournamentDetailScreen(
                            tournamentId = tournamentId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(SportNigerRoutes.ADMIN) {
                        AdminDashboardScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
