package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchwhatwhere.app.data.model.TitleItem
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

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val resultsByType: Map<String, List<TitleItem>> = emptyMap(),
    val error: Throwable? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TitleRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }
    
    fun search() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.search(query)
                .onSuccess { results ->
                    val grouped = results.groupBy { it.typa }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        resultsByType = grouped
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
    
    fun clearQuery() {
        _uiState.value = SearchUiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTitleClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
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
            // Search input
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search movies, TV shows...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, "Search")
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearQuery) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                singleLine = true
            )
            
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.error != null -> {
                        ErrorScreen(
                            message = getErrorMessage(uiState.error!!),
                            onRetry = { viewModel.search() }
                        )
                    }
                    uiState.resultsByType.isEmpty() && uiState.query.isNotEmpty() -> {
                        Text(
                            text = "No results found",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        val typeNames = mapOf(
                            "movie" to "Movies",
                            "tvSeries" to "TV Series",
                            "tvMiniSeries" to "Mini Series",
                            "tvMovie" to "TV Movies",
                            "short" to "Shorts"
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            uiState.resultsByType.forEach { (type, titles) ->
                                item {
                                    TitleCarousel(
                                        title = typeNames[type] ?: type,
                                        items = titles,
                                        onTitleClick = onTitleClick
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
