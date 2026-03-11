package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchwhatwhere.app.data.model.TitleItem
import com.watchwhatwhere.app.data.repository.MobileConfigRepository
import com.watchwhatwhere.app.data.repository.TitleRepository
import com.watchwhatwhere.app.ui.components.ErrorScreen
import com.watchwhatwhere.app.ui.components.getErrorMessage
import com.watchwhatwhere.app.ui.components.TitleCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val isLoading: Boolean = true,
    val titles: List<TitleItem> = emptyList(),
    val genres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val isLoadingMore: Boolean = false,
    val isLoadingEarlier: Boolean = false,
    val canLoadMore: Boolean = true,
    val canLoadEarlier: Boolean = false,
    val error: Throwable? = null
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: TitleRepository,
    private val configRepository: MobileConfigRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    
    private var currentType = ""
    private var currentOffset = 0
    
    /** The offset of the first item in our current window of titles */
    private var windowStartOffset = 0
    
    /** Saved scroll position for restoration on back navigation */
    var savedFirstVisibleItemIndex = 0
        private set
    var savedFirstVisibleItemScrollOffset = 0
        private set
    
    /** Whether we've already loaded data for the current type */
    private var hasLoaded = false
    
    private val pageSize: Int
        get() = configRepository.config.value?.api?.paginationSize ?: 100
    
    fun saveScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        savedFirstVisibleItemIndex = firstVisibleItemIndex
        savedFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset
    }
    
    /**
     * Load the initial page of data for a type.
     * If already loaded for this type (back navigation), skip.
     */
    fun load(type: String) {
        if (hasLoaded && type == currentType) return
        
        currentType = type
        currentOffset = 0
        windowStartOffset = 0
        hasLoaded = true
        savedFirstVisibleItemIndex = 0
        savedFirstVisibleItemScrollOffset = 0
        
        viewModelScope.launch {
            _uiState.value = BrowseUiState(isLoading = true)
            
            // Load genres from mobileconfig
            val genres = configRepository.config.value?.content?.genres ?: emptyList()
            _uiState.value = _uiState.value.copy(genres = genres)
            
            // Load titles
            repository.getTypes(type)
                .onSuccess { titles ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        titles = titles,
                        canLoadMore = titles.size >= pageSize,
                        canLoadEarlier = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e
                    )
                }
        }
    }
    
    fun selectGenre(genre: String?) {
        currentOffset = 0
        windowStartOffset = 0
        savedFirstVisibleItemIndex = 0
        savedFirstVisibleItemScrollOffset = 0
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            isLoading = true,
            error = null,
            titles = emptyList()
        )
        viewModelScope.launch {
            repository.getTypes(currentType, genre = genre)
                .onSuccess { titles ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        titles = titles,
                        canLoadMore = titles.size >= pageSize,
                        canLoadEarlier = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e
                    )
                }
        }
    }
    
    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.canLoadMore) return
        currentOffset += pageSize
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.getTypes(currentType, genre = _uiState.value.selectedGenre, offset = currentOffset)
                .onSuccess { titles ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        titles = _uiState.value.titles + titles,
                        canLoadMore = titles.size >= pageSize
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        canLoadMore = false
                    )
                }
        }
    }
    
    /**
     * Load earlier pages when the user scrolls up near the top of the current window.
     * Prepends one page before the current windowStartOffset.
     */
    fun loadEarlier() {
        if (_uiState.value.isLoadingEarlier || !_uiState.value.canLoadEarlier || windowStartOffset <= 0) return
        
        val earlierOffset = (windowStartOffset - pageSize).coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(isLoadingEarlier = true)
        viewModelScope.launch {
            repository.getTypes(currentType, genre = _uiState.value.selectedGenre, offset = earlierOffset)
                .onSuccess { titles ->
                    windowStartOffset = earlierOffset
                    _uiState.value = _uiState.value.copy(
                        isLoadingEarlier = false,
                        titles = titles + _uiState.value.titles,
                        canLoadEarlier = earlierOffset > 0
                    )
                    // Adjust saved scroll position by the number of prepended items
                    savedFirstVisibleItemIndex += titles.size
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoadingEarlier = false
                    )
                }
        }
    }
    
    fun getDisplayType(type: String): String {
        return configRepository.config.value?.content?.types
            ?.firstOrNull { it.key == type }?.label
            ?: when (type) {
                "isnowplaying" -> "Now Playing"
                "istoprated" -> "Top Rated"
                "isupcoming" -> "Upcoming"
                "free_movie" -> "Free Movies"
                "free_tv" -> "Free TV Shows"
                "tvSeries" -> "TV Series"
                "tvMiniSeries" -> "Mini Series"
                "tvMovie" -> "TV Movies"
                "tvSpecial" -> "TV Specials"
                "tvShort" -> "TV Shorts"
                else -> type.replaceFirstChar { it.uppercase() }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    type: String,
    onTitleClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel.savedFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = viewModel.savedFirstVisibleItemScrollOffset
    )
    var genreExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(type) {
        viewModel.load(type)
    }
    
    // Save scroll position whenever the user scrolls
    LaunchedEffect(gridState) {
        snapshotFlow { 
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset 
        }.collect { (index, offset) ->
            viewModel.saveScrollPosition(index, offset)
        }
    }
    
    // Infinite scroll detection — load more when nearing the end
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= uiState.titles.size - 6 && uiState.canLoadMore) {
                    viewModel.loadMore()
                }
            }
    }
    
    // Load earlier pages when scrolling up near the top of the window
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { firstIndex ->
                if (firstIndex < 6 && uiState.canLoadEarlier) {
                    viewModel.loadEarlier()
                }
            }
    }
    
    val displayType = viewModel.getDisplayType(type)
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(displayType) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Genre dropdown
                    Box {
                        TextButton(onClick = { genreExpanded = true }) {
                            Text(uiState.selectedGenre ?: "All Genres")
                        }
                        DropdownMenu(
                            expanded = genreExpanded,
                            onDismissRequest = { genreExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Genres") },
                                onClick = {
                                    viewModel.selectGenre(null)
                                    genreExpanded = false
                                }
                            )
                            uiState.genres.forEach { genre ->
                                DropdownMenuItem(
                                    text = { Text(genre) },
                                    onClick = {
                                        viewModel.selectGenre(genre)
                                        genreExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                        onRetry = { viewModel.load(type) }
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(130.dp),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.titles) { title ->
                            TitleCard(
                                title = title,
                                onClick = { onTitleClick(title.id) }
                            )
                        }
                        
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
