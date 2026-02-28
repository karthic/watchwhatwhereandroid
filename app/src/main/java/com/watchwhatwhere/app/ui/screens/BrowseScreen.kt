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
    val canLoadMore: Boolean = true,
    val error: Throwable? = null
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: TitleRepository,
    private val configRepository: MobileConfigRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    
    private var currentType = "movie"
    private var currentOffset = 0
    
    fun load(type: String) {
        currentType = type
        currentOffset = 0
        viewModelScope.launch {
            _uiState.value = BrowseUiState(isLoading = true)
            
            // Load genres from mobileconfig
            val genres = configRepository.config.value?.content?.genres ?: emptyList()
            _uiState.value = _uiState.value.copy(genres = genres)
            
            // Load titles
            val pageSize = configRepository.config.value?.api?.paginationSize ?: 100
            repository.getTypes(type)
                .onSuccess { titles ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        titles = titles,
                        canLoadMore = titles.size >= pageSize
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
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            isLoading = true,
            error = null,
            titles = emptyList()
        )
        viewModelScope.launch {
            val pageSize = configRepository.config.value?.api?.paginationSize ?: 100
            repository.getTypes(currentType, genre = genre)
                .onSuccess { titles ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        titles = titles,
                        canLoadMore = titles.size >= pageSize
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
        val pageSize = configRepository.config.value?.api?.paginationSize ?: 100
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
    val gridState = rememberLazyGridState()
    var genreExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(type) {
        viewModel.load(type)
    }
    
    // Infinite scroll detection
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= uiState.titles.size - 6 && uiState.canLoadMore) {
                    viewModel.loadMore()
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
