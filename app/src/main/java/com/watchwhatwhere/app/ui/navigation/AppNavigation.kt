package com.watchwhatwhere.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watchwhatwhere.app.BuildConfig
import com.watchwhatwhere.app.MainActivity
import com.watchwhatwhere.app.data.model.AuthState
import android.net.Uri
import com.watchwhatwhere.app.data.model.UserInfo
import com.watchwhatwhere.app.data.repository.AuthRepository
import com.watchwhatwhere.app.data.repository.MobileConfigRepository
import com.watchwhatwhere.app.data.repository.PrefetchManager
import com.watchwhatwhere.app.ui.screens.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object ForceUpdate : Screen("force_update")
    object Browse : Screen("browse/{type}") {
        fun createRoute(type: String) = "browse/$type"
    }
    object Search : Screen("search")
    object TitleDetail : Screen("title/{titleId}") {
        fun createRoute(titleId: Long) = "title/$titleId"
        const val BASE_ROUTE = "title/"
    }
    object Artist : Screen("artist/{artistId}") {
        fun createRoute(artistId: Long) = "artist/$artistId"
        const val BASE_ROUTE = "artist/"
    }
    object Login : Screen("login")
    object Lists : Screen("lists")
    object Account : Screen("account")
    object ProdCompany : Screen("prodcompany/{companyId}/{companyName}") {
        fun createRoute(companyId: Long, companyName: String) = "prodcompany/$companyId/${Uri.encode(companyName)}"
    }
    object ProviderPrefs : Screen("provider_prefs")
    object Providers : Screen("providers")
    object Contact : Screen("contact")
}

/**
 * Tabs for the bottom navigation bar.
 */
private sealed class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomTab(Screen.Home.route, "Home", Icons.Default.Home)
    object Providers : BottomTab(Screen.Providers.route, "Providers", Icons.Default.Tv)
    object LoginSignup : BottomTab(Screen.Login.route, "Login / Signup", Icons.Default.PersonAdd)
    object Lists : BottomTab(Screen.Lists.route, "Lists", Icons.Default.PlaylistPlay)
    object Account : BottomTab(Screen.Account.route, "Account", Icons.Default.AccountCircle)
    object Disconnected : BottomTab("disconnected", "Disconnected", Icons.Default.WifiOff)
}

/**
 * ViewModel that handles mobile config initialization, version checking, and auth state.
 */
@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val configRepository: MobileConfigRepository,
    private val authRepository: AuthRepository,
    val prefetchManager: PrefetchManager,
    val api: com.watchwhatwhere.app.data.api.WatchWhatWhereApi
) : ViewModel() {
    
    sealed class StartupResult {
        object Loading : StartupResult()
        object Proceed : StartupResult()
        data class ForceUpdate(val updateUrl: String) : StartupResult()
    }
    
    private val _startupResult = MutableStateFlow<StartupResult>(StartupResult.Loading)
    val startupResult: StateFlow<StartupResult> = _startupResult.asStateFlow()
    
    val prefetchStatus = prefetchManager.status
    
    val authState: StateFlow<AuthState> = authRepository.authState
    
    fun initializeConfig() {
        viewModelScope.launch {
            configRepository.initialize()
            
            val config = configRepository.config.value
            if (config != null && configRepository.isUpdateRequired(
                    current = BuildConfig.VERSION_NAME,
                    minimum = config.app.minVersion
                )) {
                _startupResult.value = StartupResult.ForceUpdate(
                    updateUrl = configRepository.getUpdateUrl()
                )
            } else {
                // Check auth session after config is loaded
                authRepository.checkSession()
                _startupResult.value = StartupResult.Proceed
            }
        }
    }
    
    fun refreshAuthState() {
        viewModelScope.launch {
            authRepository.checkSession()
        }
    }
    
    /**
     * Login with Google using the ID token from the native Sign-In SDK.
     * Sends provider=google + accesstoken to the server.
     */
    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            authRepository.loginWithProvider(
                mapOf(
                    "provider" to "google",
                    "accesstoken" to idToken
                )
            )
        }
    }
    
    fun logout() {
        authRepository.logout()
    }
    
    /** Generate PKCE code_verifier and code_challenge for OAuth */
    fun generatePkce() = authRepository.generatePkce()
    
    /**
     * Handle OAuth auth code from the WebView.
     * Exchanges the code with the provider's token endpoint (using PKCE),
     * then sends the access token to our server via loginWithProvider.
     */
    fun handleOAuthCode(
        code: String,
        provider: String,
        codeVerifier: String?,
        clientId: String,
        redirectUri: String,
        tokenEndpoint: String
    ) {
        viewModelScope.launch {
            authRepository.handleOAuthCode(code, provider, codeVerifier, clientId, redirectUri, tokenEndpoint)
        }
    }
    
    /**
     * Handle OAuth redirect for non-PKCE providers.
     * Relays the full redirect URL via OkHttp.
     */
    fun handleOAuthRedirect(redirectUrl: String) {
        viewModelScope.launch {
            authRepository.handleOAuthRedirect(redirectUrl)
        }
    }
    
    /**
     * Handle Facebook access token from implicit flow.
     * Fetches user info from Graph API and sends to server.
     */
    fun handleFacebookToken(accessToken: String) {
        viewModelScope.launch {
            authRepository.handleFacebookToken(accessToken)
        }
    }
    
    fun checkForUpdates() {
        viewModelScope.launch {
            configRepository.forceRefresh()
        }
    }
    
    /** Reactive config flow — composables should collectAsState() */
    val configFlow = configRepository.config
}

