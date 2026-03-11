package com.watchwhatwhere.app.ui.screens

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.watchwhatwhere.app.data.model.*
import com.watchwhatwhere.app.data.repository.ProfileRepository
import com.watchwhatwhere.app.data.repository.TitleRepository
import com.watchwhatwhere.app.ui.components.ErrorScreen
import com.watchwhatwhere.app.ui.components.getErrorMessage
import com.watchwhatwhere.app.ui.components.StreamingLogo
import com.watchwhatwhere.app.ui.components.TitleCarousel
import com.watchwhatwhere.app.ui.components.openWatchUrl
import com.watchwhatwhere.app.ui.theme.CardBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TitleDetailUiState(
    val isLoading: Boolean = true,
    val detail: TitleDetail? = null,
    val selectedSeason: Int = 1,
    val error: Throwable? = null
)

data class TagState(
    val vote: String? = null,       // "up" or "down"
    val onWatchlist: Boolean = false,
    val isSeen: Boolean = false,
    val starRating: Int? = null,    // 1-5
    val isShared: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class TitleDetailViewModel @Inject constructor(
    private val repository: TitleRepository,
    private val profileRepository: ProfileRepository,
    private val configRepository: com.watchwhatwhere.app.data.repository.MobileConfigRepository,
    private val authRepository: com.watchwhatwhere.app.data.repository.AuthRepository
) : ViewModel() {
    
    val baseUrl: String
        get() = configRepository.config.value?.app?.baseUrl ?: "https://watchwhatwhere.com"
    
    val isLoggedIn: Boolean
        get() = authRepository.authState.value is com.watchwhatwhere.app.data.model.AuthState.Authenticated
    
    val providerPrefsMap: Map<String, String>
        get() = (authRepository.authState.value as? com.watchwhatwhere.app.data.model.AuthState.Authenticated)
            ?.user?.providerPrefs?.associate { it.source to it.status } ?: emptyMap()
    
    val watchCostPriority: List<String>
        get() = configRepository.config.value?.content?.watchProviderTypes
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("free", "sub", "plus", "rent", "buy")
    
    val videoTypeOrder: List<String>
        get() = configRepository.config.value?.content?.videoTypeOrder
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("Teaser", "Trailer", "Clip", "Featurette", "Recap", "Opening Credits", "Behind the Scenes", "Bloopers")
    
    private val _uiState = MutableStateFlow(TitleDetailUiState())
    val uiState: StateFlow<TitleDetailUiState> = _uiState.asStateFlow()
    
    private val _tagState = MutableStateFlow(TagState())
    val tagState: StateFlow<TagState> = _tagState.asStateFlow()
    
    private var currentTitleId: Long = 0
    
    fun load(titleId: Long) {
        currentTitleId = titleId
        viewModelScope.launch {
            _uiState.value = TitleDetailUiState(isLoading = true)
            repository.getTitleDetail(titleId)
                .onSuccess { detail ->
                    _uiState.value = TitleDetailUiState(
                        isLoading = false,
                        detail = detail
                    )
                }
                .onFailure { e ->
                    _uiState.value = TitleDetailUiState(
                        isLoading = false,
                        error = e
                    )
                }
        }
        loadTags(titleId)
    }
    
    private fun loadTags(titleId: Long) {
        viewModelScope.launch {
            _tagState.value = TagState(isLoading = true)
            profileRepository.getTags(titleId.toString()).fold(
                onSuccess = { response ->
                    _tagState.value = TagState(
                        vote = response.vote,
                        onWatchlist = response.list != null,
                        isSeen = response.seen != null,
                        starRating = (response.star ?: response.starRating)?.toIntOrNull(),
                        isShared = response.share != null,
                        isLoading = false
                    )
                },
                onFailure = {
                    _tagState.value = TagState(isLoading = false)
                }
            )
        }
    }
    
    fun toggleVote(direction: String) {
        val current = _tagState.value
        val isRemoving = current.vote == direction
        val isChangingDirection = !isRemoving && current.vote != null && current.vote != direction
        _tagState.value = current.copy(
            vote = if (isRemoving) null else direction,
            starRating = if (isRemoving || isChangingDirection) null else current.starRating
        )
        viewModelScope.launch {
            // Clear star rating when changing vote direction or removing vote
            if (isRemoving || isChangingDirection) {
                profileRepository.tagTitle(
                    titleId = currentTitleId.toString(),
                    tag = "star",
                    action = "del"
                )
            }
            profileRepository.tagTitle(
                titleId = currentTitleId.toString(),
                tag = "vote",
                action = if (isRemoving) "del" else "put",
                extras = if (isRemoving) emptyMap() else mapOf("vote" to direction)
            )
        }
    }
    
    fun toggleWatchlist() {
        val current = _tagState.value
        val isRemoving = current.onWatchlist
        _tagState.value = current.copy(onWatchlist = !isRemoving)
        viewModelScope.launch {
            profileRepository.tagTitle(
                titleId = currentTitleId.toString(),
                tag = "list",
                action = if (isRemoving) "del" else "put"
            )
        }
    }
    
    fun toggleSeen() {
        val current = _tagState.value
        val isRemoving = current.isSeen
        _tagState.value = current.copy(isSeen = !isRemoving)
        viewModelScope.launch {
            profileRepository.tagTitle(
                titleId = currentTitleId.toString(),
                tag = "seen",
                action = if (isRemoving) "del" else "put"
            )
        }
    }
    
    fun setStarRating(rating: Int) {
        val current = _tagState.value
        val isRemoving = current.starRating == rating
        _tagState.value = current.copy(starRating = if (isRemoving) null else rating)
        viewModelScope.launch {
            profileRepository.tagTitle(
                titleId = currentTitleId.toString(),
                tag = "star",
                action = if (isRemoving) "del" else "put",
                extras = if (isRemoving) emptyMap() else mapOf("star" to rating.toString())
            )
        }
    }
    
    fun addToShareList() {
        viewModelScope.launch {
            var listId = profileRepository.getDefaultShareListId()
            
            // Auto-create a default share list if none is set
            if (listId == null) {
                profileRepository.createList("My Share List", "public").fold(
                    onSuccess = {
                        // Re-fetch lists to find the newly created one
                        profileRepository.getUserLists().fold(
                            onSuccess = { lists ->
                                val newList = lists.lastOrNull()
                                if (newList != null) {
                                    profileRepository.setDefaultShareList(newList.id)
                                    listId = newList.id
                                }
                            },
                            onFailure = { return@launch }
                        )
                    },
                    onFailure = { return@launch }
                )
            }
            
            if (listId != null) {
                profileRepository.addToList(listId!!, currentTitleId.toString()).fold(
                    onSuccess = { _tagState.value = _tagState.value.copy(isShared = true) },
                    onFailure = { /* silently fail */ }
                )
            }
        }
    }
    
    fun selectSeason(season: Int) {
        _uiState.value = _uiState.value.copy(selectedSeason = season)
    }
    
    fun submitReview(content: String) {
        viewModelScope.launch {
            profileRepository.submitReview(currentTitleId.toString(), content).onSuccess {
                load(currentTitleId) // Reload to show new review
            }
        }
    }
    
    suspend fun getUserReview(): String? {
        return profileRepository.getUserReview(currentTitleId.toString()).getOrNull()
    }
    
    fun deleteReview() {
        viewModelScope.launch {
            profileRepository.deleteReview(currentTitleId.toString()).onSuccess {
                load(currentTitleId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleDetailScreen(
    titleId: Long,
    onArtistClick: (Long) -> Unit,
    onTitleClick: (Long) -> Unit,
    onProdCompanyClick: (Long, String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    viewModel: TitleDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tagState by viewModel.tagState.collectAsState()
    val context = LocalContext.current
    
    var selectedVideoKey by remember { mutableStateOf<String?>(null) }
    var selectedVideoTitle by remember { mutableStateOf<String?>(null) }
    var showNoInternetDialog by remember { mutableStateOf(false) }
    
    // Network check
    val isOnline = remember {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    
    LaunchedEffect(titleId) {
        viewModel.load(titleId)
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(uiState.detail?.nama ?: "") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.error != null -> {
                        ErrorScreen(
                            message = getErrorMessage(uiState.error!!),
                            onRetry = { viewModel.load(titleId) }
                        )
                    }
                    uiState.detail != null -> {
                        val detail = uiState.detail!!
                    
                    LazyColumn {
                        // Backdrop with gradient
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(detail.data?.backdropPathSmall ?: detail.data?.backdropPath ?: detail.data?.posterPath ?: detail.data?.posterPathSmall)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                )
                                
                                // Title info overlay
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = detail.nama,
                                        style = MaterialTheme.typography.headlineLarge
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Star rating (5 stars, rounded to nearest 0.5)
                                        val ratingOutOf10 = detail.data?.averageRating?.toFloatOrNull() 
                                            ?: detail.data?.voteAverage?.toFloat()
                                        ratingOutOf10?.let { rating ->
                                            // Round to nearest 0.5 for star display
                                            val starRating = (kotlin.math.round(rating / 2f * 2) / 2f) // Round to 0.5
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                repeat(5) { index ->
                                                    val starValue = index + 1
                                                    when {
                                                        starRating >= starValue -> {
                                                            // Full star
                                                            Icon(
                                                                Icons.Default.Star,
                                                                contentDescription = null,
                                                                tint = Color.Yellow,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        starRating >= starValue - 0.5f -> {
                                                            // Half star (left half filled)
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.StarHalf,
                                                                contentDescription = null,
                                                                tint = Color.Yellow,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        else -> {
                                                            // Empty star
                                                            Icon(
                                                                Icons.Outlined.StarOutline,
                                                                contentDescription = null,
                                                                tint = Color.Gray,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    " %.1f/10".format(rating),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        detail.data?.startYear?.let { Text(it) }
                                        // Runtime - try runtimeMinutes first, then runtime
                                        val runtimeMin = detail.data?.runtimeMinutes?.toIntOrNull() ?: detail.data?.runtime
                                        runtimeMin?.let { Text("${it}min") }
                                    }
                                    
                                    // Genres
                                    detail.genres?.let { genres ->
                                        Text(
                                            text = genres.joinToString(" • "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                        
                        // ── Action Bar (hidden when offline or logged out) ──
                        if (isOnline && viewModel.isLoggedIn) {
                        item {
                            TitleActionBar(
                                tagState = tagState,
                                onVote = { direction -> viewModel.toggleVote(direction) },
                                onToggleWatchlist = { viewModel.toggleWatchlist() },
                                onToggleSeen = { viewModel.toggleSeen() },
                                onSetStar = { rating -> viewModel.setStarRating(rating) },
                                onAddToList = { viewModel.addToShareList() }
                            )
                        }
                        }
                        
                        // ── Social Share Icons ──
                        item {
                            val shareUrl = "${viewModel.baseUrl}/title/${detail.id}"
                            val shareText = "Check out ${detail.nama} on WatchWhatWhere!"
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // X (Twitter)
                                IconButton(onClick = {
                                    val url = "https://x.com/intent/tweet?text=${Uri.encode(shareText)}&url=${Uri.encode(shareUrl)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }) {
                                    Text("𝕏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                
                                // Bluesky
                                IconButton(onClick = {
                                    val bskyUri = Uri.parse("https://bsky.app/intent/compose")
                                        .buildUpon()
                                        .appendQueryParameter("text", "$shareText $shareUrl")
                                        .build()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, bskyUri))
                                }) {
                                    Text("🦋", style = MaterialTheme.typography.titleLarge)
                                }
                                
                                // Facebook
                                IconButton(onClick = {
                                    val url = "https://www.facebook.com/sharer/sharer.php?u=${Uri.encode(shareUrl)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }) {
                                    Text("f", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1877F2))
                                }
                                
                                // Reddit
                                IconButton(onClick = {
                                    val url = "https://www.reddit.com/submit?url=${Uri.encode(shareUrl)}&title=${Uri.encode(shareText)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }) {
                                    Text("⬆", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFF4500))
                                }
                                
                                // Native Share
                                IconButton(onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "$shareText\n$shareUrl")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share via"))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        // Overview
                        detail.data?.overview?.let { overview ->
                            item {
                                Text(
                                    text = overview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        // Info section (website, release date, status, budget, revenue)
                        item {
                            val data = detail.data
                            val hasInfo = data?.homepage != null || data?.releaseDate != null || 
                                         data?.firstAirDate != null || data?.status != null || 
                                         data?.budget != null || data?.revenue != null
                            
                            if (hasInfo) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    // Official Website
                                    data?.homepage?.takeIf { it.isNotBlank() }?.let { url ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Official Website",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.Gray
                                            )
                                            Text(
                                                "Visit →",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                                    }
                                    
                                    // Release Date
                                    val releaseDate = data?.releaseDate ?: data?.firstAirDate
                                    releaseDate?.let {
                                        InfoRow("Release Date", it)
                                    }
                                    
                                    // Status
                                    data?.status?.let {
                                        InfoRow("Status", it)
                                    }
                                    
                                    // Budget
                                    data?.formatMoney(data.budget)?.let {
                                        InfoRow("Budget", it)
                                    }
                                    
                                    // Revenue
                                    data?.formatMoney(data.revenue)?.let {
                                        InfoRow("Revenue", it)
                                    }
                                }
                            }
                        }
                        
                        // Watch options
                        detail.watchurls?.takeIf { it.isNotEmpty() }?.let { watchUrls ->
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Where to Watch",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Deduplicate: one item per source, pick best viewcost
                                    val costPriority = viewModel.watchCostPriority
                                    val prefs = viewModel.providerPrefsMap
                                    val deduped = watchUrls
                                        .groupBy { it.source ?: it.displayName }
                                        .map { (_, urls) ->
                                            urls.minByOrNull { costPriority.indexOf(it.viewcost).takeIf { i -> i >= 0 } ?: costPriority.size }
                                                ?: urls.first()
                                        }
                                        .sortedWith(compareBy<WatchUrl> {
                                            // Sort by pref: prio=0, default=1, hide=2
                                            when (prefs[it.source]) {
                                                "prio" -> 0
                                                "hide" -> 2
                                                else -> 1
                                            }
                                        }.thenBy {
                                            costPriority.indexOf(it.viewcost).takeIf { i -> i >= 0 } ?: costPriority.size
                                        })
                                    
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(deduped) { watchUrl ->
                                            StreamingLogo(
                                                watchUrl = watchUrl,
                                                isHidden = prefs[watchUrl.source] == "hide"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Episodes (TV only)
                        detail.episodes?.takeIf { it.isNotEmpty() }?.let { episodes ->
                            val seasons = episodes.mapNotNull { it.seasonnum }.distinct().sorted()
                            val seasonEpisodes = episodes.filter { it.seasonnum == uiState.selectedSeason }
                            
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Episodes",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        
                                        // Season selector
                                        var expanded by remember { mutableStateOf(false) }
                                        Box {
                                            TextButton(onClick = { expanded = true }) {
                                                Text("Season ${uiState.selectedSeason}")
                                            }
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                seasons.forEach { season ->
                                                    DropdownMenuItem(
                                                        text = { Text("Season $season") },
                                                        onClick = {
                                                            viewModel.selectSeason(season ?: 1)
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(seasonEpisodes) { episode ->
                                        EpisodeCard(episode = episode)
                                    }
                                }
                            }
                        }
                        
                        // Trailers
                        detail.videos?.takeIf { it.isNotEmpty() }?.let { rawVideos ->
                            val videoPriority = viewModel.videoTypeOrder.withIndex().associate { (i, v) -> v to i }
                            val videos = rawVideos.sortedBy { videoPriority[it.typa] ?: 99 }
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Trailers",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(videos) { video ->
                                            VideoCard(
                                                video = video,
                                                onClick = {
                                                    video.videokey?.let { key ->
                                                        // Check network connectivity
                                                        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
                                                        val network = connectivityManager?.activeNetwork
                                                        val capabilities = connectivityManager?.getNetworkCapabilities(network)
                                                        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                                                        
                                                        if (hasInternet) {
                                                            selectedVideoKey = key
                                                            selectedVideoTitle = video.nama
                                                        } else {
                                                            showNoInternetDialog = true
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Cast / Crew / Other tabs
                        detail.princ?.takeIf { it.isNotEmpty() }?.let { allMembers ->
                            // Categorize members
                            val castMembers = allMembers.filter { it.category == "actor" || it.category == "actress" }
                            val crewCategories = setOf("director", "writer", "producer", "composer", "cinematographer", "editor")
                            val crewMembers = allMembers.filter { it.category?.lowercase() in crewCategories }
                            val otherMembers = allMembers.filter { it !in castMembers && it !in crewMembers }
                            
                            // Build tabs list (only show tabs with members)
                            val tabs = buildList {
                                if (castMembers.isNotEmpty()) add("Cast" to castMembers)
                                if (crewMembers.isNotEmpty()) add("Crew" to crewMembers)
                                if (otherMembers.isNotEmpty()) add("Other" to otherMembers)
                            }
                            
                            if (tabs.isNotEmpty()) {
                                item {
                                    var selectedTab by remember { mutableIntStateOf(0) }
                                    
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        // Tab pills
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            tabs.forEachIndexed { index, (label, members) ->
                                                val isSelected = selectedTab == index
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(
                                                            if (isSelected) Color(0xFFE50914)
                                                            else Color.White.copy(alpha = 0.1f)
                                                        )
                                                        .clickable { selectedTab = index }
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = Color.White
                                                        )
                                                        Text(
                                                            text = "${members.size}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        // Members row for selected tab
                                        val currentMembers = tabs[selectedTab].second
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(currentMembers) { member ->
                                                CastCard(
                                                    member = member,
                                                    onClick = {
                                                        member.artistid?.let { onArtistClick(it) }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Production Companies
                        detail.prods?.takeIf { it.isNotEmpty() }?.let { prods ->
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Production",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(prods) { company ->
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.08f))
                                                    .clickable {
                                                        onProdCompanyClick(company.id, company.nama)
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                company.logoPath?.let { logo ->
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(logo)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = company.nama,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                    )
                                                }
                                                Text(
                                                    text = company.nama,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Collections
                        detail.colls?.forEach { collection ->
                            collection.others?.filter { it.id != detail.id }?.takeIf { it.isNotEmpty() }?.let { titles ->
                                val sortedTitles = titles.sortedByDescending { it.startYear ?: "" }
                                item {
                                    TitleCarousel(
                                        title = collection.nama ?: "Collection",
                                        items = sortedTitles,
                                        onTitleClick = onTitleClick,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            }
                        }
                        
                        // Reviews
                        val allReviews = (detail.reviewsSite?.map { it.copy(source = "user") } ?: emptyList()) +
                            (detail.reviews?.map { it.copy(source = it.source ?: "tmdb") } ?: emptyList())
                        allReviews.takeIf { it.isNotEmpty() }?.let { reviews ->
                            item {
                                var expanded by remember { mutableStateOf(false) }
                                var showWriteReview by remember { mutableStateOf(false) }
                                var reviewText by remember { mutableStateOf("") }
                                
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Header row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Reviews (${detail.reviewcount ?: reviews.size})",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        if (isOnline && viewModel.isLoggedIn) {
                                            TextButton(
                                                onClick = { showWriteReview = true },
                                                colors = ButtonDefaults.textButtonColors(
                                                    containerColor = Color(0xFFE50914),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Write a Review", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // Sort: user reviews first, then by date desc
                                    val sortedReviews = reviews.sortedWith(
                                        compareByDescending<com.watchwhatwhere.app.data.model.Review> { it.source == "user" }
                                            .thenByDescending { it.data ?: "" }
                                    )
                                    
                                    // Show reviews
                                    val displayReviews = if (expanded) sortedReviews else sortedReviews.take(1)
                                    displayReviews.forEachIndexed { index, review ->
                                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (review.source == "user") Color(0xFFE50914) else Color.Gray
                                                )
                                                Text(
                                                    text = review.author ?: "Anonymous",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                review.data?.let { date ->
                                                    Text(
                                                        text = date,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = review.content ?: "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.8f),
                                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    if (sortedReviews.size > 1 || (sortedReviews.firstOrNull()?.content?.length ?: 0) > 150) {
                                        Text(
                                            text = if (expanded) "Show less" else "Show all reviews...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable { expanded = !expanded }
                                                .padding(top = 4.dp)
                                        )
                                    }
                                }
                                
                                // Write/Edit Review Dialog
                                if (showWriteReview) {
                                    var isLoadingReview by remember { mutableStateOf(true) }
                                    var hasExistingReview by remember { mutableStateOf(false) }
                                    
                                    LaunchedEffect(Unit) {
                                        val existing = viewModel.getUserReview()
                                        if (existing != null) {
                                            reviewText = existing
                                            hasExistingReview = true
                                        }
                                        isLoadingReview = false
                                    }
                                    
                                    AlertDialog(
                                        onDismissRequest = { showWriteReview = false },
                                        title = { Text(if (hasExistingReview) "Edit Your Review" else "Write a Review") },
                                        text = {
                                            if (isLoadingReview) {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                                }
                                            } else {
                                                OutlinedTextField(
                                                    value = reviewText,
                                                    onValueChange = { reviewText = it },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(150.dp),
                                                    placeholder = { Text("Share your thoughts...") }
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    if (reviewText.isNotBlank()) {
                                                        viewModel.submitReview(reviewText)
                                                        reviewText = ""
                                                        showWriteReview = false
                                                    }
                                                }
                                            ) { Text(if (hasExistingReview) "Update" else "Submit") }
                                        },
                                        dismissButton = {
                                            Row {
                                                if (hasExistingReview) {
                                                    TextButton(onClick = {
                                                        viewModel.deleteReview()
                                                        reviewText = ""
                                                        showWriteReview = false
                                                    }) {
                                                        Text("Delete", color = Color(0xFFE50914))
                                                    }
                                                }
                                                TextButton(onClick = { showWriteReview = false }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Recommended
                        detail.rec?.takeIf { it.isNotEmpty() }?.let { rec ->
                            item {
                                TitleCarousel(
                                    title = "Recommended",
                                    items = rec,
                                    onTitleClick = onTitleClick,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        }
                        
                        // Similar
                        detail.sim?.takeIf { it.isNotEmpty() }?.let { sim ->
                            item {
                                TitleCarousel(
                                    title = "Similar",
                                    items = sim,
                                    onTitleClick = onTitleClick,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        }
                        
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
        }
        
        // Video player overlay - shown above everything
        selectedVideoKey?.let { key ->
            com.watchwhatwhere.app.ui.components.YouTubePlayerOverlay(
                videoKey = key,
                videoTitle = selectedVideoTitle,
                baseUrl = viewModel.baseUrl,
                onDismiss = {
                    selectedVideoKey = null
                    selectedVideoTitle = null
                }
            )
        }
        
        // No internet error dialog for trailers
        if (showNoInternetDialog) {
            AlertDialog(
                onDismissRequest = { showNoInternetDialog = false },
                title = { Text("No Internet Connection") },
                text = {
                    Text(
                        "Unable to play trailer. Please check your network connection and try again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showNoInternetDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun EpisodeCard(episode: Episode) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBackground)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(episode.resolvedStillPath)
                .crossfade(true)
                .build(),
            contentDescription = episode.nama,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
        )
        Text(
            text = "S${episode.seasonnum ?: 0}E${episode.episodenum ?: 0}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = episode.name ?: episode.nama ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun VideoCard(video: Video, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.youtubeThumbnail)
                .crossfade(true)
                .build(),
            contentDescription = video.nama,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
        )
    }
}

@Composable
fun CastCard(member: CastMember, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(member.profilePathSmall ?: member.profilePath)
                .crossfade(true)
                .build(),
            contentDescription = member.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CardBackground)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = member.name ?: "",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f)
        )
    }
    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
}

@Composable
private fun TitleActionBar(
    tagState: TagState,
    onVote: (String) -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleSeen: () -> Unit,
    onSetStar: (Int) -> Unit,
    onAddToList: () -> Unit
) {
    if (tagState.isLoading) return
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Row 1: Vote + Seen + Watchlist + Share
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbs Up
            ActionButton(
                icon = Icons.Default.ThumbUp,
                label = "Like",
                isActive = tagState.vote == "up",
                activeColor = Color(0xFF4CAF50),
                onClick = { onVote("up") }
            )
            
            // Thumbs Down
            ActionButton(
                icon = Icons.Default.ThumbDown,
                label = "Dislike",
                isActive = tagState.vote == "down",
                activeColor = Color(0xFFE53935),
                onClick = { onVote("down") }
            )
            
            // Seen
            ActionButton(
                icon = Icons.Default.Visibility,
                label = "Seen",
                isActive = tagState.isSeen,
                activeColor = Color(0xFF2196F3),
                onClick = onToggleSeen
            )
            
            // Watchlist
            ActionButton(
                icon = if (tagState.onWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = "Watchlist",
                isActive = tagState.onWatchlist,
                activeColor = Color(0xFFFF9800),
                onClick = onToggleWatchlist
            )
            
            // Add to List
            ActionButton(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Share",
                isActive = tagState.isShared,
                activeColor = Color(0xFF9C27B0),
                onClick = onAddToList
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Row 2: Star/Poop rating
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Rate: ",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            val isDownvoted = tagState.vote == "down"
            repeat(5) { index ->
                val starValue = index + 1
                val isActive = tagState.starRating != null && tagState.starRating >= starValue
                IconButton(
                    onClick = { onSetStar(starValue) },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isDownvoted) {
                        Text(
                            text = if (isActive) "💩" else "⭕",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isActive) androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                                else androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        )
                    } else {
                        Icon(
                            imageVector = if (isActive) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Rate $starValue",
                            modifier = Modifier.size(20.dp),
                            tint = if (isActive) Color(0xFFFFD700) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (isActive) activeColor else Color.Gray
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor else Color.Gray
        )
    }
}
