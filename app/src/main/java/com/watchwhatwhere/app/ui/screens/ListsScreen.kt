package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.watchwhatwhere.app.data.model.*
import com.watchwhatwhere.app.data.repository.ProfileRepository
import com.watchwhatwhere.app.ui.theme.CardBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

data class PageState<T>(
    val items: List<T> = emptyList(),
    val page: Int = -1,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {
    
    private val _watchlist = MutableStateFlow(PageState<ProfileTitleItem>())
    val watchlist: StateFlow<PageState<ProfileTitleItem>> = _watchlist.asStateFlow()
    
    private val _seenList = MutableStateFlow(PageState<ProfileTitleItem>())
    val seenList: StateFlow<PageState<ProfileTitleItem>> = _seenList.asStateFlow()
    
    private val _rated = MutableStateFlow(PageState<ProfileTitleItem>())
    val rated: StateFlow<PageState<ProfileTitleItem>> = _rated.asStateFlow()
    
    private val _reviews = MutableStateFlow(PageState<ReviewItem>())
    val reviews: StateFlow<PageState<ReviewItem>> = _reviews.asStateFlow()
    
    private val _userLists = MutableStateFlow(PageState<UserList>())
    val userLists: StateFlow<PageState<UserList>> = _userLists.asStateFlow()
    
    private val _selectedListItems = MutableStateFlow(PageState<UserListItem>())
    val selectedListItems: StateFlow<PageState<UserListItem>> = _selectedListItems.asStateFlow()
    
    var selectedTab by mutableIntStateOf(0)
    
    private val _defaultShareListId = MutableStateFlow<String?>(null)
    val defaultShareListId: StateFlow<String?> = _defaultShareListId.asStateFlow()
    
    init {
        _defaultShareListId.value = profileRepository.getDefaultShareListId()
    }
    
    fun setDefaultShareList(listId: String) {
        _defaultShareListId.value = listId
        profileRepository.setDefaultShareList(listId)
    }
    
    fun loadWatchlist(refresh: Boolean = false) {
        loadProfilePage(_watchlist, refresh) { page -> profileRepository.getWatchlist(page) }
    }
    
    fun loadSeenList(refresh: Boolean = false) {
        loadProfilePage(_seenList, refresh) { page -> profileRepository.getSeenList(page) }
    }
    
    fun loadRated(refresh: Boolean = false) {
        loadProfilePage(_rated, refresh) { page -> profileRepository.getRated(page) }
    }
    
    fun loadReviews(refresh: Boolean = false) {
        val state = _reviews.value
        if (state.isLoading) return
        val nextPage = if (refresh) 0 else state.page + 1
        if (!refresh && !state.hasMore) return
        
        _reviews.value = state.copy(
            isLoading = true,
            isRefreshing = refresh,
            error = null
        )
        
        viewModelScope.launch {
            profileRepository.getReviews(nextPage).fold(
                onSuccess = { response ->
                    _reviews.value = PageState(
                        items = if (refresh) response.items else state.items + response.items,
                        page = nextPage,
                        hasMore = response.hasMore,
                        isLoading = false,
                        isRefreshing = false
                    )
                },
                onFailure = { e ->
                    _reviews.value = state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message
                    )
                }
            )
        }
    }
    
    fun loadUserLists(refresh: Boolean = false) {
        if (_userLists.value.isLoading) return
        _userLists.value = _userLists.value.copy(isLoading = true, isRefreshing = refresh, error = null)
        
        viewModelScope.launch {
            profileRepository.getUserLists().fold(
                onSuccess = { lists ->
                    _userLists.value = PageState(
                        items = lists,
                        hasMore = false,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _userLists.value = _userLists.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message
                    )
                }
            )
        }
    }
    
    fun loadListItems(listId: String) {
        _selectedListItems.value = PageState(isLoading = true)
        viewModelScope.launch {
            profileRepository.getListItems(listId).fold(
                onSuccess = { items ->
                    _selectedListItems.value = PageState(items = items, hasMore = false)
                },
                onFailure = { e ->
                    _selectedListItems.value = PageState(error = e.message)
                }
            )
        }
    }
    
    fun createList(name: String, privacy: String = "private") {
        viewModelScope.launch {
            profileRepository.createList(name, privacy)
            loadUserLists(refresh = true)
        }
    }
    
    fun deleteList(listId: String) {
        viewModelScope.launch {
            profileRepository.deleteList(listId)
            loadUserLists(refresh = true)
        }
    }
    
    fun editReview(titleId: Long, content: String) {
        viewModelScope.launch {
            profileRepository.submitReview(titleId.toString(), content)
            loadReviews(refresh = true)
        }
    }
    
    fun deleteReview(titleId: Long) {
        viewModelScope.launch {
            profileRepository.deleteReview(titleId.toString())
            loadReviews(refresh = true)
        }
    }
    
    private fun loadProfilePage(
        stateFlow: MutableStateFlow<PageState<ProfileTitleItem>>,
        refresh: Boolean,
        fetcher: suspend (Int) -> Result<ProfilePageResponse>
    ) {
        val state = stateFlow.value
        if (state.isLoading) return
        val nextPage = if (refresh) 0 else state.page + 1
        if (!refresh && !state.hasMore) return
        
        stateFlow.value = state.copy(
            isLoading = true,
            isRefreshing = refresh,
            error = null
        )
        
        viewModelScope.launch {
            fetcher(nextPage).fold(
                onSuccess = { response ->
                    stateFlow.value = PageState(
                        items = if (refresh) response.items else state.items + response.items,
                        page = nextPage,
                        hasMore = response.hasMore,
                        isLoading = false,
                        isRefreshing = false
                    )
                },
                onFailure = { e ->
                    stateFlow.value = state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────

private enum class ListTab(val label: String) {
    WATCHLIST("Watchlist"),
    SEEN("Seen"),
    RATED("Rated"),
    REVIEWS("Reviews"),
    LISTS("Lists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onTitleClick: (Long) -> Unit,
    viewModel: ListsViewModel = hiltViewModel()
) {
    var selectedTab by viewModel::selectedTab
    val tabs = ListTab.entries
    
    // Load data when tab selected
    LaunchedEffect(selectedTab) {
        when (tabs[selectedTab]) {
            ListTab.WATCHLIST -> if (viewModel.watchlist.value.items.isEmpty() && !viewModel.watchlist.value.isLoading) viewModel.loadWatchlist()
            ListTab.SEEN -> if (viewModel.seenList.value.items.isEmpty() && !viewModel.seenList.value.isLoading) viewModel.loadSeenList()
            ListTab.RATED -> if (viewModel.rated.value.items.isEmpty() && !viewModel.rated.value.isLoading) viewModel.loadRated()
            ListTab.REVIEWS -> if (viewModel.reviews.value.items.isEmpty() && !viewModel.reviews.value.isLoading) viewModel.loadReviews()
            ListTab.LISTS -> if (viewModel.userLists.value.items.isEmpty() && !viewModel.userLists.value.isLoading) viewModel.loadUserLists()
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("My Lists") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
            
            // Tab content
            when (tabs[selectedTab]) {
                ListTab.WATCHLIST -> {
                    val state by viewModel.watchlist.collectAsState()
                    ProfileTitleGrid(
                        state = state,
                        onTitleClick = onTitleClick,
                        onLoadMore = { viewModel.loadWatchlist() },
                        onRefresh = { viewModel.loadWatchlist(refresh = true) }
                    )
                }
                ListTab.SEEN -> {
                    val state by viewModel.seenList.collectAsState()
                    ProfileTitleGrid(
                        state = state,
                        onTitleClick = onTitleClick,
                        onLoadMore = { viewModel.loadSeenList() },
                        onRefresh = { viewModel.loadSeenList(refresh = true) }
                    )
                }
                ListTab.RATED -> {
                    val state by viewModel.rated.collectAsState()
                    ProfileTitleGrid(
                        state = state,
                        onTitleClick = onTitleClick,
                        onLoadMore = { viewModel.loadRated() },
                        onRefresh = { viewModel.loadRated(refresh = true) },
                        showRatingBadge = true
                    )
                }
                ListTab.REVIEWS -> {
                    val state by viewModel.reviews.collectAsState()
                    ReviewsList(
                        state = state,
                        onTitleClick = onTitleClick,
                        onLoadMore = { viewModel.loadReviews() },
                        onRefresh = { viewModel.loadReviews(refresh = true) },
                        onEditReview = { titleId, content -> viewModel.editReview(titleId, content) },
                        onDeleteReview = { titleId -> viewModel.deleteReview(titleId) }
                    )
                }
                ListTab.LISTS -> {
                    val state by viewModel.userLists.collectAsState()
                    CustomListsTab(
                        state = state,
                        viewModel = viewModel,
                        onTitleClick = onTitleClick,
                        onRefresh = { viewModel.loadUserLists(refresh = true) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Profile title grid (watchlist, seen, rated)
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTitleGrid(
    state: PageState<ProfileTitleItem>,
    onTitleClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    showRatingBadge: Boolean = false
) {
    when {
        state.isLoading && state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null && state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
            }
        }
        state.items.isEmpty() && !state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No titles yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            val gridState = rememberLazyGridState()
            
            // Pagination trigger
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisibleItem >= state.items.size - 6 && state.hasMore && !state.isLoading
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }
            
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ProfileTitleCard(
                            item = item,
                            onClick = { onTitleClick(item.id) },
                            showRatingBadge = showRatingBadge
                        )
                    }
                    
                    // Loading indicator at bottom
                    if (state.isLoading && state.items.isNotEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTitleCard(
    item: ProfileTitleItem,
    onClick: () -> Unit,
    showRatingBadge: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.posterPathSmall ?: item.posterPath,
                    contentDescription = item.nama,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(CardBackground)
                )
                
                // Rating badge: show vote direction (thumbs) and/or star rating
                if (showRatingBadge) {
                    val voteDir = item.tagData // "up" or "down"
                    val hasStar = item.starRating != null && item.starRating > 0
                    val hasVote = voteDir == "up" || voteDir == "down"
                    
                    if (hasVote || hasStar) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(
                                    Color(0xCC000000),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (hasVote) {
                                    Icon(
                                        imageVector = if (voteDir == "up") Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                                        contentDescription = voteDir,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (voteDir == "up") Color(0xFF4CAF50) else Color(0xFFE53935)
                                    )
                                }
                                if (hasVote && hasStar) {
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (hasStar) {
                                    if (voteDir == "down") {
                                        Text("💩", style = MaterialTheme.typography.labelSmall)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFFFFD700)
                                        )
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "${item.starRating}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Title text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground)
                    .padding(6.dp)
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Reviews list
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewsList(
    state: PageState<ReviewItem>,
    onTitleClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onEditReview: (Long, String) -> Unit,
    onDeleteReview: (Long) -> Unit
) {
    when {
        state.isLoading && state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null && state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
            }
        }
        state.items.isEmpty() && !state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No reviews yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            val listState = rememberLazyListState()
            
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisibleItem >= state.items.size - 3 && state.hasMore && !state.isLoading
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) onLoadMore()
            }
            
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items) { review ->
                        ReviewCard(
                            review = review,
                            onTitleClick = onTitleClick,
                            onEditReview = onEditReview,
                            onDeleteReview = onDeleteReview
                        )
                    }
                    
                    if (state.isLoading && state.items.isNotEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: ReviewItem,
    onTitleClick: (Long) -> Unit,
    onEditReview: (Long, String) -> Unit,
    onDeleteReview: (Long) -> Unit
) {
    val titleInfo = review.title ?: return
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateText = review.tima?.let {
        try {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(it * 1000))
        } catch (_: Exception) { null }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTitleClick(titleInfo.id) }
                .padding(12.dp)
        ) {
            // Poster
            AsyncImage(
                model = titleInfo.posterPathSmall ?: titleInfo.posterPath,
                contentDescription = titleInfo.nama,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardBackground)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = titleInfo.nama ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit review",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete review",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
                }
                
                if (dateText != null) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(Modifier.height(6.dp))
                
                Text(
                    text = review.content ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
    
    // Edit dialog
    if (showEditDialog) {
        EditReviewDialog(
            currentContent = review.content ?: "",
            titleName = titleInfo.nama ?: "Unknown",
            onDismiss = { showEditDialog = false },
            onSave = { newContent ->
                onEditReview(titleInfo.id, newContent)
                showEditDialog = false
            }
        )
    }
    
    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Review") },
            text = { Text("Delete your review for \"${titleInfo.nama}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteReview(titleInfo.id)
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditReviewDialog(
    currentContent: String,
    titleName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentContent) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Review") },
        text = {
            Column {
                Text(
                    text = titleName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Your review") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )
                Text(
                    text = "${text.length} / 5000",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank() && text.length <= 5000
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────
// Custom lists tab
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomListsTab(
    state: PageState<UserList>,
    viewModel: ListsViewModel,
    onTitleClick: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<UserList?>(null) }
    
    // Create list dialog
    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, privacy ->
                viewModel.createList(name, privacy)
                showCreateDialog = false
            }
        )
    }
    
    // Selected list detail view
    if (selectedList != null) {
        val itemsState by viewModel.selectedListItems.collectAsState()
        ListDetailSheet(
            list = selectedList!!,
            itemsState = itemsState,
            onTitleClick = { id ->
                selectedList = null
                onTitleClick(id)
            },
            onDismiss = { selectedList = null }
        )
        return
    }
    
    when {
        state.isLoading && state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null && state.items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
            }
        }
        else -> {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Create button
                    item {
                        OutlinedButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create New List")
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    
                    if (state.items.isEmpty() && !state.isLoading) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "No custom lists yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    items(state.items, key = { it.id }) { list ->
                        val defaultShareId by viewModel.defaultShareListId.collectAsState()
                        UserListRow(
                            list = list,
                            isDefaultShare = list.id == defaultShareId,
                            onClick = {
                                selectedList = list
                                viewModel.loadListItems(list.id)
                            },
                            onDelete = { viewModel.deleteList(list.id) },
                            onSetDefault = { viewModel.setDefaultShareList(list.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListRow(
    list: UserList,
    isDefaultShare: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete List") },
            text = { Text("Are you sure you want to delete \"${list.nama}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.nama,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = list.privacy ?: "private",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onSetDefault) {
                Icon(
                    imageVector = if (isDefaultShare) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Set as default share list",
                    tint = if (isDefaultShare) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete list",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New List") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Public list")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, if (isPublic) "public" else "private") },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ListDetailSheet(
    list: UserList,
    itemsState: PageState<UserListItem>,
    onTitleClick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = list.nama,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        
        when {
            itemsState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            itemsState.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This list is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(itemsState.items, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTitleClick(item.id) }
                        ) {
                            Column {
                                AsyncImage(
                                    model = item.posterPathSmall ?: item.posterPath,
                                    contentDescription = item.nama,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(CardBackground)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardBackground)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = item.displayTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
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
