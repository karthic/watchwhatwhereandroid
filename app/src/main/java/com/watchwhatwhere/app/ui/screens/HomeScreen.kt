package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchwhatwhere.app.data.model.ContentType
import com.watchwhatwhere.app.data.model.HomeResponse
import com.watchwhatwhere.app.data.model.TitleItem
import com.watchwhatwhere.app.data.repository.MobileConfigRepository
import com.watchwhatwhere.app.data.repository.PrefetchManager
import com.watchwhatwhere.app.data.repository.TitleRepository
import com.watchwhatwhere.app.ui.components.ErrorScreen
import com.watchwhatwhere.app.ui.components.getErrorMessage
import com.watchwhatwhere.app.ui.components.TitleCarousel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val categories: List<Pair<String, List<TitleItem>>> = emptyList(),
    val error: Throwable? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TitleRepository,
    private val configRepository: MobileConfigRepository,
    private val prefetchManager: PrefetchManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadHome()
    }
    
    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            repository.getHome()
                .onSuccess { response ->
                    val categories = response.toCategories()
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        categories = categories
                    )
                    // Register all title IDs for background prefetch
                    val allIds = categories.flatMap { it.second }.map { it.id }
                    prefetchManager.registerTitleIds(allIds)
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        error = error
                    )
                }
        }
    }
    
    fun getContentTypes(): List<ContentType> {
        return configRepository.config.value?.content?.types ?: emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTitleClick: (Long) -> Unit,
    onBrowseClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "WatchWhatWhere",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    ErrorScreen(
                        message = getErrorMessage(uiState.error),
                        onRetry = { viewModel.loadHome() }
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(uiState.categories) { (categoryName, titles) ->
                            TitleCarousel(
                                title = categoryName,
                                items = titles,
                                onTitleClick = onTitleClick,
                                onSeeAllClick = {
                                    // Map category name to type using config, with hardcoded fallback
                                    val configTypes = viewModel.getContentTypes()
                                    val type = configTypes.firstOrNull { it.label == categoryName }?.key
                                        ?: when (categoryName) {
                                            "Trending" -> "trend"
                                            "Free to Watch" -> "free"
                                            "Free Movies" -> "free_movie"
                                            "Free TV Shows" -> "free_tv"
                                            "Now Playing" -> "isnowplaying"
                                            "Popular" -> "ispopular"
                                            "Top Rated" -> "istoprated"
                                            "Upcoming" -> "isupcoming"
                                            "Movies" -> "movie"
                                            "TV Movies" -> "tvMovie"
                                            "TV Series" -> "tvSeries"
                                            "Mini Series" -> "tvMiniSeries"
                                            "TV Specials" -> "tvSpecial"
                                            "TV Shorts" -> "tvShort"
                                            "Shorts" -> "short"
                                            "Videos" -> "video"
                                            "Video Games" -> "videoGame"
                                            else -> "movie"
                                        }
                                    onBrowseClick(type)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
