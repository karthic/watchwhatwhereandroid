package com.watchwhatwhere.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.watchwhatwhere.app.data.model.Provider
import com.watchwhatwhere.app.data.model.ProviderPref
import com.watchwhatwhere.app.data.repository.AuthRepository
import com.watchwhatwhere.app.data.repository.TitleRepository
import com.watchwhatwhere.app.data.model.AuthState
import com.watchwhatwhere.app.ui.components.ErrorScreen
import com.watchwhatwhere.app.ui.components.getErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderWithPref(
    val provider: Provider,
    val status: String? = null // null = default, "prio", "hide"
)

data class ProviderPrefsUiState(
    val isLoading: Boolean = true,
    val providers: List<ProviderWithPref> = emptyList(),
    val selectedCost: String = "free",
    val error: Throwable? = null
) {
    val costCounts: Map<String, Int>
        get() = providers.groupBy { it.provider.viewcost }
            .mapValues { it.value.size }
    
    val filteredProviders: List<ProviderWithPref>
        get() = providers
            .filter { it.provider.viewcost == selectedCost }
            .sortedWith(compareBy {
                when (it.status) {
                    "prio" -> 0
                    null -> 1
                    "hide" -> 2
                    else -> 1
                }
            })
}

@HiltViewModel
class ProviderPrefsViewModel @Inject constructor(
    private val repository: TitleRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProviderPrefsUiState())
    val uiState: StateFlow<ProviderPrefsUiState> = _uiState.asStateFlow()
    
    init {
        loadProviders()
    }
    
    private fun loadProviders() {
        viewModelScope.launch {
            _uiState.value = ProviderPrefsUiState(isLoading = true)
            repository.getProviders()
                .onSuccess { providers ->
                    // Fetch fresh prefs from API
                    val prefsMap = repository.getProviderPrefs().getOrNull()
                        ?: (authRepository.authState.value as? AuthState.Authenticated)
                            ?.user?.providerPrefs?.associate { it.source to it.status }
                        ?: emptyMap()
                    
                    val merged = providers.map { provider ->
                        ProviderWithPref(
                            provider = provider,
                            status = prefsMap[provider.source]
                        )
                    }
                    _uiState.value = ProviderPrefsUiState(
                        isLoading = false,
                        providers = merged
                    )
                }
                .onFailure { e ->
                    _uiState.value = ProviderPrefsUiState(
                        isLoading = false,
                        error = e
                    )
                }
        }
    }
    
    fun selectCost(cost: String) {
        _uiState.value = _uiState.value.copy(selectedCost = cost)
    }
    
    fun togglePref(source: String, currentStatus: String?) {
        viewModelScope.launch {
            when (currentStatus) {
                null -> {
                    // default -> prio
                    updateLocal(source, "prio")
                    repository.setProviderPref(source, "prio")
                }
                "prio" -> {
                    // prio -> default
                    updateLocal(source, null)
                    repository.deleteProviderPref(source)
                }
                "hide" -> {
                    // hide -> default
                    updateLocal(source, null)
                    repository.deleteProviderPref(source)
                }
            }
        }
    }
    
    fun toggleHide(source: String, currentStatus: String?) {
        viewModelScope.launch {
            when (currentStatus) {
                null -> {
                    // default -> hide
                    updateLocal(source, "hide")
                    repository.setProviderPref(source, "hide")
                }
                "hide" -> {
                    // hide -> default
                    updateLocal(source, null)
                    repository.deleteProviderPref(source)
                }
                "prio" -> {
                    // prio -> hide
                    updateLocal(source, "hide")
                    repository.setProviderPref(source, "hide")
                }
            }
        }
    }
    
    private fun updateLocal(source: String, newStatus: String?) {
        _uiState.value = _uiState.value.copy(
            providers = _uiState.value.providers.map {
                if (it.provider.source == source) it.copy(status = newStatus)
                else it
            }
        )
    }
}

val CostLabels = mapOf(
    "free" to "Free",
    "sub" to "Subscription",
    "plus" to "Add-on",
    "buy" to "Buy",
    "rent" to "Rent"
)

val CostOrder = listOf("free", "sub", "plus", "buy", "rent")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPrefsScreen(
    onBackClick: () -> Unit,
    viewModel: ProviderPrefsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Provider Preferences") },
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
                        onRetry = { /* reload */ }
                    )
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Description
                        Text(
                            text = "Set your streaming provider preferences. ⭐ = priority, ✕ = hidden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        // Filter chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CostOrder.forEach { cost ->
                                val count = uiState.costCounts[cost] ?: 0
                                if (count > 0) {
                                    FilterChip(
                                        selected = uiState.selectedCost == cost,
                                        onClick = { viewModel.selectCost(cost) },
                                        label = {
                                            Text(
                                                "${CostLabels[cost]} ($count)",
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Provider grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(110.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.filteredProviders) { item ->
                                ProviderCard(
                                    item = item,
                                    onTogglePrio = { viewModel.togglePref(item.provider.source, item.status) },
                                    onToggleHide = { viewModel.toggleHide(item.provider.source, item.status) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    item: ProviderWithPref,
    onTogglePrio: () -> Unit,
    onToggleHide: () -> Unit
) {
    val isPrio = item.status == "prio"
    val isHidden = item.status == "hide"
    val borderColor = when {
        isPrio -> Color(0xFFFFD700) // gold
        isHidden -> Color(0xFFFF4444).copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isHidden) 0.5f else 1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = if (isPrio) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Provider logo
            AsyncImage(
                model = item.provider.logo,
                contentDescription = item.provider.source,
                modifier = Modifier
                    .size(56.dp),
                contentScale = ContentScale.Fit
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Provider name
            Text(
                text = item.provider.source.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prio button
                IconButton(
                    onClick = onTogglePrio,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Priority",
                        tint = if (isPrio) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Hide button
                IconButton(
                    onClick = onToggleHide,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hide",
                        tint = if (isHidden) Color(0xFFFF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
