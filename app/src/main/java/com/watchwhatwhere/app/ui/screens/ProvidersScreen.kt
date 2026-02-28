package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.watchwhatwhere.app.data.api.WatchWhatWhereApi
import com.watchwhatwhere.app.data.model.AuthState
import com.watchwhatwhere.app.data.model.ProviderInfo
import com.watchwhatwhere.app.data.model.TitleItem
import com.watchwhatwhere.app.data.model.TitleItemWrapper
import com.watchwhatwhere.app.data.repository.AuthRepository
import com.watchwhatwhere.app.data.repository.MobileConfigRepository
import com.watchwhatwhere.app.data.repository.TitleRepository
import com.watchwhatwhere.app.ui.components.TitleCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────

data class ProvidersUiState(
    val providers: List<ProviderInfo> = emptyList(),
    val providerPrefs: Map<String, String> = emptyMap(),
    val selectedCost: String = "free",
    val isLoading: Boolean = true,
    val error: String? = null,
    // Title browsing
    val selectedProvider: ProviderInfo? = null,
    val titles: List<TitleItem> = emptyList(),
    val titlesLoading: Boolean = false,
    val titlesError: String? = null,
    val canLoadMore: Boolean = false,
    val currentOffset: Int = 0,
    val selectedGenre: String? = null
)

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val api: WatchWhatWhereApi,
    private val configRepository: MobileConfigRepository,
    private val titleRepository: TitleRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState.asStateFlow()

    val genres: List<String>
        get() = configRepository.config.value?.content?.genres ?: emptyList()

    init {
        loadProviders()
    }

    private fun loadProviders() {
        viewModelScope.launch {
            // Use config providers which have label, viewcost, logo
            val configProviders = configRepository.config.value?.content?.providers ?: emptyList()
            
            // Fetch provider prefs (prio/hide)
            val prefsMap = titleRepository.getProviderPrefs().getOrNull()
                ?: (authRepository.authState.value as? AuthState.Authenticated)
                    ?.user?.providerPrefs?.associate { it.source to it.status }
                ?: emptyMap()
            
            // Sort: prio first, then normal, then hidden at the end
            val sortedProviders = configProviders
                .sortedWith(compareBy<ProviderInfo> {
                    when (prefsMap[it.source]) {
                        "prio" -> 0
                        "hide" -> 2
                        else -> 1
                    }
                }.thenBy { it.label.lowercase() })
            
            _uiState.value = _uiState.value.copy(
                providers = sortedProviders,
                providerPrefs = prefsMap,
                isLoading = false,
                error = if (sortedProviders.isEmpty()) "No providers found" else null
            )
        }
    }

    fun selectCost(cost: String) {
        _uiState.value = _uiState.value.copy(selectedCost = cost)
    }

    fun selectProvider(provider: ProviderInfo) {
        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            titles = emptyList(),
            titlesLoading = true,
            titlesError = null,
            canLoadMore = false,
            currentOffset = 0,
            selectedGenre = null
        )
        loadTitles(provider.source)
    }

    fun clearProvider() {
        _uiState.value = _uiState.value.copy(
            selectedProvider = null,
            titles = emptyList(),
            titlesError = null,
            selectedGenre = null
        )
    }

    fun selectGenre(genre: String?) {
        val provider = _uiState.value.selectedProvider ?: return
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            titles = emptyList(),
            titlesLoading = true,
            titlesError = null,
            canLoadMore = false,
            currentOffset = 0
        )
        loadTitles(provider.source, genre)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.titlesLoading || !state.canLoadMore) return
        val provider = state.selectedProvider ?: return
        val nextOffset = state.currentOffset + 100
        _uiState.value = state.copy(titlesLoading = true, currentOffset = nextOffset)
        loadTitles(provider.source, state.selectedGenre, nextOffset, append = true)
    }

    private fun loadTitles(source: String, genre: String? = null, offset: Int = 0, append: Boolean = false) {
        viewModelScope.launch {
            try {
                val wrappers = when {
                    offset > 0 && genre != null -> api.getProviderTitlesWithOffset(source, genre, offset)
                    offset > 0 -> api.getProviderTitlesWithOffsetOnly(source, offset)
                    genre != null -> api.getProviderTitlesWithGenre(source, genre)
                    else -> api.getProviderTitles(source)
                }
                val titles = wrappers.map { it.data }
                val current = if (append) _uiState.value.titles else emptyList()
                _uiState.value = _uiState.value.copy(
                    titles = current + titles,
                    titlesLoading = false,
                    canLoadMore = wrappers.size >= 100
                )
            } catch (e: Exception) {
                // JSON parse errors (empty/malformed response) → treat as no titles
                val isParseError = e is kotlinx.serialization.SerializationException ||
                        e is IllegalArgumentException ||
                        e.message?.contains("JSON", ignoreCase = true) == true ||
                        e.message?.contains("Expected", ignoreCase = true) == true
                val current = if (append) _uiState.value.titles else emptyList()
                _uiState.value = _uiState.value.copy(
                    titles = current,
                    titlesLoading = false,
                    titlesError = if (isParseError) null else e.message,
                    canLoadMore = false
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Cost labels (reuse the same ones from ProviderPrefs)
// ─────────────────────────────────────────────

private val ProviderCostLabels = mapOf(
    "free" to "Free",
    "ads" to "Ads",
    "sub" to "Subscription",
    "plus" to "Add-on",
    "flatrate" to "Flat Rate",
    "rent" to "Rent",
    "buy" to "Buy"
)

// ─────────────────────────────────────────────
// Screen composable
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onTitleClick: (Long) -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // If a provider is selected, show the title browser
    if (uiState.selectedProvider != null) {
        ProviderTitleBrowser(
            provider = uiState.selectedProvider!!,
            uiState = uiState,
            genres = viewModel.genres,
            onBack = { viewModel.clearProvider() },
            onTitleClick = onTitleClick,
            onGenreSelect = { viewModel.selectGenre(it) },
            onLoadMore = { viewModel.loadMore() }
        )
        return
    }

    // Provider list grouped by cost
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Providers") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Scaffold
        }

        // Get available cost categories
        val allCosts = uiState.providers.map { it.viewcost }.distinct()
        val costOrder = ProviderCostLabels.keys.toList()
        val availableCosts = costOrder.filter { it in allCosts }
        
        // Providers for selected cost (already sorted by prio then alphabetical)
        val filteredProviders = uiState.providers
            .filter { it.viewcost == uiState.selectedCost }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cost filter chips (horizontally scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableCosts.forEach { cost ->
                    val count = uiState.providers.count { it.viewcost == cost }
                    val label = ProviderCostLabels[cost] ?: cost
                    FilterChip(
                        selected = uiState.selectedCost == cost,
                        onClick = { viewModel.selectCost(cost) },
                        label = {
                            Text(
                                text = "$label ($count)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            // Provider grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProviders, key = { it.source }) { provider ->
                    val isHidden = uiState.providerPrefs[provider.source] == "hide"
                    ProviderItem(
                        provider = provider,
                        isHidden = isHidden,
                        onClick = { viewModel.selectProvider(provider) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Provider grid item
// ─────────────────────────────────────────────

@Composable
private fun ProviderItem(
    provider: ProviderInfo,
    isHidden: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .alpha(if (isHidden) 0.35f else 1f)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = provider.logo,
            contentDescription = provider.label,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = provider.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

// ─────────────────────────────────────────────
// Provider title browser (drill-in)
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTitleBrowser(
    provider: ProviderInfo,
    uiState: ProvidersUiState,
    genres: List<String>,
    onBack: () -> Unit,
    onTitleClick: (Long) -> Unit,
    onGenreSelect: (String?) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()
    var genreExpanded by remember { mutableStateOf(false) }

    // Infinite scroll detection — re-launch when titles or canLoadMore changes
    LaunchedEffect(uiState.titles.size, uiState.canLoadMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= uiState.titles.size - 6 && uiState.canLoadMore && !uiState.titlesLoading) {
                    onLoadMore()
                }
            }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(provider.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
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
                                    genreExpanded = false
                                    onGenreSelect(null)
                                }
                            )
                            genres.forEach { genre ->
                                DropdownMenuItem(
                                    text = { Text(genre) },
                                    onClick = {
                                        genreExpanded = false
                                        onGenreSelect(genre)
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
            if (uiState.titles.isEmpty() && !uiState.titlesLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.titlesError ?: if (uiState.selectedGenre != null) "No titles found in this genre" else "No titles found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.titles, key = { it.id }) { title ->
                        TitleCard(
                            title = title,
                            onClick = { onTitleClick(title.id) }
                        )
                    }
                    // Loading indicator at bottom
                    if (uiState.titlesLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // Initial loading overlay
            if (uiState.titlesLoading && uiState.titles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