/**
 * Trim the back stack to at most [maxSize] entries.
 * Removes the oldest entries (just above Home) when the limit is exceeded.
 */
private const val MAX_BACK_STACK_SIZE = 20

private fun NavHostController.trimBackStack() {
    val entries = currentBackStack.value
    if (entries.size > MAX_BACK_STACK_SIZE) {
        // Pop the entry just above the start destination to trim from the bottom
        val homeRoute = graph.startDestinationRoute ?: return
        popBackStack(homeRoute, inclusive = false)
    }
}

/**
 * Navigate to a title detail screen, always pushing onto the stack.
 */
fun NavHostController.navigateToTitle(titleId: Long) {
    navigate(Screen.TitleDetail.createRoute(titleId))
    trimBackStack()
}

/**
 * Navigate to an artist screen, always pushing onto the stack.
 */
fun NavHostController.navigateToArtist(artistId: Long) {
    navigate(Screen.Artist.createRoute(artistId))
    trimBackStack()
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: AppNavigationViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val config by viewModel.configFlow.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Network connectivity check
    val context = LocalContext.current
    val isOnline = remember { mutableStateOf(true) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            isOnline.value = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            kotlinx.coroutines.delay(3000L) // Check every 3 seconds
        }
    }
    
    // When auth state changes to Authenticated while on the login screen, go home
    LaunchedEffect(authState, currentRoute) {
        if (authState is AuthState.Authenticated && currentRoute == Screen.Login.route) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }
    
    // Always show bottom bar except on splash and force update
    val showBottomBar = currentRoute != null &&
        currentRoute != Screen.Splash.route &&
        currentRoute != Screen.ForceUpdate.route
    
    // Determine tabs based on auth state and network
    val tabs: List<BottomTab> = if (!isOnline.value) {
        listOf(BottomTab.Home, BottomTab.Disconnected)
    } else when (authState) {
        is AuthState.Authenticated -> listOf(BottomTab.Home, BottomTab.Providers, BottomTab.Lists, BottomTab.Account)
        else -> listOf(BottomTab.Home, BottomTab.Providers, BottomTab.LoginSignup)
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            IconButton(
                                onClick = {
                                    if (tab is BottomTab.Disconnected) {
                                        showNetworkDialog = true
                                        return@IconButton
                                    }
                                    navController.navigate(tab.route) {
                                        popUpTo(Screen.Home.route) {
                                            saveState = false
                                            inclusive = (tab.route == Screen.Home.route)
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(32.dp),
                                    tint = if (selected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        // Network Status Dialog
        if (showNetworkDialog) {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val hasWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            val hasCellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            
            AlertDialog(
                onDismissRequest = { showNetworkDialog = false },
                icon = {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("No Internet Connection") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "You're currently offline. Some features are unavailable:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "\u2022 Browsing and searching\n\u2022 Playing trailers\n\u2022 Writing reviews\n\u2022 Rating and tagging titles\n\u2022 Managing lists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Titles you've viewed before are available from cache.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Network Status", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Active Network: ${if (activeNetwork != null) "Detected" else "None"}", style = MaterialTheme.typography.bodySmall)
                                Text("WiFi: ${if (hasWifi) "Connected" else "Not connected"}", style = MaterialTheme.typography.bodySmall)
                                Text("Cellular: ${if (hasCellular) "Connected" else "Not connected"}", style = MaterialTheme.typography.bodySmall)
                                Text("Internet: ${if (hasInternet) "Available" else "Unavailable"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNetworkDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            composable(Screen.Splash.route) {
                val startupResult by viewModel.startupResult.collectAsState()
                
                SplashScreen(
                    onAnimationComplete = {
                        viewModel.initializeConfig()
                    }
                )
                
                when (val result = startupResult) {
                    is AppNavigationViewModel.StartupResult.Loading -> { /* still loading */ }
                    is AppNavigationViewModel.StartupResult.Proceed -> {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                    is AppNavigationViewModel.StartupResult.ForceUpdate -> {
                        navController.navigate(Screen.ForceUpdate.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            }
            
            composable(Screen.ForceUpdate.route) {
                val config by viewModel.startupResult.collectAsState()
                val updateUrl = (config as? AppNavigationViewModel.StartupResult.ForceUpdate)?.updateUrl
                    ?: "https://izonewe.com"
                ForceUpdateScreen(updateUrl = updateUrl)
            }
            
            composable(Screen.Home.route) {
                HomeScreen(
                    onTitleClick = { titleId ->
                        navController.navigateToTitle(titleId)
                    },
                    onBrowseClick = { type ->
                        navController.navigate(Screen.Browse.createRoute(type))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }
            
            composable(Screen.Login.route) {
                val prefetchStatus by viewModel.prefetchStatus.collectAsState()
                LoginScreen(
                    authConfig = config?.auth ?: com.watchwhatwhere.app.data.model.AuthConfig(),
                    baseUrl = config?.app?.baseUrl ?: "https://watchwhatwhere.com",
                    appVersion = "${BuildConfig.VERSION_CODE} (${BuildConfig.VERSION_NAME})",
                    onLoginComplete = {
                        viewModel.refreshAuthState()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onCheckSession = {
                        viewModel.refreshAuthState()
                    },
                    onGoogleToken = { idToken ->
                        viewModel.loginWithGoogle(idToken)
                    },
                    onOAuthCode = { code, provider, codeVerifier, clientId, redirectUri, tokenEndpoint ->
                        viewModel.handleOAuthCode(code, provider, codeVerifier, clientId, redirectUri, tokenEndpoint)
                    },
                    onOAuthRedirect = { redirectUrl ->
                        viewModel.handleOAuthRedirect(redirectUrl)
                    },
                    onFacebookToken = { accessToken ->
                        viewModel.handleFacebookToken(accessToken)
                    },
                    generatePkce = { viewModel.generatePkce() },
                    onCheckForUpdates = { viewModel.checkForUpdates() },
                    prefetchStatusText = prefetchStatus.progress.ifEmpty { null },
                    onContactClick = { navController.navigate(Screen.Contact.route) }
                )
            }
            
            composable(Screen.Providers.route) {
                ProvidersScreen(
                    onTitleClick = { titleId -> navController.navigateToTitle(titleId) }
                )
            }
            
            composable(Screen.Contact.route) {
                val user = (authState as? AuthState.Authenticated)?.user
                ContactScreen(
                    onBack = { navController.popBackStack() },
                    api = viewModel.api,
                    prefillName = user?.name ?: "",
                    prefillEmail = user?.email ?: ""
                )
            }
            
            composable(Screen.Lists.route) {
                ListsScreen(
                    onTitleClick = { titleId -> navController.navigateToTitle(titleId) }
                )
            }
            
            composable(Screen.Account.route) {
                val user = (authState as? AuthState.Authenticated)?.user
                val prefetchStatus by viewModel.prefetchStatus.collectAsState()
                if (user != null) {
                    AccountScreen(
                        userInfo = user,
                        onLogout = {
                            viewModel.logout()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onCheckForUpdates = { viewModel.checkForUpdates() },
                        prefetchStatusText = prefetchStatus.progress,
                        onProviderPrefsClick = {
                            navController.navigate(Screen.ProviderPrefs.route)
                        },
                        onContactClick = {
                            navController.navigate(Screen.Contact.route)
                        }
                    )
                } else {
                    // If somehow we got here without auth, redirect to login
                    LaunchedEffect(Unit) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Account.route) { inclusive = true }
                        }
                    }
                }
            }
            
            composable(
                route = Screen.Browse.route,
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "movie"
                BrowseScreen(
                    type = type,
                    onTitleClick = { titleId ->
                        navController.navigateToTitle(titleId)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(Screen.Search.route) {
                SearchScreen(
                    onTitleClick = { titleId ->
                        navController.navigateToTitle(titleId)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Screen.TitleDetail.route,
                arguments = listOf(navArgument("titleId") { type = NavType.LongType })
            ) { backStackEntry ->
                val titleId = backStackEntry.arguments?.getLong("titleId") ?: 0L
                TitleDetailScreen(
                    titleId = titleId,
                    onArtistClick = { artistId ->
                        navController.navigateToArtist(artistId)
                    },
                    onTitleClick = { id ->
                        navController.navigateToTitle(id)
                    },
                    onProdCompanyClick = { companyId, companyName ->
                        navController.navigate(Screen.ProdCompany.createRoute(companyId, companyName)) {
                            navController.trimBackStack()
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Screen.Artist.route,
                arguments = listOf(navArgument("artistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getLong("artistId") ?: 0L
                ArtistScreen(
                    artistId = artistId,
                    onTitleClick = { titleId ->
                        navController.navigateToTitle(titleId)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Screen.ProdCompany.route,
                arguments = listOf(
                    navArgument("companyId") { type = NavType.LongType },
                    navArgument("companyName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val companyId = backStackEntry.arguments?.getLong("companyId") ?: 0L
                val companyName = Uri.decode(backStackEntry.arguments?.getString("companyName") ?: "")
                ProductionCompanyScreen(
                    companyId = companyId,
                    companyName = companyName,
                    onTitleClick = { titleId ->
                        navController.navigateToTitle(titleId)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
            
            composable(Screen.ProviderPrefs.route) {
                ProviderPrefsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
