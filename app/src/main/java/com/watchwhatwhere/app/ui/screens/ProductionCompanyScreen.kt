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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchwhatwhere.app.data.model.TitleItem
import com.watchwhatwhere.app.data.repository.MobileConfigRepository
import com.watchwhatwhere.app.data.repository.TitleRepository
import com.watchwhatwhere.app.ui.components.ErrorScreen
import com.watchwhatwhere.app.ui.components.TitleCard
import com.watchwhatwhere.app.ui.components.getErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProdCompanyUiState(
    val isLoading: Boolean = true,
    val titles: List<TitleItem> = emptyList(),
    val companyName: String = "",
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: Throwable? = null
)

@HiltViewModel
class ProdCompanyViewModel @Inject constructor(
    private val repository: TitleRepository,
    private val configRepository: MobileConfigRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProdCompanyUiState())
    val uiState: StateFlow<ProdCompanyUiState> = _uiState.asStateFlow()
    
    private var currentCompanyId: Long = 0
    private var currentOffset = 0
    
    fun load(companyId: Long, companyName: String) {
        currentCompanyId = companyId
        currentOffset = 0
        _uiState.value = ProdCompanyUiState(isLoading = true, companyName = companyName)
        viewModelScope.launch {
            val pageSize = configRepository.config.value?.api?.paginationSize ?: 100
            repository.getProdCompanyTitles(companyId)
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
            repository.getProdCompanyTitles(currentCompanyId, offset = currentOffset)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionCompanyScreen(
    companyId: Long,
    companyName: String,
    onTitleClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ProdCompanyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    
    LaunchedEffect(companyId) {
        viewModel.load(companyId, companyName)
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
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(uiState.companyName.ifEmpty { "Production Company" }) },
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
                        onRetry = { viewModel.load(companyId, companyName) }
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
